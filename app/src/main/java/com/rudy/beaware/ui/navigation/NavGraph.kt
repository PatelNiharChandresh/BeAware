package com.rudy.beaware.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.rudy.beaware.ui.screens.home.HomeScreen
import com.rudy.beaware.ui.screens.onboarding.OnboardingScreen
import com.rudy.beaware.ui.screens.picker.AppPickerScreen
import com.rudy.beaware.util.PermissionHelper

object Route {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val PICKER = "picker"
    const val STATS = "stats"
}

@Composable
fun NavGraph(
    navController: NavHostController = rememberNavController()
) {
    val context = LocalContext.current
    val startDestination = remember {
        val allGranted = PermissionHelper.hasUsageStatsPermission(context)
                && PermissionHelper.hasOverlayPermission(context)
                && PermissionHelper.hasNotificationPermission(context)
        if (allGranted) Route.HOME else Route.ONBOARDING
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Route.ONBOARDING) {
            OnboardingScreen(
                onContinue = {
                    navController.navigate(Route.HOME) {
                        popUpTo(Route.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }

        composable(Route.HOME) {
            HomeScreen(
                onNavigateToPicker = {
                    navController.navigate(Route.PICKER)
                },
                onNavigateToStats = {
                    navController.navigate(Route.STATS)
                }
            )
        }

        composable(Route.PICKER) {
            AppPickerScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Route.STATS) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "Stats — coming soon")
            }
        }
    }
}
