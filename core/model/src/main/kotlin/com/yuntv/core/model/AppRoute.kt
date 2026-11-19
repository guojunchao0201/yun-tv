package com.yuntv.core.model

sealed interface AppRoute {
    val route: String

    data object Authorization : AppRoute { override val route: String = "authorization" }
    data object Browser : AppRoute { override val route: String = "browser" }
    data object Player : AppRoute { override val route: String = "player" }
    data object Settings : AppRoute { override val route: String = "settings" }
}
