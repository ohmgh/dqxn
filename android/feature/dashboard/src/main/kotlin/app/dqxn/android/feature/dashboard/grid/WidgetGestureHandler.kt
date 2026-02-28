package app.dqxn.android.feature.dashboard.grid

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import app.dqxn.android.data.layout.GridPosition
import app.dqxn.android.data.layout.GridSize
import app.dqxn.android.feature.dashboard.coordinator.EditModeCoordinator
import app.dqxn.android.feature.dashboard.gesture.DashboardHaptics
import app.dqxn.android.sdk.contracts.widget.WidgetSpec
import javax.inject.Inject
import kotlin.math.abs

/**
 * Per-widget gesture state machine for tap, drag, and resize interactions.
 *
 * NOT a composable -- a stateless utility class that provides [Modifier] extensions. Uses manual
 * [awaitEachGesture] + [awaitFirstDown] per replication advisory section 6 (no
 * `detectDragGesturesAfterLongPress`).
 *
 * Gesture behavior:
 * - Non-edit mode: short tap invokes [onWidgetTap] if the renderer supports it. Long-press enters
 *   edit mode + focuses widget.
 * - Edit mode, not focused: tap focuses widget.
 * - Edit mode, focused: drag via pointer events with 8px cancellation threshold. Resize via
 *   separate Box handle nodes (not handled here).
 *
 * `wasInEditModeAtStart` is captured at gesture start for consistent [PointerEventPass] selection
 * throughout the gesture.
 */
public class WidgetGestureHandler
@Inject
constructor(
  private val editModeCoordinator: EditModeCoordinator,
  private val gridPlacementEngine: GridPlacementEngine,
  private val haptics: DashboardHaptics,
) {

  /**
   * Apply widget gesture handling to this [Modifier].
   *
   * @param widgetId The unique instance ID of this widget.
   * @param widgetSpec The widget's spec for aspect ratio enforcement during resize.
   * @param currentPosition The widget's current grid position (for drag start coordinates).
   * @param currentSize The widget's current grid size (for resize).
   * @param gridUnitPx The pixel size of one grid unit.
   */
  public fun Modifier.widgetGestures(
    widgetId: String,
    widgetSpec: WidgetSpec,
    currentPosition: GridPosition,
    currentSize: GridSize,
    gridUnitPx: Float,
    viewportCols: Int = 0,
    viewportRows: Int = 0,
    onWidgetTap: (() -> Unit)? = null,
  ): Modifier =
    this.pointerInput(widgetId, widgetSpec, currentPosition, currentSize, gridUnitPx) {
      awaitEachGesture {
        val wasInEditModeAtStart = editModeCoordinator.editState.value.isEditMode
        val pass = if (wasInEditModeAtStart) PointerEventPass.Initial else PointerEventPass.Main

        val down = awaitFirstDown(requireUnconsumed = false, pass = pass)
        down.consume()

        val isFocused = editModeCoordinator.editState.value.focusedWidgetId == widgetId

        if (!wasInEditModeAtStart) {
          // Non-edit mode: long-press enters edit mode + focuses widget, short tap invokes widget
          awaitLongPressOrToggleFocus(widgetId, onWidgetTap)
          return@awaitEachGesture
        }

        if (!isFocused) {
          // Edit mode, not focused: tap to focus
          awaitUpAndFocus(widgetId)
          return@awaitEachGesture
        }

        // Edit mode, focused: resize handled by separate Box nodes.
        // Drag: long-press detection with 8px cancellation threshold
        awaitDragEvents(widgetId, currentPosition, currentSize, gridUnitPx, viewportCols, viewportRows, pass)
      }
    }

  /**
   * Non-edit mode: long-press enters edit mode + focuses widget. Short tap invokes [onWidgetTap]
   * if the renderer supports it.
   *
   * Uses [AwaitPointerEventScope.withTimeoutOrNull] to detect stationary hold — a finger held
   * still produces zero pointer events after DOWN. Polls with [PointerEventPass.Final] to see
   * events after all handlers, matching old codebase behavior.
   */
  private suspend fun AwaitPointerEventScope.awaitLongPressOrToggleFocus(
    widgetId: String,
    onWidgetTap: (() -> Unit)? = null,
  ) {
    val deadline = System.currentTimeMillis() + LONG_PRESS_TIMEOUT_MS
    var longPressTriggered = false

    while (true) {
      val remainingMs = deadline - System.currentTimeMillis()

      if (!longPressTriggered && remainingMs <= 0) {
        longPressTriggered = true
        editModeCoordinator.enterEditMode()
        editModeCoordinator.focusWidget(widgetId)
      }

      val event = if (!longPressTriggered) {
        withTimeoutOrNull(remainingMs.coerceAtLeast(0)) {
          awaitPointerEvent(PointerEventPass.Final)
        }
      } else {
        awaitPointerEvent(PointerEventPass.Final)
      }

      if (event == null) {
        longPressTriggered = true
        editModeCoordinator.enterEditMode()
        editModeCoordinator.focusWidget(widgetId)
        continue
      }

      val change = event.changes.firstOrNull() ?: break

      if (change.changedToUp()) {
        if (!longPressTriggered) {
          onWidgetTap?.invoke()
        }
        break
      }

      // Movement cancellation (only before long-press fires)
      if (!longPressTriggered) {
        val posChange = change.positionChange()
        if (abs(posChange.x) > DRAG_THRESHOLD_PX || abs(posChange.y) > DRAG_THRESHOLD_PX) {
          break
        }
      }
    }
  }

  /** Edit mode, unfocused: wait for pointer up, then focus widget. */
  private suspend fun AwaitPointerEventScope.awaitUpAndFocus(widgetId: String) {
    while (true) {
      val event = awaitPointerEvent(PointerEventPass.Initial)
      if (event.changes.all { it.changedToUp() }) {
        editModeCoordinator.focusWidget(widgetId)
        break
      }
    }
  }

  /**
   * Drag gesture with long-press detection and 8px cancellation threshold per replication advisory
   * section 6.
   *
   * Uses [AwaitPointerEventScope.withTimeoutOrNull] to detect stationary hold — a finger held
   * still produces zero pointer events after DOWN.
   */
  private suspend fun AwaitPointerEventScope.awaitDragEvents(
    widgetId: String,
    currentPosition: GridPosition,
    currentSize: GridSize,
    gridUnitPx: Float,
    viewportCols: Int,
    viewportRows: Int,
    pass: PointerEventPass,
  ) {
    var longPressTriggered = false
    var totalDrag = Offset.Zero
    val deadline = System.currentTimeMillis() + LONG_PRESS_TIMEOUT_MS

    while (true) {
      val remainingMs = deadline - System.currentTimeMillis()

      if (!longPressTriggered && remainingMs <= 0) {
        longPressTriggered = true
        haptics.dragStart()
      }

      val event = if (!longPressTriggered) {
        withTimeoutOrNull(remainingMs.coerceAtLeast(0)) {
          awaitPointerEvent(pass)
        }
      } else {
        awaitPointerEvent(pass)
      }

      if (event == null) {
        longPressTriggered = true
        haptics.dragStart()
        continue
      }

      val change = event.changes.firstOrNull() ?: break

      if (change.changedToUp()) {
        if (longPressTriggered && editModeCoordinator.dragState.value != null) {
          editModeCoordinator.endDrag(gridUnitPx)
        } else if (!longPressTriggered) {
          // Short tap in edit mode on focused widget -- exit edit mode entirely (old codebase behavior)
          editModeCoordinator.exitEditMode()
        }
        break
      }

      val posChange = change.positionChange()
      if (posChange != Offset.Zero) {
        totalDrag += posChange

        if (!longPressTriggered) {
          if (abs(totalDrag.x) > DRAG_THRESHOLD_PX || abs(totalDrag.y) > DRAG_THRESHOLD_PX) {
            break // Movement cancels before long-press
          }
        } else {
          change.consume()
          if (editModeCoordinator.dragState.value == null) {
            editModeCoordinator.startDrag(
              widgetId,
              currentPosition.col,
              currentPosition.row,
              widgetWidthUnits = currentSize.widthUnits,
              widgetHeightUnits = currentSize.heightUnits,
              viewportCols = viewportCols,
              viewportRows = viewportRows,
            )
          }
          editModeCoordinator.updateDrag(totalDrag.x, totalDrag.y, gridUnitPx)
        }
      }
    }
  }

  public companion object {
    /** Long-press threshold in milliseconds for drag initiation. */
    public const val LONG_PRESS_TIMEOUT_MS: Long = 400L

    /** Movement threshold in pixels that cancels a pending long-press (scroll discrimination). */
    public const val DRAG_THRESHOLD_PX: Float = 8f

    /** Visual handle size in dp (matches Box resize handle nodes). */
    public const val HANDLE_SIZE_DP: Float = 32f
  }
}
