package com.github.chsssssss.eonje.data.firestore

import androidx.test.platform.app.InstrumentationRegistry
import com.github.chsssssss.eonje.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.firestore.MemoryLruGcSettings
import java.net.InetSocketAddress
import java.net.Socket

/**
 * 계측 테스트가 같이 쓰는 FirebaseApp·Firestore·Auth(Emulator Suite 연결). 프로세스에서 한 번만 만든다 — 같은 이름의 앱을
 * 두 번 만들 수 없고, Auth SDK가 전역 상태를 첫 앱에 묶기 때문이다. 테스트 사이의 격리는 테스트마다 새 익명 사용자(uid)로 한다.
 *
 * 프로젝트 ID는 앱과 같은 값이다(local.properties의 FIREBASE_PROJECT_ID, 없으면 demo-eonje). 에뮬레이터도 같은 ID로 띄워서
 * (`scripts/start-emulators.sh`) Auth와 Firestore가 한 프로젝트로 모인다.
 */
internal object FirebaseEmulatorEnv {
    const val HOST = "10.0.2.2"
    const val FIRESTORE_PORT = 8080
    const val AUTH_PORT = 9099
    val PROJECT: String = BuildConfig.FIREBASE_PROJECT_ID.ifBlank { "demo-eonje" }

    @Volatile
    var isStarted = false
        private set

    val app: FirebaseApp by lazy {
        isStarted = true
        FirebaseApp.initializeApp(
            InstrumentationRegistry.getInstrumentation().targetContext.applicationContext,
            FirebaseOptions.Builder().setProjectId(PROJECT).setApplicationId("1:1234567890:android:abcdef0123456789").setApiKey("fake-api-key").build(),
            "firestore-emulator-test",
        )
    }

    val firestore: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance(app).apply {
            // 설정을 먼저, 그다음 useEmulator(설정을 덮어쓰므로 순서가 반대면 운영 서버를 가리킨다).
            // 메모리 캐시 기본값(eager GC)은 서버가 받아 간 문서를 리스너가 없으면 바로 지운다. 실제 앱의 영구 캐시처럼
            // 문서를 남겨두는 LRU 정책으로 맞춘다(위젯·알림 워커가 캐시에서 읽는 동작을 같은 조건으로 확인하려고).
            firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(MemoryCacheSettings.newBuilder().setGcSettings(MemoryLruGcSettings.newBuilder().build()).build())
                .build()
            useEmulator(HOST, FIRESTORE_PORT)
        }
    }

    val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance(app).apply { useEmulator(HOST, AUTH_PORT) } }

    fun reachable(): Boolean = listOf(FIRESTORE_PORT, AUTH_PORT).all { port ->
        runCatching { Socket().use { it.connect(InetSocketAddress(HOST, port), 1_000); true } }.getOrDefault(false)
    }
}
