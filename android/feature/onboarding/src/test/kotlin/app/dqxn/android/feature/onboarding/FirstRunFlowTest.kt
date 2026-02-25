package app.dqxn.android.feature.onboarding

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.dqxn.android.sdk.ui.theme.DashboardThemeDefinition
import app.dqxn.android.sdk.ui.theme.MinimalistTheme
import app.dqxn.android.sdk.ui.theme.SlateTheme
import com.google.common.truth.Truth.assertThat
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FirstRunFlowTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private val freeThemes = persistentListOf<DashboardThemeDefinition>(SlateTheme, MinimalistTheme)

  @Test
  fun `starts on analytics consent step`() {
    renderFlow()

    composeTestRule.onNodeWithTag("analytics_consent_step").assertIsDisplayed()
  }

  @Test
  fun `advances to disclaimer after consent`() {
    renderFlow()

    // Skip consent
    composeTestRule.onNodeWithTag("consent_skip").performClick()
    advanceAnimations()

    composeTestRule.onNodeWithTag("first_launch_disclaimer").assertIsDisplayed()
  }

  @Test
  fun `advances to theme selection after disclaimer`() {
    renderFlow()

    // Skip consent
    composeTestRule.onNodeWithTag("consent_skip").performClick()
    advanceAnimations()

    // Dismiss disclaimer
    composeTestRule.onNodeWithTag("disclaimer_dismiss").performClick()
    advanceAnimations()

    composeTestRule.onNodeWithTag("theme_selection_step").assertIsDisplayed()
  }

  @Test
  fun `theme selection shows free themes only`() {
    renderFlow()

    // Navigate to theme step
    navigateToThemeStep()

    // Verify both free themes are shown
    freeThemes.forEach { theme ->
      composeTestRule.onNodeWithText(theme.displayName).assertIsDisplayed()
    }
  }

  @Test
  fun `advances to tour after theme selection`() {
    renderFlow()

    // Navigate to theme step
    navigateToThemeStep()

    // Select a theme and continue
    composeTestRule.onNodeWithTag("theme_card_${SlateTheme.themeId}").performClick()
    composeTestRule.onNodeWithTag("theme_continue").performClick()
    advanceAnimations()

    composeTestRule.onNodeWithTag("edit_mode_tour_step").assertIsDisplayed()
  }

  @Test
  fun `completing tour marks onboarding done`() {
    var completed = false
    renderFlow(onComplete = { completed = true })

    // Navigate to tour step
    navigateToTourStep()

    // Complete tour
    composeTestRule.onNodeWithTag("tour_done").performClick()

    assertThat(completed).isTrue()
  }

  @Test
  fun `back from page 2 returns to page 1`() {
    renderFlow()

    // Navigate to disclaimer (page 1)
    composeTestRule.onNodeWithTag("consent_skip").performClick()
    advanceAnimations()

    // Navigate to theme selection (page 2)
    composeTestRule.onNodeWithTag("disclaimer_dismiss").performClick()
    advanceAnimations()

    // Verify we're on theme step
    composeTestRule.onNodeWithTag("theme_selection_step").assertIsDisplayed()

    // Simulate back press via activity's onBackPressedDispatcher
    composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
    advanceAnimations()

    composeTestRule.onNodeWithTag("first_launch_disclaimer").assertIsDisplayed()
  }

  // --- Helpers ---

  private fun advanceAnimations() {
    composeTestRule.mainClock.advanceTimeBy(1000)
    composeTestRule.waitForIdle()
  }

  private fun navigateToThemeStep() {
    composeTestRule.onNodeWithTag("consent_skip").performClick()
    advanceAnimations()
    composeTestRule.onNodeWithTag("disclaimer_dismiss").performClick()
    advanceAnimations()
  }

  private fun navigateToTourStep() {
    navigateToThemeStep()
    composeTestRule.onNodeWithTag("theme_card_${SlateTheme.themeId}").performClick()
    composeTestRule.onNodeWithTag("theme_continue").performClick()
    advanceAnimations()
  }

  private fun renderFlow(
    onConsent: (Boolean) -> Unit = {},
    onDismissDisclaimer: () -> Unit = {},
    onSelectTheme: (String) -> Unit = {},
    onComplete: () -> Unit = {},
  ) {
    composeTestRule.setContent {
      FirstRunFlow(
        freeThemes = freeThemes,
        onConsent = onConsent,
        onDismissDisclaimer = onDismissDisclaimer,
        onSelectTheme = onSelectTheme,
        onComplete = onComplete,
      )
    }
  }
}
