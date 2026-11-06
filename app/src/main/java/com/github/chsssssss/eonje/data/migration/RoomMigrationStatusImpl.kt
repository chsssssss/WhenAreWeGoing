package com.github.chsssssss.eonje.data.migration

import com.github.chsssssss.eonje.data.local.SavedPostDao
import com.github.chsssssss.eonje.domain.repository.LegacyMigrationStatus
import com.github.chsssssss.eonje.domain.repository.UserSession
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomMigrationStatusImpl @Inject constructor(
    private val store: RoomMigrationStateStore,
    private val session: UserSession,
    private val postDao: SavedPostDao,
) : LegacyMigrationStatus {

    override suspend fun isComplete(): Boolean {
        val uid = session.currentUid() ?: return false
        val state = store.read()
        return state.completed && state.uid == uid
    }

    override suspend fun isLegacyDuplicate(shortcode: String): Boolean {
        if (isComplete()) return false
        return postDao.findByShortcode(shortcode) != null
    }
}
