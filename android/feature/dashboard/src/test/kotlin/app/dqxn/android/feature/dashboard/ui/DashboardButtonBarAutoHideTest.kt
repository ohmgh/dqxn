package app.dqxn.android.feature.dashboard.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import app.dqxn.android.feature.dashboard.coordinator.ProfileInfo
import app.dqxn.android.sdk.ui.theme.DashboardThemeDefinition
import app.dqxn.android.sdk.ui.theme.LocalDashboardTheme
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Robolectric Compose tests for [DashboardButtonBar] visibility, settings FAB semantics, edit mode
 * gating, and profile icon rendering.
 *
 * Tests verify:
 * - Bar visible/hidden based on isVisible prop
 * - Settings button has correct test tag (FAB styling)
 * - Add-widget button gated by edit mode
 * - Profile icons appear when 2+ profiles
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

  private val profiles =
    persistentListOf(ProfileInfo(id = "default", displayName = "Main", isDefault = true))

  @Test
  fun `bar is visible when isVisible is true`() {
    composeTestRule.setContent {
      CompositionLocalProvider(LocalDashboardTheme provides testTheme) {
        DashboardButtonBar(
          isEditMode = false,
          profiles = profiles,
          activeProfileId = "default",
          isVisible = true,
          onSettingsClick = {},
          onProfileClick = {},
          onAddWidgetClick = {},
          onEditModeToggle = {},
          onThemeToggle = {},
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
          isEditMode = false,
          profiles = profiles,
          activeProfileId = "default",
          isVisible = false,
          onSettingsClick = {},
          onProfileClick = {},
          onAddWidgetClick = {},
          onEditModeToggle = {},
          onThemeToggle = {},
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
          isEditMode = false,
          profiles = profiles,
          activeProfileId = "default",
          isVisible = true,
          onSettingsClick = {},
          onProfileClick = {},
          onAddWidgetClick = {},
          onEditModeToggle = {},
          onThemeToggle = {},
          onInteraction = {},
        )
      }
    }

    composeTestRule.onNodeWithTag("settings_button", useUnmergedTree = true).assertExists()
  }

  @Test
  fun `add widget button appears in edit mode`() {
    composeTestRule.setContent {
      CompositionLocalProvider(LocalDashboardTheme provides testTheme) {
        DashboardButtonBar(
          isEditMode = true,
          profiles = profiles,
          activeProfileId = "default",
          isVisible = true,
          onSettingsClick = {},
          onProfileClick = {},
          onAddWidgetClick = {},
          onEditModeToggle = {},
          onThemeToggle = {},
          onInteraction = {},
        )
      }
    }

    composeTestRule.onNodeWithTag("add_widget_button", useUnmergedTree = true).assertExists()
  }

  @Test
  fun `add widget button hidden in non-edit mode`() {
    composeTestRule.setContent {
      CompositionLocalProvider(LocalDashboardTheme provides testTheme) {
        DashboardButtonBar(
          isEditMode = false,
          profiles = profiles,
          activeProfileId = "default",
          isVisible = true,
          onSettingsClick = {},
          onProfileClick = {},
          onAddWidgetClick = {},
          onEditModeToggle = {},
          onThemeToggle = {},
          onInteraction = {},
        )
      }
    }

    composeTestRule.onNodeWithTag("add_widget_button").assertDoesNotExist()
  }

  @Test
  fun `profile icons appear when 2 or more profiles`() {
    val multiProfiles =
      persistentListOf(
        ProfileInfo(id = "p1", displayName = "Home", isDefault = true),
        ProfileInfo(id = "p2", displayName = "Work", isDefault = false),
      )

    composeTestRule.setContent {
      CompositionLocalProvider(LocalDashboardTheme provides testTheme) {
        DashboardButtonBar(
          isEditMode = false,
          profiles = multiProfiles,
          activeProfileId = "p1",
          isVisible = true,
          onSettingsClick = {},
          onProfileClick = {},
          onAddWidgetClick = {},
          onEditModeToggle = {},
          onThemeToggle = {},
          onInteraction = {},
        )
      }
    }

    composeTestRule.onNodeWithTag("profile_p1", useUnmergedTree = true).assertExists()
    composeTestRule.onNodeWithTag("profile_p2", useUnmergedTree = true).assertExists()
  }
}
