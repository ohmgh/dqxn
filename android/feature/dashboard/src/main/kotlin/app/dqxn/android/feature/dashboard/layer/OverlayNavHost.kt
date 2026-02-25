package app.dqxn.android.feature.dashboard.layer

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import app.dqxn.android.core.design.motion.DashboardMotion
import app.dqxn.android.data.device.PairedDeviceStore
import app.dqxn.android.feature.dashboard.command.DashboardCommand
import app.dqxn.android.feature.settings.WidgetPicker
import app.dqxn.android.feature.settings.main.MainSettings
import app.dqxn.android.feature.settings.main.MainSettingsViewModel
import app.dqxn.android.feature.settings.setup.SetupEvaluatorImpl
import app.dqxn.android.feature.settings.setup.SetupSheet
import app.dqxn.android.feature.settings.widget.WidgetSettingsSheet
import app.dqxn.android.sdk.contracts.entitlement.EntitlementManager
import app.dqxn.android.sdk.contracts.registry.DataProviderRegistry
import app.dqxn.android.sdk.contracts.registry.WidgetRegistry
import app.dqxn.android.sdk.contracts.settings.ProviderSettingsStore

/**
 * Layer 1 navigation scaffold for overlay UI (settings, widget picker, widget settings, setup).
 *
 * Route table: 5 type-safe routes via `@Serializable` route classes in [OverlayRoutes.kt]:
 * - [EmptyRoute] -- no overlay (Layer 0 visible)
 * - [WidgetPickerRoute] -- widget selection grid with [DashboardMotion.hubEnter]/[hubExit]
 * - [SettingsRoute] -- main settings with [DashboardMotion.previewEnter]/[previewExit]
 * - [WidgetSettingsRoute] -- per-widget settings with [ExitTransition.None]/[EnterTransition.None]
 *   per replication advisory section 2 (jankless preview navigation)
 * - [SetupRoute] -- provider setup wizard with [DashboardMotion.hubEnter]/[hubExit]
 *
 * WidgetSettings uses [ExitTransition.None] / [EnterTransition.None] so the widget preview
 * stays visible beneath child overlays (Setup navigated from Data Source tab).
 *
 * Settings clears the preview theme on enter to prevent advisory section 3 race condition.
 *
 * Start destination is [EmptyRoute] (no overlay shown by default). The dashboard is Layer 0 and
 * always renders beneath this NavHost.
 */
@Composable
public fun OverlayNavHost(
  navController: NavHostController,
  widgetRegistry: WidgetRegistry,
  dataProviderRegistry: DataProviderRegistry,
  providerSettingsStore: ProviderSettingsStore,
  entitlementManager: EntitlementManager,
  setupEvaluator: SetupEvaluatorImpl,
  pairedDeviceStore: PairedDeviceStore,
  mainSettingsViewModel: MainSettingsViewModel,
  onCommand: (DashboardCommand) -> Unit,
  modifier: Modifier = Modifier,
) {
  NavHost(
    navController = navController,
    startDestination = EmptyRoute,
    modifier = modifier,
    enterTransition = { EnterTransition.None },
    exitTransition = { ExitTransition.None },
  ) {
    // Empty start destination -- no overlay visible
    composable<EmptyRoute> {
      // Intentionally empty: dashboard-as-shell, Layer 0 is always visible behind this
    }

    // Widget picker -- hub transition (scale + fade)
    composable<WidgetPickerRoute>(
      enterTransition = { DashboardMotion.hubEnter },
      exitTransition = { DashboardMotion.hubExit },
      popEnterTransition = { DashboardMotion.hubEnter },
      popExitTransition = { DashboardMotion.hubExit },
    ) {
      WidgetPicker(
        widgetRegistry = widgetRegistry,
        entitlementManager = entitlementManager,
        onSelectWidget = { typeId ->
          // Navigate to widget settings after selection
          navController.navigate(WidgetSettingsRoute(widgetId = typeId))
        },
        onDismiss = {
          navController.popBackStack(EmptyRoute, inclusive = false)
        },
      )
    }

    // Main settings -- source-varying preview transitions (vertical slide)
    // Clears preview theme on enter per advisory section 3 race condition fix
    composable<SettingsRoute>(
      enterTransition = { DashboardMotion.previewEnter },
      exitTransition = { DashboardMotion.previewExit },
      popEnterTransition = { DashboardMotion.previewEnter },
      popExitTransition = { DashboardMotion.previewExit },
    ) {
      // Clear preview theme on settings enter (advisory section 3 race fix)
      onCommand(DashboardCommand.PreviewTheme(null))

      val analyticsConsent by mainSettingsViewModel.analyticsConsent.collectAsState()
      val showStatusBar by mainSettingsViewModel.showStatusBar.collectAsState()
      val keepScreenOn by mainSettingsViewModel.keepScreenOn.collectAsState()

      MainSettings(
        analyticsConsent = analyticsConsent,
        showStatusBar = showStatusBar,
        keepScreenOn = keepScreenOn,
        onSetAnalyticsConsent = mainSettingsViewModel::setAnalyticsConsent,
        onSetShowStatusBar = mainSettingsViewModel::setShowStatusBar,
        onSetKeepScreenOn = mainSettingsViewModel::setKeepScreenOn,
        onDeleteAllData = mainSettingsViewModel::deleteAllData,
        onNavigateToThemeMode = {
          // Theme mode picker -- Phase 11
        },
        onNavigateToDashPacks = {
          // Pack browser navigation -- future
        },
        onNavigateToDiagnostics = {
          // Diagnostics navigation -- Phase 11
        },
        onClose = {
          navController.popBackStack(EmptyRoute, inclusive = false)
        },
      )
    }

    // Widget settings -- ExitTransition.None / EnterTransition.None per advisory section 2
    // The widget preview stays visible when navigating to Setup from Data Source tab
    composable<WidgetSettingsRoute>(
      enterTransition = { DashboardMotion.previewEnter },
      exitTransition = { ExitTransition.None },
      popEnterTransition = { EnterTransition.None },
      popExitTransition = { DashboardMotion.previewExit },
    ) { backStackEntry ->
      val route = backStackEntry.toRoute<WidgetSettingsRoute>()

      WidgetSettingsSheet(
        widgetTypeId = route.widgetId,
        widgetRegistry = widgetRegistry,
        dataProviderRegistry = dataProviderRegistry,
        providerSettingsStore = providerSettingsStore,
        entitlementManager = entitlementManager,
        onDismiss = {
          navController.popBackStack(EmptyRoute, inclusive = false)
        },
        onNavigate = { _ ->
          // Sub-navigation for pickers -- Phase 11
        },
        onNavigateToSetup = { providerId ->
          navController.navigate(SetupRoute(providerId = providerId))
        },
        onNavigateToPackBrowser = { _ ->
          // Pack browser -- future
        },
      )
    }

    // Setup wizard -- hub transition (scale + fade)
    composable<SetupRoute>(
      enterTransition = { DashboardMotion.hubEnter },
      exitTransition = { DashboardMotion.hubExit },
      popEnterTransition = { DashboardMotion.hubEnter },
      popExitTransition = { DashboardMotion.hubExit },
    ) { backStackEntry ->
      val route = backStackEntry.toRoute<SetupRoute>()
      val provider = dataProviderRegistry.getAll().firstOrNull { it.sourceId == route.providerId }

      if (provider != null) {
        SetupSheet(
          setupSchema = provider.setupSchema,
          packId = provider.sourceId.substringBefore(':'),
          providerId = provider.sourceId,
          providerSettingsStore = providerSettingsStore,
          pairedDeviceStore = pairedDeviceStore,
          evaluator = setupEvaluator,
          entitlementManager = entitlementManager,
          onComplete = {
            navController.popBackStack()
          },
          onDismiss = {
            navController.popBackStack()
          },
        )
      }
    }
  }
}
