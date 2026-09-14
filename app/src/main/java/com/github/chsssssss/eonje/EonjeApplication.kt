package com.github.chsssssss.eonje

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import com.github.chsssssss.eonje.data.remote.WHEREWEGOING_FIREBASE_APP_NAME
import com.github.chsssssss.eonje.data.worker.InboxReminderWorker
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
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
                    .build(),
                WHEREWEGOING_FIREBASE_APP_NAME,
            )
        installAppCheck(FirebaseAppCheck.getInstance(app))
    }

    private fun installAppCheck(appCheck: FirebaseAppCheck) {
        val factory = if (BuildConfig.DEBUG) {
            DebugAppCheckProviderFactory.getInstance()
        } else {
            PlayIntegrityAppCheckProviderFactory.getInstance()
        }
        appCheck.installAppCheckProviderFactory(factory)
    }
}
