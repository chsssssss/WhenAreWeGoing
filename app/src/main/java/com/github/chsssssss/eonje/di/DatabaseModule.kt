package com.github.chsssssss.eonje.di

import android.content.Context
import androidx.room.Room
import com.github.chsssssss.eonje.data.local.EonjeDatabase
import com.github.chsssssss.eonje.data.local.ExtractedCandidateDao
import com.github.chsssssss.eonje.data.local.FolderDao
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
        // 1.0 출시 기준 스키마는 v9(app/schemas/.../9.json에 고정). 이제 사용자 데이터가 있으므로 파괴적 마이그레이션은
        // 쓰지 않는다 — 스키마를 바꿀 땐 version을 올리고 Migration(또는 AutoMigration)을 반드시 함께 추가할 것.
        // 빠뜨리면 데이터를 조용히 지우는 대신 앱 시작 시 IllegalStateException으로 바로 드러난다.
        Room.databaseBuilder(context, EonjeDatabase::class.java, "eonje.db")
            .build()

    @Provides
    fun provideSavedPostDao(database: EonjeDatabase): SavedPostDao = database.savedPostDao()

    @Provides
    fun providePlaceDao(database: EonjeDatabase): PlaceDao = database.placeDao()

    @Provides
    fun providePostPlaceCrossRefDao(database: EonjeDatabase): PostPlaceCrossRefDao =
        database.postPlaceCrossRefDao()

    @Provides
    fun provideExtractedCandidateDao(database: EonjeDatabase): ExtractedCandidateDao =
        database.extractedCandidateDao()

    @Provides
    fun provideFolderDao(database: EonjeDatabase): FolderDao = database.folderDao()
}
