package com.github.chsssssss.eonje.di

import com.github.chsssssss.eonje.data.repository.KakaoLocalRepositoryImpl
import com.github.chsssssss.eonje.data.repository.PlaceRepositoryImpl
import com.github.chsssssss.eonje.data.repository.SavedPostRepositoryImpl
import com.github.chsssssss.eonje.data.repository.TagRepositoryImpl
import com.github.chsssssss.eonje.domain.repository.KakaoLocalRepository
import com.github.chsssssss.eonje.domain.repository.PlaceRepository
import com.github.chsssssss.eonje.domain.repository.SavedPostRepository
import com.github.chsssssss.eonje.domain.repository.TagRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindSavedPostRepository(impl: SavedPostRepositoryImpl): SavedPostRepository

    @Binds
    @Singleton
    abstract fun bindPlaceRepository(impl: PlaceRepositoryImpl): PlaceRepository

    @Binds
    @Singleton
    abstract fun bindKakaoLocalRepository(impl: KakaoLocalRepositoryImpl): KakaoLocalRepository

    @Binds
    @Singleton
    abstract fun bindTagRepository(impl: TagRepositoryImpl): TagRepository
}
