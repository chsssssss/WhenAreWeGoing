package com.github.chsssssss.eonje.data.repository

import com.github.chsssssss.eonje.data.local.SavedPostDao
import com.github.chsssssss.eonje.data.local.SavedPostEntity
import com.github.chsssssss.eonje.domain.model.ResolveStatus
import com.github.chsssssss.eonje.domain.repository.SavedPostRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SavedPostRepositoryImpl @Inject constructor(
    private val dao: SavedPostDao
) : SavedPostRepository {
    override suspend fun findByShortcode(shortcode: String): SavedPostEntity? =
        dao.findByShortcode(shortcode)

    override suspend fun findById(id: String): SavedPostEntity? = dao.findById(id)

    override suspend fun findByIds(ids: List<String>): List<SavedPostEntity> = dao.findByIds(ids)

    override suspend fun save(post: SavedPostEntity) = dao.insert(post)

    override suspend fun updateStatus(id: String, status: ResolveStatus) =
        dao.updateStatus(id, status)

    override suspend fun updateExtraction(id: String, caption: String?, thumbnailUrl: String?, extractedCount: Int) =
        dao.updateExtraction(id, caption, thumbnailUrl, extractedCount)

    override suspend fun findUnresolvedByAccount(username: String): List<SavedPostEntity> =
        dao.findUnresolvedByAccount(username)

    override fun observeAll(): Flow<List<SavedPostEntity>> = dao.observeAll()

    override fun observeUnresolved(): Flow<List<SavedPostEntity>> = dao.observeUnresolved()
}
