package com.github.chsssssss.eonje.data.account

import android.util.Log
import com.github.chsssssss.eonje.data.account.merge.AccountMergeIo
import com.github.chsssssss.eonje.data.account.merge.AccountMergeJournal
import com.github.chsssssss.eonje.data.account.merge.AccountMergePlanner
import com.github.chsssssss.eonje.data.account.merge.JournalData
import com.github.chsssssss.eonje.data.account.merge.MergeStage
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
import com.github.chsssssss.eonje.domain.repository.MergeCounts
import com.github.chsssssss.eonje.domain.repository.MergePreview
import com.github.chsssssss.eonje.domain.repository.SwitchResult
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
    private val mergeJournal: AccountMergeJournal,
    private val mergeIo: AccountMergeIo,
    private val deletionHooks: AccountDeletionHooks,
    private val pendingCleanup: PendingLocalCleanup,
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

    // ── 충돌: 이미 다른 uid에 연결된 계정으로 전환 + 이 기기의 데이터 합치기 ──

    override suspend fun prepareCollisionMerge(): MergePreview? {
        val user = auth.currentUser ?: return null
        if (!user.isAnonymous) return null
        return try {
            val snapshot = mergeIo.readSnapshot(user.uid)
            if (snapshot.isEmpty) {
                mergeJournal.delete()
                MergePreview(0, 0, 0)
            } else {
                // 전환하면 익명 uid의 데이터는 읽을 수 없다 — 지금 읽어서 기기에 남긴다. 사용자가 정하기 전(SNAPSHOT)에 앱이 죽으면
                // 다음 시작에서 이 저널은 버려진다(익명 상태 그대로라 연결을 다시 하면 된다).
                mergeJournal.write(JournalData(user.uid, MergeStage.SNAPSHOT, snapshot))
                MergePreview(snapshot.folders.size, snapshot.places.size, snapshot.posts.size)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "합칠 데이터 읽기 실패(${e.javaClass.simpleName})")
            null
        }
    }

    override suspend fun switchToExistingAccount(merge: Boolean): SwitchResult {
        val credential = pendingCollisionCredential ?: return SwitchResult.Failed
        val journal = if (merge) mergeJournal.read() else null
        // **새 uid가 생기기 전에** Room 이전을 영구 종료한다. 안 하면 전환한 계정에 Room 원본이 올라가 버린다.
        migrationStore.markClosed()
        try {
            if (journal != null) mergeJournal.write(journal.copy(stage = MergeStage.SWITCHING)) else mergeJournal.delete()
            auth.signInWithCredential(credential).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseNetworkException) {
            return SwitchResult.NetworkError
        } catch (e: Exception) {
            Log.w(TAG, "계정 전환 실패(${e.javaClass.simpleName})")
            return SwitchResult.Failed
        }
        pendingCollisionCredential = null
        _state.value = currentState()
        if (journal == null) {
            mergeJournal.delete()
            cleanup.scheduleThumbnailMaintenance()
            return SwitchResult.Switched(merged = null)
        }
        val uid = auth.currentUser?.uid ?: return SwitchResult.SwitchedMergePending
        return try {
            SwitchResult.Switched(merged = runMerge(uid, journal.copy(stage = MergeStage.SWITCHING)))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 저널이 남아 있다 — 다음 시작에 이어서 합친다.
            Log.w(TAG, "합치기 중단(${e.javaClass.simpleName}) — 다음에 이어서 합친다")
            SwitchResult.SwitchedMergePending
        }
    }

    override suspend fun cancelCollisionMerge() {
        pendingCollisionCredential = null
        mergeJournal.delete()
    }

    override suspend fun resumePendingMerge(): MergeCounts? {
        val data = mergeJournal.read() ?: return null
        val uid = auth.currentUser?.uid
        return try {
            when (data.stage) {
                // 사용자가 정하기 전이거나, 합치기를 골랐지만 전환이 끝나지 않았다(uid가 그대로) — 익명 상태 그대로이니 저널을 버린다.
                MergeStage.SNAPSHOT -> { mergeJournal.delete(); null }
                MergeStage.SWITCHING ->
                    if (uid == null || uid == data.fromUid) { mergeJournal.delete(); null } else runMerge(uid, data)
                // 계획이 있다. 같은 계정일 때만 이어서 쓴다(그사이 다른 계정으로 바뀌었으면 버린다).
                MergeStage.MERGING -> if (uid != null && uid == data.targetUid) runMerge(uid, data) else { mergeJournal.delete(); null }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "끊긴 합치기 재개 실패(${e.javaClass.simpleName})")
            null
        }
    }

    /** 계획을 계산(없으면)해 저널에 저장하고, 쓰고, 썸네일을 옮기고, 저널을 지운다. 어디서 끊겨도 같은 계획으로 다시 하면 된다(멱등). */
    private suspend fun runMerge(uid: String, data: JournalData): MergeCounts {
        val plan = data.plan ?: AccountMergePlanner.plan(data.snapshot, mergeIo.readTarget(uid, data.snapshot)).also { computed ->
            mergeJournal.write(data.copy(stage = MergeStage.MERGING, targetUid = uid, plan = computed))
        }
        mergeIo.execute(uid, plan)
        mergeIo.adoptThumbnails(plan.thumbnailAdoptions)
        mergeJournal.delete()
        cleanup.scheduleThumbnailMaintenance()
        return MergeCounts(plan.folderWrites.size, plan.placeWrites.size, plan.postWrites.size)
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
        // pause()도 try 안에서 부른다: pause()가 게이트를 켠 뒤 예외를 던져도 finally가 반드시 resume()을 불러서 게이트가 풀린다.
        // (밖에서 부르면 그 예외로 finally에 못 들어가 게이트가 프로세스가 끝날 때까지 켜진 채 남는다.)
        try {
            workControl.pause()
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
                    deletionHooks.afterDeletePass(uid, pass)
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
            // 로컬 정리를 시작하기 전에 "정리 대기" 표시를 남긴다(그 시점의 썸네일 이름 목록 포함). 정리가 실패하거나 앱이 죽으면 표시가
            // 남아서 다음 시작에 이어서 정리한다. 표시를 못 남겨도 아래 정리는 그대로 한다.
            val pending = pendingCleanup.begin(uid)

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
            // 서버 데이터와 계정은 이미 지워졌다 — 로컬 정리가 실패해도 삭제 실패로 보고하지 않는다. 표시가 남아 다음 시작에 이어서 정리한다.
            // 새 익명 계정은 아래에서 로그인을 시작하기 전이고(게이트도 켜져 있다) 정리는 표시에 담긴 파일 이름만 지운다.
            pendingCleanup.finish(pending)
            _state.value = currentState()
            authSession.startAnonymousSignIn()
            return DeleteResult.Success
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 위에서 단계별로 처리하지 못한 예외(예: pause 실패)도 호출자(화면)를 죽이지 않고 "데이터 단계에서 멈춤"으로 알린다.
            Log.w(TAG, "계정 삭제 중 예상 못 한 오류(${e.javaClass.simpleName})")
            return DeleteResult.Failed(DeleteStep.DATA)
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
