package com.github.chsssssss.eonje.di

import com.github.chsssssss.eonje.data.remote.instagram.InstagramBusinessDiscoveryApi
import com.github.chsssssss.eonje.data.remote.kakao.KakaoLocalApi
import com.github.chsssssss.eonje.data.remote.llm.ClaudeApi
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import javax.inject.Qualifier
import javax.inject.Singleton

private const val KAKAO_BASE_URL = "https://dapi.kakao.com/"
private const val INSTAGRAM_BASE_URL = "https://graph.facebook.com/"
private const val CLAUDE_BASE_URL = "https://api.anthropic.com/"

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class KakaoRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class InstagramRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ClaudeRetrofit

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json { ignoreUnknownKeys = true }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(
            HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
        )
        .build()

    @Provides
    @Singleton
    @KakaoRetrofit
    fun provideKakaoRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(KAKAO_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun provideKakaoLocalApi(@KakaoRetrofit retrofit: Retrofit): KakaoLocalApi =
        retrofit.create(KakaoLocalApi::class.java)

    @Provides
    @Singleton
    @InstagramRetrofit
    fun provideInstagramRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(INSTAGRAM_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun provideInstagramBusinessDiscoveryApi(
        @InstagramRetrofit retrofit: Retrofit
    ): InstagramBusinessDiscoveryApi = retrofit.create(InstagramBusinessDiscoveryApi::class.java)

    @Provides
    @Singleton
    @ClaudeRetrofit
    fun provideClaudeRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(CLAUDE_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun provideClaudeApi(@ClaudeRetrofit retrofit: Retrofit): ClaudeApi =
        retrofit.create(ClaudeApi::class.java)
}
