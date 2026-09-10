package com.github.chsssssss.eonje.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.offset
import com.github.chsssssss.eonje.ui.navigation.EonjeDestinations
import com.github.chsssssss.eonje.ui.theme.EonjeColors
import com.github.chsssssss.eonje.ui.theme.Typography

private data class NavTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

private val tabs = listOf(
    NavTab(EonjeDestinations.HOME, "홈", Icons.Filled.Home, Icons.Outlined.Home),
    NavTab(EonjeDestinations.INBOX, "인박스", Icons.Filled.Inbox, Icons.Outlined.Inbox),
    NavTab(EonjeDestinations.SETTINGS, "설정", Icons.Filled.Settings, Icons.Outlined.Settings),
)

@Composable
fun BottomNavBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    inboxBadgeCount: Int,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(EonjeColors.navBackground)
            .border(width = 1.dp, color = EonjeColors.surfaceVariant)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        tabs.forEach { tab ->
            val selected = tab.route == currentRoute
            NavTabItem(
                tab = tab,
                selected = selected,
                badgeCount = if (tab.route == EonjeDestinations.INBOX) inboxBadgeCount else 0,
                onClick = { onNavigate(tab.route) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun NavTabItem(
    tab: NavTab,
    selected: Boolean,
    badgeCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentColor = if (selected) EonjeColors.accent else EonjeColors.textTertiary
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .height(30.dp)
                .padding(horizontal = 8.dp)
                .let {
                    if (selected) it.background(EonjeColors.accent.copy(alpha = 0.16f), RoundedCornerShape(15.dp)) else it
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(modifier = Modifier.size(20.dp)) {
                Icon(
                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                    contentDescription = tab.label,
                    tint = contentColor,
                    modifier = Modifier.size(20.dp),
                )
                if (badgeCount > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 6.dp, y = (-6).dp)
                            .size(16.dp)
                            .background(EonjeColors.accent, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = badgeCount.coerceAtMost(99).toString(),
                            style = Typography.labelSmall,
                            color = EonjeColors.onAccent,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
        Text(
            text = tab.label,
            style = Typography.labelSmall,
            color = contentColor,
        )
    }
}
