package com.github.chsssssss.eonje

import dagger.hilt.android.EntryPointAccessors
import com.github.chsssssss.eonje.di.AuthSessionEntryPoint
import com.github.chsssssss.eonje.data.firestore.FirestoreSetup
import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import com.github.chsssssss.eonje.data.remote.WHEREWEGOING_FIREBASE_APP_NAME
import com.github.chsssssss.eonje.data.remote.appCheckProviderFactory
import com.github.chsssssss.eonje.data.worker.InboxReminderWorker
import com.github.chsssssss.eonje.data.worker.RoomMigrationWorker
import kotlinx.coroutines.launch
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.appCheck
import com.kakao.vectormap.KakaoMapSdk
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class EonjeApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        KakaoMapSdk.init(this, BuildConfig.KAKAO_NATIVE_APP_KEY)
        initGeminiFirebaseApp()
        initWherewegoingFirebaseApp()
        initFirestoreAndAuth()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            InboxReminderWorker.WORK_NAME,
            InboxReminderWorker.EXISTING_POLICY,
            InboxReminderWorker.periodicRequest(),
        )
    }

    /**
     * Firebase AI Logic(Gemini)은 결제 계정이 연결된 프로젝트에서 호출하면 무료 티어 대신
     * Prepay(선불 크레딧) 결제로 전환돼버린다. 그래서 Apify 게이트웨이용 프로젝트(Blaze)와
     * 분리된, 결제 계정을 절대 연결하지 않는 별도 프로젝트를 쓴다. Firebase AI Logic SDK는
     * 이름과 무관하게 "기본(default) FirebaseApp"만 찾기 때문에 이 프로젝트를 기본 앱으로 둔다.
     */
    private fun initGeminiFirebaseApp() {
        val projectId = BuildConfig.FIREBASE_GEMINI_PROJECT_ID
        val applicationId = BuildConfig.FIREBASE_GEMINI_APPLICATION_ID
        val apiKey = BuildConfig.FIREBASE_GEMINI_API_KEY
        if (projectId.isBlank() || applicationId.isBlank() || apiKey.isBlank()) return

        if (FirebaseApp.getApps(this).none { it.name == FirebaseApp.DEFAULT_APP_NAME }) {
            val options = FirebaseOptions.Builder()
                .setProjectId(projectId)
                .setApplicationId(applicationId)
                .setApiKey(apiKey)
                .setGcmSenderId(projectNumberOf(applicationId))
                .build()
            FirebaseApp.initializeApp(this, options)
        }
        installAppCheck(Firebase.appCheck)
    }

    /**
     * fetchInstagramMeta Cloud Function(Apify 게이트웨이) 전용 프로젝트 — Secret Manager를 쓰려면
     * Blaze 플랜이 필요해서 Gemini 프로젝트와는 분리해뒀다. Firebase AI Logic과 달리 Functions는
     * named app을 그대로 받아들인다.
     */
    private fun initWherewegoingFirebaseApp() {
        val projectId = BuildConfig.FIREBASE_PROJECT_ID
        val applicationId = BuildConfig.FIREBASE_APPLICATION_ID
        val apiKey = BuildConfig.FIREBASE_API_KEY
        if (projectId.isBlank() || applicationId.isBlank() || apiKey.isBlank()) return

        val app = FirebaseApp.getApps(this).firstOrNull { it.name == WHEREWEGOING_FIREBASE_APP_NAME }
            ?: FirebaseApp.initializeApp(
                this,
                FirebaseOptions.Builder()
                    .setProjectId(projectId)
                    .setApplicationId(applicationId)
                    .setApiKey(apiKey)
                    .setGcmSenderId(projectNumberOf(applicationId))
                    .build(),
                WHEREWEGOING_FIREBASE_APP_NAME,
            )
        installAppCheck(FirebaseAppCheck.getInstance(app))
    }

    /**
     * 데이터 원본(Firestore)과 익명 로그인. 반드시 named 앱 "wherewegoing"(Blaze)에 붙인다 — default 앱은 Gemini 전용(Spark)이다.
     * Firestore 설정은 첫 사용 전에 지정해야 하므로 Firebase 앱을 만든 직후, 어떤 저장소도 만들어지기 전에 여기서 한다.
     */
    private fun initFirestoreAndAuth() {
        val app = FirebaseApp.getApps(this).firstOrNull { it.name == WHEREWEGOING_FIREBASE_APP_NAME } ?: return
        FirestoreSetup.initialize(app, this) // 설정 → (요청됐으면) 캐시 정리. 어떤 저장소보다 먼저
        EntryPointAccessors.fromApplication(this, AuthSessionEntryPoint::class.java).authSession().startAnonymousSignIn()
        // 계정 합치기가 도중에 끊겼으면(저널이 남아 있으면) 이어서 끝낸다. 없으면 파일 존재 확인뿐이다.
        EntryPointAccessors.fromApplication(this, AuthSessionEntryPoint::class.java).let { entry ->
            entry.applicationScope().launch { entry.accountRepository().resumePendingMerge() }
        }
        // 업데이트 전의 Room 데이터를 Firestore로 한 번 옮긴다. 이미 끝났으면 바로 끝나는 가벼운 작업이다.
        RoomMigrationWorker.enqueue(WorkManager.getInstance(this))
    }

    /**
     * Firebase 앱 ID(`1:<프로젝트 번호>:android:<해시>`)에서 프로젝트 번호를 꺼낸다.
     * google-services.json 없이 옵션을 직접 만들면 gcmSenderId가 비는데, Play Integrity App Check는
     * 이 값을 `Long.parseLong`해서 클라우드 프로젝트 번호로 쓰기 때문에 비어 있으면 릴리스(스토어) 빌드에서
     * 토큰 발급이 항상 실패한다. 디버그 빌드는 Debug Provider라 이 값을 안 써서 드러나지 않았다.
     */
    private fun projectNumberOf(applicationId: String): String? =
        applicationId.split(":").getOrNull(1)?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }

    /** 디버그는 Debug Provider, 릴리스는 Play Integrity — 빌드 타입별 소스셋(src/debug, src/release)에서 고른다. */
    private fun installAppCheck(appCheck: FirebaseAppCheck) {
        appCheck.installAppCheckProviderFactory(appCheckProviderFactory())
    }
}
