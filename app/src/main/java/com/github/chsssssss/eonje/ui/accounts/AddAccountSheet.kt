package com.github.chsssssss.eonje.ui.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.chsssssss.eonje.ui.components.FilledPillButton
import com.github.chsssssss.eonje.ui.components.OutlinedPillButton
import com.github.chsssssss.eonje.ui.components.PlaceholderImage
import com.github.chsssssss.eonje.ui.theme.EonjeColors
import com.github.chsssssss.eonje.ui.theme.EonjeTheme
import com.github.chsssssss.eonje.ui.theme.Typography

@Composable
fun AddAccountSheetContent(
    username: String,
    onUsernameChange: (String) -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp, 4.dp, 20.dp, 22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("계정 추가", style = Typography.titleLarge, color = EonjeColors.textPrimary)
            Text("2단계 · 계정 확인", style = Typography.bodySmall, color = EonjeColors.textMuted)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(EonjeColors.surfaceVariant)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("@", style = Typography.bodyLarge, color = EonjeColors.textMuted)
            BasicTextField(
                value = username,
                onValueChange = onUsernameChange,
                textStyle = TextStyle(
                    color = EonjeColors.textPrimary,
                    fontSize = Typography.bodyLarge.fontSize,
                ),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.Filled.Check, contentDescription = null, tint = EonjeColors.success, modifier = Modifier.size(18.dp))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(EonjeColors.surfaceVariant)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlaceholderImage(modifier = Modifier.size(60.dp), circle = true)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("성수동 기록", style = Typography.titleMedium, color = EonjeColors.textPrimary)
                Text("@$username · 게시물 412", style = Typography.bodySmall, color = EonjeColors.textMuted)
                Text("성수 · 뚝섬 로컬 맛집 아카이브", style = Typography.labelSmall, color = EonjeColors.textTertiary)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PlaceholderImage(modifier = Modifier.size(76.dp), cornerRadius = 12.dp, label = "최근")
            PlaceholderImage(modifier = Modifier.size(76.dp), cornerRadius = 12.dp, label = "최근")
            PlaceholderImage(modifier = Modifier.size(76.dp), cornerRadius = 12.dp, label = "최근")
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(76.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(EonjeColors.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text("최근 게시물", style = Typography.labelSmall, color = EonjeColors.textMuted)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedPillButton(text = "취소", onClick = onCancel)
            FilledPillButton(text = "이 계정 등록", onClick = onConfirm, modifier = Modifier.weight(1f))
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1B1D21)
@Composable
private fun AddAccountSheetPreview() {
    EonjeTheme {
        AddAccountSheetContent(
            username = "seongsu.list",
            onUsernameChange = {},
            onCancel = {},
            onConfirm = {},
        )
    }
}
