package com.github.chsssssss.eonje.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.github.chsssssss.eonje.MainActivity
import com.github.chsssssss.eonje.domain.repository.SavedPostRepository
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first

private val WidgetBackground = Color(0xFF1B1D21)
private val WidgetTextPrimary = Color(0xFFE6E7EA)
private val WidgetAccent = Color(0xFFF2A65A)
private val WidgetMuted = Color(0xFF7C828B)

/** 4단계(다듬기): 인박스에 정리 대기 중인 게시물이 몇 개인지 보여주는 홈 화면 위젯. */
class InboxWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = repositoryFrom(context)
        val count = repository.observeUnresolved().first().size

        provideContent {
            WidgetContent(count = count)
        }
    }

    private fun repositoryFrom(context: Context): SavedPostRepository =
        EntryPointAccessors.fromApplication(context, InboxWidgetEntryPoint::class.java)
            .savedPostRepository()
}

@Composable
private fun WidgetContent(count: Int) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetBackground)
            .padding(16.dp)
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "언제가지",
            style = TextStyle(color = ColorProvider(WidgetMuted), fontSize = 12.sp),
        )
        Spacer(modifier = GlanceModifier.height(4.dp))
        Text(
            text = if (count > 0) "${count}개 정리 대기" else "정리할 게시물이 없어요",
            style = TextStyle(
                color = ColorProvider(if (count > 0) WidgetAccent else WidgetTextPrimary),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}
