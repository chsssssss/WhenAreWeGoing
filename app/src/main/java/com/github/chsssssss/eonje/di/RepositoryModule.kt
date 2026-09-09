package com.github.chsssssss.eonje.di

import com.github.chsssssss.eonje.data.repository.CachedMediaRepositoryImpl
import com.github.chsssssss.eonje.data.repository.CaptionParsingRepositoryImpl
import com.github.chsssssss.eonje.data.repository.ExtractedCandidateRepositoryImpl
import com.github.chsssssss.eonje.data.repository.InstagramBusinessDiscoveryRepositoryImpl
import com.github.chsssssss.eonje.data.repository.KakaoLocalRepositoryImpl
import com.github.chsssssss.eonje.data.repository.PlaceRepositoryImpl
import com.github.chsssssss.eonje.data.repository.SavedPostRepositoryImpl
import com.github.chsssssss.eonje.data.repository.TagRepositoryImpl
import com.github.chsssssss.eonje.data.repository.WatchedAccountRepositoryImpl
import com.github.chsssssss.eonje.domain.repository.CachedMediaRepository
import com.github.chsssssss.eonje.domain.repository.CaptionParsingRepository
import com.github.chsssssss.eonje.domain.repository.ExtractedCandidateRepository
import com.github.chsssssss.eonje.domain.repository.InstagramBusinessDiscoveryRepository
import com.github.chsssssss.eonje.domain.repository.KakaoLocalRepository
import com.github.chsssssss.eonje.domain.repository.PlaceRepository
import com.github.chsssssss.eonje.domain.repository.SavedPostRepository
import com.github.chsssssss.eonje.domain.repository.TagRepository
import com.github.chsssssss.eonje.domain.repository.WatchedAccountRepository
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

    @Binds
    @Singleton
    abstract fun bindWatchedAccountRepository(impl: WatchedAccountRepositoryImpl): WatchedAccountRepository

    @Binds
    @Singleton
    abstract fun bindCachedMediaRepository(impl: CachedMediaRepositoryImpl): CachedMediaRepository

    @Binds
    @Singleton
    abstract fun bindExtractedCandidateRepository(impl: ExtractedCandidateRepositoryImpl): ExtractedCandidateRepository

    @Binds
    @Singleton
    abstract fun bindInstagramBusinessDiscoveryRepository(
        impl: InstagramBusinessDiscoveryRepositoryImpl
    ): InstagramBusinessDiscoveryRepository

    @Binds
    @Singleton
    abstract fun bindCaptionParsingRepository(impl: CaptionParsingRepositoryImpl): CaptionParsingRepository
}
