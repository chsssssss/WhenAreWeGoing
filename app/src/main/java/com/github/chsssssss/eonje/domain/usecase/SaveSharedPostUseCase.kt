package com.github.chsssssss.eonje.domain.usecase

import com.github.chsssssss.eonje.data.local.SavedPostEntity
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
    private val repository: SavedPostRepository
) {
    suspend operator fun invoke(sharedText: String): SaveSharedPostResult {
        val rawUrl = InstagramUrlParser.findUrl(sharedText)
            ?: return SaveSharedPostResult.NoUrlFound
        val cleanUrl = InstagramUrlParser.stripQueryParams(rawUrl)
        val shortcode = InstagramUrlParser.extractShortcode(cleanUrl)

        if (shortcode != null && repository.findByShortcode(shortcode) != null) {
            return SaveSharedPostResult.AlreadySaved
        }

        repository.save(
            SavedPostEntity(
                id = UUID.randomUUID().toString(),
                shortcode = shortcode,
                instagramUrl = cleanUrl,
                caption = null,
                thumbnailUrl = null,
                status = ResolveStatus.PENDING,
                extractedCount = 0,
                createdAt = System.currentTimeMillis()
            )
        )
        return SaveSharedPostResult.Saved
    }
}
