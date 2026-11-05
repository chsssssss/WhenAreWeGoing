package com.github.chsssssss.eonje.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import com.github.chsssssss.eonje.domain.notification.InboxReminderNotifier
import com.github.chsssssss.eonje.domain.repository.SavedPostRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/** 4단계(다듬기): 정리되지 않은 게시물이 쌓여 있으면 주기적으로 상기시킨다. */
@HiltWorker
class InboxReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val savedPostRepository: SavedPostRepository,
    private val notifier: InboxReminderNotifier,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        // 백그라운드 작업이라 서버를 읽지 않는다 — 로컬 데이터가 비어 있으면 알림을 보내지 않는다.
        val count = savedPostRepository.loadCachedUnresolved().size
        if (count > 0) {
            notifier.notifyPending(count)
        }
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "inbox_reminder"
        val EXISTING_POLICY = ExistingPeriodicWorkPolicy.KEEP

        fun periodicRequest() =
            PeriodicWorkRequestBuilder<InboxReminderWorker>(7, TimeUnit.DAYS).build()
    }
}
