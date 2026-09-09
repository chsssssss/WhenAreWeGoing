package com.github.chsssssss.eonje.domain.usecase

import androidx.work.WorkManager
import com.github.chsssssss.eonje.data.local.SavedPostEntity
import com.github.chsssssss.eonje.data.worker.CaptionParsingWorker
import com.github.chsssssss.eonje.domain.model.ResolveStatus
import com.github.chsssssss.eonje.domain.repository.SavedPostRepository
import com.github.chsssssss.eonje.domain.util.InstagramUrlParser
import java.util.UUID
import javax.inject.Inject

sealed interface SaveSharedPostResult {
    data object Saved : SaveSharedPostResult
    data object AlreadySaved : SaveSharedPostResult
    data object NoUrlFound : SaveSharedPostResult
}

class SaveSharedPostUseCase @Inject constructor(
    private val repository: SavedPostRepository,
    private val workManager: WorkManager,
) {
    suspend operator fun invoke(sharedText: String): SaveSharedPostResult {
        val rawUrl = InstagramUrlParser.findUrl(sharedText)
            ?: return SaveSharedPostResult.NoUrlFound
        val cleanUrl = InstagramUrlParser.stripQueryParams(rawUrl)
        val shortcode = InstagramUrlParser.extractShortcode(cleanUrl)

        if (shortcode != null && repository.findByShortcode(shortcode) != null) {
            return SaveSharedPostResult.AlreadySaved
        }

        val postId = UUID.randomUUID().toString()
        repository.save(
            SavedPostEntity(
                id = postId,
                shortcode = shortcode,
                instagramUrl = cleanUrl,
                caption = null,
                thumbnailUrl = null,
                status = ResolveStatus.PENDING,
                extractedCount = 0,
                createdAt = System.currentTimeMillis()
            )
        )

        // F2로 위임: shortcode가 있을 때만 매칭 시도, 실패해도 저장 자체는 이미 끝났다.
        if (shortcode != null) {
            workManager.enqueue(CaptionParsingWorker.request(postId))
        }

        return SaveSharedPostResult.Saved
    }
}
