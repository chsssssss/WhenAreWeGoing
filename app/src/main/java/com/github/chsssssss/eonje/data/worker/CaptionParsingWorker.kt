package com.github.chsssssss.eonje.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.github.chsssssss.eonje.domain.usecase.MatchPostResult
import com.github.chsssssss.eonje.domain.usecase.MatchPostUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

private const val KEY_POST_ID = "postId"
private const val MAX_RETRY_ATTEMPTS = 5

@HiltWorker
class CaptionParsingWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val matchPostUseCase: MatchPostUseCase,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val postId = inputData.getString(KEY_POST_ID) ?: return Result.failure()
        return when (matchPostUseCase(postId)) {
            MatchPostResult.Done -> Result.success()
            MatchPostResult.Retry -> {
                if (runAttemptCount >= MAX_RETRY_ATTEMPTS) {
                    // 네트워크 없음 → 지수 백오프 재시도. 재시도 한도를 넘기면 조용히 UNRESOLVED로 넘긴다.
                    matchPostUseCase.markUnresolved(postId)
                    Result.success()
                } else {
                    Result.retry()
                }
            }
        }
    }

    companion object {
        /** 인박스 UI가 "정리 중" 표시를 하려고 WorkManager에서 게시물별 진행 상태를 관찰할 때 쓰는 태그. */
        const val TAG = "caption_parsing"

        fun request(postId: String) =
            OneTimeWorkRequestBuilder<CaptionParsingWorker>()
                .setInputData(inputData(postId))
                .addTag(TAG)
                .addTag(postId)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    java.util.concurrent.TimeUnit.MILLISECONDS,
                )
                .build()

        private fun inputData(postId: String): Data = workDataOf(KEY_POST_ID to postId)
    }
}
