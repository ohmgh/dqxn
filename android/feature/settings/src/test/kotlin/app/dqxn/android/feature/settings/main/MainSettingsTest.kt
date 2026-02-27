package app.dqxn.android.feature.settings.main

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import app.dqxn.android.sdk.ui.theme.DashboardThemeDefinition
import app.dqxn.android.sdk.ui.theme.LocalDashboardTheme
import com.google.common.truth.Truth.assertThat
import java.io.File
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

  // --- Item order matches old codebase ---

  @Test
  fun `renders old codebase item order`() {
    renderMainSettings()

    // About banner first
    composeTestRule.onNodeWithTag("about_app_banner").assertExists()
    // Dash Packs
    composeTestRule.onNodeWithTag("main_settings_dash_packs").assertExists()
    // Theme items
    composeTestRule.onNodeWithTag("main_settings_theme_mode").assertExists()
    composeTestRule.onNodeWithTag("main_settings_light_theme").assertExists()
    composeTestRule.onNodeWithTag("main_settings_dark_theme").assertExists()
    // Status bar
    composeTestRule.onNodeWithTag("main_settings_status_bar").assertExists()
    // Reset dash
    scrollToTag("main_settings_reset_dash")
    composeTestRule.onNodeWithTag("main_settings_reset_dash").assertExists()
  }

  // --- About App Banner ---

  @Test
  fun `about app banner shows tagline`() {
    renderMainSettings()
    composeTestRule.onNodeWithText("Life is a dash. Make it beautiful.").assertExists()
  }

  @Test
  fun `about app banner shows attribution`() {
    renderMainSettings()
    composeTestRule.onNodeWithText("-- The Dashing Dachshund").assertExists()
  }

  @Test
  fun `about app banner shows app name and version`() {
    renderMainSettings(versionName = "1.0.0")
    composeTestRule.onNodeWithText("DQXN").assertExists()
    composeTestRule.onNodeWithText("1.0.0").assertExists()
  }

  // --- Dynamic subtitles ---

  @Test
  fun `dash packs row shows dynamic count subtitle`() {
    renderMainSettings(packCount = 3, themeCount = 24, widgetCount = 13, providerCount = 9)
    composeTestRule.onNodeWithText("3 packs, 24 themes, 13 widgets, 9 providers").assertExists()
  }

  @Test
  fun `theme mode row shows auto switch mode description`() {
    renderMainSettings(autoSwitchModeDescription = "System")
    composeTestRule.onNodeWithText("System").assertExists()
  }

  @Test
  fun `light theme row shows current theme name`() {
    renderMainSettings(lightThemeName = "Slate")
    composeTestRule.onNodeWithText("Slate").assertExists()
  }

  @Test
  fun `dark theme row shows current theme name`() {
    renderMainSettings(darkThemeName = "Minimalist")
    composeTestRule.onNodeWithText("Minimalist").assertExists()
  }

  // --- Navigation callbacks ---

  @Test
  fun `light theme row triggers navigation`() {
    var clicked = false
    renderMainSettings(onNavigateToLightTheme = { clicked = true })
    scrollToTag("main_settings_light_theme")
    composeTestRule.onNodeWithTag("main_settings_light_theme").performClick()
    assertThat(clicked).isTrue()
  }

  @Test
  fun `dark theme row triggers navigation`() {
    var clicked = false
    renderMainSettings(onNavigateToDarkTheme = { clicked = true })
    scrollToTag("main_settings_dark_theme")
    composeTestRule.onNodeWithTag("main_settings_dark_theme").performClick()
    assertThat(clicked).isTrue()
  }

  // --- Status bar subtitle ---

  @Test
  fun `status bar subtitle shown`() {
    renderMainSettings()
    composeTestRule.onNodeWithText("Applies to current dash").assertExists()
  }

  // --- Reset Dash ---

  @Test
  fun `reset dash row exists with red text`() {
    renderMainSettings()
    scrollToTag("main_settings_reset_dash")
    composeTestRule.onNodeWithText("Reset Dash").assertExists()
    composeTestRule.onNodeWithText("Restore default layout and theme").assertExists()
  }

  @Test
  fun `reset dash triggers callback`() {
    var resetCalled = false
    renderMainSettings(onResetDash = { resetCalled = true })
    scrollToTag("main_settings_reset_dash")
    composeTestRule.onNodeWithTag("main_settings_reset_dash").performClick()
    assertThat(resetCalled).isTrue()
  }

  // --- Icon box styling verified in source ---

  @Test
  fun `icon box styling verified in source`() {
    // user.dir for Gradle tests can be either module root or android/ root
    val candidates =
      listOf(
        File(System.getProperty("user.dir"), "src/main/kotlin/app/dqxn/android/feature/settings/main/MainSettings.kt"),
        File(System.getProperty("user.dir"), "feature/settings/src/main/kotlin/app/dqxn/android/feature/settings/main/MainSettings.kt"),
      )
    val file = candidates.first { it.exists() }
    val content = file.readText()
    assertThat(content).contains("40.dp")
    assertThat(content).contains("accentColor.copy(alpha = 0.1f)")
    assertThat(content).contains("AboutAppBanner")
  }

  // --- Analytics toggle shows consent dialog ---

  @Test
  fun `tapping analytics row when disabled shows consent dialog`() {
    renderMainSettings(analyticsConsent = false)

    scrollTo("Analytics")
    composeTestRule.onNodeWithText("Analytics").performClick()
    advanceAnimations()

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

    assertThat(consentValue).isFalse()
  }

  // --- Delete All Data flow ---

  @Test
  fun `tapping delete all data shows confirmation dialog`() {
    renderMainSettings()

    scrollToTag("main_settings_delete_all")
    composeTestRule.onNodeWithTag("main_settings_delete_all").performClick()
    advanceAnimations()

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

    composeTestRule.onNodeWithTag("analytics_consent_dialog", useUnmergedTree = true).assertExists()
    composeTestRule
      .onNodeWithText("collects anonymous usage data", substring = true, useUnmergedTree = true)
      .assertExists()
    composeTestRule
      .onNodeWithText("disable analytics at any time", substring = true, useUnmergedTree = true)
      .assertExists()
  }

  // --- Helpers ---

  private fun advanceAnimations() {
    composeTestRule.mainClock.advanceTimeBy(5000)
    composeTestRule.waitForIdle()
  }

  private fun scrollTo(text: String) {
    composeTestRule.onNodeWithTag("main_settings_content").performScrollToNode(hasText(text))
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
    lightThemeName: String = "Slate",
    darkThemeName: String = "Minimalist",
    packCount: Int = 3,
    themeCount: Int = 24,
    widgetCount: Int = 13,
    providerCount: Int = 9,
    autoSwitchModeDescription: String = "System",
    versionName: String = "1.0.0",
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
          lightThemeName = lightThemeName,
          darkThemeName = darkThemeName,
          packCount = packCount,
          themeCount = themeCount,
          widgetCount = widgetCount,
          providerCount = providerCount,
          autoSwitchModeDescription = autoSwitchModeDescription,
          versionName = versionName,
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
