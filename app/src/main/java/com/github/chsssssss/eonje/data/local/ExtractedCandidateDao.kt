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
}
