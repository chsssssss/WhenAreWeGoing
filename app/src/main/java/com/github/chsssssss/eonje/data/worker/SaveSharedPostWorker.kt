package com.github.chsssssss.eonje.data.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.github.chsssssss.eonje.domain.repository.UserSession
import com.github.chsssssss.eonje.domain.usecase.SaveSharedPostUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/**
 * 로그인 전에 들어온 공유를 대신 저장한다(설치 직후 오프라인 등). URL은 입력 데이터로 WorkManager DB에 먼저 남아 있으니
 * 프로세스가 죽어도 유실되지 않는다. 이 Worker가 직접 익명 로그인을 시도한다 — 앱 프로세스가 죽은 뒤 Worker만 다시 떠도 재시도된다.
 *
 * 재시도 횟수에 상한을 두지 않는다: 상한에서 포기하면 그 URL이 사라지기 때문이다. 네트워크 제약(CONNECTED)이 있어서
 * 오프라인인 동안엔 시도 횟수를 쓰지 않고 기다리며, 실패하면 지수 백오프(최대 5시간 간격)로 계속 다시 시도한다.
 */
@HiltWorker
class SaveSharedPostWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val userSession: UserSession,
    private val saveSharedPost: SaveSharedPostUseCase,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val url = inputData.getString(KEY_URL) ?: return Result.failure()
        val shortcode = inputData.getString(KEY_SHORTCODE)
        val createdAt = inputData.getLong(KEY_CREATED_AT, System.currentTimeMillis())

        if (!userSession.signInIfNeeded()) return Result.retry()
        return try {
            saveSharedPost.saveQueued(url, shortcode, createdAt)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "대기 중이던 공유 저장 실패(${e.javaClass.simpleName}) — 다시 시도")
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "SaveSharedPostWorker"
        private const val KEY_URL = "url"
        private const val KEY_SHORTCODE = "shortcode"
        private const val KEY_CREATED_AT = "createdAt"

        fun uniqueName(shareKey: String) = "save-shared-$shareKey"

        fun inputData(url: String, shortcode: String?, createdAt: Long): Data =
            workDataOf(KEY_URL to url, KEY_SHORTCODE to shortcode, KEY_CREATED_AT to createdAt)

        fun request(url: String, shortcode: String?, createdAt: Long): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<SaveSharedPostWorker>()
                .setInputData(inputData(url, shortcode, createdAt))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
                .build()
    }
}
