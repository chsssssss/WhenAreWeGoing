package com.github.chsssssss.eonje.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * 날짜가 바뀌면 위젯을 한 번 다시 그려 오늘의 장소로 바꾼다. 하루 1회 주기 작업이고 Room만 읽는다.
 * 기기가 잠들어 있으면 WorkManager가 늦게 실행할 수 있지만, 선택은 날짜 기준이라 그려지는 순간엔 항상 맞는 장소가 나온다.
 */
class TodayPlaceWidgetWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        TodayPlaceWidget().updateAll(applicationContext)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "today_place_widget_daily"

        /** 이미 예약돼 있으면 그대로 둔다(KEEP) — 위젯 갱신 때마다 불러도 비용이 없다. */
        fun schedule(context: Context) {
            val now = LocalDateTime.now()
            // 자정 직후 몇 분 여유를 둬서 날짜 경계에서 어제 날짜로 계산되는 일을 피한다.
            val nextRun = LocalDate.now().plusDays(1).atStartOfDay().plusMinutes(5)
            val request = PeriodicWorkRequestBuilder<TodayPlaceWidgetWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(Duration.between(now, nextRun).toMillis(), TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
