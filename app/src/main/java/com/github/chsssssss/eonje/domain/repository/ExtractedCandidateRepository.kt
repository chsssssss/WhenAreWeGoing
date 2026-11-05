package com.github.chsssssss.eonje.domain.repository

import com.github.chsssssss.eonje.domain.model.ExtractedCandidate
import kotlinx.coroutines.flow.Flow

interface ExtractedCandidateRepository {
    fun observeForPost(postId: String): Flow<List<ExtractedCandidate>>
    suspend fun replaceForPost(postId: String, candidates: List<ExtractedCandidate>)
}
