package com.github.chsssssss.eonje.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.github.chsssssss.eonje.MainActivity
import com.github.chsssssss.eonje.R
import com.github.chsssssss.eonje.domain.model.ResolveStatus
import com.github.chsssssss.eonje.ui.theme.eonjeColorScheme
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/** 위젯에 그릴 값만 추린 것 — 도메인 Place를 그대로 넘기지 않는다. */
private data class TodayPlace(
    val id: String,
    val name: String,
    val neighborhood: String?,
    val folderLabel: String?,
)

/** 앱과 같은 Material 3 색 — 시스템 라이트/다크에 따라 자동으로 바뀐다. */
private val WidgetColors = ColorProviders(
    light = eonjeColorScheme(dark = false),
    dark = eonjeColorScheme(dark = true),
)

/**
 * '오늘의 맛집' — 확정(RESOLVED)된 장소 중 하나를 하루 동안 보여준다. Room 읽기만 하고 네트워크는 쓰지 않는다.
 * 선택은 [TodayPickStore]에 날짜와 함께 저장해서 같은 날엔 같은 장소, 다음 날엔 어제와 다른 장소가 나온다.
 */
class TodayPlaceWidget : GlanceAppWidget() {

    // 실제 크기에 맞춰 1칸 높이(기본 3x1)에선 가로형 한 줄 레이아웃, 2칸 이상이면 라벨 칩이 위에 붙는 세로형.
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val place = loadTodayPlace(context)
        provideContent {
            GlanceTheme(colors = WidgetColors) {
                WidgetContent(place = place, onClick = openAppAction(context, place?.id))
            }
        }
    }

    private suspend fun loadTodayPlace(context: Context): TodayPlace? {
        val entryPoint = EntryPointAccessors.fromApplication(context, TodayPlaceWidgetEntryPoint::class.java)
        val candidates = entryPoint.placeRepository().observeAll().first()
            .filter { it.status == ResolveStatus.RESOLVED && !it.name.isNullOrBlank() }
        val pick = TodayPickStore.resolve(
            context = context,
            candidateIds = candidates.map { it.id },
            today = LocalDate.now().toString(),
        ) ?: return null
        val place = candidates.first { it.id == pick.placeId }
        val folder = place.folderId?.let { folderId ->
            entryPoint.folderRepository().observeAll().first().firstOrNull { it.id == folderId }
        }
        return TodayPlace(
            id = place.id,
            name = place.name.orEmpty(),
            neighborhood = neighborhoodOf(place.address),
            folderLabel = folder?.let { "${it.iconKey} ${it.name}" },
        )
    }

    companion object {
        /** MainActivity가 위젯에서 열렸는지 구분하는 extra. */
        const val EXTRA_FROM_WIDGET = "com.github.chsssssss.eonje.extra.FROM_TODAY_PLACE_WIDGET"

        /** 탭한 장소 id. 빈 상태 탭이면 없음. 그 사이 장소가 삭제됐으면 앱이 홈으로만 연다. */
        const val EXTRA_PLACE_ID = "com.github.chsssssss.eonje.extra.PLACE_ID"

        private fun openAppAction(context: Context, placeId: String?): Action {
            val intent = Intent(context, MainActivity::class.java).apply {
                // 이미 떠 있는 MainActivity를 재사용해 onNewIntent로 받는다(인스턴스가 쌓이지 않게).
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_FROM_WIDGET, true)
                if (placeId != null) putExtra(EXTRA_PLACE_ID, placeId)
            }
            return actionStartActivity(intent)
        }
    }
}

@Composable
private fun WidgetContent(place: TodayPlace?, onClick: Action) {
    val compact = LocalSize.current.height < 100.dp
    val description = if (place == null) {
        "오늘의 맛집. 저장된 맛집이 없어요. 인스타에서 맛집을 공유해 보세요. 탭하면 앱을 열어요"
    } else {
        listOfNotNull("오늘의 맛집", place.name, place.neighborhood, place.folderLabel?.let { "폴더 $it" })
            .joinToString(", ") + ". 탭하면 앱에서 이 장소를 열어요"
    }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            // 흰 단색 네모 대신 크림→살구빛 그라데이션 + 넉넉한 모서리. 색은 values(-night)/colors_widget.xml이라
            // 시스템 다크 모드를 따라간다. 모서리는 드로어블에도 있어서 cornerRadius가 무시되는 API 30 이하도 둥글다.
            .cornerRadius(24.dp)
            .background(ImageProvider(R.drawable.widget_background))
            .padding(horizontal = 16.dp, vertical = if (compact) 8.dp else 16.dp)
            .clickable(onClick)
            .semantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (compact) {
            CompactRow(place)
            return@Column
        }
        Header()
        Spacer(modifier = GlanceModifier.height(10.dp))
        if (place == null) {
            Text(
                text = "인스타에서 맛집을 공유해 보세요",
                maxLines = 2,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
            return@Column
        }
        Text(
            text = place.name,
            maxLines = 2,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        val secondary = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp)
        place.neighborhood?.let {
            Spacer(modifier = GlanceModifier.height(2.dp))
            Text(text = it, maxLines = 1, style = secondary)
        }
        place.folderLabel?.let {
            Spacer(modifier = GlanceModifier.height(2.dp))
            Text(text = it, maxLines = 1, style = secondary)
        }
    }
}

/** 1칸 높이(기본 3x1) 가로형: 왼쪽 동그란 핀 배지, 오른쪽에 라벨·이름·동네 · 폴더 세 줄. */
@Composable
private fun CompactRow(place: TodayPlace?) {
    // 런처 칸이 아주 낮으면 라벨 줄을 빼서 이름이 잘리지 않게 한다.
    val showLabel = LocalSize.current.height >= 64.dp
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = GlanceModifier
                .size(40.dp)
                .background(ImageProvider(R.drawable.widget_badge_background)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_map_pin_highlighted),
                contentDescription = null,
                colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
                modifier = GlanceModifier.size(20.dp),
            )
        }
        Spacer(modifier = GlanceModifier.width(12.dp))
        Column {
            if (showLabel) {
                Text(
                    text = "오늘의 맛집",
                    maxLines = 1,
                    style = TextStyle(
                        color = GlanceTheme.colors.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
            Text(
                text = place?.name ?: "인스타에서 맛집을 공유해 보세요",
                maxLines = 1,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = if (place == null) 13.sp else 15.sp,
                    fontWeight = if (place == null) FontWeight.Medium else FontWeight.Bold,
                ),
            )
            val meta = place?.let { listOfNotNull(it.neighborhood, it.folderLabel).joinToString(" · ") }
            if (!meta.isNullOrEmpty()) {
                Text(
                    text = meta,
                    maxLines = 1,
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                )
            }
        }
    }
}

@Composable
private fun Header() {
    // 라벨을 알약 모양 칩에 담아 딱딱한 제목 줄 느낌을 줄인다.
    Row(
        modifier = GlanceModifier
            .background(ImageProvider(R.drawable.widget_chip_background))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_map_pin_highlighted),
            contentDescription = null,
            colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
            modifier = GlanceModifier.size(12.dp),
        )
        Spacer(modifier = GlanceModifier.width(3.dp))
        Text(
            text = "오늘의 맛집",
            style = TextStyle(
                color = GlanceTheme.colors.primary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}
