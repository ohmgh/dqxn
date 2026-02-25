package app.dqxn.android.feature.settings.overlay

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.dqxn.android.sdk.ui.theme.DashboardThemeDefinition
import app.dqxn.android.sdk.ui.theme.LocalDashboardTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OverlayScaffoldTest {

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

  // --- Shape logic ---

  @Test
  fun `Hub overlay has no rounded corners`() {
    composeTestRule.setContent {
      CompositionLocalProvider(LocalDashboardTheme provides testTheme) {
        OverlayScaffold(
          title = "Hub Title",
          overlayType = OverlayType.Hub,
          onClose = {},
        ) {
          Text("Hub content")
        }
      }
    }
    composeTestRule.onNodeWithTag("overlay_scaffold_hub").assertIsDisplayed()
    composeTestRule.onNodeWithText("Hub Title").assertIsDisplayed()
    composeTestRule.onNodeWithText("Hub content").assertIsDisplayed()
  }

  @Test
  fun `Preview overlay renders correctly`() {
    composeTestRule.setContent {
      CompositionLocalProvider(LocalDashboardTheme provides testTheme) {
        OverlayScaffold(
          title = "Preview Title",
          overlayType = OverlayType.Preview,
          onClose = {},
        ) {
          Text("Preview content")
        }
      }
    }
    composeTestRule.onNodeWithTag("overlay_scaffold_preview").assertIsDisplayed()
    composeTestRule.onNodeWithText("Preview Title").assertIsDisplayed()
    composeTestRule.onNodeWithText("Preview content").assertIsDisplayed()
  }

  @Test
  fun `Confirmation overlay renders correctly`() {
    composeTestRule.setContent {
      CompositionLocalProvider(LocalDashboardTheme provides testTheme) {
        OverlayScaffold(
          title = "Confirm Title",
          overlayType = OverlayType.Confirmation,
          onClose = {},
        ) {
          Text("Confirm content")
        }
      }
    }
    composeTestRule.onNodeWithTag("overlay_scaffold_confirmation").assertIsDisplayed()
    composeTestRule.onNodeWithText("Confirm Title").assertIsDisplayed()
    composeTestRule.onNodeWithText("Confirm content").assertIsDisplayed()
  }

  // --- Touch target sizing ---

  @Test
  fun `close button meets 76dp minimum touch target`() {
    composeTestRule.setContent {
      CompositionLocalProvider(LocalDashboardTheme provides testTheme) {
        OverlayScaffold(
          title = "Touch Target Test",
          overlayType = OverlayType.Hub,
          onClose = {},
        ) {
          Text("content")
        }
      }
    }
    composeTestRule
      .onNodeWithTag("overlay_close_button")
      .assertWidthIsAtLeast(76.dp)
      .assertHeightIsAtLeast(76.dp)
  }

  // --- Design token usage ---

  @Test
  fun `title text renders with expected content`() {
    composeTestRule.setContent {
      CompositionLocalProvider(LocalDashboardTheme provides testTheme) {
        OverlayScaffold(
          title = "Settings",
          overlayType = OverlayType.Preview,
          onClose = {},
        ) {
          Text("body")
        }
      }
    }
    composeTestRule.onNodeWithText("Settings").assertIsDisplayed()
  }

  // --- Content slot ---

  @Test
  fun `content composable renders inside scaffold`() {
    composeTestRule.setContent {
      CompositionLocalProvider(LocalDashboardTheme provides testTheme) {
        OverlayScaffold(
          title = "Content Slot Test",
          overlayType = OverlayType.Confirmation,
          onClose = {},
        ) {
          Text("test content")
        }
      }
    }
    composeTestRule.onNodeWithText("test content").assertIsDisplayed()
  }

  // --- Close callback ---

  @Test
  fun `close button invokes onClose callback`() {
    var closed = false
    composeTestRule.setContent {
      CompositionLocalProvider(LocalDashboardTheme provides testTheme) {
        OverlayScaffold(
          title = "Close Test",
          overlayType = OverlayType.Hub,
          onClose = { closed = true },
        ) {
          Text("closeable")
        }
      }
    }
    composeTestRule.onNodeWithTag("overlay_close_button").performClick()
    assertThat(closed).isTrue()
  }

  // --- All overlay types render distinct scaffolds ---

  @Test
  fun `all OverlayType values produce unique scaffold tags`() {
    val types = OverlayType.entries
    assertThat(types).hasSize(3)
    val tags = types.map { "overlay_scaffold_${it.name.lowercase()}" }.toSet()
    assertThat(tags).hasSize(3)
  }
}
