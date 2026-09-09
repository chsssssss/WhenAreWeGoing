package com.github.chsssssss.eonje.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.chsssssss.eonje.domain.model.ResolveStatus
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

    @Query("UPDATE saved_posts SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: ResolveStatus)

    @Query("SELECT * FROM saved_posts ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<SavedPostEntity>>

    @Query("SELECT * FROM saved_posts WHERE status != 'RESOLVED' ORDER BY createdAt DESC")
    fun observeUnresolved(): Flow<List<SavedPostEntity>>
}
