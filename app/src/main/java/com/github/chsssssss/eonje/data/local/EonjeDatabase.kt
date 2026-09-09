package com.github.chsssssss.eonje.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        SavedPostEntity::class,
        PlaceEntity::class,
        PostPlaceCrossRef::class,
        TagEntity::class,
        PlaceTagCrossRef::class,
    ],
    version = 3,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class EonjeDatabase : RoomDatabase() {
    abstract fun savedPostDao(): SavedPostDao
    abstract fun placeDao(): PlaceDao
    abstract fun postPlaceCrossRefDao(): PostPlaceCrossRefDao
    abstract fun tagDao(): TagDao
    abstract fun placeTagCrossRefDao(): PlaceTagCrossRefDao
}
