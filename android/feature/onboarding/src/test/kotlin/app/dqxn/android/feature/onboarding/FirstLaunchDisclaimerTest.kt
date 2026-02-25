package app.dqxn.android.feature.onboarding

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FirstLaunchDisclaimerTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun `disclaimer text is displayed`() {
    composeTestRule.setContent { FirstLaunchDisclaimer(onDismiss = {}) }

    composeTestRule
      .onNodeWithText("informational purposes only", substring = true)
      .assertIsDisplayed()
  }

  @Test
  fun `dismiss button calls callback`() {
    var dismissed = false
    composeTestRule.setContent { FirstLaunchDisclaimer(onDismiss = { dismissed = true }) }

    composeTestRule.onNodeWithTag("disclaimer_dismiss").performClick()

    assertThat(dismissed).isTrue()
  }

  @Test
  fun `disclaimer uses correct test tag`() {
    composeTestRule.setContent { FirstLaunchDisclaimer(onDismiss = {}) }

    composeTestRule.onNodeWithTag("first_launch_disclaimer").assertIsDisplayed()
  }
}
