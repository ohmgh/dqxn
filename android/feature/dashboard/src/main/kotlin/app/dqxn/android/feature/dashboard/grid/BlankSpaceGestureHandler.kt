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
import kotlin.math.abs

/**
 * Gesture handler for blank (empty) canvas areas.
 *
 * Uses `requireUnconsumed = true` on [awaitFirstDown] so widget gesture handlers consume their
 * events first -- this handler only fires on genuinely blank areas.
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
   * Apply blank space gesture handling to this [Modifier]. Must be placed AFTER widget gesture
   * modifiers in the composition tree so widgets consume their events first.
   *
   * @param onTapBlankSpace Called when blank space is tapped in non-edit mode (short press, no
   *   long-press triggered). Used by DashboardScreen for bottom bar toggle.
   */
  public fun Modifier.blankSpaceGestures(
    onTapBlankSpace: () -> Unit = {},
  ): Modifier =
    this.pointerInput(Unit) {
      awaitEachGesture {
        // Only fire on unconsumed events -- widgets consume theirs via down.consume()
        val down = awaitFirstDown(requireUnconsumed = true, pass = PointerEventPass.Main)

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
      val event = awaitPointerEvent(PointerEventPass.Main)
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
          awaitPointerEvent(PointerEventPass.Main)
        }
      } else {
        awaitPointerEvent(PointerEventPass.Main)
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

      // Movement cancellation (only before long-press fires)
      if (!longPressTriggered) {
        val posChange = change.positionChange()
        if (
          abs(posChange.x) > CANCELLATION_THRESHOLD_PX ||
            abs(posChange.y) > CANCELLATION_THRESHOLD_PX
        ) {
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
