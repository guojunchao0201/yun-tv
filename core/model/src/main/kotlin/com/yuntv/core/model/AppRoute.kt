package com.yuntv.core.model

sealed interface AppRoute {
    val route: String

    data object Authorization : AppRoute { override val route: String = "authorization" }
    data object Browser : AppRoute { override val route: String = "browser" }
    data object Player : AppRoute {
        const val FS_ID_ARGUMENT = "fsId"
        override val route: String = "player/{$FS_ID_ARGUMENT}"
        fun createRoute(fsId: Long): String = "player/$fsId"
    }
    data object Settings : AppRoute { override val route: String = "settings" }
}
