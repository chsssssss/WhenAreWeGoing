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

    // UNRESOLVED로 넘어갈 때는 extractedCount를 0으로 되돌린다 — 그렇지 않으면 "추출 1건, 검색 0건"으로
    // 미확정된 게시물이 이전에 저장된 extractedCount(예: 1)를 그대로 들고 있어서, 인박스에 실제로는
    // 저장된 후보가 하나도 없는데도 "후보 1곳" 칩이 표시되는 문제가 생긴다.
    @Query(
        "UPDATE saved_posts SET status = :status, unresolvedReason = :unresolvedReason, " +
            "extractedCount = CASE WHEN :status = 'UNRESOLVED' THEN 0 ELSE extractedCount END " +
            "WHERE id = :id"
    )
    suspend fun updateStatus(id: String, status: ResolveStatus, unresolvedReason: UnresolvedReason?)

    @Query(
        "UPDATE saved_posts SET caption = :caption, thumbnailUrl = :thumbnailUrl, extractedCount = :extractedCount WHERE id = :id"
    )
    suspend fun updateExtraction(id: String, caption: String?, thumbnailUrl: String?, extractedCount: Int)

    @Query("SELECT * FROM saved_posts ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<SavedPostEntity>>

    @Query("SELECT * FROM saved_posts WHERE status != 'RESOLVED' ORDER BY createdAt DESC")
    fun observeUnresolved(): Flow<List<SavedPostEntity>>

    @Query("DELETE FROM saved_posts WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)
}
