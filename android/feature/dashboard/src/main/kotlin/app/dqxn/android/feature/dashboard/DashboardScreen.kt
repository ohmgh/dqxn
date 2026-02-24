package app.dqxn.android.feature.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.rememberNavController
import app.dqxn.android.feature.dashboard.command.DashboardCommand
import app.dqxn.android.feature.dashboard.layer.CriticalBannerHost
import app.dqxn.android.feature.dashboard.layer.DashboardLayer
import app.dqxn.android.feature.dashboard.layer.NotificationBannerHost
import app.dqxn.android.feature.dashboard.layer.OverlayNavHost
import app.dqxn.android.feature.dashboard.profile.ProfilePageTransition
import app.dqxn.android.feature.dashboard.ui.AUTO_HIDE_DELAY_MS
import app.dqxn.android.feature.dashboard.ui.DashboardButtonBar
import app.dqxn.android.sdk.ui.theme.LocalDashboardTheme
import kotlinx.coroutines.delay

/**
 * Root screen composable assembling all dashboard layers.
 *
 * Layer stack (back to front):
 * 0. DashboardLayer (grid + widgets, always present, dashboard-as-shell F1.13)
 * 0.5 NotificationBannerHost (non-critical banners)
 * 0.8 DashboardButtonBar (auto-hiding bottom bar, floats over canvas)
 * 1. OverlayNavHost (settings, widget picker, etc. -- populated Phase 10)
 * 1.5 CriticalBannerHost (safe mode banner, above all overlays)
 *
 * State collection: all StateFlows collected via `collectAsState()` (Layer 0 per CLAUDE.md).
 * Overlay pause (F1.14): when an overlay route is active, widget bindings are paused.
 */
@Composable
public fun DashboardScreen(
  viewModel: DashboardViewModel = hiltViewModel(),
) {
  val navController = rememberNavController()

  // Collect state from all coordinators (Layer 0 -- collectAsState, NOT collectAsStateWithLifecycle)
  val layoutState by viewModel.layoutCoordinator.layoutState.collectAsState()
  val configBoundaries by viewModel.layoutCoordinator.configurationBoundaries.collectAsState()
  val editState by viewModel.editModeCoordinator.editState.collectAsState()
  val themeState by viewModel.themeCoordinator.themeState.collectAsState()
  val profileState by viewModel.profileCoordinator.profileState.collectAsState()
  val banners by viewModel.notificationCoordinator.activeBanners.collectAsState()
  val dragState by viewModel.editModeCoordinator.dragState.collectAsState()
  val resizeState by viewModel.editModeCoordinator.resizeState.collectAsState()

  // Pause bindings when overlay is active (F1.14)
  val currentEntry by navController.currentBackStackEntryFlow.collectAsState(initial = null)
  val hasOverlay = currentEntry?.destination?.route != null &&
    currentEntry?.destination?.route != ROUTE_EMPTY

  DisposableEffect(hasOverlay) {
    if (hasOverlay) {
      viewModel.widgetBindingCoordinator.pauseAll()
    } else {
      viewModel.widgetBindingCoordinator.resumeAll()
    }
    onDispose {}
  }

  // Bottom bar auto-hide
  var isBarVisible by remember { mutableStateOf(true) }
  LaunchedEffect(isBarVisible) {
    if (isBarVisible) {
      delay(AUTO_HIDE_DELAY_MS)
      isBarVisible = false
    }
  }

  // Show bar in edit mode always
  LaunchedEffect(editState.isEditMode) {
    if (editState.isEditMode) {
      isBarVisible = true
    }
  }

  val onCommand: (DashboardCommand) -> Unit = viewModel::dispatch

  CompositionLocalProvider(LocalDashboardTheme provides themeState.displayTheme) {
    Box(Modifier.fillMaxSize()) {
      // Layer 0: Dashboard with profile paging
      ProfilePageTransition(
        profiles = profileState.profiles,
        activeProfileId = profileState.activeProfileId,
        isEditMode = editState.isEditMode,
        isReducedMotion = viewModel.reducedMotionHelper.isReducedMotion,
        onSwitchProfile = { profileId ->
          onCommand(DashboardCommand.SwitchProfile(profileId))
        },
      ) { _ ->
        DashboardLayer(
          widgets = layoutState.widgets,
          viewportCols = DEFAULT_VIEWPORT_COLS,
          viewportRows = DEFAULT_VIEWPORT_ROWS,
          editState = editState,
          dragState = dragState,
          resizeState = resizeState,
          configurationBoundaries = configBoundaries,
          widgetBindingCoordinator = viewModel.widgetBindingCoordinator,
          widgetRegistry = viewModel.widgetRegistry,
          editModeCoordinator = viewModel.editModeCoordinator,
          reducedMotionHelper = viewModel.reducedMotionHelper,
          widgetGestureHandler = viewModel.widgetGestureHandler,
          blankSpaceGestureHandler = viewModel.blankSpaceGestureHandler,
          semanticsOwnerHolder = viewModel.semanticsOwnerHolder,
          userPreferencesRepository = viewModel.userPreferencesRepository,
          onCommand = onCommand,
        )
      }

      // Layer 0.5: Non-critical banners
      NotificationBannerHost(
        banners = banners,
        onDismiss = { bannerId ->
          viewModel.notificationCoordinator.dismissBanner(bannerId)
        },
        onAction = { bannerId, actionId ->
          // Action routing handled in Phase 10
        },
      )

      // Layer 0.8: Bottom bar (auto-hiding, floats over canvas)
      Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
      ) {
        DashboardButtonBar(
          isEditMode = editState.isEditMode,
          profiles = profileState.profiles,
          activeProfileId = profileState.activeProfileId,
          isVisible = isBarVisible || editState.isEditMode,
          onSettingsClick = {
            // Navigate to settings overlay -- populated Phase 10
          },
          onProfileClick = { profileId ->
            onCommand(DashboardCommand.SwitchProfile(profileId))
          },
          onAddWidgetClick = {
            // Navigate to widget picker overlay -- populated Phase 10
          },
          onEditModeToggle = {
            if (editState.isEditMode) {
              onCommand(DashboardCommand.ExitEditMode)
            } else {
              onCommand(DashboardCommand.EnterEditMode)
            }
          },
          onThemeToggle = {
            onCommand(DashboardCommand.CycleThemeMode)
          },
          onInteraction = { isBarVisible = true },
        )
      }

      // Layer 1: Overlay navigation host (empty route table, populated Phase 10)
      OverlayNavHost(
        navController = navController,
        onCommand = onCommand,
      )

      // Layer 1.5: Critical banners (safe mode -- above all overlays)
      CriticalBannerHost(
        banners = banners,
        onAction = { bannerId, actionId ->
          when (actionId) {
            "reset" -> onCommand(DashboardCommand.ResetLayout)
            "report" -> {
              // Crash reporting action -- Phase 10+
            }
          }
        },
      )
    }
  }
}

private const val ROUTE_EMPTY = "empty"
private const val DEFAULT_VIEWPORT_COLS = 20
private const val DEFAULT_VIEWPORT_ROWS = 12
