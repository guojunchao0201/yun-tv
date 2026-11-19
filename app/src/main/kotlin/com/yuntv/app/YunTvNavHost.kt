package com.yuntv.app

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.yuntv.core.model.AppRoute
import com.yuntv.feature.auth.AuthorizationScreen
import com.yuntv.feature.browser.BrowserScreen
import com.yuntv.feature.player.PlayerScreen
import com.yuntv.feature.settings.SettingsScreen

@Composable
fun YunTvNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = AppRoute.Browser.route,
    ) {
        composable(AppRoute.Browser.route) {
            BrowserScreen(
                onOpenSettings = { navController.navigate(AppRoute.Settings.route) },
                onPlayDemo = { navController.navigate(AppRoute.Player.route) },
            )
        }
        composable(AppRoute.Settings.route) {
            SettingsScreen(
                onAuthorize = { navController.navigate(AppRoute.Authorization.route) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(AppRoute.Authorization.route) {
            AuthorizationScreen(onBack = { navController.popBackStack() })
        }
        composable(AppRoute.Player.route) {
            PlayerScreen(onBack = { navController.popBackStack() })
        }
    }
}
