package com.github.chsssssss.eonje

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
