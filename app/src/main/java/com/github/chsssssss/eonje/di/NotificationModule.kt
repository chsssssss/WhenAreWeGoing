package com.github.chsssssss.eonje.di

import com.github.chsssssss.eonje.data.notification.PlaceSavedNotifierImpl
import com.github.chsssssss.eonje.domain.notification.PlaceSavedNotifier
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationModule {
    @Binds
    @Singleton
    abstract fun bindPlaceSavedNotifier(impl: PlaceSavedNotifierImpl): PlaceSavedNotifier
}
