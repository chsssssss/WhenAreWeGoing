package com.github.chsssssss.eonje

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.github.chsssssss.eonje.domain.model.ThemeMode
import com.github.chsssssss.eonje.domain.repository.ThemePreferenceRepository
import com.github.chsssssss.eonje.ui.components.BottomNavBar
import com.github.chsssssss.eonje.ui.navigation.AppShellViewModel
import com.github.chsssssss.eonje.ui.navigation.EonjeDestinations
import com.github.chsssssss.eonje.ui.navigation.EonjeNavHost
import com.github.chsssssss.eonje.ui.theme.EonjeColors
import com.github.chsssssss.eonje.ui.theme.EonjeTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var themePreferenceRepository: ThemePreferenceRepository

    // EonjeApp의 hiltViewModel()과 같은 액티비티 스코프·같은 키라 동일한 인스턴스다.
    private val shellViewModel: AppShellViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        // 실제 준비 상태(시작 화면 결정 + 장소 목록 첫 로딩)까지만 유지한다. 상한을 둬서 DB가 느리거나
        // 멈춰도 스플래시에 갇히지 않는다 — 넘기면 EonjeApp의 빈 배경/홈의 로딩 흐름이 이어받는다.
        val splashStart = SystemClock.uptimeMillis()
        splashScreen.setKeepOnScreenCondition {
            !shellViewModel.isReady.value &&
                SystemClock.uptimeMillis() - splashStart < SPLASH_MAX_KEEP_MILLIS
        }
        enableEdgeToEdge()
        setContent {
            val themeMode by themePreferenceRepository.themeMode.collectAsState()
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            EonjeTheme(darkTheme = darkTheme) {
                EonjeApp(shellViewModel)
            }
        }
    }

    private companion object {
        const val SPLASH_MAX_KEEP_MILLIS = 1_000L
    }
}

@Composable
private fun EonjeApp(shellViewModel: AppShellViewModel = hiltViewModel()) {
    val onboardingCompleted by shellViewModel.onboardingCompleted.collectAsState()
    val completed = onboardingCompleted
    if (completed == null) {
        Box(modifier = Modifier.fillMaxSize().background(EonjeColors.background))
        return
    }
    // 처음 읽은 값으로 한 번만 정한다 — 온보딩을 끝내서 값이 바뀌어도 NavHost 그래프를 다시 만들지 않는다.
    val startDestination = remember {
        if (completed) EonjeDestinations.HOME else EonjeDestinations.ONBOARDING
    }

    // 권한 요청 팝업이 온보딩 위에 뜨지 않도록 온보딩을 끝낸 뒤에 요청한다.
    if (completed) NotificationPermissionRequester()

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
            startDestination = startDestination,
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
