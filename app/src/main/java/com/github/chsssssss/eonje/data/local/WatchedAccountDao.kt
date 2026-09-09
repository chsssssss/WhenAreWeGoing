package com.github.chsssssss.eonje.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchedAccountDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(account: WatchedAccountEntity)

    @Query("SELECT * FROM watched_accounts WHERE username = :username LIMIT 1")
    suspend fun findByUsername(username: String): WatchedAccountEntity?

    @Query("SELECT * FROM watched_accounts ORDER BY username ASC")
    fun observeAll(): Flow<List<WatchedAccountEntity>>

    @Query("SELECT * FROM watched_accounts ORDER BY username ASC")
    suspend fun getAll(): List<WatchedAccountEntity>

    @Query("UPDATE watched_accounts SET lastSyncedAt = :syncedAt, lastSyncError = NULL WHERE username = :username")
    suspend fun markSynced(username: String, syncedAt: Long)

    @Query("UPDATE watched_accounts SET lastSyncError = :error WHERE username = :username")
    suspend fun markSyncFailed(username: String, error: String)

    @Query("DELETE FROM watched_accounts WHERE username = :username")
    suspend fun delete(username: String)
}
