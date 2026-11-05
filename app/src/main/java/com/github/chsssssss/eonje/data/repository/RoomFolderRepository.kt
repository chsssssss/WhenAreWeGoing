package com.github.chsssssss.eonje.data.repository

import com.github.chsssssss.eonje.data.local.FolderDao
import com.github.chsssssss.eonje.data.local.PlaceDao
import com.github.chsssssss.eonje.data.mapper.toDomain
import com.github.chsssssss.eonje.data.mapper.toEntity
import com.github.chsssssss.eonje.domain.model.Folder
import com.github.chsssssss.eonje.domain.repository.FolderRepository
import com.github.chsssssss.eonje.domain.repository.PlacesChangedNotifier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject

class RoomFolderRepository @Inject constructor(
    private val folderDao: FolderDao,
    private val placeDao: PlaceDao,
    private val placesChangedNotifier: PlacesChangedNotifier,
) : FolderRepository {

    override fun observeAll(): Flow<List<Folder>> = folderDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun loadCachedAll(): List<Folder> = folderDao.observeAll().first().map { it.toDomain() }

    override suspend fun findOrCreateByName(name: String): Folder {
        val trimmed = name.trim()
        folderDao.findByName(trimmed)?.let { return it.toDomain() }
        val folder = Folder(id = UUID.randomUUID().toString(), name = trimmed, createdAt = System.currentTimeMillis())
        folderDao.insert(folder.toEntity())
        return folder
    }

    override suspend fun createWithAppearance(name: String, color: Int, iconKey: String): Folder {
        val trimmed = name.trim()
        val existing = folderDao.findByName(trimmed)
        if (existing != null) {
            folderDao.updateAppearance(existing.id, color, iconKey)
            return existing.toDomain().copy(color = color, iconKey = iconKey)
        }
        val folder = Folder(
            id = UUID.randomUUID().toString(),
            name = trimmed,
            createdAt = System.currentTimeMillis(),
            color = color,
            iconKey = iconKey,
        )
        folderDao.insert(folder.toEntity())
        return folder
    }

    override suspend fun delete(folderId: String) {
        placeDao.clearFolder(folderId)
        folderDao.delete(folderId)
        // 이 폴더에 있던 장소는 미분류가 된다 — 위젯의 폴더 줄도 바뀌어야 한다.
        placesChangedNotifier.notifyPlacesChanged()
    }
}
