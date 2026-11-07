package com.github.chsssssss.eonje.data.firestore

import com.github.chsssssss.eonje.data.account.FirestoreCacheClearFlag
import com.github.chsssssss.eonje.data.remote.WHEREWEGOING_FIREBASE_APP_NAME
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings

/**
 * Firestore·Auth는 반드시 named FirebaseApp "wherewegoing"(Blaze)에 붙인다. `Firebase.firestore`·`Firebase.auth` 같은
 * KTX 단축형은 default 앱(Gemini 전용, Spark)에 붙으므로 쓰지 않는다.
 */
internal fun wherewegoingApp(): FirebaseApp = try {
    FirebaseApp.getInstance(WHEREWEGOING_FIREBASE_APP_NAME)
} catch (e: IllegalStateException) {
    throw IllegalStateException("FirebaseApp \"$WHEREWEGOING_FIREBASE_APP_NAME\"이 초기화되지 않았다 — local.properties의 FIREBASE_* 값을 확인하라", e)
}

object FirestoreSetup {
    /**
     * 영구 캐시 상한. 장소·게시물 문서는 하나에 0.5~5KB라 수천 건이 쌓여도 몇 MB다 — 20MB면 정리(GC)로 지워질 일이 거의 없다.
     * (위젯·백그라운드 작업이 캐시에서만 읽기 때문에, 캐시가 지워지면 그 화면이 비는 점을 고려한 값)
     */
    const val CACHE_SIZE_BYTES = 20L * 1024 * 1024

    /**
     * 첫 사용 **전에** 한 번만 불러야 한다(EonjeApplication 초기화). 순서가 중요하다:
     * 캐시 설정 → (디버그, 옵션) 에뮬레이터 연결. useEmulator가 설정을 덮어쓰므로 그 뒤에 설정을 지정하면 안 된다.
     */
    fun configure(app: FirebaseApp) {
        val firestore = FirebaseFirestore.getInstance(app)
        firestore.firestoreSettings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(PersistentCacheSettings.newBuilder().setSizeBytes(CACHE_SIZE_BYTES).build())
            .build()
        connectFirebaseEmulators(FirebaseAuth.getInstance(app), firestore)
    }

    /**
     * 앱 시작(EonjeApplication)이 부르는 초기화: **설정 → 캐시 정리(요청됐으면)** 순서. Firestore는 첫 사용 전에만 설정을 바꾸고 캐시를
     * 지울 수 있어서 이 둘이 어떤 저장소보다 먼저 와야 한다. [configure]는 테스트에서 에뮬레이터용 설정으로 바꿔 끼울 수 있다.
     */
    fun initialize(app: FirebaseApp, context: android.content.Context, configure: (FirebaseApp) -> Unit = ::configure) {
        configure(app)
        clearCacheIfRequested(app, context)
    }

    /**
     * 로그아웃·계정 삭제 뒤 "다음 콜드 스타트에 캐시를 지운다"고 표시해 둔 경우 지운다. Firestore는 첫 사용 **전에만** 캐시를 지울 수 있어서
     * 실행 중엔 못 한다 — [configure] 직후, 어떤 저장소도 만들어지기 전에 부른다. 지우기에 성공해야 표시를 없앤다(실패하면 다음에 다시).
     */
    fun clearCacheIfRequested(app: FirebaseApp, context: android.content.Context) {
        if (!FirestoreCacheClearFlag.isRequested(context)) return
        FirebaseFirestore.getInstance(app).clearPersistence()
            .addOnSuccessListener { FirestoreCacheClearFlag.clear(context) }
            .addOnFailureListener { android.util.Log.w("EonjeFirestore", "캐시 정리 실패(${it.javaClass.simpleName}) — 다음 실행에 다시 시도") }
    }
}
