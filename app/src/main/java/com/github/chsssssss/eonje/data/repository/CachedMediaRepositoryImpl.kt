package com.github.chsssssss.eonje.data.repository

import com.github.chsssssss.eonje.data.local.CachedMediaDao
import com.github.chsssssss.eonje.data.local.CachedMediaEntity
import com.github.chsssssss.eonje.domain.model.DiscoveredMedia
import com.github.chsssssss.eonje.domain.repository.CachedMediaRepository
import javax.inject.Inject

class CachedMediaRepositoryImpl @Inject constructor(
    private val dao: CachedMediaDao,
) : CachedMediaRepository {
    override suspend fun findByShortcode(shortcode: String): CachedMediaEntity? =
        dao.findByShortcode(shortcode)

    override suspend fun replaceForAccount(username: String, media: List<DiscoveredMedia>) {
        val now = System.currentTimeMillis()
        dao.insertAll(media.map { it.toEntity(username, now) })
        dao.pruneToRecent(username)
    }

    override suspend fun cacheSingle(username: String, media: DiscoveredMedia) {
        dao.insertAll(listOf(media.toEntity(username, System.currentTimeMillis())))
    }

    private fun DiscoveredMedia.toEntity(username: String, cachedAt: Long) = CachedMediaEntity(
        shortcode = shortcode,
        username = username,
        caption = caption,
        permalink = permalink,
        mediaType = mediaType,
        mediaUrls = mediaUrls,
        timestamp = timestamp,
        cachedAt = cachedAt,
    )
}
