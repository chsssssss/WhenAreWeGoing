package com.github.chsssssss.eonje.data.firestore

import com.github.chsssssss.eonje.data.local.ThumbnailStore
import com.github.chsssssss.eonje.di.ApplicationScope
import com.github.chsssssss.eonje.domain.model.ResolveStatus
import com.github.chsssssss.eonje.domain.model.SavedPost
import com.github.chsssssss.eonje.domain.model.UnresolvedReason
import com.github.chsssssss.eonje.domain.repository.NotSignedInException
import com.github.chsssssss.eonje.domain.repository.SavedPostRepository
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
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
 * 게시물 저장소(Firestore). users/{uid}/posts/{postId}.
 *
 * - 쓰기는 서버 응답을 기다리지 않는다(오프라인에서도 막히지 않음). 화면은 리스너 스냅샷으로 갱신된다.
 * - thumbnailUrl은 문서에 없다. 읽을 때 이 기기에 {postId}.jpg가 있으면 file:// 경로로 채운다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class FirestoreSavedPostRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: AuthSession,
    private val thumbnailStore: ThumbnailStore,
    @ApplicationScope private val scope: CoroutineScope,
) : SavedPostRepository {

    // 배지·인박스·알림이 같은 리스너 하나를 나눠 쓴다. 보는 화면이 없으면 5초 뒤 해제된다.
    private val unresolved: Flow<List<SavedPost>> = auth.uid
        .flatMapLatest { uid ->
            if (uid == null) flowOf(emptyList()) else unresolvedQuery(uid).snapshots().map { it.toPostsNewestFirst() }
        }
        .catch { logListenerError("미확정 게시물", it); emit(emptyList()) }
        .shareIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), replay = 1)

    /** status != RESOLVED. 정렬은 클라이언트에서 한다(서버 정렬을 쓰려면 복합 인덱스가 필요하고, 미확정 게시물은 적다). */
    private fun unresolvedQuery(uid: String): Query =
        firestore.posts(uid).whereNotEqualTo(Fields.STATUS, ResolveStatus.RESOLVED.name)

    override fun observeUnresolved(): Flow<List<SavedPost>> = unresolved

    override fun observeAll(): Flow<List<SavedPost>> = auth.uid.flatMapLatest { uid ->
        if (uid == null) flowOf(emptyList()) else firestore.posts(uid).snapshots().map { it.toPostsNewestFirst() }
    }.catch { logListenerError("게시물 전체", it); emit(emptyList()) }

    /**
     * 공유 수신(메인 스레드)에서 부르므로 서버를 기다리지 않고 **이 기기의 로컬 데이터만** 본다.
     * 다른 기기에서 먼저 저장한 같은 게시물은 못 찾을 수 있다(동기화가 끝나기 전엔 중복이 생길 수 있음).
     */
    override suspend fun findByShortcode(shortcode: String): SavedPost? {
        val uid = auth.currentUid() ?: return null
        val snapshot = try {
            firestore.posts(uid).whereEqualTo(Fields.SHORTCODE, shortcode).limit(1).get(Source.CACHE).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return null
        }
        return snapshot.documents.firstNotNullOfOrNull { it.toSavedPost(thumbnailStore::existingUrl) }
    }

    override suspend fun findById(id: String): SavedPost? {
        val uid = requireUid()
        return firestore.posts(uid).document(id).getCacheFirst()?.takeIf { it.exists() }
            ?.toSavedPost(thumbnailStore::existingUrl)
    }

    override suspend fun findByIds(ids: List<String>): List<SavedPost> {
        if (ids.isEmpty()) return emptyList()
        val uid = requireUid()
        val found = ids.chunked(WHERE_IN_LIMIT).flatMap { chunk ->
            val query = firestore.posts(uid).whereIn(FieldPath.documentId(), chunk)
            query.getCacheFirst()?.toPosts().orEmpty()
        }
        return found.sortedByDescending { it.createdAt }
    }

    override suspend fun save(post: SavedPost) {
        val uid = requireUid()
        val ref = firestore.posts(uid).document(post.id)
        // placeIds·candidates는 건드리지 않는다(같은 게시물을 다시 저장해도 연결 정보가 지워지지 않게 mergeFields).
        ref.set(post.toFields(), SetOptions.mergeFields(POST_DOMAIN_FIELDS)).logFailure("게시물 저장")
        // 서버 응답은 기다리지 않는다. 대신 같은 큐에서 로컬 캐시를 읽어서, 방금 쓴 게 로컬에 반영된 뒤에 돌아온다 —
        // 공유 수신이 finish() 직후 프로세스가 정리돼도 저장이 로컬에 남도록.
        // 주의: DocumentReference.get(Source.CACHE)는 결과가 메인 스레드를 거쳐서, 공유 수신처럼 메인 스레드를 runBlocking으로
        // 막고 있으면 서로 기다리다 ANR이 난다(실기기 검증에서 재현). 쿼리의 캐시 읽기는 메인 스레드를 거치지 않아서 이걸 쓴다.
        try {
            firestore.posts(uid).whereEqualTo(FieldPath.documentId(), post.id).limit(1).get(Source.CACHE).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 반영 확인이 안 돼도 쓰기는 이미 요청됐다.
        }
    }

    override suspend fun updateStatus(id: String, status: ResolveStatus, unresolvedReason: UnresolvedReason?) {
        val uid = requireUid()
        val updates = mutableMapOf<String, Any?>(
            Fields.STATUS to status.name,
            Fields.UNRESOLVED_REASON to unresolvedReason?.name,
        )
        // UNRESOLVED로 넘어갈 땐 extractedCount를 0으로 되돌린다 — 안 그러면 실제로는 후보가 하나도 없는데
        // 인박스에 "후보 1곳" 칩이 남는다(Room의 CASE 로직과 같은 규칙을 같은 쓰기 안에서 처리).
        if (status == ResolveStatus.UNRESOLVED) updates[Fields.EXTRACTED_COUNT] = 0
        firestore.posts(uid).document(id).update(updates).logFailure("게시물 상태 변경")
    }

    // thumbnailUrl은 문서에 저장하지 않는다(기기마다 다른 file:// 경로). 파일이 만들어졌는지는 읽을 때 확인한다.
    override suspend fun updateExtraction(id: String, caption: String?, thumbnailUrl: String?, extractedCount: Int) {
        val uid = requireUid()
        firestore.posts(uid).document(id)
            .update(mapOf(Fields.CAPTION to caption, Fields.EXTRACTED_COUNT to extractedCount))
            .logFailure("게시물 인식 결과 저장")
    }

    override suspend fun deleteByIds(ids: List<String>) {
        if (ids.isEmpty()) return
        val uid = requireUid()
        firestore.commitInChunks("게시물 삭제", ids.map { id -> { batch -> batch.delete(firestore.posts(uid).document(id)) } })
        // 기기에 받아둔 썸네일 파일도 같이 지운다(기존 동작 그대로).
        thumbnailStore.deleteAll(ids)
    }

    // 썸네일 경로는 문서에 없으므로 지울 값이 없다(깨진 파일은 정리 쪽에서 이미 삭제됨 → 읽을 때 자연히 null).
    override suspend fun clearLocalThumbnail(id: String) = Unit

    override suspend fun cachedPostIds(): Set<String> {
        val uid = auth.currentUid() ?: return emptySet()
        return readFromCache(firestore.posts(uid))?.documents?.mapTo(HashSet()) { it.id }.orEmpty()
    }

    override suspend fun loadCachedUnresolved(): List<SavedPost> {
        val uid = auth.currentUid() ?: return emptyList()
        return readFromCache(unresolvedQuery(uid))?.toPostsNewestFirst().orEmpty()
    }

    private suspend fun readFromCache(query: Query): QuerySnapshot? = try {
        query.get(Source.CACHE).await()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private suspend fun requireUid(): String =
        auth.awaitUid(UID_WAIT_TIMEOUT_MILLIS) ?: throw NotSignedInException()

    private fun QuerySnapshot.toPosts(): List<SavedPost> =
        documents.mapNotNull { it.toSavedPost(thumbnailStore::existingUrl) }

    private fun QuerySnapshot.toPostsNewestFirst(): List<SavedPost> = toPosts().sortedByDescending { it.createdAt }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val UID_WAIT_TIMEOUT_MILLIS = 2_000L

        /** whereIn은 값을 30개까지만 받는다. */
        const val WHERE_IN_LIMIT = 30
    }
}
