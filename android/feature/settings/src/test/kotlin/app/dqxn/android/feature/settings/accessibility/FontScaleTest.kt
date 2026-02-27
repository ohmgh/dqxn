package app.dqxn.android.feature.settings.accessibility

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import app.dqxn.android.feature.settings.main.MainSettings
import app.dqxn.android.sdk.ui.theme.DashboardThemeDefinition
import app.dqxn.android.sdk.ui.theme.LocalDashboardTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Font scale rendering verification for MainSettings.
 *
 * Ensures the settings UI renders without crashes, overflow, or invisible elements at system font
 * scales 1.0x, 1.5x, and 2.0x. NF33 requires that system font scale is respected in settings
 * (dashboard widgets are exempt as they use fixed sizes).
 *
 * Each test overrides [LocalDensity] with a custom [Density] that only changes [Density.fontScale],
 * keeping the base density at the Robolectric default. This simulates the user changing the system
 * font scale in Android accessibility settings.
 */
@RunWith(RobolectricTestRunner::class)
class FontScaleTest {

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

  /** All key text elements that must remain visible at any font scale. */
  private val expectedTextNodes =
    listOf(
      "ADVANCED",
      "Theme Mode",
      "Show Status Bar",
      "Keep Screen On",
      "Dash Packs",
      "Analytics",
      "Diagnostics",
      "Delete All Data",
    )

  @Test
  fun `settings renders at font scale 1_0x baseline`() {
    renderMainSettingsAtFontScale(1.0f)
    verifyAllTextNodesDisplayed()
  }

  @Test
  fun `settings renders at font scale 1_5x without overflow`() {
    renderMainSettingsAtFontScale(1.5f)
    verifyAllTextNodesDisplayed()
  }

  @Test
  fun `settings renders at font scale 2_0x without overflow`() {
    renderMainSettingsAtFontScale(2.0f)
    verifyAllTextNodesDisplayed()
  }

  // --- Helpers ---

  private fun verifyAllTextNodesDisplayed() {
    for (text in expectedTextNodes) {
      scrollTo(text)
      composeTestRule.onNodeWithText(text).assertIsDisplayed()
    }
  }

  private fun scrollTo(text: String) {
    composeTestRule
      .onNodeWithTag("main_settings_content")
      .performScrollToNode(hasText(text))
    composeTestRule.waitForIdle()
  }

  private fun renderMainSettingsAtFontScale(fontScale: Float) {
    composeTestRule.setContent {
      val baseDensity = LocalDensity.current
      val scaledDensity =
        Density(density = baseDensity.density, fontScale = fontScale)
      CompositionLocalProvider(
        LocalDensity provides scaledDensity,
        LocalDashboardTheme provides testTheme,
      ) {
        MainSettings(
          analyticsConsent = false,
          showStatusBar = false,
          keepScreenOn = true,
          onSetAnalyticsConsent = {},
          onSetShowStatusBar = {},
          onSetKeepScreenOn = {},
          onDeleteAllData = {},
          onNavigateToThemeMode = {},
          onNavigateToLightTheme = {},
          onNavigateToDarkTheme = {},
          onNavigateToDashPacks = {},
          onNavigateToDiagnostics = {},
          onResetDash = {},
          onClose = {},
        )
      }
    }
  }
}
