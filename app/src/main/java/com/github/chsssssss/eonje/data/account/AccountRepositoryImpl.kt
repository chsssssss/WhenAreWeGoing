package com.github.chsssssss.eonje.data.account

import android.util.Log
import com.github.chsssssss.eonje.data.firestore.AuthSession
import com.github.chsssssss.eonje.data.firestore.commitInChunksAndAwait
import com.github.chsssssss.eonje.data.firestore.folders
import com.github.chsssssss.eonje.data.firestore.places
import com.github.chsssssss.eonje.data.firestore.posts
import com.github.chsssssss.eonje.data.migration.RoomMigrationStateStore
import com.github.chsssssss.eonje.di.ApplicationScope
import com.github.chsssssss.eonje.domain.repository.AccountRepository
import com.github.chsssssss.eonje.domain.repository.AccountState
import com.github.chsssssss.eonje.domain.repository.DeleteResult
import com.github.chsssssss.eonje.domain.repository.DeleteStep
import com.github.chsssssss.eonje.domain.repository.LinkResult
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 계정 연결·로그아웃·삭제(named 앱 wherewegoing의 Auth·Firestore). 이메일·토큰은 로그에 남기지 않는다 — 작업 이름과 오류 종류만.
 *
 * **Room 이전 영구 종료 플래그**([RoomMigrationStateStore.markClosed])는 새 uid가 생기기 **전에** 켠다: 로그아웃, 계정 삭제, 다른
 * 계정으로 전환. 안 켜면 새 익명 uid에서 Room 원본이 다시 올라가 지운 데이터가 되살아난다.
 */
@Singleton
class AccountRepositoryImpl @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val migrationStore: RoomMigrationStateStore,
    private val cleanup: AccountLocalCleanup,
    private val workControl: AccountWorkControl,
    private val authSession: AuthSession,
    @ApplicationScope scope: CoroutineScope,
) : AccountRepository {

    private val _state = MutableStateFlow(currentState())
    override val state: StateFlow<AccountState> = _state.asStateFlow()

    /** 충돌(이미 다른 uid에 연결된 계정)이 났을 때 그 계정으로 로그인하려고 들고 있는 자격 증명. 합치기 흐름(다음 단계)이 쓴다. */
    @Volatile
    internal var pendingCollisionCredential: AuthCredential? = null

    init {
        // 로그인·로그아웃·전환은 리스너로 따라간다. 연결(link)은 uid가 안 바뀌어서 리스너가 안 불리므로 연결 뒤에 직접 갱신한다.
        auth.addAuthStateListener { _state.value = currentState() }
    }

    private fun currentState(): AccountState {
        val user = auth.currentUser ?: return AccountState()
        return AccountState(
            signedIn = true,
            isAnonymous = user.isAnonymous,
            email = user.email ?: user.providerData.firstNotNullOfOrNull { it.email },
        )
    }

    override suspend fun linkWithGoogle(idToken: String): LinkResult {
        val user = auth.currentUser ?: return LinkResult.Failed
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        return try {
            user.linkWithCredential(credential).await()
            _state.value = currentState()
            LinkResult.Success
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseAuthUserCollisionException) {
            // 같은 계정으로 로그인할 수 있는 자격 증명. SDK가 준 것이 있으면 그걸, 없으면 방금 만든 걸 쓴다.
            pendingCollisionCredential = e.updatedCredential ?: credential
            LinkResult.Collision
        } catch (e: FirebaseNetworkException) {
            LinkResult.NetworkError
        } catch (e: Exception) {
            Log.w(TAG, "계정 연결 실패(${e.javaClass.simpleName})")
            LinkResult.Failed
        }
    }

    override suspend fun signOut() {
        val user = auth.currentUser ?: return
        if (user.isAnonymous) return
        // 새 익명 uid가 생기기 전에 Room 이전을 영구 종료한다. Room 데이터는 지우지 않는다(사용자가 지우라고 한 게 아니다).
        migrationStore.markClosed()
        auth.signOut()
        _state.value = currentState()
        cleanup.clearDeviceCaches()
        cleanup.requestFirestoreCacheClear()
        authSession.startAnonymousSignIn()
    }

    override suspend fun deleteAccount(googleIdToken: String?): DeleteResult {
        val user = auth.currentUser ?: return DeleteResult.Failed(DeleteStep.DATA)
        val uid = user.uid
        val anonymous = user.isAnonymous
        workControl.pause()
        try {
            // 1) 연결된 계정은 먼저 재인증한다(최근 로그인이 아니면 계정 삭제가 거부된다).
            if (!anonymous) {
                if (googleIdToken == null) return DeleteResult.Failed(DeleteStep.REAUTH, requiresReauth = true)
                try {
                    user.reauthenticate(GoogleAuthProvider.getCredential(googleIdToken, null)).await()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "재인증 실패(${e.javaClass.simpleName})")
                    return DeleteResult.Failed(DeleteStep.REAUTH, requiresReauth = true)
                }
            }

            // 2) 서버 데이터 삭제(서버 확정까지 기다린다). 끝난 뒤 한 번 더 읽어서, 삭제 중에 들어온 문서가 있으면 지운다.
            try {
                // 지우고 다시 읽어서 0건이 나올 때까지 반복한다 — 삭제 중에 들어온 문서(공유 저장 등)가 있으면 다음 회차에 지운다.
                var clean = false
                for (pass in 1..MAX_DELETE_PASSES) {
                    if (deleteAllUserData(uid) == 0) { clean = true; break }
                }
                check(clean) { "삭제 뒤에도 문서가 남았다" }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "데이터 삭제 실패(${e.javaClass.simpleName})")
                return DeleteResult.Failed(DeleteStep.DATA)
            }

            // 3) 새 uid가 생기기 전에 Room 이전을 영구 종료한다.
            migrationStore.markClosed()

            // 4) 계정 삭제. 익명 계정이 삭제를 거부하면(오래된 로그인 등) 로그아웃만 한다 — 데이터는 이미 없어서 남는 건 빈 익명 계정뿐이다.
            try {
                user.delete().await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (anonymous) {
                    Log.w(TAG, "익명 계정 삭제 실패(${e.javaClass.simpleName}) — 로그아웃으로 대신한다")
                    auth.signOut()
                } else {
                    Log.w(TAG, "계정 삭제 실패(${e.javaClass.simpleName})")
                    return DeleteResult.Failed(DeleteStep.ACCOUNT, requiresReauth = e is FirebaseAuthRecentLoginRequiredException)
                }
            }

            // 5) 로컬 정리. 사용자가 직접 요청한 삭제라서 Room의 옛 데이터도 비운다.
            cleanup.clearDeviceCaches()
            cleanup.clearLegacyRoom()
            cleanup.requestFirestoreCacheClear()
            _state.value = currentState()
            authSession.startAnonymousSignIn()
            return DeleteResult.Success
        } finally {
            workControl.resume()
        }
    }

    private fun collections(uid: String): List<CollectionReference> = listOf(
        firestore.places(uid), firestore.posts(uid), firestore.folders(uid),
    )

    /** 세 컬렉션의 문서를 서버에서 읽어 전부 지운다(서버 확정까지 기다린다). 지운 문서 수를 돌려준다. */
    private suspend fun deleteAllUserData(uid: String): Int {
        var deleted = 0
        collections(uid).forEach { collection ->
            val docs = collection.get(Source.SERVER).await().documents
            if (docs.isNotEmpty()) {
                firestore.commitInChunksAndAwait("계정 데이터 삭제", docs.map { doc -> { batch -> batch.delete(doc.reference) } })
                deleted += docs.size
            }
        }
        return deleted
    }

    private companion object {
        const val TAG = "EonjeAccount"
        const val MAX_DELETE_PASSES = 3
    }
}
