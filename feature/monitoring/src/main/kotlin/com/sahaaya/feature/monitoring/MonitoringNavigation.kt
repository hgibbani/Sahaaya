package com.sahaaya.feature.monitoring

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.sahaaya.feature.monitoring.demo.DemoModeScreen
import com.sahaaya.feature.monitoring.fall.FallCountdownScreen
import com.sahaaya.feature.monitoring.settings.MonitoringSettingsScreen

object MonitoringRoutes {
    const val SETTINGS = "monitoring/settings"
    const val DEMO = "monitoring/demo"

    const val ARG_EVENT_ID = "eventId"
    private const val FALL_COUNTDOWN_BASE = "monitoring/fall"
    const val FALL_COUNTDOWN = "$FALL_COUNTDOWN_BASE/{$ARG_EVENT_ID}"

    fun fallCountdown(eventId: String): String = "$FALL_COUNTDOWN_BASE/$eventId"
}

/**
 * @param includeDemoMode wired from `BuildConfig.DEBUG` in `:app`. When false
 *   the route is never registered, so the developer screen cannot be reached in
 *   a release build even by deep link.
 */
fun NavGraphBuilder.monitoringScreens(
    onNavigateBack: () -> Unit,
    onOpenFallCountdown: (eventId: String) -> Unit,
    includeDemoMode: Boolean,
) {
    composable(MonitoringRoutes.SETTINGS) {
        MonitoringSettingsScreen(onNavigateBack = onNavigateBack)
    }

    composable(
        route = MonitoringRoutes.FALL_COUNTDOWN,
        arguments = listOf(
            navArgument(MonitoringRoutes.ARG_EVENT_ID) { type = NavType.StringType },
        ),
    ) { entry ->
        FallCountdownScreen(
            eventId = entry.arguments?.getString(MonitoringRoutes.ARG_EVENT_ID).orEmpty(),
            onDismiss = onNavigateBack,
        )
    }

    if (includeDemoMode) {
        composable(MonitoringRoutes.DEMO) {
            DemoModeScreen(
                onNavigateBack = onNavigateBack,
                onFallSimulated = onOpenFallCountdown,
            )
        }
    }
}
