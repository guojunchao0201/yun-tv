package com.yuntv.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.yuntv.core.model.AppRoute
import com.yuntv.core.model.AuthState
import com.yuntv.feature.auth.AuthorizationScreen
import com.yuntv.feature.browser.BrowserScreen
import com.yuntv.feature.player.PlayerScreen
import com.yuntv.feature.settings.SettingsScreen
import kotlinx.coroutines.launch

@Composable
fun YunTvNavHost(container: AppContainer) {
    val authState by container.authRepository.observeAuthState().collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    when (authState) {
        is AuthState.Authenticated,
        is AuthState.OfflineWithCachedData,
        -> AuthenticatedNavigation(
            authState = authState,
            onLogout = { scope.launch { container.authRepository.logout() } },
        )
        else -> AuthorizationScreen(
            state = authState,
            onStartAuthorization = { scope.launch { container.authRepository.authorize() } },
            onCancel = container.authRepository::cancelAuthorization,
        )
    }
}

@Composable
private fun AuthenticatedNavigation(
    authState: AuthState,
    onLogout: () -> Unit,
) {
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
                isOffline = authState is AuthState.OfflineWithCachedData,
                onLogout = onLogout,
                onBack = { navController.popBackStack() },
            )
        }
        composable(AppRoute.Player.route) {
            PlayerScreen(onBack = { navController.popBackStack() })
        }
    }
}
