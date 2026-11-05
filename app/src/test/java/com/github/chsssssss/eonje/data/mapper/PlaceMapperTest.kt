package com.github.chsssssss.eonje.data.mapper

import com.github.chsssssss.eonje.data.local.PlaceEntity
import com.github.chsssssss.eonje.domain.model.Place
import com.github.chsssssss.eonje.domain.model.ResolveStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaceMapperTest {

    private val entity = PlaceEntity(
        id = "place-1",
        name = "투썸플레이스 평택리버타워점",
        address = "경기 평택시 오성면 강변로 521",
        latitude = 36.99,
        longitude = 127.08,
        kakaoPlaceId = "1234567",
        category = "카페",
        memo = "옛 메모",
        status = ResolveStatus.RESOLVED,
        createdAt = 1_700_000_000_000L,
        resolvedAt = 1_700_000_100_000L,
        folderId = "folder-1",
    )

    @Test
    fun `Entity를 도메인으로 바꾸면 memo만 빠지고 나머지는 그대로다`() {
        assertEquals(
            Place(
                id = "place-1",
                name = "투썸플레이스 평택리버타워점",
                address = "경기 평택시 오성면 강변로 521",
                latitude = 36.99,
                longitude = 127.08,
                kakaoPlaceId = "1234567",
                category = "카페",
                status = ResolveStatus.RESOLVED,
                createdAt = 1_700_000_000_000L,
                resolvedAt = 1_700_000_100_000L,
                folderId = "folder-1",
            ),
            entity.toDomain(),
        )
    }

    @Test
    fun `memo를 넘기면 Entity에 그대로 실리고 안 넘기면 null이다`() {
        val domain = entity.toDomain()

        assertEquals(entity, domain.toEntity(memo = "옛 메모"))
        assertNull(domain.toEntity().memo)
    }

    @Test
    fun `null 가능 필드와 미분류 폴더는 null 그대로 오간다`() {
        val minimal = PlaceEntity(
            id = "p", name = null, address = null, latitude = null, longitude = null, kakaoPlaceId = null,
            category = null, memo = null, status = ResolveStatus.PENDING, createdAt = 0L, resolvedAt = null, folderId = null,
        )

        assertEquals(minimal, minimal.toDomain().toEntity())
        assertNull(minimal.toDomain().folderId)
    }

    @Test
    fun `모든 상태가 매핑에서 보존된다`() {
        for (status in ResolveStatus.entries) {
            assertEquals(status, entity.copy(status = status).toDomain().toEntity().status)
        }
    }
}
