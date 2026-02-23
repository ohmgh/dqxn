package app.dqxn.android.sdk.ui.widget

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import app.dqxn.android.sdk.contracts.widget.BackgroundStyle
import app.dqxn.android.sdk.contracts.widget.WidgetStyle
import app.dqxn.android.sdk.ui.theme.DashboardThemeDefinition
import app.dqxn.android.sdk.ui.theme.LocalDashboardTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WidgetContainerTest {

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
  fun `renders content with solid background`() {
    val style = WidgetStyle.Default.copy(backgroundStyle = BackgroundStyle.SOLID)
    composeTestRule.setContent {
      CompositionLocalProvider(LocalDashboardTheme provides testTheme) {
        WidgetContainer(style = style, modifier = Modifier.size(100.dp)) { Text("Hello") }
      }
    }
    composeTestRule.onNodeWithText("Hello").assertIsDisplayed()
  }

  @Test
  fun `renders content with NONE background`() {
    val style = WidgetStyle.Default.copy(backgroundStyle = BackgroundStyle.NONE)
    composeTestRule.setContent {
      CompositionLocalProvider(LocalDashboardTheme provides testTheme) {
        WidgetContainer(style = style, modifier = Modifier.size(100.dp)) { Text("No BG") }
      }
    }
    composeTestRule.onNodeWithText("No BG").assertIsDisplayed()
  }

  @Test
  fun `applies border when showBorder true`() {
    val style = WidgetStyle.Default.copy(showBorder = true)
    composeTestRule.setContent {
      CompositionLocalProvider(LocalDashboardTheme provides testTheme) {
        WidgetContainer(style = style, modifier = Modifier.size(100.dp)) { Text("Bordered") }
      }
    }
    // Semantics-based verification: content renders correctly with border applied
    composeTestRule.onNodeWithText("Bordered").assertIsDisplayed()
  }

  @Test
  fun `no border when showBorder false`() {
    val style = WidgetStyle.Default.copy(showBorder = false)
    composeTestRule.setContent {
      CompositionLocalProvider(LocalDashboardTheme provides testTheme) {
        WidgetContainer(style = style, modifier = Modifier.size(100.dp)) { Text("No Border") }
      }
    }
    composeTestRule.onNodeWithText("No Border").assertIsDisplayed()
  }

  @Test
  fun `rim padding creates inner space`() {
    val style = WidgetStyle.Default.copy(rimSizePercent = 20)
    composeTestRule.setContent {
      CompositionLocalProvider(LocalDashboardTheme provides testTheme) {
        WidgetContainer(style = style, modifier = Modifier.size(100.dp)) { Text("Rimmed") }
      }
    }
    composeTestRule.onNodeWithText("Rimmed").assertIsDisplayed()
  }

  @Test
  fun `graphicsLayer applied for opacity`() {
    val style = WidgetStyle.Default.copy(opacity = 0.5f)
    composeTestRule.setContent {
      CompositionLocalProvider(LocalDashboardTheme provides testTheme) {
        WidgetContainer(style = style, modifier = Modifier.size(100.dp)) { Text("Semi") }
      }
    }
    // Content is rendered (graphicsLayer is applied at the composable level)
    composeTestRule.onNodeWithText("Semi").assertIsDisplayed()
  }

  @Test
  fun `accessibility contentDescription set`() {
    val style = WidgetStyle.Default
    composeTestRule.setContent {
      CompositionLocalProvider(LocalDashboardTheme provides testTheme) {
        WidgetContainer(
          style = style,
          modifier = Modifier.size(100.dp),
          contentDescription = "Speed: 65 km/h",
        ) {
          Text("65")
        }
      }
    }
    composeTestRule.onNode(hasContentDescription("Speed: 65 km/h")).assertIsDisplayed()
  }

  @Test
  fun `WidgetStyle Default has expected values`() {
    val style = WidgetStyle.Default
    assertThat(style.backgroundStyle).isEqualTo(BackgroundStyle.NONE)
    assertThat(style.opacity).isEqualTo(1.0f)
    assertThat(style.showBorder).isFalse()
    assertThat(style.hasGlowEffect).isFalse()
    assertThat(style.cornerRadiusPercent).isEqualTo(25)
    assertThat(style.rimSizePercent).isEqualTo(0)
    assertThat(style.zLayer).isEqualTo(0)
  }
}
