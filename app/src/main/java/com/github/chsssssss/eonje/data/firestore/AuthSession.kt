package com.github.chsssssss.eonje.data.firestore

import android.util.Log
import com.github.chsssssss.eonje.di.ApplicationScope
import com.github.chsssssss.eonje.domain.repository.UserSession
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/**
 * 익명 로그인 세션. 사용자 데이터는 전부 users/{uid} 아래라서 uid가 준비돼야 Firestore를 쓸 수 있다.
 *
 * 로그인은 앱 시작 시 [startAnonymousSignIn]으로 시작한다. 설치 직후 오프라인이면 실패하므로 백오프로 계속 다시 시도한다.
 * 한 번 로그인하면 SDK가 로컬에 저장해서 이후엔 오프라인이어도 uid가 바로 있다.
 */
@Singleton
class AuthSession @Inject constructor(
    private val auth: FirebaseAuth,
    @ApplicationScope private val scope: CoroutineScope,
) : UserSession {
    // 앱 시작 루프와 SaveSharedPostWorker가 동시에 로그인하면 익명 사용자가 둘 생길 수 있어서 한 번에 하나만 시도한다.
    private val signInMutex = Mutex()

    /** 지금 로그인된 uid. 로그인 전이면 null. */
    override val uid: StateFlow<String?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.uid) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }.stateIn(scope, SharingStarted.Eagerly, auth.currentUser?.uid)

    /** 기다리지 않고 지금 값만 본다(백그라운드 작업·메인 스레드에서 쓰는 읽기용). */
    override fun currentUid(): String? = auth.currentUser?.uid

    /** uid가 생길 때까지 기다린다. 제한 시간 안에 안 생기면 null. */
    suspend fun awaitUid(timeoutMillis: Long): String? =
        currentUid() ?: withTimeoutOrNull(timeoutMillis) { uid.filterNotNull().first() }

    override fun isSignedIn(): Boolean = auth.currentUser != null

    override suspend fun signInIfNeeded(): Boolean {
        if (auth.currentUser != null) return true
        return signInMutex.withLock {
            if (auth.currentUser != null) return@withLock true
            try {
                withTimeout(SIGN_IN_TIMEOUT_MILLIS) { auth.signInAnonymously().await() }
                true
            } catch (e: TimeoutCancellationException) {
                Log.w(TAG, "익명 로그인 시간 초과")
                false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "익명 로그인 실패(${e.javaClass.simpleName})")
                false
            }
        }
    }

    fun startAnonymousSignIn() {
        if (auth.currentUser != null) return
        scope.launch {
            var backoff = INITIAL_BACKOFF_MILLIS
            while (!signInIfNeeded()) {
                delay(backoff)
                backoff = min(backoff * 2, MAX_BACKOFF_MILLIS)
            }
        }
    }

    private companion object {
        const val TAG = "EonjeAuth"
        const val INITIAL_BACKOFF_MILLIS = 2_000L
        const val MAX_BACKOFF_MILLIS = 60_000L
        const val SIGN_IN_TIMEOUT_MILLIS = 30_000L
    }
}
