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
import app.dqxn.android.feature.dashboard.coordinator.EditModeCoordinator
import javax.inject.Inject

/**
 * Gesture handler for blank (empty) canvas areas.
 *
 * Uses [PointerEventPass.Final] on [awaitFirstDown] so it sees events AFTER both
 * [HorizontalPager][androidx.compose.foundation.pager.HorizontalPager] (parent, Initial pass) and
 * widget gesture handlers (children, Main pass) have processed. Checks [isConsumed] to skip events
 * already claimed by widgets — blank space events arrive unconsumed on Final pass because no widget
 * child consumed them on Main.
 *
 * Behavior:
 * - Long-press (400ms with 8px cancellation): enter edit mode
 * - Tap in edit mode: exit edit mode
 * - Tap in view mode: calls [onTapBlankSpace] (used for bottom bar toggle)
 */
public class BlankSpaceGestureHandler
@Inject
constructor(
  private val editModeCoordinator: EditModeCoordinator,
) {

  /**
   * Apply blank space gesture handling to this [Modifier]. Applied on the grid Layout (parent of
   * widget composables). Uses Final pass so widgets consume on Main pass first.
   *
   * @param onTapBlankSpace Called when blank space is tapped in non-edit mode (short press, no
   *   long-press triggered). Used by DashboardScreen for bottom bar toggle.
   */
  public fun Modifier.blankSpaceGestures(
    onTapBlankSpace: () -> Unit = {},
  ): Modifier =
    this.pointerInput(Unit) {
      awaitEachGesture {
        // Final pass: sees events after pager (Initial) and widgets (Main) have processed.
        // requireUnconsumed = false so pager's Initial-pass consumption doesn't block us.

        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)

        // If a widget child consumed this event on Main pass, skip -- not blank space.
        if (down.isConsumed) {
          return@awaitEachGesture
        }

        val isEditMode = editModeCoordinator.editState.value.isEditMode

        if (isEditMode) {
          // Tap in edit mode -> exit
          awaitUpAndExitEditMode()
          return@awaitEachGesture
        }

        // Non-edit mode: long-press to enter edit mode, tap for callback
        awaitLongPressOrTap(onTapBlankSpace)
      }
    }

  /** Wait for pointer up and exit edit mode. */
  private suspend fun AwaitPointerEventScope.awaitUpAndExitEditMode() {
    while (true) {
      val event = awaitPointerEvent(PointerEventPass.Final)
      if (event.changes.all { it.changedToUp() }) {
        editModeCoordinator.exitEditMode()
        break
      }
    }
  }

  /**
   * Long-press detection with 400ms threshold and 8px cancellation. Movement exceeding 8px before
   * the threshold expires cancels the gesture (scroll discrimination).
   *
   * If pointer goes up before long-press triggers (and not cancelled by movement), calls
   * [onTapBlankSpace] for bottom bar toggle.
   *
   * Uses [AwaitPointerEventScope.withTimeoutOrNull] to detect stationary hold — a finger held
   * still produces zero pointer events, so elapsed-time checks between [awaitPointerEvent] calls
   * never fire for a motionless press.
   */
  private suspend fun AwaitPointerEventScope.awaitLongPressOrTap(
    onTapBlankSpace: () -> Unit,
  ) {
    val deadline = System.currentTimeMillis() + LONG_PRESS_TIMEOUT_MS
    var longPressTriggered = false
    var totalDragDistance = 0f

    while (true) {
      val remainingMs = deadline - System.currentTimeMillis()

      // Check if timeout already expired (from processing previous events)
      if (!longPressTriggered && remainingMs <= 0) {
        longPressTriggered = true
        editModeCoordinator.enterEditMode()
      }

      // If long-press already triggered, just wait for UP to end gesture
      val event = if (!longPressTriggered) {
        withTimeoutOrNull(remainingMs.coerceAtLeast(0)) {
          awaitPointerEvent(PointerEventPass.Final)
        }
      } else {
        awaitPointerEvent(PointerEventPass.Final)
      }

      if (event == null) {
        // Timeout with no pointer event → long press
        longPressTriggered = true
        editModeCoordinator.enterEditMode()
        continue
      }

      val change = event.changes.firstOrNull() ?: break

      if (change.changedToUp()) {
        if (!longPressTriggered) {
          onTapBlankSpace()
        }
        break
      }

      // Movement cancellation (only before long-press fires) — cumulative distance
      if (!longPressTriggered) {
        val posChange = change.positionChange()
        totalDragDistance += posChange.getDistance()
        if (totalDragDistance > CANCELLATION_THRESHOLD_PX) {
          break
        }
      }
    }
  }

  public companion object {
    /** Long-press threshold in milliseconds. */
    public const val LONG_PRESS_TIMEOUT_MS: Long = 400L

    /** Movement threshold in pixels that cancels a pending long-press. */
    public const val CANCELLATION_THRESHOLD_PX: Float = 8f
  }
}
