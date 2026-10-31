package com.github.chsssssss.eonje.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.github.chsssssss.eonje.data.analytics.Events
import dagger.hilt.android.EntryPointAccessors

/**
 * '오늘의 맛집' 위젯 Receiver. 클래스 이름은 7단계(랜덤 추천 위젯) 그대로 둔다 — 홈 화면에 놓인 위젯은 이
 * 컴포넌트 이름에 묶여 있어서, 이름을 바꾸면 업데이트 후 사용자의 기존 위젯이 깨진다.
 */
class PlaceRecommendationWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayPlaceWidget()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        // 앱 업데이트 전부터 위젯을 둔 사용자는 onEnabled가 다시 오지 않으므로 여기서도 예약한다(KEEP이라 중복 없음).
        TodayPlaceWidgetWorker.schedule(context)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        TodayPlaceWidgetWorker.schedule(context)
        logEvent(context, Events.WIDGET_ADDED)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        TodayPlaceWidgetWorker.cancel(context)
        logEvent(context, Events.WIDGET_REMOVED)
    }

    private fun logEvent(context: Context, name: String) {
        runCatching {
            EntryPointAccessors.fromApplication(context, TodayPlaceWidgetEntryPoint::class.java)
                .analytics()
                .logEvent(name, null)
        }
    }
}
