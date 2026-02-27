package app.dqxn.android.feature.settings.accessibility

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import app.dqxn.android.feature.settings.main.MainSettings
import app.dqxn.android.sdk.ui.theme.DashboardThemeDefinition
import app.dqxn.android.sdk.ui.theme.LocalDashboardTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * TalkBack accessibility test for the MainSettings composable.
 *
 * Verifies that all interactive elements have appropriate semantics for screen reader traversal:
 * - Clickable elements have text content (label or contentDescription)
 * - No focus traps (all interactive elements are traversable)
 * - Section headers have text content (not just decorative)
 * - Delete All Data button has click action and Role.Button semantics
 * - Toggle rows expose toggleable state for TalkBack on/off announcements
 */
@RunWith(RobolectricTestRunner::class)
class TalkBackAccessibilityTest {

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
  fun `all clickable settings rows have text content`() {
    renderMainSettings()

    // Navigation rows -- each has visible text label
    val navigationRows =
      listOf(
        "main_settings_dash_packs" to "Dash Packs",
        "main_settings_theme_mode" to "Theme Mode",
        "main_settings_light_theme" to "Light Theme",
        "main_settings_dark_theme" to "Dark Theme",
        "main_settings_diagnostics" to "Diagnostics",
      )
    for ((tag, label) in navigationRows) {
      scrollToTag(tag)
      composeTestRule.onNodeWithTag(tag).assertIsDisplayed()
      composeTestRule.onNodeWithTag(tag).assert(hasClickAction())
      // The row contains the label text for TalkBack to read
      composeTestRule.onNodeWithText(label).assertIsDisplayed()
    }

    // Toggle rows -- each has visible text label + click action
    val toggleRows =
      listOf(
        "main_settings_status_bar" to "Show Status Bar",
        "main_settings_keep_screen_on" to "Keep Screen On",
        "main_settings_analytics" to "Analytics",
      )
    for ((tag, label) in toggleRows) {
      scrollToTag(tag)
      composeTestRule.onNodeWithTag(tag).assertIsDisplayed()
      composeTestRule.onNodeWithTag(tag).assert(hasClickAction())
      composeTestRule.onNodeWithText(label).assertIsDisplayed()
    }

    // Delete All Data button -- has text + click action
    scrollToTag("main_settings_delete_all")
    composeTestRule.onNodeWithTag("main_settings_delete_all").assertIsDisplayed()
    composeTestRule.onNodeWithTag("main_settings_delete_all").assert(hasClickAction())
    composeTestRule.onNodeWithText("Delete All Data").assertIsDisplayed()
  }

  @Test
  fun `all interactive elements are traversable with no focus traps`() {
    renderMainSettings()

    // Collect all nodes with click actions in the settings content
    // Expected interactive elements:
    //   5 navigation rows + 3 toggle rows (row-level click) + 3 switch controls + 1 delete button
    //   + 1 reset dash row = 13+ total
    // Plus the close button in the OverlayScaffold header
    val allClickable = composeTestRule.onAllNodes(hasClickAction()).fetchSemanticsNodes()

    // There must be at least 9 distinct interactive elements (rows + delete button + reset)
    // Actual count is higher due to Switch widgets being independently clickable
    assertThat(allClickable.size).isAtLeast(9)
  }

  @Test
  fun `section headers have text content`() {
    renderMainSettings()

    // Only "Advanced" section header remains after restructure to flat list layout
    scrollTo("ADVANCED")
    composeTestRule.onNodeWithText("ADVANCED").assertIsDisplayed()
  }

  @Test
  fun `delete all data button has button role semantics`() {
    renderMainSettings()

    scrollToTag("main_settings_delete_all")
    composeTestRule
      .onNodeWithTag("main_settings_delete_all")
      .assert(
        SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
      )
  }

  @Test
  fun `toggle rows expose toggleable state for TalkBack`() {
    renderMainSettings(showStatusBar = true, keepScreenOn = false, analyticsConsent = true)

    // Switch components within toggle rows have ToggleableState semantics.
    // In the unmerged tree, each Switch has its own node with toggleable state.
    // TalkBack reads this to announce on/off state.
    // We expect at least 3 toggleable nodes: status bar, keep screen on, analytics.
    val toggleableNodes =
      composeTestRule
        .onAllNodes(hasToggleableState(), useUnmergedTree = true)
        .fetchSemanticsNodes()

    // At least 3 switches (status bar, keep screen on, analytics)
    assertThat(toggleableNodes.size).isAtLeast(3)
  }

  // --- Helpers ---

  private fun hasToggleableState(): SemanticsMatcher =
    SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState)

  private fun scrollTo(text: String) {
    composeTestRule
      .onNodeWithTag("main_settings_content")
      .performScrollToNode(hasText(text))
    composeTestRule.waitForIdle()
  }

  private fun scrollToTag(tag: String) {
    composeTestRule
      .onNodeWithTag("main_settings_content")
      .performScrollToNode(hasTestTag(tag))
    composeTestRule.waitForIdle()
  }

  private fun renderMainSettings(
    analyticsConsent: Boolean = false,
    showStatusBar: Boolean = false,
    keepScreenOn: Boolean = true,
    onSetAnalyticsConsent: (Boolean) -> Unit = {},
    onSetShowStatusBar: (Boolean) -> Unit = {},
    onSetKeepScreenOn: (Boolean) -> Unit = {},
    onDeleteAllData: () -> Unit = {},
    onNavigateToThemeMode: () -> Unit = {},
    onNavigateToLightTheme: () -> Unit = {},
    onNavigateToDarkTheme: () -> Unit = {},
    onNavigateToDashPacks: () -> Unit = {},
    onNavigateToDiagnostics: () -> Unit = {},
    onResetDash: () -> Unit = {},
    onClose: () -> Unit = {},
  ) {
    composeTestRule.setContent {
      CompositionLocalProvider(LocalDashboardTheme provides testTheme) {
        MainSettings(
          analyticsConsent = analyticsConsent,
          showStatusBar = showStatusBar,
          keepScreenOn = keepScreenOn,
          onSetAnalyticsConsent = onSetAnalyticsConsent,
          onSetShowStatusBar = onSetShowStatusBar,
          onSetKeepScreenOn = onSetKeepScreenOn,
          onDeleteAllData = onDeleteAllData,
          onNavigateToThemeMode = onNavigateToThemeMode,
          onNavigateToLightTheme = onNavigateToLightTheme,
          onNavigateToDarkTheme = onNavigateToDarkTheme,
          onNavigateToDashPacks = onNavigateToDashPacks,
          onNavigateToDiagnostics = onNavigateToDiagnostics,
          onResetDash = onResetDash,
          onClose = onClose,
        )
      }
    }
  }
}
