package com.github.chsssssss.eonje.di

import com.github.chsssssss.eonje.data.repository.CaptionParsingRepositoryImpl
import com.github.chsssssss.eonje.data.repository.ExtractedCandidateRepositoryImpl
import com.github.chsssssss.eonje.data.repository.FolderRepositoryImpl
import com.github.chsssssss.eonje.data.repository.InstagramMetaLookupRepositoryImpl
import com.github.chsssssss.eonje.data.repository.KakaoLocalRepositoryImpl
import com.github.chsssssss.eonje.data.repository.LocationRepositoryImpl
import com.github.chsssssss.eonje.data.repository.PlaceRepositoryImpl
import com.github.chsssssss.eonje.data.repository.SavedPostRepositoryImpl
import com.github.chsssssss.eonje.domain.repository.CaptionParsingRepository
import com.github.chsssssss.eonje.domain.repository.ExtractedCandidateRepository
import com.github.chsssssss.eonje.domain.repository.FolderRepository
import com.github.chsssssss.eonje.domain.repository.InstagramMetaLookupRepository
import com.github.chsssssss.eonje.domain.repository.KakaoLocalRepository
import com.github.chsssssss.eonje.domain.repository.LocationRepository
import com.github.chsssssss.eonje.domain.repository.PlaceRepository
import com.github.chsssssss.eonje.domain.repository.SavedPostRepository
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
    abstract fun bindLocationRepository(impl: LocationRepositoryImpl): LocationRepository

    @Binds
    @Singleton
    abstract fun bindExtractedCandidateRepository(impl: ExtractedCandidateRepositoryImpl): ExtractedCandidateRepository

    @Binds
    @Singleton
    abstract fun bindCaptionParsingRepository(impl: CaptionParsingRepositoryImpl): CaptionParsingRepository

    @Binds
    @Singleton
    abstract fun bindInstagramMetaLookupRepository(
        impl: InstagramMetaLookupRepositoryImpl
    ): InstagramMetaLookupRepository

    @Binds
    @Singleton
    abstract fun bindFolderRepository(impl: FolderRepositoryImpl): FolderRepository
}
