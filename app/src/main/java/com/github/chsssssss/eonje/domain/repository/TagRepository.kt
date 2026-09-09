package com.github.chsssssss.eonje.domain.repository

import com.github.chsssssss.eonje.data.local.TagEntity
import kotlinx.coroutines.flow.Flow

interface TagRepository {
    fun observeAll(): Flow<List<TagEntity>>
    fun observeTagsForPlace(placeId: String): Flow<List<TagEntity>>
    fun observePlaceIdsForTag(tagId: String): Flow<List<String>>

    /** Reuses an existing tag by name rather than creating a duplicate. */
    suspend fun findOrCreateByName(name: String): TagEntity

    suspend fun attachTagToPlace(placeId: String, tagId: String)
    suspend fun detachTagFromPlace(placeId: String, tagId: String)
}
