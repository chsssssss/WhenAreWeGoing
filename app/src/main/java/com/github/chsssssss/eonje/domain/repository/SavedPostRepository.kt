package com.github.chsssssss.eonje.domain.repository

import com.github.chsssssss.eonje.data.local.SavedPostEntity
import com.github.chsssssss.eonje.domain.model.ResolveStatus
import com.github.chsssssss.eonje.domain.model.UnresolvedReason
import kotlinx.coroutines.flow.Flow

interface SavedPostRepository {
    suspend fun findByShortcode(shortcode: String): SavedPostEntity?
    suspend fun findById(id: String): SavedPostEntity?
    suspend fun findByIds(ids: List<String>): List<SavedPostEntity>
    suspend fun save(post: SavedPostEntity)
    suspend fun updateStatus(id: String, status: ResolveStatus, unresolvedReason: UnresolvedReason?)
    suspend fun updateExtraction(id: String, caption: String?, thumbnailUrl: String?, extractedCount: Int)
    suspend fun findUnresolvedByAccount(username: String): List<SavedPostEntity>
    suspend fun deleteByIds(ids: List<String>)
    fun observeAll(): Flow<List<SavedPostEntity>>
    fun observeUnresolved(): Flow<List<SavedPostEntity>>
}
