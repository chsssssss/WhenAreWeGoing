package com.github.chsssssss.eonje.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ExtractedCandidateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(candidates: List<ExtractedCandidateEntity>)

    @Query("SELECT * FROM extracted_candidates WHERE postId = :postId ORDER BY extractionIndex ASC, rank ASC")
    fun observeForPost(postId: String): Flow<List<ExtractedCandidateEntity>>

    @Query("DELETE FROM extracted_candidates WHERE postId = :postId")
    suspend fun deleteForPost(postId: String)

    // 이전용: 게시물 묶음의 후보를 한 번에 읽는다.
    @Query("SELECT * FROM extracted_candidates WHERE postId IN (:postIds) ORDER BY postId, extractionIndex ASC, rank ASC")
    suspend fun forPosts(postIds: List<String>): List<ExtractedCandidateEntity>
}
