package com.github.chsssssss.eonje.data.repository

import com.github.chsssssss.eonje.data.local.ExtractedCandidateDao
import com.github.chsssssss.eonje.data.local.ExtractedCandidateEntity
import com.github.chsssssss.eonje.domain.model.ExtractedCandidate
import com.github.chsssssss.eonje.domain.model.PlaceCandidate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** replaceForPost는 기존 후보를 지우고 새로 넣으며, 빈 목록이면 지우기만 한다(동작은 도메인 전환 전과 같다). */
class ExtractedCandidateRepositoryImplTest {

    private val dao = FakeDao()
    private val repository = ExtractedCandidateRepositoryImpl(dao)

    @Test
    fun `후보를 저장하고 게시물 단위로 순서대로 읽는다`() = runBlocking {
        repository.replaceForPost(
            "post-1",
            listOf(candidate("post-1", 1, 1, "B2"), candidate("post-1", 0, 0, "A1"), candidate("post-1", 1, 0, "B1")),
        )

        val read = repository.observeForPost("post-1").first()

        assertEquals(listOf("A1", "B1", "B2"), read.map { it.place.name }) // extractionIndex, rank 순
        assertEquals(listOf(0, 1, 1), read.map { it.extractionIndex })
    }

    @Test
    fun `replaceForPost는 같은 게시물의 이전 후보를 지우고 다른 게시물은 건드리지 않는다`() = runBlocking {
        repository.replaceForPost("post-1", listOf(candidate("post-1", 0, 0, "옛 후보")))
        repository.replaceForPost("post-2", listOf(candidate("post-2", 0, 0, "다른 게시물")))

        repository.replaceForPost("post-1", listOf(candidate("post-1", 0, 0, "새 후보")))

        assertEquals(listOf("새 후보"), repository.observeForPost("post-1").first().map { it.place.name })
        assertEquals(listOf("다른 게시물"), repository.observeForPost("post-2").first().map { it.place.name })
    }

    @Test
    fun `빈 목록이면 지우기만 한다`() = runBlocking {
        repository.replaceForPost("post-1", listOf(candidate("post-1", 0, 0, "후보")))

        repository.replaceForPost("post-1", emptyList())

        assertTrue(repository.observeForPost("post-1").first().isEmpty())
    }

    private fun candidate(postId: String, index: Int, rank: Int, name: String) = ExtractedCandidate(
        postId = postId,
        extractionIndex = index,
        extractedName = "추출 $index",
        rank = rank,
        place = PlaceCandidate("kakao-$name", name, "주소", null, 37.0, 127.0),
    )

    private class FakeDao : ExtractedCandidateDao {
        private val rows = mutableListOf<ExtractedCandidateEntity>()

        override suspend fun insertAll(candidates: List<ExtractedCandidateEntity>) {
            candidates.forEach { c ->
                rows.removeAll { it.postId == c.postId && it.extractionIndex == c.extractionIndex && it.rank == c.rank }
                rows += c
            }
        }

        override fun observeForPost(postId: String): Flow<List<ExtractedCandidateEntity>> =
            MutableStateFlow(rows.filter { it.postId == postId }.sortedWith(compareBy({ it.extractionIndex }, { it.rank })))

        override suspend fun deleteForPost(postId: String) {
            rows.removeAll { it.postId == postId }
        }
    }
}
