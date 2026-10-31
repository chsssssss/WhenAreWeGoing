package com.github.chsssssss.eonje.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll
import com.github.chsssssss.eonje.domain.repository.PlacesChangedNotifier
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 장소가 저장·삭제되거나 폴더가 바뀌면 '오늘의 맛집' 위젯을 다시 그린다. 놓인 위젯이 없으면 updateAll은 바로 끝난다.
 * 저장 흐름(워커 자동 확정, 정리 화면, 홈)을 기다리게 하지 않도록 별도 스코프에서 던져두고 실패는 삼킨다.
 */
@Singleton
class TodayPlaceWidgetUpdater @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : PlacesChangedNotifier {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun notifyPlacesChanged() {
        scope.launch {
            runCatching { TodayPlaceWidget().updateAll(context) }
                .onFailure { Log.w(TAG, "widget update failed", it) }
        }
    }

    private companion object {
        const val TAG = "TodayPlaceWidget"
    }
}
