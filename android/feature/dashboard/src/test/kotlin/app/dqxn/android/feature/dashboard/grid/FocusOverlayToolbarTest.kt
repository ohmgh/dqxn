package app.dqxn.android.feature.dashboard.grid

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI tests for [FocusOverlayToolbar] (F1.8).
 *
 * Verifies toolbar renders delete/settings buttons with correct test tags, and that click
 * callbacks are wired correctly. Uses Robolectric for off-device compose-ui-test execution.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FocusOverlayToolbarTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `toolbar renders with delete and settings buttons`() {
        composeTestRule.setContent {
            FocusOverlayToolbar(
                widgetId = "w1",
                onDelete = {},
                onSettings = {},
            )
        }

        composeTestRule.onNodeWithTag("focus_toolbar_w1").assertExists()
        composeTestRule.onNodeWithTag("focus_delete_w1", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag("focus_settings_w1", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `delete button fires onDelete callback`() {
        var deleted = false

        composeTestRule.setContent {
            FocusOverlayToolbar(
                widgetId = "w1",
                onDelete = { deleted = true },
                onSettings = {},
            )
        }

        composeTestRule.onNodeWithTag("focus_delete_w1", useUnmergedTree = true).performClick()
        assertThat(deleted).isTrue()
    }

    @Test
    fun `settings button fires onSettings callback`() {
        var settingsOpened = false

        composeTestRule.setContent {
            FocusOverlayToolbar(
                widgetId = "w1",
                onDelete = {},
                onSettings = { settingsOpened = true },
            )
        }

        composeTestRule.onNodeWithTag("focus_settings_w1", useUnmergedTree = true).performClick()
        assertThat(settingsOpened).isTrue()
    }
}
