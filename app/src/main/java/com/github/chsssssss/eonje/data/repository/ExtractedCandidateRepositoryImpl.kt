package com.github.chsssssss.eonje.data.repository

import com.github.chsssssss.eonje.data.local.ExtractedCandidateDao
import com.github.chsssssss.eonje.data.mapper.toDomain
import com.github.chsssssss.eonje.data.mapper.toEntity
import com.github.chsssssss.eonje.domain.model.ExtractedCandidate
import com.github.chsssssss.eonje.domain.repository.ExtractedCandidateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ExtractedCandidateRepositoryImpl @Inject constructor(
    private val dao: ExtractedCandidateDao,
) : ExtractedCandidateRepository {
    override fun observeForPost(postId: String): Flow<List<ExtractedCandidate>> =
        dao.observeForPost(postId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun replaceForPost(postId: String, candidates: List<ExtractedCandidate>) {
        dao.deleteForPost(postId)
        if (candidates.isNotEmpty()) dao.insertAll(candidates.map { it.toEntity() })
    }
}
