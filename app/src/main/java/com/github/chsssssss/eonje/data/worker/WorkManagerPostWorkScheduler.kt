package com.github.chsssssss.eonje.data.worker

import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.github.chsssssss.eonje.domain.repository.PostWorkScheduler
import javax.inject.Inject

class WorkManagerPostWorkScheduler @Inject constructor(
    private val workManager: WorkManager,
) : PostWorkScheduler {

    override fun scheduleCaptionParsing(postId: String) {
        // KEEP: 이미 돌고 있거나 재시도 대기 중이면 그대로 둔다 → Apify를 중복 호출하지 않는다.
        workManager.enqueueUniqueWork("caption-$postId", ExistingWorkPolicy.KEEP, CaptionParsingWorker.request(postId))
    }

    override fun scheduleSaveSharedPost(shareKey: String, url: String, shortcode: String?, createdAt: Long) {
        workManager.enqueueUniqueWork(
            SaveSharedPostWorker.uniqueName(shareKey),
            ExistingWorkPolicy.KEEP,
            SaveSharedPostWorker.request(url, shortcode, createdAt),
        )
    }
}
