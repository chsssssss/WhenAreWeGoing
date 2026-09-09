package com.github.chsssssss.eonje.data.repository

import com.github.chsssssss.eonje.data.local.PlaceTagCrossRef
import com.github.chsssssss.eonje.data.local.PlaceTagCrossRefDao
import com.github.chsssssss.eonje.data.local.TagDao
import com.github.chsssssss.eonje.data.local.TagEntity
import com.github.chsssssss.eonje.domain.repository.TagRepository
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject

class TagRepositoryImpl @Inject constructor(
    private val tagDao: TagDao,
    private val crossRefDao: PlaceTagCrossRefDao,
) : TagRepository {

    override fun observeAll(): Flow<List<TagEntity>> = tagDao.observeAll()

    override fun observeTagsForPlace(placeId: String): Flow<List<TagEntity>> =
        tagDao.observeTagsForPlace(placeId)

    override fun observePlaceIdsForTag(tagId: String): Flow<List<String>> =
        crossRefDao.observePlaceIdsForTag(tagId)

    override suspend fun findOrCreateByName(name: String): TagEntity {
        val trimmed = name.trim()
        tagDao.findByName(trimmed)?.let { return it }
        val tag = TagEntity(id = UUID.randomUUID().toString(), name = trimmed, isPreset = false)
        tagDao.insert(tag)
        return tag
    }

    override suspend fun attachTagToPlace(placeId: String, tagId: String) =
        crossRefDao.insert(PlaceTagCrossRef(placeId = placeId, tagId = tagId))

    override suspend fun detachTagFromPlace(placeId: String, tagId: String) =
        crossRefDao.delete(placeId, tagId)
}
