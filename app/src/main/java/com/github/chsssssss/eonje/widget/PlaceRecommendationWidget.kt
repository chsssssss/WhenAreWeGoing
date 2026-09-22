package com.github.chsssssss.eonje.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.action.ActionParameters
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.github.chsssssss.eonje.MainActivity
import com.github.chsssssss.eonje.data.local.PlaceEntity
import com.github.chsssssss.eonje.domain.model.ResolveStatus
import com.github.chsssssss.eonje.domain.repository.PlaceRepository
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first

private val WidgetBackground = Color(0xFF1B1D21)
private val WidgetTextPrimary = Color(0xFFE6E7EA)
private val WidgetAccent = Color(0xFFF2A65A)
private val WidgetMuted = Color(0xFF7C828B)

/** 저장된 장소 중 하나를 랜덤으로 추천하는 홈 화면 위젯. "오늘 뭐 먹지"용 진입점. */
class PlaceRecommendationWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = repositoryFrom(context)
        val place = repository.observeAll().first()
            .filter { it.status == ResolveStatus.RESOLVED && !it.name.isNullOrBlank() }
            .randomOrNull()

        provideContent {
            WidgetContent(place = place)
        }
    }

    private fun repositoryFrom(context: Context): PlaceRepository =
        EntryPointAccessors.fromApplication(context, PlaceRecommendationWidgetEntryPoint::class.java)
            .placeRepository()
}

class ShuffleRecommendationAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        PlaceRecommendationWidget().update(context, glanceId)
    }
}

@Composable
private fun WidgetContent(place: PlaceEntity?) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetBackground)
            .padding(16.dp),
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .clickable(actionStartActivity<MainActivity>()),
        ) {
            Text(
                text = "오늘은 여기 어때요?",
                style = TextStyle(color = ColorProvider(WidgetMuted), fontSize = 12.sp),
            )
            Spacer(modifier = GlanceModifier.height(4.dp))
            if (place == null) {
                Text(
                    text = "아직 저장된 장소가 없어요",
                    style = TextStyle(
                        color = ColorProvider(WidgetTextPrimary),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            } else {
                Text(
                    text = place.name.orEmpty(),
                    maxLines = 1,
                    style = TextStyle(
                        color = ColorProvider(WidgetAccent),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                if (!place.address.isNullOrBlank()) {
                    Spacer(modifier = GlanceModifier.height(2.dp))
                    Text(
                        text = place.address.orEmpty(),
                        maxLines = 1,
                        style = TextStyle(color = ColorProvider(WidgetMuted), fontSize = 12.sp),
                    )
                }
            }
        }
        if (place != null) {
            Spacer(modifier = GlanceModifier.height(8.dp))
            Row(
                modifier = GlanceModifier.clickable(actionRunCallback<ShuffleRecommendationAction>()),
            ) {
                Text(
                    text = "다른 곳 보기",
                    style = TextStyle(
                        color = ColorProvider(WidgetTextPrimary),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Start,
                    ),
                )
            }
        }
    }
}
