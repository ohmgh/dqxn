package app.dqxn.android.feature.settings.main

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import app.dqxn.android.sdk.ui.theme.DashboardThemeDefinition
import app.dqxn.android.sdk.ui.theme.LocalDashboardTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MainSettingsTest {

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

  // --- 4 sections render ---

  @Test
  fun `renders all 4 section headers`() {
    renderMainSettings()

    // First two sections visible without scrolling
    composeTestRule.onNodeWithText("APPEARANCE").assertIsDisplayed()
    composeTestRule.onNodeWithText("BEHAVIOR").assertIsDisplayed()

    // Scroll to reveal remaining sections
    scrollTo("DATA & PRIVACY")
    composeTestRule.onNodeWithText("DATA & PRIVACY").assertIsDisplayed()

    scrollTo("DANGER ZONE")
    composeTestRule.onNodeWithText("DANGER ZONE").assertIsDisplayed()
  }

  // --- Analytics toggle shows consent dialog ---

  @Test
  fun `tapping analytics row when disabled shows consent dialog`() {
    renderMainSettings(analyticsConsent = false)

    scrollTo("Analytics")
    composeTestRule.onNodeWithText("Analytics").performClick()
    advanceAnimations()

    // Node exists in unmerged tree (merged semantics hide it from default finders)
    composeTestRule.onNodeWithTag("analytics_consent_dialog", useUnmergedTree = true).assertExists()
  }

  @Test
  fun `confirming consent dialog calls setAnalyticsConsent true`() {
    var consentValue: Boolean? = null
    renderMainSettings(
      analyticsConsent = false,
      onSetAnalyticsConsent = { consentValue = it },
    )

    scrollTo("Analytics")
    composeTestRule.onNodeWithText("Analytics").performClick()
    advanceAnimations()
    composeTestRule.onNodeWithTag("analytics_consent_confirm").performClick()

    assertThat(consentValue).isTrue()
  }

  @Test
  fun `dismissing consent dialog does not call setAnalyticsConsent`() {
    var consentValue: Boolean? = null
    renderMainSettings(
      analyticsConsent = false,
      onSetAnalyticsConsent = { consentValue = it },
    )

    scrollTo("Analytics")
    composeTestRule.onNodeWithText("Analytics").performClick()
    advanceAnimations()
    composeTestRule.onNodeWithTag("analytics_consent_cancel").performClick()

    assertThat(consentValue).isNull()
  }

  @Test
  fun `tapping analytics row when enabled calls setAnalyticsConsent false immediately`() {
    var consentValue: Boolean? = null
    renderMainSettings(
      analyticsConsent = true,
      onSetAnalyticsConsent = { consentValue = it },
    )

    scrollTo("Analytics")
    composeTestRule.onNodeWithText("Analytics").performClick()

    // Disabling does not show dialog -- immediate toggle
    assertThat(consentValue).isFalse()
  }

  // --- Delete All Data flow ---

  @Test
  fun `tapping delete all data shows confirmation dialog`() {
    renderMainSettings()

    scrollToTag("main_settings_delete_all")
    composeTestRule.onNodeWithTag("main_settings_delete_all").performClick()
    advanceAnimations()

    // Node exists in unmerged tree (merged semantics hide it from default finders)
    composeTestRule.onNodeWithTag("delete_all_data_dialog", useUnmergedTree = true).assertExists()
    composeTestRule.onNodeWithTag("delete_all_data_confirm", useUnmergedTree = true).assertExists()
  }

  @Test
  fun `confirming delete all data calls deleteAllData`() {
    var deleteCalled = false
    renderMainSettings(onDeleteAllData = { deleteCalled = true })

    scrollToTag("main_settings_delete_all")
    composeTestRule.onNodeWithTag("main_settings_delete_all").performClick()
    advanceAnimations()
    composeTestRule.onNodeWithTag("delete_all_data_confirm").performClick()

    assertThat(deleteCalled).isTrue()
  }

  @Test
  fun `canceling delete dialog does not call deleteAllData`() {
    var deleteCalled = false
    renderMainSettings(onDeleteAllData = { deleteCalled = true })

    scrollToTag("main_settings_delete_all")
    composeTestRule.onNodeWithTag("main_settings_delete_all").performClick()
    advanceAnimations()
    composeTestRule.onNodeWithTag("delete_all_data_cancel").performClick()

    assertThat(deleteCalled).isFalse()
  }

  // --- Diagnostics navigation row ---

  @Test
  fun `diagnostics row renders and is clickable`() {
    var diagnosticsClicked = false
    renderMainSettings(onNavigateToDiagnostics = { diagnosticsClicked = true })

    scrollToTag("main_settings_diagnostics")
    composeTestRule.onNodeWithTag("main_settings_diagnostics").assertIsDisplayed()
    composeTestRule.onNodeWithTag("main_settings_diagnostics").performClick()

    assertThat(diagnosticsClicked).isTrue()
  }

  // --- Consent dialog explains data collected ---

  @Test
  fun `consent dialog explains data collected and right to revoke`() {
    renderMainSettings(analyticsConsent = false)

    scrollTo("Analytics")
    composeTestRule.onNodeWithText("Analytics").performClick()
    advanceAnimations()

    // Verify consent dialog is shown with explanation content (useUnmergedTree for merged semantics)
    composeTestRule
      .onNodeWithTag("analytics_consent_dialog", useUnmergedTree = true)
      .assertExists()
    // The dialog body contains text about data collected and right to revoke
    composeTestRule
      .onNodeWithText("collects anonymous usage data", substring = true, useUnmergedTree = true)
      .assertExists()
    composeTestRule
      .onNodeWithText("disable analytics at any time", substring = true, useUnmergedTree = true)
      .assertExists()
  }

  // --- Helpers ---

  /** Advance compose animations to completion so AnimatedVisibility content is rendered. */
  private fun advanceAnimations() {
    // Spring animations (dampingRatio=0.65, stiffness=300) need time to settle
    composeTestRule.mainClock.advanceTimeBy(5000)
    composeTestRule.waitForIdle()
  }

  /** Scroll the main_settings_content to reveal the node with the given text. */
  private fun scrollTo(text: String) {
    composeTestRule
      .onNodeWithTag("main_settings_content")
      .performScrollToNode(hasText(text))
    composeTestRule.waitForIdle()
  }

  /** Scroll the main_settings_content to reveal the node with the given test tag. */
  private fun scrollToTag(tag: String) {
    composeTestRule
      .onNodeWithTag("main_settings_content")
      .performScrollToNode(
        androidx.compose.ui.test.hasTestTag(tag)
      )
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
    onNavigateToDashPacks: () -> Unit = {},
    onNavigateToDiagnostics: () -> Unit = {},
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
          onNavigateToDashPacks = onNavigateToDashPacks,
          onNavigateToDiagnostics = onNavigateToDiagnostics,
          onClose = onClose,
        )
      }
    }
  }
}
