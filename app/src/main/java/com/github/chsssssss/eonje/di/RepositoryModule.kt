package com.github.chsssssss.eonje.di

import com.github.chsssssss.eonje.data.repository.CaptionParsingRepositoryImpl
import com.github.chsssssss.eonje.data.repository.RoomExtractedCandidateRepository
import com.github.chsssssss.eonje.data.repository.RoomFolderRepository
import com.github.chsssssss.eonje.data.repository.InstagramMetaLookupRepositoryImpl
import com.github.chsssssss.eonje.data.repository.KakaoLocalRepositoryImpl
import com.github.chsssssss.eonje.data.repository.LocationRepositoryImpl
import com.github.chsssssss.eonje.data.repository.OnboardingRepositoryImpl
import com.github.chsssssss.eonje.data.repository.RoomPlaceRepository
import com.github.chsssssss.eonje.data.repository.RoomSavedPostRepository
import com.github.chsssssss.eonje.data.repository.ThemePreferenceRepositoryImpl
import com.github.chsssssss.eonje.domain.repository.CaptionParsingRepository
import com.github.chsssssss.eonje.domain.repository.ExtractedCandidateRepository
import com.github.chsssssss.eonje.domain.repository.FolderRepository
import com.github.chsssssss.eonje.domain.repository.InstagramMetaLookupRepository
import com.github.chsssssss.eonje.domain.repository.KakaoLocalRepository
import com.github.chsssssss.eonje.domain.repository.LocationRepository
import com.github.chsssssss.eonje.domain.repository.OnboardingRepository
import com.github.chsssssss.eonje.domain.repository.PlaceRepository
import com.github.chsssssss.eonje.domain.repository.PlacesChangedNotifier
import com.github.chsssssss.eonje.widget.TodayPlaceWidgetUpdater
import com.github.chsssssss.eonje.domain.repository.SavedPostRepository
import com.github.chsssssss.eonje.domain.repository.ThemePreferenceRepository
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
    abstract fun bindSavedPostRepository(impl: RoomSavedPostRepository): SavedPostRepository

    @Binds
    @Singleton
    abstract fun bindPlaceRepository(impl: RoomPlaceRepository): PlaceRepository

    @Binds
    @Singleton
    abstract fun bindKakaoLocalRepository(impl: KakaoLocalRepositoryImpl): KakaoLocalRepository

    @Binds
    @Singleton
    abstract fun bindLocationRepository(impl: LocationRepositoryImpl): LocationRepository

    @Binds
    @Singleton
    abstract fun bindExtractedCandidateRepository(impl: RoomExtractedCandidateRepository): ExtractedCandidateRepository

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
    abstract fun bindFolderRepository(impl: RoomFolderRepository): FolderRepository

    @Binds
    @Singleton
    abstract fun bindPlacesChangedNotifier(impl: TodayPlaceWidgetUpdater): PlacesChangedNotifier

    @Binds
    @Singleton
    abstract fun bindThemePreferenceRepository(
        impl: ThemePreferenceRepositoryImpl
    ): ThemePreferenceRepository

    @Binds
    @Singleton
    abstract fun bindOnboardingRepository(impl: OnboardingRepositoryImpl): OnboardingRepository
}
