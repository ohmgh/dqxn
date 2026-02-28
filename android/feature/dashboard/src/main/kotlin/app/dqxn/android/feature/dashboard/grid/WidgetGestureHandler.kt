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
import androidx.compose.ui.unit.dp
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
 * - Non-edit mode: tap focuses widget (if unfocused) or unfocuses (if focused). No drag/resize.
 * - Edit mode, not focused: tap focuses widget.
 * - Edit mode, focused: drag via pointer events with 8px cancellation threshold. Resize via corner
 *   handles with immediate start.
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
  ): Modifier =
    this.pointerInput(widgetId, widgetSpec, currentPosition, currentSize, gridUnitPx) {
      awaitEachGesture {
        val wasInEditModeAtStart = editModeCoordinator.editState.value.isEditMode
        val pass = if (wasInEditModeAtStart) PointerEventPass.Initial else PointerEventPass.Main

        val down = awaitFirstDown(requireUnconsumed = !wasInEditModeAtStart, pass = pass)

        // In edit mode, consume immediately to prevent blank space handler from firing
        if (wasInEditModeAtStart) {
          down.consume()
        }

        val isFocused = editModeCoordinator.editState.value.focusedWidgetId == widgetId

        if (!wasInEditModeAtStart) {
          // Non-edit mode: short tap toggles focus, long-press enters edit mode + focuses widget
          awaitLongPressOrToggleFocus(widgetId)
          return@awaitEachGesture
        }

        if (!isFocused) {
          // Edit mode, not focused: tap to focus
          awaitUpAndFocus(widgetId)
          return@awaitEachGesture
        }

        // Edit mode, focused: check for resize handle hit, then fall through to drag
        val touchTarget = HANDLE_TOUCH_TARGET_DP.dp.toPx()
        val widgetWidthPx = currentSize.widthUnits * gridUnitPx
        val widgetHeightPx = currentSize.heightUnits * gridUnitPx
        val hitHandle =
          detectResizeHandle(down.position, widgetWidthPx, widgetHeightPx, touchTarget)

        if (hitHandle != null) {
          // Resize: immediate start, no long-press
          editModeCoordinator.startResize(
            widgetId,
            hitHandle,
            currentSize,
            currentPosition,
            widgetSpec,
          )
          awaitResizeEvents(hitHandle, gridUnitPx)
          return@awaitEachGesture
        }

        // Drag: long-press detection with 8px cancellation threshold
        awaitDragEvents(widgetId, currentPosition, currentSize, gridUnitPx, viewportCols, viewportRows, pass)
      }
    }

  /**
   * Non-edit mode: long-press enters edit mode + focuses widget. Short tap toggles focus.
   * Mirrors old codebase behavior: long-press on widget → enterEditMode() + focusWidget(widgetId).
   *
   * Uses [AwaitPointerEventScope.withTimeoutOrNull] to detect stationary hold — a finger held
   * still produces zero pointer events after DOWN.
   */
  private suspend fun AwaitPointerEventScope.awaitLongPressOrToggleFocus(widgetId: String) {
    val deadline = System.currentTimeMillis() + LONG_PRESS_TIMEOUT_MS
    var longPressTriggered = false

    while (true) {
      val remainingMs = deadline - System.currentTimeMillis()

      if (!longPressTriggered && remainingMs <= 0) {
        longPressTriggered = true
        haptics.dragStart()
        editModeCoordinator.enterEditMode()
        editModeCoordinator.focusWidget(widgetId)
      }

      val event = if (!longPressTriggered) {
        withTimeoutOrNull(remainingMs.coerceAtLeast(0)) {
          awaitPointerEvent(PointerEventPass.Main)
        }
      } else {
        awaitPointerEvent(PointerEventPass.Main)
      }

      if (event == null) {
        longPressTriggered = true
        haptics.dragStart()
        editModeCoordinator.enterEditMode()
        editModeCoordinator.focusWidget(widgetId)
        continue
      }

      val change = event.changes.firstOrNull() ?: break

      if (change.changedToUp()) {
        if (!longPressTriggered) {
          // Short tap: toggle focus
          val currentFocus = editModeCoordinator.editState.value.focusedWidgetId
          if (currentFocus == widgetId) {
            editModeCoordinator.focusWidget(null)
          } else {
            editModeCoordinator.focusWidget(widgetId)
          }
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

  /**
   * Resize gesture from a corner handle. Immediate start (no long-press). Touch targets 48dp per
   * F1.7.
   */
  private suspend fun AwaitPointerEventScope.awaitResizeEvents(
    handle: ResizeHandle,
    gridUnitPx: Float,
  ) {
    var accumulatedDeltaX = 0f
    var accumulatedDeltaY = 0f

    while (true) {
      val event = awaitPointerEvent(PointerEventPass.Initial)
      val change = event.changes.firstOrNull() ?: break

      if (change.changedToUp()) {
        editModeCoordinator.endResize()
        break
      }

      val posChange = change.positionChange()
      if (posChange != Offset.Zero) {
        change.consume()

        // Invert deltas for left/top handles per replication advisory section 6
        val dx =
          when (handle) {
            ResizeHandle.TOP_LEFT,
            ResizeHandle.BOTTOM_LEFT -> -posChange.x
            else -> posChange.x
          }
        val dy =
          when (handle) {
            ResizeHandle.TOP_LEFT,
            ResizeHandle.TOP_RIGHT -> -posChange.y
            else -> posChange.y
          }

        accumulatedDeltaX += dx
        accumulatedDeltaY += dy

        val deltaWidthUnits = (accumulatedDeltaX / gridUnitPx).toInt()
        val deltaHeightUnits = (accumulatedDeltaY / gridUnitPx).toInt()

        editModeCoordinator.updateResize(deltaWidthUnits, deltaHeightUnits)
      }
    }
  }

  /**
   * Detect which resize handle (if any) was hit by the [tapPosition] within a widget of the given
   * pixel dimensions. Returns null if no handle was hit.
   */
  internal fun detectResizeHandle(
    tapPosition: Offset,
    widgetWidthPx: Float,
    widgetHeightPx: Float,
    touchTargetPx: Float,
  ): ResizeHandle? {
    val inLeft = tapPosition.x < touchTargetPx
    val inRight = tapPosition.x > widgetWidthPx - touchTargetPx
    val inTop = tapPosition.y < touchTargetPx
    val inBottom = tapPosition.y > widgetHeightPx - touchTargetPx

    return when {
      inTop && inLeft -> ResizeHandle.TOP_LEFT
      inTop && inRight -> ResizeHandle.TOP_RIGHT
      inBottom && inLeft -> ResizeHandle.BOTTOM_LEFT
      inBottom && inRight -> ResizeHandle.BOTTOM_RIGHT
      else -> null
    }
  }

  public companion object {
    /** Long-press threshold in milliseconds for drag initiation. */
    public const val LONG_PRESS_TIMEOUT_MS: Long = 400L

    /** Movement threshold in pixels that cancels a pending long-press (scroll discrimination). */
    public const val DRAG_THRESHOLD_PX: Float = 8f

    /** Visual handle size in dp. */
    public const val HANDLE_SIZE_DP: Float = 32f

    /** Touch target size in dp for resize handles per F1.7. */
    public const val HANDLE_TOUCH_TARGET_DP: Float = 48f
  }
}
