package com.github.chsssssss.eonje.data.repository

import com.github.chsssssss.eonje.data.local.SavedPostDao
import com.github.chsssssss.eonje.data.mapper.toDomain
import com.github.chsssssss.eonje.data.mapper.toEntity
import com.github.chsssssss.eonje.data.local.ThumbnailStore
import com.github.chsssssss.eonje.domain.model.ResolveStatus
import com.github.chsssssss.eonje.domain.model.SavedPost
import com.github.chsssssss.eonje.domain.model.UnresolvedReason
import com.github.chsssssss.eonje.domain.repository.SavedPostRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class RoomSavedPostRepository @Inject constructor(
    private val dao: SavedPostDao,
    private val thumbnailStore: ThumbnailStore,
) : SavedPostRepository {
    override suspend fun findByShortcode(shortcode: String): SavedPost? =
        dao.findByShortcode(shortcode)?.toDomain()

    override suspend fun findById(id: String): SavedPost? = dao.findById(id)?.toDomain()

    override suspend fun findByIds(ids: List<String>): List<SavedPost> = dao.findByIds(ids).map { it.toDomain() }

    override suspend fun save(post: SavedPost) = dao.insert(post.toEntity())

    override suspend fun updateStatus(id: String, status: ResolveStatus, unresolvedReason: UnresolvedReason?) =
        dao.updateStatus(id, status, unresolvedReason)

    override suspend fun updateExtraction(id: String, caption: String?, thumbnailUrl: String?, extractedCount: Int) =
        dao.updateExtraction(id, caption, thumbnailUrl, extractedCount)

    override suspend fun deleteByIds(ids: List<String>) {
        dao.deleteByIds(ids)
        thumbnailStore.deleteAll(ids)
    }

    override suspend fun clearLocalThumbnail(id: String) = dao.clearLocalThumbnail(id)

    override fun observeAll(): Flow<List<SavedPost>> = dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun cachedPostIds(): Set<String> = dao.observeAll().first().mapTo(HashSet()) { it.id }

    override suspend fun loadCachedUnresolved(): List<SavedPost> = dao.observeUnresolved().first().map { it.toDomain() }

    override fun observeUnresolved(): Flow<List<SavedPost>> =
        dao.observeUnresolved().map { rows -> rows.map { it.toDomain() } }
}
