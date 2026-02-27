package app.dqxn.android.feature.dashboard.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import app.dqxn.android.sdk.ui.theme.DashboardThemeDefinition
import app.dqxn.android.sdk.ui.theme.LocalDashboardTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Robolectric Compose tests for [DashboardButtonBar] visibility and button semantics.
 *
 * Tests verify:
 * - Bar visible/hidden based on isVisible prop
 * - Settings button has correct test tag (FAB styling)
 * - Add Widget FAB always present when bar is visible
 */
@RunWith(RobolectricTestRunner::class)
class DashboardButtonBarAutoHideTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val testTheme =
    DashboardThemeDefinition(
      themeId = "test",
      displayName = "Test",
      primaryTextColor = Color.White,
      secondaryTextColor = Color.Gray,
      accentColor = Color.Cyan,
      widgetBorderColor = Color.Red,
      backgroundBrush = Brush.verticalGradient(listOf(Color.Black, Color.DarkGray)),
      widgetBackgroundBrush = Brush.verticalGradient(listOf(Color.DarkGray, Color.Black)),
    )

  @Test
  fun `bar is visible when isVisible is true`() {
    composeTestRule.setContent {
      CompositionLocalProvider(LocalDashboardTheme provides testTheme) {
        DashboardButtonBar(
          isVisible = true,
          onSettingsClick = {},
          onAddWidgetClick = {},
          onInteraction = {},
        )
      }
    }

    composeTestRule.onNodeWithTag("bottom_bar").assertIsDisplayed()
  }

  @Test
  fun `bar is hidden when isVisible is false`() {
    composeTestRule.setContent {
      CompositionLocalProvider(LocalDashboardTheme provides testTheme) {
        DashboardButtonBar(
          isVisible = false,
          onSettingsClick = {},
          onAddWidgetClick = {},
          onInteraction = {},
        )
      }
    }

    composeTestRule.onNodeWithTag("bottom_bar").assertDoesNotExist()
  }

  @Test
  fun `settings button has correct semantics`() {
    composeTestRule.setContent {
      CompositionLocalProvider(LocalDashboardTheme provides testTheme) {
        DashboardButtonBar(
          isVisible = true,
          onSettingsClick = {},
          onAddWidgetClick = {},
          onInteraction = {},
        )
      }
    }

    composeTestRule.onNodeWithTag("settings_button", useUnmergedTree = true).assertExists()
  }

  @Test
  fun `add widget FAB always present when visible`() {
    composeTestRule.setContent {
      CompositionLocalProvider(LocalDashboardTheme provides testTheme) {
        DashboardButtonBar(
          isVisible = true,
          onSettingsClick = {},
          onAddWidgetClick = {},
          onInteraction = {},
        )
      }
    }

    composeTestRule.onNodeWithTag("add_widget_button", useUnmergedTree = true).assertExists()
  }
}
