package com.github.chsssssss.eonje.domain.repository

import com.github.chsssssss.eonje.data.local.ExtractedCandidateEntity
import kotlinx.coroutines.flow.Flow

interface ExtractedCandidateRepository {
    fun observeForPost(postId: String): Flow<List<ExtractedCandidateEntity>>
    suspend fun replaceForPost(postId: String, candidates: List<ExtractedCandidateEntity>)
}
