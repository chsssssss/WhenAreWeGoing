package com.github.chsssssss.eonje.data.repository

import com.github.chsssssss.eonje.data.local.PlaceDao
import com.github.chsssssss.eonje.data.local.PlaceEntity
import com.github.chsssssss.eonje.data.local.PostPlaceCrossRef
import com.github.chsssssss.eonje.data.local.PostPlaceCrossRefDao
import com.github.chsssssss.eonje.domain.model.ResolveStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * "이미 저장된 가게" 처리 검증: @fooddaegu, @daeguhotple 두 계정의 서로 다른 게시물이
 * 카카오 로컬 검색에서 같은 kakaoPlaceId로 매칭됐다고 가정하고, 장소가 중복 생성되지
 * 않고 게시물만 같은 장소에 연결되는지 확인한다.
 */
class PlaceRepositoryImplTest {

    @Test
    fun `save reuses existing place when kakaoPlaceId already saved`() = runBlocking {
        val placeDao = FakePlaceDao()
        val crossRefDao = FakePostPlaceCrossRefDao()
        val repository = PlaceRepositoryImpl(placeDao, crossRefDao)

        // @fooddaegu 게시물에서 추출한 가게를 먼저 확정 저장
        val firstId = repository.save(place(kakaoPlaceId = "kakao-1234", name = "대구탕집"))
        repository.linkPostToPlace(postId = "post-fooddaegu", placeId = firstId)

        // @daeguhotple의 다른 게시물이 같은 kakaoPlaceId로 매칭돼도 새 장소를 만들면 안 된다
        val secondId = repository.save(place(kakaoPlaceId = "kakao-1234", name = "대구탕집 본점"))
        repository.linkPostToPlace(postId = "post-daeguhotple", placeId = secondId)

        assertEquals("같은 kakaoPlaceId면 동일한 장소 id를 반환해야 한다", firstId, secondId)
        assertEquals("장소는 한 번만 insert돼야 한다", 1, placeDao.insertedCount)
        assertEquals(
            "두 게시물 모두 같은 장소에 연결돼야 한다",
            listOf("post-fooddaegu", "post-daeguhotple"),
            repository.postIdsForPlace(firstId),
        )
    }

    @Test
    fun `save creates separate places when kakaoPlaceId differs`() = runBlocking {
        val placeDao = FakePlaceDao()
        val crossRefDao = FakePostPlaceCrossRefDao()
        val repository = PlaceRepositoryImpl(placeDao, crossRefDao)

        val firstId = repository.save(place(kakaoPlaceId = "kakao-1111", name = "가게 A"))
        val secondId = repository.save(place(kakaoPlaceId = "kakao-2222", name = "가게 B"))

        assertNotEquals(firstId, secondId)
        assertEquals(2, placeDao.insertedCount)
    }

    private fun place(kakaoPlaceId: String?, name: String) = PlaceEntity(
        id = "generated-$name",
        name = name,
        address = null,
        latitude = null,
        longitude = null,
        kakaoPlaceId = kakaoPlaceId,
        category = null,
        memo = null,
        status = ResolveStatus.RESOLVED,
        createdAt = 0L,
        resolvedAt = 0L,
    )

    private class FakePlaceDao : PlaceDao {
        private val places = mutableMapOf<String, PlaceEntity>()
        var insertedCount = 0
            private set

        override suspend fun insert(place: PlaceEntity) {
            places[place.id] = place
            insertedCount++
        }

        override suspend fun findByKakaoPlaceId(kakaoPlaceId: String): PlaceEntity? =
            places.values.find { it.kakaoPlaceId == kakaoPlaceId }

        override suspend fun findById(id: String): PlaceEntity? = places[id]

        override fun observeAll(): Flow<List<PlaceEntity>> = MutableStateFlow(places.values.toList())
    }

    private class FakePostPlaceCrossRefDao : PostPlaceCrossRefDao {
        private val refs = mutableListOf<PostPlaceCrossRef>()

        override suspend fun insert(crossRef: PostPlaceCrossRef) {
            if (refs.none { it.postId == crossRef.postId && it.placeId == crossRef.placeId }) {
                refs += crossRef
            }
        }

        override suspend fun placeIdsForPost(postId: String): List<String> =
            refs.filter { it.postId == postId }.map { it.placeId }

        override suspend fun postIdsForPlace(placeId: String): List<String> =
            refs.filter { it.placeId == placeId }.map { it.postId }
    }
}
