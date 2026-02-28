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
 *   edit mode + focuses widget, then continues to drag if finger moves without lifting.
 * - Edit mode, not focused: tap focuses widget.
 * - Edit mode, focused: immediate drag on movement (no second long-press gate). Resize via
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
        // Use Initial pass in edit mode to intercept before widget's internal click handlers.
        // Use Main pass otherwise to not interfere with normal widget interaction.
        // Old codebase: Initial in edit mode, Main otherwise.
        val pass = if (wasInEditModeAtStart) PointerEventPass.Initial else PointerEventPass.Main

        val down = awaitFirstDown(requireUnconsumed = false, pass = pass)
        down.consume()

        val isFocused = editModeCoordinator.editState.value.focusedWidgetId == widgetId

        if (!wasInEditModeAtStart) {
          // Non-edit mode: long-press enters edit mode + focuses widget.
          // Returns CONTINUE_TO_DRAG if long-press fired and finger is still down.
          val result = awaitLongPressOrToggleFocus(widgetId, onWidgetTap)
          if (result != LongPressResult.CONTINUE_TO_DRAG) return@awaitEachGesture
          // Fall through to immediate drag tracking (finger still held after long-press)
        } else if (!isFocused) {
          // Edit mode, not focused: tap to focus
          awaitUpAndFocus(widgetId)
          return@awaitEachGesture
        }

        // Edit mode, focused (or just entered via long-press): immediate drag on movement.
        // Uses Final pass so resize handles (children, Main pass) process events first.
        awaitDragEvents(
          widgetId, currentPosition, currentSize, gridUnitPx,
          viewportCols, viewportRows, PointerEventPass.Final,
        )
      }
    }

  /** Result of the long-press detection phase. */
  private enum class LongPressResult {
    /** Short tap detected — invoke widget tap action. */
    TAP,
    /** Long-press fired and finger lifted — edit mode entered, no drag. */
    LONG_PRESS_UP,
    /** Long-press fired and finger moved — caller should continue to drag tracking. */
    CONTINUE_TO_DRAG,
    /** Gesture cancelled (e.g., movement before long-press threshold). */
    CANCELLED,
  }

  /**
   * Non-edit mode: long-press enters edit mode + focuses widget. Short tap invokes [onWidgetTap]
   * if the renderer supports it.
   *
   * Returns [LongPressResult.CONTINUE_TO_DRAG] when long-press fires and the finger subsequently
   * moves, allowing the caller to fall through to drag tracking in the same gesture (no finger
   * lift required).
   *
   * Uses [AwaitPointerEventScope.withTimeoutOrNull] to detect stationary hold — a finger held
   * still produces zero pointer events after DOWN. Polls with [PointerEventPass.Final] to see
   * events after all handlers, matching old codebase behavior.
   */
  private suspend fun AwaitPointerEventScope.awaitLongPressOrToggleFocus(
    widgetId: String,
    onWidgetTap: (() -> Unit)? = null,
  ): LongPressResult {
    val deadline = System.currentTimeMillis() + LONG_PRESS_TIMEOUT_MS
    var longPressTriggered = false

    while (true) {
      val remainingMs = deadline - System.currentTimeMillis()

      if (!longPressTriggered && remainingMs <= 0) {
        longPressTriggered = true
        editModeCoordinator.enterEditMode()
        editModeCoordinator.focusWidget(widgetId)
        haptics.dragStart()
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
        haptics.dragStart()
        continue
      }

      val change = event.changes.firstOrNull() ?: return LongPressResult.CANCELLED

      if (change.changedToUp()) {
        return if (!longPressTriggered) {
          onWidgetTap?.invoke()
          LongPressResult.TAP
        } else {
          LongPressResult.LONG_PRESS_UP
        }
      }

      val posChange = change.positionChange()
      if (!longPressTriggered) {
        // Movement cancellation (only before long-press fires)
        if (abs(posChange.x) > DRAG_THRESHOLD_PX || abs(posChange.y) > DRAG_THRESHOLD_PX) {
          return LongPressResult.CANCELLED
        }
      } else if (posChange != Offset.Zero) {
        // Long-press already fired and finger moved — transition to drag
        return LongPressResult.CONTINUE_TO_DRAG
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
   * Drag tracking for a focused widget in edit mode.
   *
   * Uses [PointerEventPass.Final] so resize handle children (on [PointerEventPass.Main]) process
   * events first. Checks [PointerEvent.changes.any { it.pressed }] instead of [changedToUp] so
   * consumed events from resize handles don't falsely trigger pointer-up handling.
   *
   * When a resize is active (coordinator's resizeState is non-null), drag processing is suppressed
   * — matching old codebase behavior where `isResizing` gates the long-press/drag path.
   *
   * Tap (pointer up with no drag and no resize) exits edit mode per old codebase behavior.
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
    var totalDrag = Offset.Zero
    var dragStarted = false

    while (true) {
      val event = awaitPointerEvent(pass)
      val anyPressed = event.changes.any { it.pressed }

      if (!anyPressed) {
        // Pointer released
        if (dragStarted && editModeCoordinator.dragState.value != null) {
          editModeCoordinator.endDrag(gridUnitPx)
        } else if (!dragStarted && editModeCoordinator.resizeState.value == null) {
          // Tap on focused widget with no drag/resize — exit edit mode (old codebase behavior)
          editModeCoordinator.exitEditMode()
        }
        break
      }

      // Skip drag processing when resize is active (handle children own the gesture)
      if (editModeCoordinator.resizeState.value != null) continue

      val change = event.changes.firstOrNull() ?: break
      val posChange = change.positionChange()
      if (posChange != Offset.Zero) {
        totalDrag += posChange
        change.consume()
        if (!dragStarted) {
          dragStarted = true
          haptics.dragStart()
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

  public companion object {
    /** Long-press threshold in milliseconds for drag initiation. */
    public const val LONG_PRESS_TIMEOUT_MS: Long = 400L

    /** Movement threshold in pixels that cancels a pending long-press (scroll discrimination). */
    public const val DRAG_THRESHOLD_PX: Float = 8f

    /** Visual handle size in dp (matches Box resize handle nodes). */
    public const val HANDLE_SIZE_DP: Float = 32f
  }
}
