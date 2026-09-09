package com.github.chsssssss.eonje.data.repository

import com.github.chsssssss.eonje.data.local.ExtractedCandidateDao
import com.github.chsssssss.eonje.data.local.ExtractedCandidateEntity
import com.github.chsssssss.eonje.domain.repository.ExtractedCandidateRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ExtractedCandidateRepositoryImpl @Inject constructor(
    private val dao: ExtractedCandidateDao,
) : ExtractedCandidateRepository {
    override fun observeForPost(postId: String): Flow<List<ExtractedCandidateEntity>> =
        dao.observeForPost(postId)

    override suspend fun replaceForPost(postId: String, candidates: List<ExtractedCandidateEntity>) {
        dao.deleteForPost(postId)
        if (candidates.isNotEmpty()) dao.insertAll(candidates)
    }
}
