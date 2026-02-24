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
 */
public class BlankSpaceGestureHandler
@Inject
constructor(
  private val editModeCoordinator: EditModeCoordinator,
) {

  /**
   * Apply blank space gesture handling to this [Modifier]. Must be placed AFTER widget gesture
   * modifiers in the composition tree so widgets consume their events first.
   */
  public fun Modifier.blankSpaceGestures(): Modifier = this.pointerInput(Unit) {
    awaitEachGesture {
      // Only fire on unconsumed events -- widgets consume theirs via down.consume()
      val down = awaitFirstDown(requireUnconsumed = true, pass = PointerEventPass.Main)

      val isEditMode = editModeCoordinator.editState.value.isEditMode

      if (isEditMode) {
        // Tap in edit mode -> exit
        awaitUpAndExitEditMode()
        return@awaitEachGesture
      }

      // Non-edit mode: long-press to enter edit mode
      awaitLongPress()
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
   * Uses elapsed time tracking rather than `coroutineScope` + `delay` because
   * [AwaitPointerEventScope] is a restricted suspension scope.
   */
  private suspend fun AwaitPointerEventScope.awaitLongPress() {
    val startTimeMs = System.currentTimeMillis()
    var longPressTriggered = false

    while (true) {
      val event = awaitPointerEvent(PointerEventPass.Main)
      val change = event.changes.firstOrNull() ?: break

      if (change.changedToUp()) {
        break
      }

      val elapsedMs = System.currentTimeMillis() - startTimeMs

      if (!longPressTriggered && elapsedMs >= LONG_PRESS_TIMEOUT_MS) {
        longPressTriggered = true
        editModeCoordinator.enterEditMode()
      }

      val posChange = change.positionChange()
      if (posChange != Offset.Zero && !longPressTriggered) {
        if (abs(posChange.x) > CANCELLATION_THRESHOLD_PX ||
          abs(posChange.y) > CANCELLATION_THRESHOLD_PX
        ) {
          // Movement exceeds threshold -- cancel
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
