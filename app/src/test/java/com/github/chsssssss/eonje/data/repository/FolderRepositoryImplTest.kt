package com.github.chsssssss.eonje.data.repository

import com.github.chsssssss.eonje.data.local.FolderDao
import com.github.chsssssss.eonje.data.local.FolderEntity
import com.github.chsssssss.eonje.data.local.PlaceDao
import com.github.chsssssss.eonje.data.local.PlaceEntity
import com.github.chsssssss.eonje.domain.model.Folder
import com.github.chsssssss.eonje.domain.model.FolderVisuals
import com.github.chsssssss.eonje.domain.model.ResolveStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 폴더 저장소의 동작(이름 재사용, 외형 갱신, 삭제 시 미분류 처리)이 도메인 모델 전환 뒤에도 그대로인지 확인한다. */
class FolderRepositoryImplTest {

    private val folderDao = FakeFolderDao()
    private val placeDao = FakePlaceDao()
    private var notified = 0
    private val repository = FolderRepositoryImpl(folderDao, placeDao, placesChangedNotifier = { notified++ })

    @Test
    fun `findOrCreateByName은 공백을 정리하고 같은 이름이면 기존 폴더를 돌려준다`() = runBlocking {
        val created = repository.findOrCreateByName("  성수 맛집 ")
        val again = repository.findOrCreateByName("성수 맛집")

        assertEquals("성수 맛집", created.name)
        assertEquals(FolderVisuals.DEFAULT_COLOR, created.color)
        assertEquals(created, again)
        assertEquals("두 번째는 새로 만들지 않는다", 1, folderDao.rows.size)
    }

    @Test
    fun `createWithAppearance는 새 폴더에 색상과 아이콘을 저장한다`() = runBlocking {
        val folder = repository.createWithAppearance("카페", 0xFF1E88E5.toInt(), "☕")

        assertEquals(Folder(folder.id, "카페", folder.createdAt, 0xFF1E88E5.toInt(), "☕"), folder)
        assertEquals("☕", folderDao.rows.single().iconKey)
    }

    @Test
    fun `같은 이름이 있으면 createWithAppearance는 새로 만들지 않고 색상과 아이콘만 갱신해 돌려준다`() = runBlocking {
        val first = repository.createWithAppearance("카페", 0xFFE53935.toInt(), "📍")

        val updated = repository.createWithAppearance("카페", 0xFF43A047.toInt(), "☕")

        assertEquals(first.id, updated.id)
        assertEquals(0xFF43A047.toInt(), updated.color)
        assertEquals("☕", updated.iconKey)
        assertEquals(1, folderDao.rows.size)
        assertEquals("☕", folderDao.rows.single().iconKey)
    }

    @Test
    fun `폴더를 지우면 그 폴더의 장소는 미분류가 되고 위젯에 알린다`() = runBlocking {
        val folder = repository.findOrCreateByName("지울 폴더")
        val other = repository.findOrCreateByName("남길 폴더")
        placeDao.rows += place("p1", folder.id)
        placeDao.rows += place("p2", other.id)

        repository.delete(folder.id)

        assertNull(placeDao.rows.first { it.id == "p1" }.folderId)
        assertEquals(other.id, placeDao.rows.first { it.id == "p2" }.folderId)
        assertEquals(listOf("남길 폴더"), repository.observeAll().first().map { it.name })
        assertTrue(notified > 0)
    }

    private fun place(id: String, folderId: String?) = PlaceEntity(
        id = id, name = id, address = null, latitude = null, longitude = null, kakaoPlaceId = null,
        category = null, memo = null, status = ResolveStatus.RESOLVED, createdAt = 0L, resolvedAt = 0L, folderId = folderId,
    )

    private class FakeFolderDao : FolderDao {
        val rows = mutableListOf<FolderEntity>()
        override suspend fun insert(folder: FolderEntity) {
            if (rows.none { it.id == folder.id }) rows += folder
        }
        override suspend fun findByName(name: String): FolderEntity? = rows.firstOrNull { it.name == name }
        override fun observeAll(): Flow<List<FolderEntity>> = MutableStateFlow(rows.sortedBy { it.name })
        override suspend fun updateAppearance(id: String, color: Int, iconKey: String) {
            val i = rows.indexOfFirst { it.id == id }
            if (i >= 0) rows[i] = rows[i].copy(color = color, iconKey = iconKey)
        }
        override suspend fun delete(id: String) {
            rows.removeAll { it.id == id }
        }
    }

    private class FakePlaceDao : PlaceDao {
        val rows = mutableListOf<PlaceEntity>()
        override suspend fun insert(place: PlaceEntity) { rows += place }
        override suspend fun findByKakaoPlaceId(kakaoPlaceId: String): PlaceEntity? = null
        override suspend fun findById(id: String): PlaceEntity? = rows.firstOrNull { it.id == id }
        override fun observeAll(): Flow<List<PlaceEntity>> = MutableStateFlow(rows.toList())
        override suspend fun updateFolder(placeId: String, folderId: String?) = Unit
        override suspend fun updateFolderForPlaces(placeIds: List<String>, folderId: String?) = Unit
        override suspend fun clearFolder(folderId: String) {
            rows.replaceAll { if (it.folderId == folderId) it.copy(folderId = null) else it }
        }
        override suspend fun delete(id: String) { rows.removeAll { it.id == id } }
    }
}
