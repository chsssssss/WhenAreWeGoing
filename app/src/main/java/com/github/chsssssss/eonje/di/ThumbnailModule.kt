package com.github.chsssssss.eonje.di

import com.github.chsssssss.eonje.data.local.ThumbnailMaintenanceState
import com.github.chsssssss.eonje.data.local.ThumbnailMaintenanceStateImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ThumbnailModule {
    @Binds
    @Singleton
    abstract fun bindThumbnailMaintenanceState(impl: ThumbnailMaintenanceStateImpl): ThumbnailMaintenanceState
}
