package com.github.chsssssss.eonje.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.github.chsssssss.eonje.data.analytics.Events
import com.github.chsssssss.eonje.ui.home.HomeScreen
import com.github.chsssssss.eonje.ui.inbox.InboxScreen
import com.github.chsssssss.eonje.ui.onboarding.OnboardingScreen
import com.github.chsssssss.eonje.ui.resolve.ResolveScreen
import com.github.chsssssss.eonje.ui.settings.SettingsScreen

@Composable
fun EonjeNavHost(
    navController: NavHostController,
    startDestination: String,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable(EonjeDestinations.HOME) {
            HomeScreen(
                onOpenGuide = {
                    navController.navigate(EonjeDestinations.onboardingRoute(Events.OnboardingEntry.HOME_EMPTY))
                },
            )
        }
        composable(EonjeDestinations.INBOX) {
            InboxScreen(
                onItemClick = { postId -> navController.navigate(EonjeDestinations.resolveRoute(postId)) },
            )
        }
        composable(EonjeDestinations.SETTINGS) {
            SettingsScreen(
                onOpenGuide = {
                    navController.navigate(EonjeDestinations.onboardingRoute(Events.OnboardingEntry.SETTINGS))
                },
            )
        }
        composable(
            route = EonjeDestinations.ONBOARDING,
            arguments = listOf(
                navArgument(EonjeDestinations.ONBOARDING_ENTRY_ARG) {
                    type = NavType.StringType
                    defaultValue = Events.OnboardingEntry.FIRST_RUN
                },
            ),
        ) {
            OnboardingScreen(
                onFinish = {
                    // 다시 보기로 들어왔으면 원래 화면으로, 첫 실행이면(뒤에 아무것도 없음) 홈으로 교체한다.
                    if (navController.previousBackStackEntry != null) {
                        navController.popBackStack()
                    } else {
                        navController.navigate(EonjeDestinations.HOME) {
                            popUpTo(EonjeDestinations.ONBOARDING) { inclusive = true }
                        }
                    }
                },
            )
        }
        composable(
            route = EonjeDestinations.RESOLVE,
            arguments = listOf(navArgument(EonjeDestinations.RESOLVE_POST_ID_ARG) { type = NavType.StringType }),
        ) {
            ResolveScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}
