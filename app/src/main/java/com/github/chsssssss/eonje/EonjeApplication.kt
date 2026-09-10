package com.github.chsssssss.eonje

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import com.github.chsssssss.eonje.data.worker.BusinessDiscoverySyncWorker
import com.github.chsssssss.eonje.data.worker.InboxReminderWorker
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
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
        initFirebaseAppCheck()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            BusinessDiscoverySyncWorker.WORK_NAME,
            BusinessDiscoverySyncWorker.EXISTING_POLICY,
            BusinessDiscoverySyncWorker.periodicRequest(),
        )
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            InboxReminderWorker.WORK_NAME,
            InboxReminderWorker.EXISTING_POLICY,
            InboxReminderWorker.periodicRequest(),
        )
    }

    /**
     * Firebase AI Logic은 App Check 강제 적용 이후로 Provider가 설치돼 있지 않으면 요청 자체를 거부한다.
     * google-services.json 없이 기본 FirebaseApp을 직접 초기화하고, 디버그 빌드는 Debug Provider,
     * 릴리스 빌드는 Play Integrity Provider를 쓴다.
     */
    private fun initFirebaseAppCheck() {
        val projectId = BuildConfig.FIREBASE_PROJECT_ID
        val applicationId = BuildConfig.FIREBASE_APPLICATION_ID
        val apiKey = BuildConfig.FIREBASE_API_KEY
        if (projectId.isBlank() || applicationId.isBlank() || apiKey.isBlank()) return

        if (FirebaseApp.getApps(this).none { it.name == FirebaseApp.DEFAULT_APP_NAME }) {
            val options = FirebaseOptions.Builder()
                .setProjectId(projectId)
                .setApplicationId(applicationId)
                .setApiKey(apiKey)
                .build()
            FirebaseApp.initializeApp(this, options)
        }

        val factory = if (BuildConfig.DEBUG) {
            DebugAppCheckProviderFactory.getInstance()
        } else {
            PlayIntegrityAppCheckProviderFactory.getInstance()
        }
        Firebase.appCheck.installAppCheckProviderFactory(factory)
    }
}
