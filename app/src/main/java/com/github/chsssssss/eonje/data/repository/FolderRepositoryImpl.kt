package com.github.chsssssss.eonje.data.repository

import com.github.chsssssss.eonje.data.local.FolderDao
import com.github.chsssssss.eonje.data.local.FolderEntity
import com.github.chsssssss.eonje.domain.repository.FolderRepository
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject

class FolderRepositoryImpl @Inject constructor(
    private val folderDao: FolderDao,
) : FolderRepository {

    override fun observeAll(): Flow<List<FolderEntity>> = folderDao.observeAll()

    override suspend fun findOrCreateByName(name: String): FolderEntity {
        val trimmed = name.trim()
        folderDao.findByName(trimmed)?.let { return it }
        val folder = FolderEntity(id = UUID.randomUUID().toString(), name = trimmed, createdAt = System.currentTimeMillis())
        folderDao.insert(folder)
        return folder
    }
}
