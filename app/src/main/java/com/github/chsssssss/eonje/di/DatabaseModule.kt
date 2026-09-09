package com.github.chsssssss.eonje.di

import android.content.Context
import androidx.room.Room
import com.github.chsssssss.eonje.data.local.EonjeDatabase
import com.github.chsssssss.eonje.data.local.PlaceDao
import com.github.chsssssss.eonje.data.local.PostPlaceCrossRefDao
import com.github.chsssssss.eonje.data.local.SavedPostDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): EonjeDatabase =
        Room.databaseBuilder(context, EonjeDatabase::class.java, "eonje.db")
            // Pre-release, no shipped installs yet — destructive migration is fine until 1.0.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideSavedPostDao(database: EonjeDatabase): SavedPostDao = database.savedPostDao()

    @Provides
    fun providePlaceDao(database: EonjeDatabase): PlaceDao = database.placeDao()

    @Provides
    fun providePostPlaceCrossRefDao(database: EonjeDatabase): PostPlaceCrossRefDao =
        database.postPlaceCrossRefDao()
}
