package com.github.chsssssss.eonje.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "watched_accounts")
data class WatchedAccountEntity(
    @PrimaryKey val username: String,
    val igUserId: String,
    val profileImageUrl: String?,
    val lastSyncedAt: Long?,
    val lastSyncError: String? = null,
)
