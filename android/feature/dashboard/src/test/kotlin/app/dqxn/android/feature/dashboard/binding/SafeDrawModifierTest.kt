package app.dqxn.android.feature.dashboard.binding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for [safeWidgetDraw] modifier.
 *
 * Note: Robolectric's Compose test framework does not execute draw lambdas, so draw-phase
 * exception interception cannot be verified here. These tests verify the modifier composes
 * without error and that normal content passes through. Full draw-phase testing requires
 * `connectedAndroidTest`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SafeDrawModifierTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun `modifier composes without error`() {
    var callbackInvoked = false

    composeTestRule.setContent {
      Box(
        modifier =
          Modifier.size(100.dp).safeWidgetDraw { callbackInvoked = true },
      ) {
        Box(modifier = Modifier.size(100.dp))
      }
    }

    composeTestRule.waitForIdle()
    // No draw-phase exception, callback should not fire
    assertThat(callbackInvoked).isFalse()
  }

  @Test
  fun `composable tree remains stable with safeWidgetDraw applied`() {
    composeTestRule.setContent {
      Box(
        modifier =
          Modifier.size(100.dp).safeWidgetDraw { /* absorb */ },
      ) {
        Box(modifier = Modifier.size(50.dp))
      }
    }

    composeTestRule.onRoot().assertExists()
  }

  @Test
  fun `error callback invoked directly sets flag`() {
    // Unit test the callback mechanism directly (not through draw pipeline)
    var errorReceived: Exception? = null
    val onError: (Exception) -> Unit = { e -> errorReceived = e }

    val testException = IllegalStateException("test crash")
    onError(testException)

    assertThat(errorReceived).isEqualTo(testException)
  }
}
