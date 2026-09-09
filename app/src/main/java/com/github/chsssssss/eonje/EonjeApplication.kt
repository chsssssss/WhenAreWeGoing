package com.github.chsssssss.eonje

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import com.github.chsssssss.eonje.data.worker.BusinessDiscoverySyncWorker
import com.github.chsssssss.eonje.data.worker.InboxReminderWorker
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
}
