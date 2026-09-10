package com.github.chsssssss.eonje.di

import com.github.chsssssss.eonje.data.remote.instagram.InstagramBusinessDiscoveryApi
import com.github.chsssssss.eonje.data.remote.kakao.KakaoLocalApi
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
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

private const val KAKAO_BASE_URL = "https://dapi.kakao.com/"
private const val INSTAGRAM_BASE_URL = "https://graph.facebook.com/"

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class KakaoRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class InstagramRetrofit

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
    fun provideInstagramRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit {
        // Business Discovery의 media.limit(N){children{...}}} 확장 쿼리는 Meta 쪽 처리에
        // 기본 10초 타임아웃을 넘기는 경우가 흔해서, 이 API만 넉넉하게 늘려준다.
        val instagramOkHttpClient = okHttpClient.newBuilder()
            .callTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        return Retrofit.Builder()
            .baseUrl(INSTAGRAM_BASE_URL)
            .client(instagramOkHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    fun provideInstagramBusinessDiscoveryApi(
        @InstagramRetrofit retrofit: Retrofit
    ): InstagramBusinessDiscoveryApi = retrofit.create(InstagramBusinessDiscoveryApi::class.java)
}
