package com.github.chsssssss.eonje.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.github.chsssssss.eonje.ui.home.HomeScreen
import com.github.chsssssss.eonje.ui.inbox.InboxScreen
import com.github.chsssssss.eonje.ui.resolve.ResolveScreen
import com.github.chsssssss.eonje.ui.settings.SettingsScreen

@Composable
fun EonjeNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = EonjeDestinations.HOME,
        modifier = modifier,
    ) {
        composable(EonjeDestinations.HOME) {
            HomeScreen()
        }
        composable(EonjeDestinations.INBOX) {
            InboxScreen(
                onItemClick = { postId -> navController.navigate(EonjeDestinations.resolveRoute(postId)) },
            )
        }
        composable(EonjeDestinations.SETTINGS) {
            SettingsScreen()
        }
        composable(
            route = EonjeDestinations.RESOLVE,
            arguments = listOf(navArgument(EonjeDestinations.RESOLVE_POST_ID_ARG) { type = NavType.StringType }),
        ) {
            ResolveScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}
