package com.yuntv.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
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
            container = container,
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
    container: AppContainer,
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
                repository = container.cloudDriveRepository,
                coverRepository = container.coverRepository,
                onOpenSettings = { navController.navigate(AppRoute.Settings.route) },
                onPlay = { navController.navigate(AppRoute.Player.createRoute(it)) },
            )
        }
        composable(AppRoute.Settings.route) {
            SettingsScreen(
                isOffline = authState is AuthState.OfflineWithCachedData,
                onLogout = onLogout,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = AppRoute.Player.route,
            arguments = listOf(navArgument(AppRoute.Player.FS_ID_ARGUMENT) { type = NavType.LongType }),
        ) { entry ->
            PlayerScreen(
                fsId = checkNotNull(entry.arguments?.getLong(AppRoute.Player.FS_ID_ARGUMENT)),
                cloudDriveRepository = container.cloudDriveRepository,
                progressRepository = container.playbackProgressRepository,
                playbackHttpClient = container.playbackHttpClient,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
