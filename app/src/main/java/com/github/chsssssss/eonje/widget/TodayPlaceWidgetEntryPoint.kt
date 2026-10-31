package com.github.chsssssss.eonje.widget

import com.github.chsssssss.eonje.domain.repository.FolderRepository
import com.github.chsssssss.eonje.domain.repository.PlaceRepository
import com.google.firebase.analytics.FirebaseAnalytics
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * GlanceAppWidget·AppWidgetProvider는 Android가 직접 생성하므로 일반적인 생성자 주입이 닿지 않는다.
 * Hilt의 EntryPoint로 앱 싱글톤 그래프에서 필요한 의존성만 꺼내온다. 위젯은 Room 읽기만 한다 — 네트워크 없음.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface TodayPlaceWidgetEntryPoint {
    fun placeRepository(): PlaceRepository
    fun folderRepository(): FolderRepository
    fun analytics(): FirebaseAnalytics
}
