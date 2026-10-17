package com.github.chsssssss.eonje.di

import android.content.Context
import com.google.firebase.analytics.FirebaseAnalytics
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AnalyticsModule {
    // Firebase.analytics(KTX)는 기본 FirebaseApp(=Gemini 프로젝트)이 없으면 예외를 던지므로 쓰지 않는다.
    // getInstance(context)는 FirebaseApp과 무관하게 google_app_id 리소스(wherewegoing)로만 동작한다.
    @Provides
    @Singleton
    fun provideFirebaseAnalytics(@ApplicationContext context: Context): FirebaseAnalytics =
        FirebaseAnalytics.getInstance(context)
}
