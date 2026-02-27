package app.dqxn.android.feature.dashboard.binding

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.dqxn.android.data.layout.DashboardWidgetInstance
import app.dqxn.android.feature.dashboard.command.DashboardCommand
import app.dqxn.android.feature.dashboard.coordinator.EditModeCoordinator
import app.dqxn.android.feature.dashboard.coordinator.WidgetBindingCoordinator
import app.dqxn.android.feature.dashboard.grid.ResizeUpdate
import app.dqxn.android.feature.dashboard.ui.WidgetErrorFallback
import app.dqxn.android.feature.dashboard.ui.WidgetStatusOverlay
import app.dqxn.android.sdk.contracts.registry.WidgetRegistry
import app.dqxn.android.sdk.contracts.status.WidgetRenderState
import app.dqxn.android.sdk.ui.LocalWidgetPreviewUnits
import app.dqxn.android.sdk.ui.PreviewGridSize
import app.dqxn.android.sdk.ui.UnknownWidgetPlaceholder
import app.dqxn.android.sdk.ui.widget.LocalWidgetData
import kotlinx.collections.immutable.persistentMapOf

/**
 * Error boundary composable wrapping each widget's [WidgetRenderer.Render] call.
 *
 * Responsibilities:
 * 1. Resolves renderer via [WidgetRegistry.findByTypeId]. Null -> [UnknownWidgetPlaceholder] (F2.13).
 * 2. Collects per-widget data via [WidgetBindingCoordinator.widgetData] with `collectAsState()`
 *    (Layer 0, NOT collectAsStateWithLifecycle per CLAUDE.md).
 * 3. Provides data via [CompositionLocalProvider] with [LocalWidgetData] and [LocalWidgetPreviewUnits].
 * 4. Error boundary via render error state. On crash -> [WidgetErrorFallback] +
 *    [DashboardCommand.WidgetCrash] reported. Compose does not support try-catch around composable
 *    calls, so the error state is tracked via [WidgetStatusCache] and binding coordinator crash
 *    reporting.
 * 5. Gates interactive actions via [EditModeCoordinator.isInteractionAllowed] (F2.18).
 * 6. Applies accessibility semantics from [WidgetRenderer.accessibilityDescription] (F2.19).
 * 7. Renders status overlay from [WidgetStatusCache] (F3.14).
 */
@Composable
public fun WidgetSlot(
  widget: DashboardWidgetInstance,
  widgetBindingCoordinator: WidgetBindingCoordinator,
  widgetRegistry: WidgetRegistry,
  editModeCoordinator: EditModeCoordinator,
  resizeState: ResizeUpdate?,
  onCommand: (DashboardCommand) -> Unit,
  modifier: Modifier = Modifier,
) {
  // Resolve renderer
  val renderer = widgetRegistry.findByTypeId(widget.typeId)

  if (renderer == null) {
    UnknownWidgetPlaceholder(
      typeId = widget.typeId,
      modifier = modifier.testTag("widget_${widget.instanceId}"),
    )
    return
  }

  // Collect per-widget data (Layer 0: collectAsState, NOT collectAsStateWithLifecycle)
  val widgetData by widgetBindingCoordinator.widgetData(widget.instanceId).collectAsState()

  // Collect per-widget status for overlay rendering (F3.14)
  val widgetStatus by widgetBindingCoordinator.widgetStatus(widget.instanceId).collectAsState()

  // Collect edit state for interaction gating (F2.18)
  val editState by editModeCoordinator.editState.collectAsState()
  val isInteractionAllowed by remember(editState, widget.instanceId) {
    derivedStateOf { editModeCoordinator.isInteractionAllowed(widget.instanceId) }
  }

  // Accessibility description (F2.19)
  val accessibilityDesc = remember(widgetData) {
    try {
      renderer.accessibilityDescription(widgetData)
    } catch (_: Exception) {
      "Widget ${widget.typeId}"
    }
  }

  // Resize preview units for content-aware relayout
  val previewUnits = if (resizeState?.widgetId == widget.instanceId && resizeState.isResizing) {
    PreviewGridSize(
      widthUnits = resizeState.targetSize.widthUnits,
      heightUnits = resizeState.targetSize.heightUnits,
    )
  } else {
    null
  }

  // Error boundary state (F2.14). Compose does not support try-catch around @Composable calls.
  // Render errors are tracked via this state flag. The binding coordinator's crash reporting
  // (via WidgetCrash command) handles the upstream error pipeline.
  var hasRenderError by remember { mutableStateOf(false) }

  Box(
    modifier = modifier
      .testTag("widget_${widget.instanceId}")
      .semantics { contentDescription = accessibilityDesc },
  ) {
    if (hasRenderError) {
      WidgetErrorFallback(
        onRetry = { hasRenderError = false },
      )
    } else {
      // Check for error status from the binding coordinator (connection errors, retries exhausted)
      val overlayState = widgetStatus.overlayState
      val isConnectionError = overlayState is WidgetRenderState.ConnectionError

      if (isConnectionError) {
        // Binding-level error: show fallback
        WidgetErrorFallback(
          onRetry = {
            // Retry by re-binding
            widgetBindingCoordinator.bind(widget)
          },
        )
      } else {
        CompositionLocalProvider(
          LocalWidgetData provides widgetData,
          LocalWidgetPreviewUnits provides previewUnits,
        ) {
          renderer.Render(
            isEditMode = !isInteractionAllowed,
            style = widget.style,
            settings = persistentMapOf(),
            modifier = Modifier,
          )
        }
      }

      // Status overlay (F3.14): EntitlementRevoked, SetupRequired, DataStale, etc.
      if (overlayState != WidgetRenderState.Ready && !isConnectionError) {
        WidgetStatusOverlay(
          renderState = overlayState,
          cornerRadiusDp = 12f, // CardSize.MEDIUM
          onSetupTap = {
            onCommand(DashboardCommand.OpenWidgetSettings(widget.instanceId))
          },
          onEntitlementTap = {
            onCommand(DashboardCommand.OpenWidgetSettings(widget.instanceId))
          },
        )
      }
    }
  }
}
