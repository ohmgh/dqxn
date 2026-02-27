package app.dqxn.android.feature.dashboard.layer

import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PreviewOverlayTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun `content is displayed`() {
    composeTestRule.setContent {
      PreviewOverlay(
        previewFraction = 0.15f,
        onDismiss = {},
      ) {
        Text("Sheet content", modifier = Modifier.testTag("sheet_content"))
      }
    }

    composeTestRule.onNodeWithTag("sheet_content").assertExists()
    composeTestRule.onNodeWithTag("preview_content").assertExists()
  }

  @Test
  fun `dismiss zone tap calls onDismiss`() {
    var dismissed = false

    composeTestRule.setContent {
      PreviewOverlay(
        previewFraction = 0.15f,
        onDismiss = { dismissed = true },
      ) {
        Text("Content")
      }
    }

    composeTestRule.onNodeWithTag("preview_dismiss_zone").performClick()
    assertThat(dismissed).isTrue()
  }

  @Test
  fun `preview content zone exists`() {
    composeTestRule.setContent {
      PreviewOverlay(
        previewFraction = 0.38f,
        onDismiss = {},
      ) {
        Text("Widget settings")
      }
    }

    composeTestRule.onNodeWithTag("preview_content").assertExists()
    composeTestRule.onNodeWithTag("preview_dismiss_zone").assertExists()
  }
}
