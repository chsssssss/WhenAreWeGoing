package com.github.chsssssss.eonje.data.repository

import com.github.chsssssss.eonje.data.local.FolderDao
import com.github.chsssssss.eonje.data.local.FolderEntity
import com.github.chsssssss.eonje.data.local.PlaceDao
import com.github.chsssssss.eonje.domain.repository.FolderRepository
import com.github.chsssssss.eonje.domain.repository.PlacesChangedNotifier
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject

class FolderRepositoryImpl @Inject constructor(
    private val folderDao: FolderDao,
    private val placeDao: PlaceDao,
    private val placesChangedNotifier: PlacesChangedNotifier,
) : FolderRepository {

    override fun observeAll(): Flow<List<FolderEntity>> = folderDao.observeAll()

    override suspend fun findOrCreateByName(name: String): FolderEntity {
        val trimmed = name.trim()
        folderDao.findByName(trimmed)?.let { return it }
        val folder = FolderEntity(id = UUID.randomUUID().toString(), name = trimmed, createdAt = System.currentTimeMillis())
        folderDao.insert(folder)
        return folder
    }

    override suspend fun createWithAppearance(name: String, color: Int, iconKey: String): FolderEntity {
        val trimmed = name.trim()
        val existing = folderDao.findByName(trimmed)
        if (existing != null) {
            folderDao.updateAppearance(existing.id, color, iconKey)
            return existing.copy(color = color, iconKey = iconKey)
        }
        val folder = FolderEntity(
            id = UUID.randomUUID().toString(),
            name = trimmed,
            createdAt = System.currentTimeMillis(),
            color = color,
            iconKey = iconKey,
        )
        folderDao.insert(folder)
        return folder
    }

    override suspend fun delete(folderId: String) {
        placeDao.clearFolder(folderId)
        folderDao.delete(folderId)
        // 이 폴더에 있던 장소는 미분류가 된다 — 위젯의 폴더 줄도 바뀌어야 한다.
        placesChangedNotifier.notifyPlacesChanged()
    }
}
