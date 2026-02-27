package app.dqxn.android.feature.dashboard.layer

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.agentic.android.semantics.SemanticsOwnerHolder
import app.dqxn.android.data.layout.DashboardWidgetInstance
import app.dqxn.android.data.preferences.UserPreferencesRepository
import app.dqxn.android.feature.dashboard.binding.WidgetSlot
import app.dqxn.android.feature.dashboard.command.DashboardCommand
import app.dqxn.android.feature.dashboard.coordinator.EditModeCoordinator
import app.dqxn.android.feature.dashboard.coordinator.EditState
import app.dqxn.android.feature.dashboard.coordinator.WidgetBindingCoordinator
import app.dqxn.android.feature.dashboard.gesture.ReducedMotionHelper
import app.dqxn.android.feature.dashboard.grid.BlankSpaceGestureHandler
import app.dqxn.android.feature.dashboard.grid.ConfigurationBoundary
import app.dqxn.android.feature.dashboard.grid.DashboardGrid
import app.dqxn.android.feature.dashboard.grid.DragUpdate
import app.dqxn.android.feature.dashboard.grid.ResizeUpdate
import app.dqxn.android.feature.dashboard.grid.WidgetGestureHandler
import app.dqxn.android.sdk.contracts.registry.WidgetRegistry
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.Flow

/**
 * Root Layer 0 composable. Persists beneath all overlays (F1.13 dashboard-as-shell).
 *
 * Responsibilities:
 * - Hosts [DashboardGrid] with all widget rendering
 * - Registers [SemanticsOwnerHolder] (debug only) for agentic `dump-semantics`
 * - Configuration boundary visualization in edit mode (F1.26)
 * - Orientation lock (F1.15) via [Activity.requestedOrientation]
 * - Keep screen on (F1.16) via [FLAG_KEEP_SCREEN_ON]
 * - Status bar toggle (F1.2) via [WindowInsetsControllerCompat]
 * - Lifecycle pause/resume (NF-L1) for widget binding lifecycle
 */
@Composable
public fun DashboardLayer(
  widgets: ImmutableList<DashboardWidgetInstance>,
  viewportCols: Int,
  viewportRows: Int,
  editState: EditState,
  dragState: DragUpdate?,
  resizeState: ResizeUpdate?,
  configurationBoundaries: ImmutableList<ConfigurationBoundary>,
  widgetBindingCoordinator: WidgetBindingCoordinator,
  widgetRegistry: WidgetRegistry,
  editModeCoordinator: EditModeCoordinator,
  reducedMotionHelper: ReducedMotionHelper,
  widgetGestureHandler: WidgetGestureHandler,
  blankSpaceGestureHandler: BlankSpaceGestureHandler,
  semanticsOwnerHolder: SemanticsOwnerHolder,
  userPreferencesRepository: UserPreferencesRepository,
  onCommand: (DashboardCommand) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val view = LocalView.current
  val lifecycleOwner = LocalLifecycleOwner.current

  // Register SemanticsOwner for agentic tree inspection (debug only)
  DisposableEffect(view) {
    val semanticsOwner = try {
      // Access the SemanticsOwner from the Compose view hierarchy
      val field = view.javaClass.getDeclaredField("semanticsOwner")
      field.isAccessible = true
      field.get(view)
    } catch (_: Exception) {
      null
    }
    if (semanticsOwner != null) {
      semanticsOwnerHolder.register(semanticsOwner)
    }
    onDispose {
      semanticsOwnerHolder.unregister()
    }
  }

  // Orientation lock (F1.15)
  val orientationLock by userPreferencesRepository.orientationLock.collectAsState(initial = "auto")
  DisposableEffect(orientationLock) {
    val activity = context as? Activity
    if (activity != null) {
      activity.requestedOrientation = when (orientationLock) {
        "landscape" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        "portrait" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
      }
    }
    onDispose {
      val act = context as? Activity
      act?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
  }

  // Keep screen on (F1.16)
  val keepScreenOn by userPreferencesRepository.keepScreenOn.collectAsState(initial = true)
  DisposableEffect(keepScreenOn) {
    val activity = context as? Activity
    if (activity != null) {
      if (keepScreenOn) {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
      } else {
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
      }
    }
    onDispose {
      val act = context as? Activity
      act?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
  }

  // Status bar toggle (F1.2)
  DisposableEffect(editState.showStatusBar) {
    val activity = context as? Activity ?: return@DisposableEffect onDispose {}
    val window = activity.window
    val controller = WindowCompat.getInsetsController(window, view)
    if (editState.showStatusBar) {
      controller.show(WindowInsetsCompat.Type.systemBars())
    } else {
      controller.hide(WindowInsetsCompat.Type.systemBars())
      controller.systemBarsBehavior =
        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
    onDispose {}
  }

  // Lifecycle pause/resume (NF-L1)
  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_RESUME -> widgetBindingCoordinator.resumeAll()
        Lifecycle.Event.ON_PAUSE -> widgetBindingCoordinator.pauseAll()
        else -> {}
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
    }
  }

  Box(modifier = modifier.fillMaxSize()) {
    DashboardGrid(
      widgets = widgets,
      viewportCols = viewportCols,
      viewportRows = viewportRows,
      editState = editState,
      dragState = dragState,
      resizeState = resizeState,
      configurationBoundaries = configurationBoundaries,
      widgetBindingCoordinator = widgetBindingCoordinator,
      widgetRegistry = widgetRegistry,
      editModeCoordinator = editModeCoordinator,
      reducedMotionHelper = reducedMotionHelper,
      widgetGestureHandler = widgetGestureHandler,
      blankSpaceGestureHandler = blankSpaceGestureHandler,
      onCommand = onCommand,
    )
  }
}
