package com.github.chsssssss.eonje.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.github.chsssssss.eonje.domain.usecase.MatchPostUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

private const val KEY_POST_ID = "postId"

@HiltWorker
class CaptionParsingWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val matchPostUseCase: MatchPostUseCase,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val postId = inputData.getString(KEY_POST_ID) ?: return Result.failure()
        matchPostUseCase(postId)
        return Result.success()
    }

    companion object {
        fun request(postId: String) =
            OneTimeWorkRequestBuilder<CaptionParsingWorker>()
                .setInputData(inputData(postId))
                .build()

        private fun inputData(postId: String): Data = workDataOf(KEY_POST_ID to postId)
    }
}
