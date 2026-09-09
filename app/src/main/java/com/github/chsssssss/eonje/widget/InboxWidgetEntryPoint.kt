package com.github.chsssssss.eonje.widget

import com.github.chsssssss.eonje.domain.repository.SavedPostRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * GlanceAppWidget은 Android가 직접 생성하므로 일반적인 생성자 주입이 닿지 않는다.
 * Hilt의 EntryPoint로 앱 싱글톤 그래프에서 필요한 의존성만 꺼내온다.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface InboxWidgetEntryPoint {
    fun savedPostRepository(): SavedPostRepository
}
