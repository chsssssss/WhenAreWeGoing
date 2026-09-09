package com.github.chsssssss.eonje

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.github.chsssssss.eonje.ui.components.BottomNavBar
import com.github.chsssssss.eonje.ui.navigation.AppShellViewModel
import com.github.chsssssss.eonje.ui.navigation.EonjeDestinations
import com.github.chsssssss.eonje.ui.navigation.EonjeNavHost
import com.github.chsssssss.eonje.ui.theme.EonjeColors
import com.github.chsssssss.eonje.ui.theme.EonjeTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EonjeTheme {
                EonjeApp()
            }
        }
    }
}

@Composable
private fun EonjeApp(shellViewModel: AppShellViewModel = hiltViewModel()) {
    NotificationPermissionRequester()

    val navController = rememberNavController()
    val badgeCount by shellViewModel.inboxBadgeCount.collectAsState()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    Scaffold(
        containerColor = EonjeColors.background,
        bottomBar = {
            if (currentRoute in EonjeDestinations.topLevelRoutes) {
                BottomNavBar(
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo(EonjeDestinations.HOME) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    inboxBadgeCount = badgeCount,
                )
            }
        },
    ) { innerPadding ->
        EonjeNavHost(
            navController = navController,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

/**
 * POST_NOTIFICATIONS는 API 33+에서 런타임 권한이라 요청하지 않으면 기본값이 거부다.
 * 거부돼도 알림만 안 뜰 뿐 앱은 정상 동작한다 — 공유 저장, 인박스 정리 등 핵심 흐름과 무관.
 */
@Composable
private fun NotificationPermissionRequester() {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = {},
    )

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
