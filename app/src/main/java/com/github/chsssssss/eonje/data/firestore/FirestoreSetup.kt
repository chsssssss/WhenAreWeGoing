package com.github.chsssssss.eonje.data.firestore

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
}
