package com.github.chsssssss.eonje.data.mapper

import com.github.chsssssss.eonje.data.local.FolderEntity
import com.github.chsssssss.eonje.domain.model.Folder
import com.github.chsssssss.eonje.domain.model.FolderVisuals
import org.junit.Assert.assertEquals
import org.junit.Test

class FolderMapperTest {

    @Test
    fun `Entity와 도메인이 모든 필드를 그대로 주고받는다`() {
        val entity = FolderEntity(id = "f1", name = "성수 맛집", createdAt = 1_700_000_000_000L, color = 0xFF1E88E5.toInt(), iconKey = "☕")

        val domain = entity.toDomain()

        assertEquals(Folder(id = "f1", name = "성수 맛집", createdAt = 1_700_000_000_000L, color = 0xFF1E88E5.toInt(), iconKey = "☕"), domain)
        assertEquals(entity, domain.toEntity())
    }

    @Test
    fun `기본 색상과 아이콘은 Entity와 도메인이 같다`() {
        val entity = FolderEntity(id = "f2", name = "회사 근처", createdAt = 0L)
        val domain = Folder(id = "f2", name = "회사 근처", createdAt = 0L)

        assertEquals(FolderVisuals.DEFAULT_COLOR, domain.color)
        assertEquals(FolderVisuals.DEFAULT_ICON, domain.iconKey)
        assertEquals(entity.toDomain(), domain)
        assertEquals(entity, domain.toEntity())
    }
}
