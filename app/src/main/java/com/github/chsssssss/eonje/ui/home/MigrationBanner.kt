package com.github.chsssssss.eonje.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.github.chsssssss.eonje.domain.repository.LegacyMigrationPhase
import com.github.chsssssss.eonje.ui.theme.EonjeColors
import com.github.chsssssss.eonje.ui.theme.Typography
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest

/** 홈 상단에 띄우는 이전 안내. */
enum class MigrationBanner(val message: String) {
    MIGRATING("기존 데이터를 옮기는 중이에요"),
    WAITING("인터넷에 연결되면 기존 데이터를 옮겨요. 데이터는 안전해요"),
}

/** 이 시간 안에 끝나면 배너를 아예 안 보여준다(깜빡임 방지). */
internal const val MIGRATION_BANNER_DELAY_MILLIS = 1_500L

/**
 * 이전이 필요한 상태가 [showDelayMillis] 이상 이어질 때만 배너를 낸다. 이전이 끝나면(없음) 바로 사라진다.
 * 배너를 낸 뒤에 이전 중 ↔ 대기가 바뀌면 문구만 바로 바뀐다(지연을 다시 기다리지 않는다).
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun migrationBannerFlow(
    phase: Flow<LegacyMigrationPhase>,
    showDelayMillis: Long = MIGRATION_BANNER_DELAY_MILLIS,
): Flow<MigrationBanner?> {
    val needed = phase.map { it != LegacyMigrationPhase.NOT_NEEDED }.distinctUntilChanged()
    val delayElapsed = needed.transformLatest { isNeeded ->
        emit(false)
        if (isNeeded) {
            delay(showDelayMillis)
            emit(true)
        }
    }
    return combine(phase, delayElapsed) { p, elapsed ->
        if (!elapsed) null else when (p) {
            LegacyMigrationPhase.NOT_NEEDED -> null
            LegacyMigrationPhase.MIGRATING -> MigrationBanner.MIGRATING
            LegacyMigrationPhase.WAITING -> MigrationBanner.WAITING
        }
    }.distinctUntilChanged()
}

/**
 * 이전이 끝나기 전엔 "저장된 장소가 없다"는 말이 틀릴 수 있다(옛 데이터가 아직 안 옮겨진 것뿐이다).
 * 그래서 빈 상태 안내 카드는 이전 대상이 없을 때만 보여준다. 신규 설치 사용자(이전 대상 없음)는 그대로다.
 */
internal fun hasNoSavedPlaces(storedPlacesEmpty: Boolean, phase: LegacyMigrationPhase): Boolean =
    storedPlacesEmpty && phase == LegacyMigrationPhase.NOT_NEEDED

/** 홈 목록 영역에 무엇을 그릴지. */
internal enum class PlaceListContent { GUIDE_CARD, MIGRATION_PLACEHOLDER, NO_RESULTS, LIST }

internal fun placeListContent(hasNoSavedPlaces: Boolean, placesEmpty: Boolean, migrationActive: Boolean): PlaceListContent = when {
    hasNoSavedPlaces -> PlaceListContent.GUIDE_CARD
    // 이전 중인데 아직 보여줄 장소가 없다 — "저장된 장소가 없어요"라고 말하지 않고 비워둔다(위 배너가 이유를 말해준다).
    placesEmpty && migrationActive -> PlaceListContent.MIGRATION_PLACEHOLDER
    placesEmpty -> PlaceListContent.NO_RESULTS
    else -> PlaceListContent.LIST
}

@Composable
internal fun MigrationNoticeBanner(banner: MigrationBanner, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(shape)
            .background(EonjeColors.surface)
            .border(1.dp, EonjeColors.border, shape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (banner) {
            MigrationBanner.MIGRATING -> CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = EonjeColors.iconMuted,
            )
            MigrationBanner.WAITING -> Icon(
                Icons.Filled.CloudOff,
                contentDescription = null,
                tint = EonjeColors.iconMuted,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            banner.message,
            style = Typography.bodySmall,
            color = EonjeColors.textSecondary,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}
