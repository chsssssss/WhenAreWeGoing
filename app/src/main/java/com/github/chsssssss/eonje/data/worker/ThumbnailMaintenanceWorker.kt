package com.github.chsssssss.eonje.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.github.chsssssss.eonje.data.analytics.Events
import com.github.chsssssss.eonje.data.local.ThumbnailMaintenance
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.logEvent
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

/**
 * 앱을 열 때마다 한 번씩 도는 썸네일 폴더 정리(고아 파일 삭제) + 업데이트 후 1회만 하는 기존 파일 줄이기.
 * 저장 파이프라인과 무관한 백그라운드 작업이다. 네트워크는 캐시 기준으로 고아로 의심되는 파일이 있을 때만 쓴다 — 그 게시물이 서버에도
 * 없는지 Firestore에 물어서(최대 60개, 읽기 비용 있음), 서버에도 없다고 확인된 파일만 지운다(오프라인이면 지우지 않고 미룬다).
 * 실패해도 앱 동작에는 영향이 없다.
 */
@HiltWorker
class ThumbnailMaintenanceWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val maintenance: ThumbnailMaintenance,
    private val analytics: FirebaseAnalytics,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val report = try {
            maintenance.run(nowMillis = System.currentTimeMillis())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // DB·DataStore 읽기 오류 같은 일시적 문제는 몇 번만 다시 시도하고, 안 되면 다음 앱 실행에 맡긴다.
            return if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
        }
        report.compaction?.let { compaction ->
            analytics.logEvent(Events.THUMBNAIL_COMPACTION_DONE) {
                param(Events.Param.FILE_COUNT, compaction.processedFiles.toLong())
                param(Events.Param.SAVED_BYTES, compaction.savedBytes)
            }
        }
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "thumbnail_maintenance"
        private const val MAX_ATTEMPTS = 3

        /** 이미 돌고 있거나 대기 중이면 새로 만들지 않는다(KEEP). 배터리가 부족하면 충전·회복될 때까지 보류한다. */
        fun enqueue(workManager: WorkManager) {
            val request = OneTimeWorkRequestBuilder<ThumbnailMaintenanceWorker>()
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build()
            workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
