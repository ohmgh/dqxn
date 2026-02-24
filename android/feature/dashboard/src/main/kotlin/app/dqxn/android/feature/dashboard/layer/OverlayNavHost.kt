package app.dqxn.android.feature.dashboard.layer

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import app.dqxn.android.feature.dashboard.command.DashboardCommand

/**
 * Layer 1 navigation scaffold for overlay UI (settings, widget picker, widget settings, setup).
 *
 * Route table is EMPTY at Phase 7 -- routes populated in Phase 10:
 * - WidgetPicker
 * - Settings
 * - WidgetSettings
 * - Setup
 *
 * WidgetSettings uses [ExitTransition.None] / [EnterTransition.None] per replication advisory
 * section 2 (jankless preview navigation).
 *
 * Start destination is [ROUTE_EMPTY] (no overlay shown by default). The dashboard is Layer 0 and
 * always renders beneath this NavHost.
 */
@Composable
public fun OverlayNavHost(
  navController: NavHostController,
  onCommand: (DashboardCommand) -> Unit,
  modifier: Modifier = Modifier,
) {
  NavHost(
    navController = navController,
    startDestination = ROUTE_EMPTY,
    modifier = modifier,
    enterTransition = { EnterTransition.None },
    exitTransition = { ExitTransition.None },
  ) {
    // Empty start destination -- no overlay visible
    composable(ROUTE_EMPTY) {
      // Intentionally empty: dashboard-as-shell, Layer 0 is always visible behind this
    }

    // Phase 10 routes:
    // composable("widget_picker") { WidgetPickerScreen(...) }
    // composable("settings") { SettingsScreen(...) }
    // composable("widget_settings/{widgetId}") { WidgetSettingsScreen(...) }
    // composable("setup/{widgetId}") { SetupSheet(...) }
  }
}

/** Empty route serving as the default "no overlay" state. */
internal const val ROUTE_EMPTY: String = "empty"
