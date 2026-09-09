package com.github.chsssssss.eonje.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.github.chsssssss.eonje.data.local.EonjeDatabase
import com.github.chsssssss.eonje.data.local.PlaceDao
import com.github.chsssssss.eonje.data.local.PlaceTagCrossRefDao
import com.github.chsssssss.eonje.data.local.PostPlaceCrossRefDao
import com.github.chsssssss.eonje.data.local.SavedPostDao
import com.github.chsssssss.eonje.data.local.TagDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val PRESET_TAGS = listOf(
    "preset-date" to "데이트",
    "preset-solo" to "혼밥",
    "preset-group" to "모임",
    "preset-cafe" to "카페",
)

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): EonjeDatabase =
        Room.databaseBuilder(context, EonjeDatabase::class.java, "eonje.db")
            // Pre-release, no shipped installs yet — destructive migration is fine until 1.0.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    PRESET_TAGS.forEach { (id, name) ->
                        db.execSQL(
                            "INSERT INTO tags (id, name, isPreset) VALUES (?, ?, 1)",
                            arrayOf(id, name),
                        )
                    }
                }
            })
            .build()

    @Provides
    fun provideSavedPostDao(database: EonjeDatabase): SavedPostDao = database.savedPostDao()

    @Provides
    fun providePlaceDao(database: EonjeDatabase): PlaceDao = database.placeDao()

    @Provides
    fun providePostPlaceCrossRefDao(database: EonjeDatabase): PostPlaceCrossRefDao =
        database.postPlaceCrossRefDao()

    @Provides
    fun provideTagDao(database: EonjeDatabase): TagDao = database.tagDao()

    @Provides
    fun providePlaceTagCrossRefDao(database: EonjeDatabase): PlaceTagCrossRefDao =
        database.placeTagCrossRefDao()
}
