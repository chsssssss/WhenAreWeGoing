package com.github.chsssssss.eonje.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaceTagCrossRefDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(crossRef: PlaceTagCrossRef)

    @Query("DELETE FROM PlaceTagCrossRef WHERE placeId = :placeId AND tagId = :tagId")
    suspend fun delete(placeId: String, tagId: String)

    @Query("SELECT DISTINCT placeId FROM PlaceTagCrossRef WHERE tagId = :tagId")
    fun observePlaceIdsForTag(tagId: String): Flow<List<String>>
}
