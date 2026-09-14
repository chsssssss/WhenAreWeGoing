package com.github.chsssssss.eonje.domain.repository

import com.github.chsssssss.eonje.data.local.FolderEntity
import kotlinx.coroutines.flow.Flow

interface FolderRepository {
    fun observeAll(): Flow<List<FolderEntity>>

    /** Reuses an existing folder by name rather than creating a duplicate. */
    suspend fun findOrCreateByName(name: String): FolderEntity
}
