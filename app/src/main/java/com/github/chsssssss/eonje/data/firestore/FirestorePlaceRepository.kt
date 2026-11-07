package com.github.chsssssss.eonje.data.firestore

import com.github.chsssssss.eonje.data.local.ThumbnailStore
import com.github.chsssssss.eonje.di.ApplicationScope
import com.github.chsssssss.eonje.domain.model.Place
import com.github.chsssssss.eonje.domain.model.SavedPost
import com.github.chsssssss.eonje.domain.repository.NotSignedInException
import com.github.chsssssss.eonje.domain.repository.PlaceRepository
import com.github.chsssssss.eonje.domain.repository.PlacesChangedNotifier
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 장소 저장소(Firestore). users/{uid}/places/{placeId}.
 * 게시물과의 연결은 게시물 문서의 placeIds 배열에 들어 있다(장소 문서엔 게시물 정보가 없다).
 * 쓰기는 서버 응답을 기다리지 않는다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class FirestorePlaceRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: AuthSession,
    private val thumbnailStore: ThumbnailStore,
    private val placesChangedNotifier: PlacesChangedNotifier,
    @ApplicationScope private val scope: CoroutineScope,
    private val sync: PlaceSync = PlaceSync(firestore, InMemoryPlaceSyncStore()),
) : PlaceRepository {

    // 화면은 로컬 캐시만 듣는다(과금 없음). 캐시는 PlaceSync가 채운다: 처음·오래됐을 때 전체 1회, 그다음엔 바뀐 장소만.
    // 그래서 앱을 열 때 읽기가 장소 수가 아니라 "그동안 바뀐 장소 수"(없으면 1)가 된다. 구독이 끊기면 둘 다 해제된다.
    private val allPlaces: Flow<List<Place>> = auth.uid
        .flatMapLatest { uid ->
            if (uid == null) {
                flowOf(emptyList())
            } else {
                channelFlow {
                    launch { sync.run(uid) }
                    firestore.places(uid).orderBy(Fields.CREATED_AT, Query.Direction.DESCENDING).cacheSnapshots()
                        .map { snapshot -> snapshot.documents.mapNotNull { it.toPlaceOrNull(thumbnailStore::existingUrl) } }
                        .collect { send(it) }
                }
            }
        }
        .catch { logListenerError("장소", it); emit(emptyList()) }
        .shareIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), replay = 1)

    override fun observeAll(): Flow<List<Place>> = allPlaces

    // 장소 하나의 게시물만 듣는다(상세 모드가 열려 있는 동안). 장소 전체의 게시물을 듣지 않는다 — 읽기 수가 라이브러리 크기에 비례해버린다.
    override fun observePostsForPlace(placeId: String): Flow<List<SavedPost>> = auth.uid
        .flatMapLatest { uid ->
            if (uid == null) {
                flowOf(emptyList())
            } else {
                firestore.posts(uid).whereArrayContains(Fields.PLACE_IDS, placeId).snapshots()
                    .map { snapshot ->
                        snapshot.documents.mapNotNull { it.toSavedPost(thumbnailStore::existingUrl) }.sortedByDescending { it.createdAt }
                    }
            }
        }
        .catch { logListenerError("장소의 게시물", it); emit(emptyList()) }

    override suspend fun loadCachedAll(): List<Place> {
        val uid = auth.currentUid() ?: return emptyList()
        return try {
            firestore.places(uid).orderBy(Fields.CREATED_AT, Query.Direction.DESCENDING).get(Source.CACHE).await()
                .documents.mapNotNull { it.toPlaceOrNull(thumbnailStore::existingUrl) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun findByKakaoPlaceId(kakaoPlaceId: String): Place? =
        findDocByKakaoPlaceId(requireUid(), kakaoPlaceId)?.toPlaceOrNull()

    // 묘비도 같이 돌려준다(저장할 때 되살리려고). 공개 조회는 위에서 묘비를 걸러 "없음"으로 취급한다.
    private suspend fun findDocByKakaoPlaceId(uid: String, kakaoPlaceId: String): DocumentSnapshot? =
        firestore.places(uid).whereEqualTo(Fields.KAKAO_PLACE_ID, kakaoPlaceId).limit(1)
            .getCacheFirst()?.documents?.firstOrNull()

    override suspend fun findById(id: String): Place? {
        val uid = requireUid()
        return firestore.places(uid).document(id).getCacheFirst()?.takeIf { it.exists() }?.toPlaceOrNull()
    }

    override suspend fun save(place: Place): String {
        val uid = requireUid()
        val existing = place.kakaoPlaceId?.let { findDocByKakaoPlaceId(uid, it) }
        if (existing != null && !existing.isTombstone()) return existing.id
        // 삭제했던 장소(묘비)를 다시 저장하면 새 문서를 만들지 않고 그 문서를 되살린다 — 같은 kakaoPlaceId 문서가 둘이 되지 않게.
        // 문서 전체를 쓰므로 deleted·expireAt은 사라지고 updatedAt이 새로 찍힌다.
        val id = existing?.id ?: place.id
        firestore.places(uid).document(id).set(place.copy(id = id).toFields()).logFailure("장소 저장")
        placesChangedNotifier.notifyPlacesChanged()
        return id
    }

    override suspend fun linkPostToPlace(postId: String, placeId: String) {
        val uid = requireUid()
        // 게시물의 placeIds와 장소의 postIds(역방향)를 한 배치로 쓴다 — 홈이 게시물 문서를 안 읽고도 개수·썸네일을 알려면 둘이 같이 가야 한다.
        firestore.commitInChunks(
            "게시물-장소 연결",
            listOf(
                { batch -> batch.update(firestore.posts(uid).document(postId), Fields.PLACE_IDS, FieldValue.arrayUnion(placeId)) },
                { batch -> batch.update(firestore.places(uid).document(placeId), placeUpdate(Fields.POST_IDS to FieldValue.arrayUnion(postId))) },
            ),
        )
        placesChangedNotifier.notifyPlacesChanged()
    }

    override suspend fun postIdsForPlace(placeId: String): List<String> {
        val uid = requireUid()
        return firestore.posts(uid).whereArrayContains(Fields.PLACE_IDS, placeId)
            .getCacheFirst()?.documents?.map { it.id }.orEmpty()
    }

    override suspend fun assignFolder(placeId: String, folderId: String?) {
        val uid = requireUid()
        firestore.places(uid).document(placeId).update(placeUpdate(Fields.FOLDER_ID to folderId)).logFailure("장소 폴더 배정")
        placesChangedNotifier.notifyPlacesChanged()
    }

    override suspend fun assignFolderToPlaces(placeIds: List<String>, folderId: String?) {
        if (placeIds.isEmpty()) return
        val uid = requireUid()
        firestore.commitInChunks(
            "장소 폴더 일괄 배정",
            placeIds.map { id -> { batch -> batch.update(firestore.places(uid).document(id), placeUpdate(Fields.FOLDER_ID to folderId)) } },
        )
        placesChangedNotifier.notifyPlacesChanged()
    }

    override suspend fun delete(placeId: String) {
        val uid = requireUid()
        // 이 장소를 가리키는 게시물의 placeIds에서 빼고, 장소 문서는 지우지 않고 묘비로 바꾼다(다른 기기에 삭제를 알리려고 — 묘비는
        // 90일 뒤 TTL이 지운다). 게시물 자체는 남는다(기존 동작).
        val existing = firestore.places(uid).document(placeId).getCacheFirst()?.takeIf { it.exists() }
        if (existing != null && existing.isTombstone()) return
        val linkedPosts = firestore.posts(uid).whereArrayContains(Fields.PLACE_IDS, placeId)
            .getCacheFirst()?.documents.orEmpty()
        val ops = buildList<(com.google.firebase.firestore.WriteBatch) -> Unit> {
            linkedPosts.forEach { doc ->
                add { batch -> batch.update(doc.reference, Fields.PLACE_IDS, FieldValue.arrayRemove(placeId)) }
            }
            add { batch ->
                batch.set(
                    firestore.places(uid).document(placeId),
                    tombstoneFields(existing?.getString(Fields.KAKAO_PLACE_ID), System.currentTimeMillis()),
                )
            }
        }
        firestore.commitInChunks("장소 삭제", ops)
        placesChangedNotifier.notifyPlacesChanged()
    }

    private suspend fun requireUid(): String =
        auth.awaitUid(UID_WAIT_TIMEOUT_MILLIS) ?: throw NotSignedInException()

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val UID_WAIT_TIMEOUT_MILLIS = 2_000L
    }
}
