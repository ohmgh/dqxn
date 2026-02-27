package app.dqxn.android.feature.dashboard.layer

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import app.dqxn.android.core.design.motion.DashboardMotion
import app.dqxn.android.data.device.PairedDeviceStore
import app.dqxn.android.feature.dashboard.command.DashboardCommand
import app.dqxn.android.feature.dashboard.coordinator.ThemeCoordinator
import app.dqxn.android.feature.diagnostics.DiagnosticsScreen
import app.dqxn.android.feature.onboarding.FirstRunFlow
import app.dqxn.android.feature.onboarding.OnboardingViewModel
import app.dqxn.android.feature.settings.PackBrowserContent
import app.dqxn.android.feature.settings.R as SettingsR
import app.dqxn.android.feature.settings.WidgetPicker
import app.dqxn.android.feature.settings.main.MainSettings
import app.dqxn.android.feature.settings.main.MainSettingsViewModel
import app.dqxn.android.sdk.contracts.setup.SetupEvaluator
import app.dqxn.android.feature.settings.setup.SetupSheet
import app.dqxn.android.feature.settings.overlay.OverlayScaffold
import app.dqxn.android.feature.settings.overlay.OverlayType
import app.dqxn.android.feature.settings.theme.AutoSwitchModeContent
import app.dqxn.android.feature.settings.theme.ThemeSelector
import app.dqxn.android.feature.settings.theme.ThemeStudio
import app.dqxn.android.feature.settings.widget.WidgetSettingsSheet
import app.dqxn.android.sdk.contracts.entitlement.EntitlementManager
import app.dqxn.android.sdk.contracts.registry.DataProviderRegistry
import app.dqxn.android.sdk.contracts.registry.WidgetRegistry
import app.dqxn.android.sdk.contracts.settings.ProviderSettingsStore
import app.dqxn.android.sdk.ui.theme.DashboardThemeDefinition
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.launch

/**
 * Layer 1 navigation scaffold for overlay UI.
 *
 * Route table: 11 type-safe routes via `@Serializable` route classes in [OverlayRoutes.kt]:
 * - [EmptyRoute] -- no overlay (Layer 0 visible)
 * - [WidgetPickerRoute] -- widget selection grid, hub transitions
 * - [SettingsRoute] -- main settings, source-varying transitions
 * - [WidgetSettingsRoute] -- per-widget settings, preview transitions with None exit/popEnter
 * - [SetupRoute] -- provider setup wizard, hub transitions
 * - [AutoSwitchModeRoute] -- auto-switch mode selector, preview transitions
 * - [ThemeSelectorRoute] -- theme browser, preview transitions, popEnter=fadeIn(150ms)
 * - [ThemeStudioRoute] -- custom theme editor, preview transitions, popEnter=fadeIn(150ms)
 * - [DiagnosticsRoute] -- diagnostics hub, hub transitions
 * - [OnboardingRoute] -- first-run onboarding, hub transitions
 * - [PackBrowserRoute] -- pack browser, source-varying hub transitions
 *
 * **Source-varying transitions (replication advisory section 4):**
 * - Settings exit to ThemeSelector: fadeOut(100ms) not previewExit
 * - Settings popEnter from ThemeSelector: fadeIn(150ms) not previewEnter
 * - Settings exit to Diagnostics/Onboarding (hub routes): ExitTransition.None
 * - Settings popEnter from Diagnostics/Onboarding: EnterTransition.None
 * - ThemeSelector popEnter: fadeIn(150ms) NOT previewEnter (prevents double-slide)
 *
 * Start destination is [EmptyRoute] (no overlay shown by default).
 */
@Composable
public fun OverlayNavHost(
  navController: NavHostController,
  widgetRegistry: WidgetRegistry,
  dataProviderRegistry: DataProviderRegistry,
  providerSettingsStore: ProviderSettingsStore,
  entitlementManager: EntitlementManager,
  setupEvaluator: SetupEvaluator,
  pairedDeviceStore: PairedDeviceStore,
  mainSettingsViewModel: MainSettingsViewModel,
  themeCoordinator: ThemeCoordinator,
  allThemes: ImmutableList<DashboardThemeDefinition>,
  customThemeCount: Int,
  onCommand: (DashboardCommand) -> Unit,
  onShowToast: (String) -> Unit,
  onSimulateFreeUser: ((Boolean) -> Unit)? = null,
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
          navController.navigate(WidgetSettingsRoute(widgetId = typeId))
        },
        onDismiss = { navController.popBackStack(EmptyRoute, inclusive = false) },
      )
    }

    // Main settings -- source-varying transitions per replication advisory section 4
    // Exit/popEnter adapt based on navigation target/source:
    //   -> ThemeSelector: fadeOut(100ms) / fadeIn(150ms) (smooth cross-fade)
    //   -> Diagnostics/Onboarding: None (hub overlay covers)
    //   -> default: previewExit / previewEnter
    composable<SettingsRoute>(
      enterTransition = { DashboardMotion.previewEnter },
      exitTransition = {
        val target = targetState.destination.route ?: ""
        when {
          target.contains(THEME_SELECTOR_ROUTE_PATTERN) -> fadeOut(tween(100))
          target.contains(AUTO_SWITCH_MODE_ROUTE_PATTERN) -> fadeOut(tween(100))
          target.contains(DIAGNOSTICS_ROUTE_PATTERN) -> ExitTransition.None
          target.contains(ONBOARDING_ROUTE_PATTERN) -> ExitTransition.None
          target.contains(PACK_BROWSER_ROUTE_PATTERN) -> ExitTransition.None
          else -> DashboardMotion.previewExit
        }
      },
      popEnterTransition = {
        val source = initialState.destination.route ?: ""
        when {
          source.contains(THEME_SELECTOR_ROUTE_PATTERN) -> fadeIn(tween(150))
          source.contains(AUTO_SWITCH_MODE_ROUTE_PATTERN) -> fadeIn(tween(150))
          source.contains(DIAGNOSTICS_ROUTE_PATTERN) -> EnterTransition.None
          source.contains(ONBOARDING_ROUTE_PATTERN) -> EnterTransition.None
          source.contains(PACK_BROWSER_ROUTE_PATTERN) -> EnterTransition.None
          else -> DashboardMotion.previewEnter
        }
      },
      popExitTransition = { DashboardMotion.previewExit },
    ) {
      // Clear preview theme on settings enter (advisory section 3 race fix)
      LaunchedEffect(Unit) { onCommand(DashboardCommand.PreviewTheme(null)) }

      val analyticsConsent by mainSettingsViewModel.analyticsConsent.collectAsState()
      val showStatusBar by mainSettingsViewModel.showStatusBar.collectAsState()
      val keepScreenOn by mainSettingsViewModel.keepScreenOn.collectAsState()

      val themeState by themeCoordinator.themeState.collectAsState()

      PreviewOverlay(
        previewFraction = 0.15f,
        onDismiss = { navController.popBackStack(EmptyRoute, inclusive = false) },
      ) {
        MainSettings(
          analyticsConsent = analyticsConsent,
          showStatusBar = showStatusBar,
          keepScreenOn = keepScreenOn,
          lightThemeName = themeState.lightTheme.displayName,
          darkThemeName = themeState.darkTheme.displayName,
          packCount = widgetRegistry.getAll().map { it.typeId.substringBefore(':') }.toSet().size,
          themeCount = allThemes.size,
          widgetCount = widgetRegistry.getAll().size,
          providerCount = dataProviderRegistry.getAll().size,
          autoSwitchModeDescription = themeState.autoSwitchMode.name.lowercase().replace('_', ' ')
            .replaceFirstChar { it.uppercase() },
          versionName = "1.0",
          onSetAnalyticsConsent = mainSettingsViewModel::setAnalyticsConsent,
          onSetShowStatusBar = mainSettingsViewModel::setShowStatusBar,
          onSetKeepScreenOn = mainSettingsViewModel::setKeepScreenOn,
          onDeleteAllData = mainSettingsViewModel::deleteAllData,
          onNavigateToThemeMode = {
            navController.navigate(AutoSwitchModeRoute)
          },
          onNavigateToLightTheme = {
            onCommand(DashboardCommand.PreviewTheme(themeState.lightTheme))
            navController.navigate(ThemeSelectorRoute(isDark = false))
          },
          onNavigateToDarkTheme = {
            onCommand(DashboardCommand.PreviewTheme(themeState.darkTheme))
            navController.navigate(ThemeSelectorRoute(isDark = true))
          },
          onNavigateToDashPacks = {
            navController.navigate(PackBrowserRoute)
          },
          onNavigateToDiagnostics = { navController.navigate(DiagnosticsRoute) },
          onResetDash = { onCommand(DashboardCommand.ResetLayout) },
          onClose = { navController.popBackStack(EmptyRoute, inclusive = false) },
        )
      }
    }

    // Widget settings -- ExitTransition.None / EnterTransition.None per advisory section 2
    composable<WidgetSettingsRoute>(
      enterTransition = { DashboardMotion.previewEnter },
      exitTransition = { ExitTransition.None },
      popEnterTransition = { EnterTransition.None },
      popExitTransition = { DashboardMotion.previewExit },
    ) { backStackEntry ->
      val route = backStackEntry.toRoute<WidgetSettingsRoute>()

      PreviewOverlay(
        previewFraction = 0.38f,
        onDismiss = { navController.popBackStack(EmptyRoute, inclusive = false) },
      ) {
        WidgetSettingsSheet(
          widgetTypeId = route.widgetId,
          widgetRegistry = widgetRegistry,
          dataProviderRegistry = dataProviderRegistry,
          providerSettingsStore = providerSettingsStore,
          entitlementManager = entitlementManager,
          onDismiss = { navController.popBackStack(EmptyRoute, inclusive = false) },
          onNavigate = { _ ->
            // Sub-navigation for pickers -- Phase 11
          },
          onNavigateToSetup = { providerId ->
            navController.navigate(SetupRoute(providerId = providerId))
          },
          onNavigateToPackBrowser = { _ ->
            navController.navigate(PackBrowserRoute)
          },
        )
      }
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
          onComplete = { navController.popBackStack() },
          onDismiss = { navController.popBackStack() },
        )
      }
    }

    // Auto-switch mode selector -- preview transitions
    composable<AutoSwitchModeRoute>(
      enterTransition = { DashboardMotion.previewEnter },
      exitTransition = { DashboardMotion.previewExit },
      popEnterTransition = { fadeIn(tween(150)) },
      popExitTransition = { DashboardMotion.previewExit },
    ) {
      val themeState by themeCoordinator.themeState.collectAsState()

      PreviewOverlay(
        previewFraction = 0.15f,
        onDismiss = { navController.popBackStack() },
      ) {
        OverlayScaffold(
          title = stringResource(SettingsR.string.main_settings_theme_mode),
          overlayType = OverlayType.Preview,
          onBack = { navController.popBackStack() },
        ) {
          AutoSwitchModeContent(
            selectedMode = themeState.autoSwitchMode,
            illuminanceThreshold = themeState.illuminanceThreshold,
            entitlementManager = entitlementManager,
            onModeSelected = { mode -> onCommand(DashboardCommand.SetAutoSwitchMode(mode)) },
            onIlluminanceThresholdChanged = { threshold ->
              onCommand(DashboardCommand.SetIlluminanceThreshold(threshold))
            },
          )
        }
      }
    }

    // Theme selector -- preview transitions with CRITICAL popEnter override
    // popEnter uses fadeIn(150ms) NOT previewEnter to prevent double-slide when returning
    // from a theme sub-screen (replication advisory section 4)
    // exitTransition source-varying: fadeOut to ThemeStudio (sub-screen cross-fade),
    // previewExit otherwise
    composable<ThemeSelectorRoute>(
      enterTransition = { DashboardMotion.previewEnter },
      exitTransition = {
        val target = targetState.destination.route ?: ""
        when {
          target.contains(THEME_STUDIO_ROUTE_PATTERN) -> fadeOut(tween(100))
          else -> DashboardMotion.previewExit
        }
      },
      popEnterTransition = {
        val source = initialState.destination.route ?: ""
        when {
          source.contains(THEME_STUDIO_ROUTE_PATTERN) -> fadeIn(tween(150))
          else -> fadeIn(tween(150))
        }
      },
      popExitTransition = { DashboardMotion.previewExit },
    ) { backStackEntry ->
      val route = backStackEntry.toRoute<ThemeSelectorRoute>()
      val themeState by themeCoordinator.themeState.collectAsState()

      PreviewOverlay(
        previewFraction = 0.15f,
        onDismiss = {
          onCommand(DashboardCommand.PreviewTheme(null))
          navController.popBackStack()
        },
      ) {
        ThemeSelector(
          allThemes = allThemes,
          isDark = route.isDark,
          previewTheme = themeState.previewTheme,
          customThemeCount = customThemeCount,
          entitlementManager = entitlementManager,
          onPreviewTheme = { theme -> onCommand(DashboardCommand.PreviewTheme(theme)) },
          onApplyTheme = { themeId -> onCommand(DashboardCommand.SetTheme(themeId)) },
          onClearPreview = { onCommand(DashboardCommand.PreviewTheme(null)) },
          onCloneToCustom = { sourceTheme ->
            // Clone: navigate to ThemeStudio. ThemeStudio uses existingTheme param
            // to create a copy with a new custom ID.
            onCommand(DashboardCommand.PreviewTheme(sourceTheme))
            navController.navigate(ThemeStudioRoute(themeId = sourceTheme.themeId))
          },
          onOpenStudio = { existingTheme ->
            // Edit: navigate to ThemeStudio with the existing custom theme
            onCommand(DashboardCommand.PreviewTheme(existingTheme))
            navController.navigate(ThemeStudioRoute(themeId = existingTheme.themeId))
          },
          onDeleteCustom = { themeId ->
            // Delete: clear preview first (advisory section 3 delete-while-previewing fix),
            // then dispatch delete command
            onCommand(DashboardCommand.PreviewTheme(null))
            onCommand(DashboardCommand.DeleteCustomTheme(themeId))
          },
          onCreateNewTheme = {
            // Navigate to ThemeStudio for new theme creation
            navController.navigate(ThemeStudioRoute(themeId = null))
          },
          onShowToast = onShowToast,
          onBack = { navController.popBackStack() },
        )
      }
    }

    // Theme studio -- preview transitions (sub-screen of ThemeSelector)
    // popEnter uses fadeIn(150ms) NOT previewEnter to prevent double-slide
    composable<ThemeStudioRoute>(
      enterTransition = { DashboardMotion.previewEnter },
      exitTransition = { DashboardMotion.previewExit },
      popEnterTransition = { fadeIn(tween(150)) },
      popExitTransition = { DashboardMotion.previewExit },
    ) { backStackEntry ->
      val route = backStackEntry.toRoute<ThemeStudioRoute>()
      val existingTheme = allThemes.firstOrNull { it.themeId == route.themeId }

      PreviewOverlay(
        previewFraction = 0.15f,
        onDismiss = {
          onCommand(DashboardCommand.PreviewTheme(null))
          navController.popBackStack()
        },
      ) {
        ThemeStudio(
          existingTheme = existingTheme,
          customThemeCount = customThemeCount,
          onAutoSave = { theme -> onCommand(DashboardCommand.SaveCustomTheme(theme)) },
          onDelete = { themeId ->
            // Delete-while-previewing: clear preview BEFORE delete (advisory section 3)
            onCommand(DashboardCommand.PreviewTheme(null))
            onCommand(DashboardCommand.DeleteCustomTheme(themeId))
            navController.popBackStack()
          },
          onClearPreview = { onCommand(DashboardCommand.PreviewTheme(null)) },
          onClose = { navController.popBackStack() },
        )
      }
    }

    // Pack browser -- source-varying hub transitions
    // PackBrowserContent wraps itself in OverlayScaffold(Hub), no PreviewOverlay needed
    // Enter from Settings: horizontal slide-in; default: fade
    composable<PackBrowserRoute>(
      enterTransition = {
        val source = initialState.destination.route ?: ""
        when {
          source.contains(SETTINGS_ROUTE_PATTERN) -> DashboardMotion.packBrowserEnterFromSettings
          else -> DashboardMotion.packBrowserEnterDefault
        }
      },
      exitTransition = { DashboardMotion.hubExit },
      popEnterTransition = { DashboardMotion.hubEnter },
      popExitTransition = {
        val target = targetState.destination.route ?: ""
        when {
          target.contains(SETTINGS_ROUTE_PATTERN) -> DashboardMotion.packBrowserPopExitToSettings
          else -> DashboardMotion.packBrowserPopExitDefault
        }
      },
    ) {
      PackBrowserContent(
        widgetRegistry = widgetRegistry,
        dataProviderRegistry = dataProviderRegistry,
        allThemes = allThemes,
        entitlementManager = entitlementManager,
        onSelectPack = { _ ->
          // Pack detail navigation -- future
        },
        onDismiss = { navController.popBackStack() },
        onSimulateFreeUser = onSimulateFreeUser,
      )
    }

    // Diagnostics -- hub transition (scale + fade)
    composable<DiagnosticsRoute>(
      enterTransition = { DashboardMotion.hubEnter },
      exitTransition = { DashboardMotion.hubExit },
      popEnterTransition = { DashboardMotion.hubEnter },
      popExitTransition = { DashboardMotion.hubExit },
    ) {
      DiagnosticsScreen(
        onClose = { navController.popBackStack() },
      )
    }

    // Onboarding -- hub transition (scale + fade)
    composable<OnboardingRoute>(
      enterTransition = { DashboardMotion.hubEnter },
      exitTransition = { DashboardMotion.hubExit },
      popEnterTransition = { DashboardMotion.hubEnter },
      popExitTransition = { DashboardMotion.hubExit },
    ) {
      val onboardingViewModel: OnboardingViewModel = hiltViewModel()
      val scope = rememberCoroutineScope()

      FirstRunFlow(
        freeThemes = onboardingViewModel.freeThemes,
        onConsent = { enabled -> onboardingViewModel.setAnalyticsConsent(enabled) },
        onDismissDisclaimer = { onboardingViewModel.setHasSeenDisclaimer() },
        onSelectTheme = { themeId -> onboardingViewModel.selectTheme(themeId) },
        onComplete = {
          scope.launch {
            onboardingViewModel.completeOnboarding()
            navController.popBackStack(EmptyRoute, inclusive = false)
          }
        },
      )
    }
  }
}

/**
 * Route pattern strings for source-varying transition matching. Navigation Compose serializes
 * `@Serializable` route classes using their qualified name.
 */
private val THEME_SELECTOR_ROUTE_PATTERN = ThemeSelectorRoute::class.qualifiedName!!
private val THEME_STUDIO_ROUTE_PATTERN = ThemeStudioRoute::class.qualifiedName!!
private val AUTO_SWITCH_MODE_ROUTE_PATTERN = AutoSwitchModeRoute::class.qualifiedName!!
private val DIAGNOSTICS_ROUTE_PATTERN = DiagnosticsRoute::class.qualifiedName!!
private val ONBOARDING_ROUTE_PATTERN = OnboardingRoute::class.qualifiedName!!
private val PACK_BROWSER_ROUTE_PATTERN = PackBrowserRoute::class.qualifiedName!!
private val SETTINGS_ROUTE_PATTERN = SettingsRoute::class.qualifiedName!!
