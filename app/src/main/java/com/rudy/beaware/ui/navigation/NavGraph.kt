package com.rudy.beaware.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.rudy.beaware.R
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.rudy.beaware.ui.screens.home.HomeScreen
import com.rudy.beaware.ui.screens.onboarding.OnboardingScreen
import com.rudy.beaware.ui.screens.picker.AppPickerScreen
import com.rudy.beaware.util.PermissionHelper
import timber.log.Timber

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
        val dest = if (allGranted) Route.HOME else Route.ONBOARDING
        Timber.d("NavGraph: startDestination=%s (allGranted=%s)", dest, allGranted)
        dest
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Route.ONBOARDING) {
            Timber.d("NavGraph: composing OnboardingScreen")
            OnboardingScreen(
                onContinue = {
                    Timber.d("NavGraph: onContinue — navigating to Home, popping Onboarding")
                    navController.navigate(Route.HOME) {
                        popUpTo(Route.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }

        composable(Route.HOME) {
            Timber.d("NavGraph: composing HomeScreen")
            HomeScreen(
                onNavigateToPicker = {
                    Timber.d("NavGraph: navigating to Picker")
                    navController.navigate(Route.PICKER)
                },
                onNavigateToStats = {
                    Timber.d("NavGraph: navigating to Stats")
                    navController.navigate(Route.STATS)
                }
            )
        }

        composable(Route.PICKER) {
            Timber.d("NavGraph: composing AppPickerScreen")
            AppPickerScreen(
                onNavigateBack = {
                    Timber.d("NavGraph: Picker — navigating back")
                    navController.popBackStack()
                }
            )
        }

        composable(Route.STATS) {
            Timber.d("NavGraph: composing Stats placeholder")
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(text = stringResource(R.string.stats_coming_soon))
            }
        }
    }
}
