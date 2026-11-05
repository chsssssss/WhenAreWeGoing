package com.github.chsssssss.eonje.data.firestore

import com.github.chsssssss.eonje.data.local.ThumbnailStore
import com.github.chsssssss.eonje.di.ApplicationScope
import com.github.chsssssss.eonje.domain.model.Place
import com.github.chsssssss.eonje.domain.model.ResolveStatus
import com.github.chsssssss.eonje.domain.model.SavedPost
import com.github.chsssssss.eonje.domain.repository.NotSignedInException
import com.github.chsssssss.eonje.domain.repository.PlaceRepository
import com.github.chsssssss.eonje.domain.repository.PlacesChangedNotifier
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
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
) : PlaceRepository {

    private val allPlaces: Flow<List<Place>> = auth.uid
        .flatMapLatest { uid ->
            if (uid == null) {
                flowOf(emptyList())
            } else {
                firestore.places(uid).orderBy(Fields.CREATED_AT, Query.Direction.DESCENDING).snapshots()
                    .map { snapshot -> snapshot.documents.map { it.toPlace() } }
            }
        }
        .catch { logListenerError("장소", it); emit(emptyList()) }
        .shareIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), replay = 1)

    // 확정된(RESOLVED) 게시물 한 묶음을 듣고 메모리에서 장소별로 나눈다 — 장소마다 질의하면 읽기 비용이 장소 수만큼 든다.
    // 장소에 연결된 게시물은 항상 RESOLVED다(확정할 때 연결하고 상태를 바꾼다).
    private val postsByPlace: Flow<Map<String, List<SavedPost>>> = auth.uid
        .flatMapLatest { uid ->
            if (uid == null) {
                flowOf(emptyMap())
            } else {
                firestore.posts(uid).whereEqualTo(Fields.STATUS, ResolveStatus.RESOLVED.name).snapshots().map { snapshot ->
                    val grouped = HashMap<String, MutableList<SavedPost>>()
                    snapshot.documents.forEach { doc ->
                        val post = doc.toSavedPost(thumbnailStore::existingUrl) ?: return@forEach
                        doc.placeIds().forEach { placeId -> grouped.getOrPut(placeId) { mutableListOf() } += post }
                    }
                    grouped.mapValues { (_, posts) -> posts.sortedByDescending { it.createdAt } }
                }
            }
        }
        .catch { logListenerError("장소별 게시물", it); emit(emptyMap()) }
        .shareIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), replay = 1)

    override fun observeAll(): Flow<List<Place>> = allPlaces

    override fun observePostsByPlace(): Flow<Map<String, List<SavedPost>>> = postsByPlace

    override suspend fun loadCachedAll(): List<Place> {
        val uid = auth.currentUid() ?: return emptyList()
        return try {
            firestore.places(uid).orderBy(Fields.CREATED_AT, Query.Direction.DESCENDING).get(Source.CACHE).await()
                .documents.map { it.toPlace() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun findByKakaoPlaceId(kakaoPlaceId: String): Place? {
        val uid = requireUid()
        return firestore.places(uid).whereEqualTo(Fields.KAKAO_PLACE_ID, kakaoPlaceId).limit(1)
            .getCacheFirst()?.documents?.firstOrNull()?.toPlace()
    }

    override suspend fun findById(id: String): Place? {
        val uid = requireUid()
        return firestore.places(uid).document(id).getCacheFirst()?.takeIf { it.exists() }?.toPlace()
    }

    override suspend fun save(place: Place): String {
        val existing = place.kakaoPlaceId?.let { findByKakaoPlaceId(it) }
        if (existing != null) return existing.id
        val uid = requireUid()
        firestore.places(uid).document(place.id).set(place.toFields()).logFailure("장소 저장")
        placesChangedNotifier.notifyPlacesChanged()
        return place.id
    }

    override suspend fun linkPostToPlace(postId: String, placeId: String) {
        val uid = requireUid()
        firestore.posts(uid).document(postId).update(Fields.PLACE_IDS, FieldValue.arrayUnion(placeId))
            .logFailure("게시물-장소 연결")
    }

    override suspend fun postIdsForPlace(placeId: String): List<String> {
        val uid = requireUid()
        return firestore.posts(uid).whereArrayContains(Fields.PLACE_IDS, placeId)
            .getCacheFirst()?.documents?.map { it.id }.orEmpty()
    }

    override suspend fun assignFolder(placeId: String, folderId: String?) {
        val uid = requireUid()
        firestore.places(uid).document(placeId).update(Fields.FOLDER_ID, folderId).logFailure("장소 폴더 배정")
        placesChangedNotifier.notifyPlacesChanged()
    }

    override suspend fun assignFolderToPlaces(placeIds: List<String>, folderId: String?) {
        if (placeIds.isEmpty()) return
        val uid = requireUid()
        firestore.commitInChunks(
            "장소 폴더 일괄 배정",
            placeIds.map { id -> { batch -> batch.update(firestore.places(uid).document(id), Fields.FOLDER_ID, folderId) } },
        )
        placesChangedNotifier.notifyPlacesChanged()
    }

    override suspend fun delete(placeId: String) {
        val uid = requireUid()
        // 이 장소를 가리키는 게시물의 placeIds에서 빼고, 장소 문서를 지운다. 게시물 자체는 남는다(기존 동작).
        val linkedPosts = firestore.posts(uid).whereArrayContains(Fields.PLACE_IDS, placeId)
            .getCacheFirst()?.documents.orEmpty()
        val ops = buildList<(com.google.firebase.firestore.WriteBatch) -> Unit> {
            linkedPosts.forEach { doc ->
                add { batch -> batch.update(doc.reference, Fields.PLACE_IDS, FieldValue.arrayRemove(placeId)) }
            }
            add { batch -> batch.delete(firestore.places(uid).document(placeId)) }
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
