package com.github.chsssssss.eonje.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.github.chsssssss.eonje.ui.theme.EonjeColors
import com.github.chsssssss.eonje.ui.theme.EonjeTheme
import com.github.chsssssss.eonje.ui.theme.Typography

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    SettingsContent(modifier = modifier)
}

@Composable
private fun SettingsContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(EonjeColors.background)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("설정", style = Typography.headlineMedium, color = EonjeColors.textPrimary)
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("아직 설정할 게 없어요", style = Typography.bodyMedium, color = EonjeColors.textMuted)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121316)
@Composable
private fun SettingsScreenPreview() {
    EonjeTheme {
        SettingsContent()
    }
}
