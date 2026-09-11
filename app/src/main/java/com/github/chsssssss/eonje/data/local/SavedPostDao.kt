package com.github.chsssssss.eonje.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.chsssssss.eonje.domain.model.ResolveStatus
import com.github.chsssssss.eonje.domain.model.UnresolvedReason
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedPostDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(post: SavedPostEntity)

    @Query("SELECT * FROM saved_posts WHERE shortcode = :shortcode LIMIT 1")
    suspend fun findByShortcode(shortcode: String): SavedPostEntity?

    @Query("SELECT * FROM saved_posts WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): SavedPostEntity?

    @Query("SELECT * FROM saved_posts WHERE id IN (:ids) ORDER BY createdAt DESC")
    suspend fun findByIds(ids: List<String>): List<SavedPostEntity>

    @Query("UPDATE saved_posts SET status = :status, unresolvedReason = :unresolvedReason WHERE id = :id")
    suspend fun updateStatus(id: String, status: ResolveStatus, unresolvedReason: UnresolvedReason?)

    @Query(
        "UPDATE saved_posts SET caption = :caption, thumbnailUrl = :thumbnailUrl, extractedCount = :extractedCount WHERE id = :id"
    )
    suspend fun updateExtraction(id: String, caption: String?, thumbnailUrl: String?, extractedCount: Int)

    @Query("SELECT * FROM saved_posts ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<SavedPostEntity>>

    @Query("SELECT * FROM saved_posts WHERE status != 'RESOLVED' ORDER BY createdAt DESC")
    fun observeUnresolved(): Flow<List<SavedPostEntity>>

    @Query(
        """
        SELECT saved_posts.* FROM saved_posts
        INNER JOIN cached_media ON saved_posts.shortcode = cached_media.shortcode
        WHERE cached_media.username = :username AND saved_posts.status = 'UNRESOLVED'
        """
    )
    suspend fun findUnresolvedByAccount(username: String): List<SavedPostEntity>

    @Query("DELETE FROM saved_posts WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)
}
