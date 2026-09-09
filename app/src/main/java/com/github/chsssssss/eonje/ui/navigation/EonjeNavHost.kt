package com.github.chsssssss.eonje.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.github.chsssssss.eonje.ui.accounts.AccountsScreen
import com.github.chsssssss.eonje.ui.home.HomeScreen
import com.github.chsssssss.eonje.ui.inbox.InboxScreen
import com.github.chsssssss.eonje.ui.placedetail.PlaceDetailScreen
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
            HomeScreen(
                onPlaceClick = { placeId -> navController.navigate(EonjeDestinations.placeDetailRoute(placeId)) },
            )
        }
        composable(EonjeDestinations.INBOX) {
            InboxScreen(
                onItemClick = { postId -> navController.navigate(EonjeDestinations.resolveRoute(postId)) },
                onNavigateToAccounts = { navController.navigate(EonjeDestinations.ACCOUNTS) },
            )
        }
        composable(EonjeDestinations.SETTINGS) {
            SettingsScreen(
                onNavigateToAccounts = { navController.navigate(EonjeDestinations.ACCOUNTS) },
            )
        }
        composable(
            route = EonjeDestinations.RESOLVE,
            arguments = listOf(navArgument(EonjeDestinations.RESOLVE_POST_ID_ARG) { type = NavType.StringType }),
        ) {
            ResolveScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(
            route = EonjeDestinations.PLACE_DETAIL,
            arguments = listOf(navArgument(EonjeDestinations.PLACE_DETAIL_ID_ARG) { type = NavType.StringType }),
        ) {
            PlaceDetailScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(EonjeDestinations.ACCOUNTS) {
            AccountsScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}
