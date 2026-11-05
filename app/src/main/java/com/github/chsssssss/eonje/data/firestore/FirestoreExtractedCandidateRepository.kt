package com.github.chsssssss.eonje.data.firestore

import com.github.chsssssss.eonje.domain.model.ExtractedCandidate
import com.github.chsssssss.eonje.domain.repository.ExtractedCandidateRepository
import com.github.chsssssss.eonje.domain.repository.NotSignedInException
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 검색 후보 저장소(Firestore). 후보는 별도 컬렉션이 아니라 **게시물 문서의 candidates 배열**에 들어 있다.
 * 정리하면 사라지는 임시 데이터이고, 항목당 최대 5개라 문서 크기(1MiB 한도)에 한참 못 미친다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class FirestoreExtractedCandidateRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: AuthSession,
) : ExtractedCandidateRepository {

    override fun observeForPost(postId: String): Flow<List<ExtractedCandidate>> = auth.uid
        .flatMapLatest { uid ->
            if (uid == null) {
                flowOf(emptyList())
            } else {
                firestore.posts(uid).document(postId).snapshots().map { if (it.exists()) it.toCandidates() else emptyList() }
            }
        }
        .catch { logListenerError("후보", it); emit(emptyList()) }

    override suspend fun replaceForPost(postId: String, candidates: List<ExtractedCandidate>) {
        val uid = auth.awaitUid(UID_WAIT_TIMEOUT_MILLIS) ?: throw NotSignedInException()
        // 배열 전체를 교체한다(이전 후보는 사라짐). 빈 목록이면 비우기만 한다. 게시물 문서가 없으면 update가 실패한다(로그만 남김).
        firestore.posts(uid).document(postId).update(Fields.CANDIDATES, candidates.map { it.toMap() })
            .logFailure("후보 저장")
    }

    private companion object {
        const val UID_WAIT_TIMEOUT_MILLIS = 2_000L
    }
}
