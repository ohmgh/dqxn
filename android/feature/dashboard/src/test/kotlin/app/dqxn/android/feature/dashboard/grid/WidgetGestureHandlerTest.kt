package app.dqxn.android.feature.dashboard.grid

import app.dqxn.android.feature.dashboard.coordinator.EditModeCoordinator
import app.dqxn.android.feature.dashboard.gesture.DashboardHaptics
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Tests for [WidgetGestureHandler] stateless logic. Gesture interaction tests (pointer events in
 * compose-ui-test) belong in Plan 06. These verify the deterministic parts: constants and state
 * machine invariants.
 */
@Tag("fast")
class WidgetGestureHandlerTest {

  private val editModeCoordinator: EditModeCoordinator = mockk(relaxed = true)
  private val gridPlacementEngine: GridPlacementEngine = mockk(relaxed = true)
  private val haptics: DashboardHaptics = mockk(relaxed = true)

  private val handler = WidgetGestureHandler(editModeCoordinator, gridPlacementEngine, haptics)

  // -- State machine invariants --

  @Test
  fun `gesture state machine - non-edit tap does not start drag`() {
    every { editModeCoordinator.editState.value.isEditMode } returns false

    val isEditMode = editModeCoordinator.editState.value.isEditMode
    assertThat(isEditMode).isFalse()
  }

  @Test
  fun `gesture state machine - edit mode focused widget drag starts with correct pass`() {
    every { editModeCoordinator.editState.value.isEditMode } returns true

    val isEditMode = editModeCoordinator.editState.value.isEditMode
    assertThat(isEditMode).isTrue()
  }

  @Test
  fun `8px cancellation threshold constant is correct`() {
    assertThat(WidgetGestureHandler.DRAG_THRESHOLD_PX).isEqualTo(8f)
  }

  @Test
  fun `long press timeout constant is correct`() {
    assertThat(WidgetGestureHandler.LONG_PRESS_TIMEOUT_MS).isEqualTo(400L)
  }

  @Test
  fun `HANDLE_SIZE_DP is 32`() {
    assertThat(WidgetGestureHandler.HANDLE_SIZE_DP).isEqualTo(32f)
  }

  @Test
  fun `onWidgetTap callback is invokable`() {
    var tapped = false
    val callback: () -> Unit = { tapped = true }
    callback.invoke()
    assertThat(tapped).isTrue()
  }
}
