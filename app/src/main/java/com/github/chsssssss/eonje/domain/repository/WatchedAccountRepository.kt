package com.github.chsssssss.eonje.domain.repository

import com.github.chsssssss.eonje.data.local.WatchedAccountEntity
import kotlinx.coroutines.flow.Flow

interface WatchedAccountRepository {
    fun observeAll(): Flow<List<WatchedAccountEntity>>
    suspend fun getAll(): List<WatchedAccountEntity>
    suspend fun findByUsername(username: String): WatchedAccountEntity?
    suspend fun save(account: WatchedAccountEntity)
    suspend fun delete(username: String)
    suspend fun markSynced(username: String)
    suspend fun markSyncFailed(username: String, error: String)
}
