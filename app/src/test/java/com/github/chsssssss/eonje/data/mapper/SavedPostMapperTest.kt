package com.github.chsssssss.eonje.data.mapper

import com.github.chsssssss.eonje.data.local.Converters
import com.github.chsssssss.eonje.data.local.SavedPostEntity
import com.github.chsssssss.eonje.domain.model.ResolveStatus
import com.github.chsssssss.eonje.domain.model.SavedPost
import com.github.chsssssss.eonje.domain.model.UnresolvedReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SavedPostMapperTest {

    private val entity = SavedPostEntity(
        id = "post-1",
        shortcode = "Cabc123",
        instagramUrl = "https://www.instagram.com/p/Cabc123/",
        caption = "성수동 맛집",
        thumbnailUrl = "file:///data/user/0/app/files/thumbnails/post-1.jpg",
        status = ResolveStatus.UNRESOLVED,
        extractedCount = 2,
        createdAt = 1_700_000_000_000L,
        unresolvedReason = UnresolvedReason.NETWORK_ERROR,
    )

    @Test
    fun `Entity를 도메인으로 바꿔도 모든 필드가 그대로다`() {
        assertEquals(
            SavedPost(
                id = "post-1",
                shortcode = "Cabc123",
                instagramUrl = "https://www.instagram.com/p/Cabc123/",
                caption = "성수동 맛집",
                thumbnailUrl = "file:///data/user/0/app/files/thumbnails/post-1.jpg",
                status = ResolveStatus.UNRESOLVED,
                extractedCount = 2,
                createdAt = 1_700_000_000_000L,
                unresolvedReason = UnresolvedReason.NETWORK_ERROR,
            ),
            entity.toDomain(),
        )
    }

    @Test
    fun `도메인을 Entity로 되돌려도 같다`() {
        assertEquals(entity, entity.toDomain().toEntity())
    }

    @Test
    fun `null 가능 필드는 null 그대로 오간다`() {
        val minimal = SavedPostEntity(
            id = "p", shortcode = null, instagramUrl = "https://www.instagram.com/p/x/", caption = null,
            thumbnailUrl = null, status = ResolveStatus.PENDING, extractedCount = 0, createdAt = 0L, unresolvedReason = null,
        )

        val domain = minimal.toDomain()

        assertNull(domain.shortcode)
        assertNull(domain.caption)
        assertNull(domain.thumbnailUrl)
        assertNull(domain.unresolvedReason)
        assertEquals(minimal, domain.toEntity())
    }

    @Test
    fun `모든 상태와 사유가 매핑에서 보존된다`() {
        for (status in ResolveStatus.entries) {
            assertEquals(status, entity.copy(status = status).toDomain().toEntity().status)
        }
        for (reason in UnresolvedReason.entries) {
            assertEquals(reason, entity.copy(unresolvedReason = reason).toDomain().toEntity().unresolvedReason)
        }
    }

    @Test
    fun `상태와 사유는 저장될 때 enum 이름 문자열이 되고 그대로 복원된다`() {
        val converters = Converters()
        for (status in ResolveStatus.entries) {
            assertEquals(status.name, converters.fromResolveStatus(status))
            assertEquals(status, converters.toResolveStatus(converters.fromResolveStatus(status)))
        }
        for (reason in UnresolvedReason.entries) {
            assertEquals(reason.name, converters.fromUnresolvedReason(reason))
            assertEquals(reason, converters.toUnresolvedReason(converters.fromUnresolvedReason(reason)))
        }
        assertNull(converters.fromUnresolvedReason(null))
        assertNull(converters.toUnresolvedReason(null))
    }
}
