package com.github.chsssssss.eonje.data.mapper

import com.github.chsssssss.eonje.data.local.ExtractedCandidateEntity
import com.github.chsssssss.eonje.domain.model.ExtractedCandidate
import com.github.chsssssss.eonje.domain.model.PlaceCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExtractedCandidateMapperTest {

    private val entity = ExtractedCandidateEntity(
        postId = "post-1",
        extractionIndex = 2,
        extractedName = "반마리닭국수",
        rank = 1,
        kakaoPlaceId = "9876",
        name = "반마리닭국수 후평점",
        address = "강원 춘천시 후평동 1",
        category = "음식점 > 국수",
        latitude = 37.88,
        longitude = 127.74,
    )

    @Test
    fun `Entity를 도메인으로 바꾸면 후보 정보가 PlaceCandidate로 묶인다`() {
        assertEquals(
            ExtractedCandidate(
                postId = "post-1",
                extractionIndex = 2,
                extractedName = "반마리닭국수",
                rank = 1,
                place = PlaceCandidate("9876", "반마리닭국수 후평점", "강원 춘천시 후평동 1", "음식점 > 국수", 37.88, 127.74),
            ),
            entity.toDomain(),
        )
    }

    @Test
    fun `도메인을 Entity로 되돌려도 같다`() {
        assertEquals(entity, entity.toDomain().toEntity())
    }

    @Test
    fun `카테고리가 없는 후보도 그대로 오간다`() {
        val noCategory = entity.copy(category = null)

        assertNull(noCategory.toDomain().place.category)
        assertEquals(noCategory, noCategory.toDomain().toEntity())
    }
}
