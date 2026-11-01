package com.saferoute.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.saferoute.ui.screens.HomeScreen
import com.saferoute.ui.screens.LoginScreen
import com.saferoute.ui.screens.MapScreen
import com.saferoute.ui.screens.NavigationScreen
import com.saferoute.ui.screens.ProfileScreen
import com.saferoute.ui.screens.ReportHazardScreen
import com.saferoute.ui.screens.SettingsScreen
import com.saferoute.ui.screens.SplashScreen

sealed class Screen(val route: String) {
    data object Splash : Screen("splash")
    data object Login : Screen("login")
    data object Home : Screen("home")
    data object Map : Screen("map")
    data object Navigation : Screen("navigation")
    data object ReportHazard : Screen("report_hazard")
    data object Profile : Screen("profile")
    data object Settings : Screen("settings")
}

@Composable
fun SafeRouteNavGraph(
    navController: NavHostController,
    isAuthenticated: Boolean,
    onSignOut: () -> Unit
) {
    LaunchedEffect(isAuthenticated) {
        val currentRoute = navController.currentDestination?.route
        if (!isAuthenticated && currentRoute != Screen.Login.route && currentRoute != Screen.Splash.route) {
            navController.navigate(Screen.Login.route) {
                popUpTo(Screen.Home.route) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = if (isAuthenticated) Screen.Home.route else Screen.Login.route
    ) {
        composable(Screen.Splash.route) {
            SplashScreen(
                onNavigateToHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                },
                onNavigateToLogin = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToMap = { navController.navigate(Screen.Map.route) },
                onNavigateToReport = { navController.navigate(Screen.ReportHazard.route) },
                onNavigateToProfile = { navController.navigate(Screen.Profile.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
            )
        }

        composable(Screen.Map.route) {
            MapScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Navigation.route) {
            NavigationScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.ReportHazard.route) {
            ReportHazardScreen(
                onNavigateBack = { navController.popBackStack() },
                onReportSubmitted = { navController.popBackStack() }
            )
        }

        composable(Screen.Profile.route) {
            ProfileScreen(
                onNavigateBack = { navController.popBackStack() },
                onSignOut = {
                    onSignOut()
                }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
