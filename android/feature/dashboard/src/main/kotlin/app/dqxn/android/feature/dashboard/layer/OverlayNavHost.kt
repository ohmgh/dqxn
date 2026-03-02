package app.dqxn.android.feature.dashboard.layer

import androidx.activity.compose.BackHandler
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideOutVertically
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
import app.dqxn.android.data.style.WidgetStyleStore
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
 * - [SettingsRoute] -- main settings, hub transitions with source-varying exit/popEnter
 * - [WidgetSettingsRoute] -- per-widget settings, preview transitions with None exit/popEnter
 * - [SetupRoute] -- provider setup wizard, hub transitions
 * - [AutoSwitchModeRoute] -- auto-switch mode selector, preview transitions
 * - [ThemeSelectorRoute] -- theme browser, preview transitions, popEnter=fadeIn(150ms)
 * - [ThemeStudioRoute] -- custom theme editor, preview transitions, popEnter=fadeIn(150ms)
 * - [DiagnosticsRoute] -- diagnostics hub, hub transitions
 * - [OnboardingRoute] -- first-run onboarding, hub transitions
 * - [PackBrowserRoute] -- pack browser, hub transitions (crossfade from WidgetSettings for shared element)
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
  widgetStyleStore: WidgetStyleStore,
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
    // Pop defaults match old codebase — ensures programmatic popBackStack() always animates
    // even if a per-route override is missing or route pattern matching falls through.
    popEnterTransition = { fadeIn() },
    popExitTransition = { slideOutVertically { it } },
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
          onCommand(DashboardCommand.AddWidgetByTypeId(typeId))
          navController.navigate(EmptyRoute) {
            popUpTo<WidgetPickerRoute> { inclusive = true }
          }
        },
        onDismiss = { navController.popBackStack(EmptyRoute, inclusive = false) },
      )
    }

    // Main settings -- hub overlay with source-varying exit/popEnter transitions
    // Exit/popEnter adapt based on navigation target/source:
    //   -> ThemeSelector/AutoSwitchMode: fadeOut(100ms) / fadeIn(150ms) (smooth cross-fade)
    //   -> Diagnostics/Onboarding/PackBrowser: None (hub overlay covers)
    //   -> default: hubExit / hubEnter
    composable<SettingsRoute>(
      enterTransition = { DashboardMotion.hubEnter },
      exitTransition = {
        // Subsheet routes: previewExit (slide down) so interruption reversal produces a
        // slide-up, matching the popEnterTransition (previewEnter). fadeOut/None reversals
        // produce a subtle fade-in that isn't perceived as an entry animation.
        val target = targetState.destination.route ?: ""
        when {
          target.contains(THEME_SELECTOR_ROUTE_PATTERN) -> DashboardMotion.previewExit
          target.contains(AUTO_SWITCH_MODE_ROUTE_PATTERN) -> DashboardMotion.previewExit
          target.contains(DIAGNOSTICS_ROUTE_PATTERN) -> DashboardMotion.previewExit
          target.contains(ONBOARDING_ROUTE_PATTERN) -> DashboardMotion.previewExit
          target.contains(PACK_BROWSER_ROUTE_PATTERN) -> DashboardMotion.previewExit
          else -> DashboardMotion.hubExit
        }
      },
      popEnterTransition = {
        val source = initialState.destination.route ?: ""
        when {
          // Subsheet routes: slideIn from bottom so Settings enters off-screen, avoiding
          // z-order occlusion of the subsheet's exit animation (AnimatedContent draws
          // pop-enter target on top; fadeIn would occlude in-place, slideIn doesn't).
          source.contains(THEME_SELECTOR_ROUTE_PATTERN) -> DashboardMotion.previewEnter
          source.contains(AUTO_SWITCH_MODE_ROUTE_PATTERN) -> DashboardMotion.previewEnter
          source.contains(DIAGNOSTICS_ROUTE_PATTERN) -> DashboardMotion.previewEnter
          source.contains(ONBOARDING_ROUTE_PATTERN) -> DashboardMotion.previewEnter
          source.contains(PACK_BROWSER_ROUTE_PATTERN) -> DashboardMotion.previewEnter
          else -> DashboardMotion.hubEnter
        }
      },
      popExitTransition = { DashboardMotion.hubExit },
    ) {
      // Clear preview theme on settings enter (advisory section 3 race fix)
      LaunchedEffect(Unit) { onCommand(DashboardCommand.PreviewTheme(null)) }

      val analyticsConsent by mainSettingsViewModel.analyticsConsent.collectAsState()
      val showStatusBar by mainSettingsViewModel.showStatusBar.collectAsState()
      val keepScreenOn by mainSettingsViewModel.keepScreenOn.collectAsState()

      val themeState by themeCoordinator.themeState.collectAsState()

      MainSettings(
        analyticsConsent = analyticsConsent,
        showStatusBar = showStatusBar,
        keepScreenOn = keepScreenOn,
        lightThemeName = themeState.lightTheme.displayName,
        darkThemeName = themeState.darkTheme.displayName,
        packCount = (
          widgetRegistry.getAll().map { it.typeId.substringBefore(':') } +
            dataProviderRegistry.getAll().map { it.sourceId.substringBefore(':') } +
            allThemes.mapNotNull { it.packId }
          ).toSet().size,
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

    // Widget settings -- ExitTransition.None / EnterTransition.None per advisory section 2
    composable<WidgetSettingsRoute>(
      enterTransition = { DashboardMotion.previewEnter },
      exitTransition = { ExitTransition.None },
      popEnterTransition = { EnterTransition.None },
      popExitTransition = { DashboardMotion.previewExit },
    ) { backStackEntry ->
      val route = backStackEntry.toRoute<WidgetSettingsRoute>()
      val handleDismiss = { navController.popBackStack(EmptyRoute, inclusive = false); Unit }

      BackHandler { handleDismiss() }
      PreviewOverlay(
        previewFraction = 0.38f,
        onDismiss = handleDismiss,
      ) {
        WidgetSettingsSheet(
          widgetTypeId = route.typeId,
          widgetInstanceId = route.widgetId,
          widgetRegistry = widgetRegistry,
          dataProviderRegistry = dataProviderRegistry,
          providerSettingsStore = providerSettingsStore,
          widgetStyleStore = widgetStyleStore,
          entitlementManager = entitlementManager,
          onDismiss = handleDismiss,
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
      val handleDismiss = { navController.popBackStack(); Unit }

      BackHandler { handleDismiss() }
      PreviewOverlay(
        previewFraction = 0.15f,
        onDismiss = handleDismiss,
      ) {
        OverlayScaffold(
          title = stringResource(SettingsR.string.main_settings_theme_mode),
          overlayType = OverlayType.Preview,
          onBack = handleDismiss,
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
      // Unified dismiss — DisposableEffect in ThemeSelector handles preview cleanup on disposal,
      // so we only popBackStack here. No pre-pop state changes that could cause recomposition
      // races that skip the NavHost pop exit animation.
      val handleDismiss = { navController.popBackStack(); Unit }

      BackHandler { handleDismiss() }
      PreviewOverlay(
        previewFraction = 0.15f,
        onDismiss = handleDismiss,
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
          onBack = handleDismiss,
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
      val handleDismiss = { navController.popBackStack(); Unit }

      BackHandler { handleDismiss() }
      PreviewOverlay(
        previewFraction = 0.15f,
        onDismiss = handleDismiss,
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
          onClose = handleDismiss,
        )
      }
    }

    // Pack browser -- hub transitions matching diagnostics sheet pattern
    // PackBrowserContent wraps itself in OverlayScaffold(Hub), no PreviewOverlay needed
    // From WidgetSettings: minimal crossfade so shared element card morph drives the transition
    composable<PackBrowserRoute>(
      enterTransition = {
        val source = initialState.destination.route ?: ""
        when {
          source.contains(WIDGET_SETTINGS_ROUTE_PATTERN) -> fadeIn(tween(300))
          else -> DashboardMotion.hubEnter
        }
      },
      exitTransition = { DashboardMotion.hubExit },
      popEnterTransition = { DashboardMotion.hubEnter },
      popExitTransition = {
        val target = targetState.destination.route ?: ""
        when {
          target.contains(WIDGET_SETTINGS_ROUTE_PATTERN) -> fadeOut(tween(300))
          else -> DashboardMotion.hubExit
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
private val WIDGET_SETTINGS_ROUTE_PATTERN = WidgetSettingsRoute::class.qualifiedName!!
