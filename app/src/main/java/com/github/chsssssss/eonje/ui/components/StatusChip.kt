package com.github.chsssssss.eonje.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.github.chsssssss.eonje.ui.theme.EonjeColors
import com.github.chsssssss.eonje.ui.theme.Typography

/**
 * Inbox card status pill — "후보 N곳" (accent, has candidates) or "직접 찾기" (outline, none yet).
 */
@Composable
fun CandidateStatusChip(candidateCount: Int, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(13.dp)
    val hasCandidates = candidateCount > 0
    Row(
        modifier = modifier
            .height(26.dp)
            .clip(shape)
            .let {
                if (hasCandidates) it.background(EonjeColors.accent.copy(alpha = 0.13f))
                else it.border(1.dp, EonjeColors.border, shape)
            }
            .padding(start = 9.dp, end = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(if (hasCandidates) EonjeColors.accent else EonjeColors.iconMuted, CircleShape)
        )
        Text(
            text = if (hasCandidates) "후보 ${candidateCount}곳" else "직접 찾기",
            style = Typography.labelSmall,
            color = if (hasCandidates) EonjeColors.accent else EonjeColors.textTertiary,
        )
    }
}

/** 인박스 카드 상태 표시 — WorkManager에서 캡션 파싱이 아직 안 끝난 게시물에 뜬다. */
@Composable
fun ProcessingStatusChip(modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(13.dp)
    Row(
        modifier = modifier
            .height(26.dp)
            .clip(shape)
            .background(EonjeColors.surfaceVariant)
            .padding(start = 9.dp, end = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(10.dp),
            color = EonjeColors.textMuted,
            strokeWidth = 1.5.dp,
        )
        Text(
            text = "정리 중",
            style = Typography.labelSmall,
            color = EonjeColors.textMuted,
        )
    }
}
