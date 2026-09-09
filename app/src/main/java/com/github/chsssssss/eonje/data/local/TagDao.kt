package com.github.chsssssss.eonje.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tag: TagEntity)

    @Query("SELECT * FROM tags WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): TagEntity?

    @Query("SELECT * FROM tags ORDER BY isPreset DESC, name ASC")
    fun observeAll(): Flow<List<TagEntity>>

    @Query(
        """
        SELECT tags.* FROM tags
        INNER JOIN PlaceTagCrossRef ON tags.id = PlaceTagCrossRef.tagId
        WHERE PlaceTagCrossRef.placeId = :placeId
        ORDER BY tags.name ASC
        """
    )
    fun observeTagsForPlace(placeId: String): Flow<List<TagEntity>>
}
