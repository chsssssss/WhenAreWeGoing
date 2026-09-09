package com.github.chsssssss.eonje.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

private const val MAX_CACHED_PER_ACCOUNT = 50

@Dao
interface CachedMediaDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(media: List<CachedMediaEntity>)

    @Query("SELECT * FROM cached_media WHERE shortcode = :shortcode LIMIT 1")
    suspend fun findByShortcode(shortcode: String): CachedMediaEntity?

    @Query(
        """
        DELETE FROM cached_media WHERE username = :username AND shortcode NOT IN (
            SELECT shortcode FROM cached_media WHERE username = :username
            ORDER BY timestamp DESC LIMIT $MAX_CACHED_PER_ACCOUNT
        )
        """
    )
    suspend fun pruneToRecent(username: String)
}
