package com.github.chsssssss.eonje.ui.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import com.github.chsssssss.eonje.ui.theme.EonjeColors
import com.github.chsssssss.eonje.ui.theme.EonjeTheme
import com.github.chsssssss.eonje.ui.theme.Typography

@Composable
fun AddAccountSheetContent(
    username: String,
    isRegistering: Boolean,
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
            Text("인스타그램 프로페셔널 계정만 등록할 수 있어요", style = Typography.bodySmall, color = EonjeColors.textMuted)
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
                enabled = !isRegistering,
                textStyle = TextStyle(
                    color = EonjeColors.textPrimary,
                    fontSize = Typography.bodyLarge.fontSize,
                ),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedPillButton(text = "취소", onClick = onCancel)
            FilledPillButton(
                text = if (isRegistering) "확인 중…" else "이 계정 등록",
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1B1D21)
@Composable
private fun AddAccountSheetPreview() {
    EonjeTheme {
        AddAccountSheetContent(
            username = "seongsu.list",
            isRegistering = false,
            onUsernameChange = {},
            onCancel = {},
            onConfirm = {},
        )
    }
}
