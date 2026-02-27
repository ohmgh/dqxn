package app.dqxn.android.feature.settings.theme

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import app.dqxn.android.sdk.ui.theme.DashboardThemeDefinition
import app.dqxn.android.sdk.ui.theme.LocalDashboardTheme
import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ThemeStudioTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val containerTheme =
    DashboardThemeDefinition(
      themeId = "container",
      displayName = "Container",
      primaryTextColor = Color.White,
      secondaryTextColor = Color.Gray,
      accentColor = Color.Cyan,
      highlightColor = Color.Cyan,
      widgetBorderColor = Color.Red,
      backgroundBrush = Brush.verticalGradient(listOf(Color.Black, Color.DarkGray)),
      widgetBackgroundBrush = Brush.verticalGradient(listOf(Color.DarkGray, Color.Black)),
    )

  private val existingTheme =
    DashboardThemeDefinition(
      themeId = "custom_123",
      displayName = "My Custom",
      primaryTextColor = Color.White,
      secondaryTextColor = Color.LightGray,
      accentColor = Color.Blue,
      highlightColor = Color.Yellow,
      widgetBorderColor = Color.Green,
      backgroundBrush = Brush.verticalGradient(listOf(Color.Black, Color.DarkGray)),
      widgetBackgroundBrush = Brush.verticalGradient(listOf(Color.DarkGray, Color.Black)),
    )

  @Test
  fun `max 12 banner shown at count equals 12`() {
    composeTestRule.setContent {
      CompositionLocalProvider(LocalDashboardTheme provides containerTheme) {
        ThemeStudio(
          existingTheme = existingTheme,
          customThemeCount = 12,
          onAutoSave = {},
          onDelete = {},
          onClearPreview = {},
          onClose = {},
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.mainClock.advanceTimeBy(1000)
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("max_themes_banner", useUnmergedTree = true).assertIsDisplayed()
  }

  @Test
  fun `max 12 banner hidden at count below 12`() {
    composeTestRule.setContent {
      CompositionLocalProvider(LocalDashboardTheme provides containerTheme) {
        ThemeStudio(
          existingTheme = existingTheme,
          customThemeCount = 5,
          onAutoSave = {},
          onDelete = {},
          onClearPreview = {},
          onClose = {},
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.mainClock.advanceTimeBy(1000)
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("max_themes_banner", useUnmergedTree = true).assertDoesNotExist()
  }

  @Test
  fun `all 8 theme properties accessible via swatch row and isDark toggle`() {
    composeTestRule.setContent {
      CompositionLocalProvider(LocalDashboardTheme provides containerTheme) {
        ThemeStudio(
          existingTheme = existingTheme,
          customThemeCount = 1,
          onAutoSave = {},
          onDelete = {},
          onClearPreview = {},
          onClose = {},
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.mainClock.advanceTimeBy(1000)
    composeTestRule.waitForIdle()

    // 7 swatch test tags (one per SwatchType)
    composeTestRule.onNodeWithTag("swatch_PRIMARY_TEXT", useUnmergedTree = true).assertExists()
    composeTestRule.onNodeWithTag("swatch_SECONDARY_TEXT", useUnmergedTree = true).assertExists()
    composeTestRule.onNodeWithTag("swatch_ACCENT", useUnmergedTree = true).assertExists()
    composeTestRule.onNodeWithTag("swatch_HIGHLIGHT", useUnmergedTree = true).assertExists()
    composeTestRule.onNodeWithTag("swatch_WIDGET_BORDER", useUnmergedTree = true).assertExists()
    composeTestRule.onNodeWithTag("swatch_BACKGROUND", useUnmergedTree = true).assertExists()
    composeTestRule.onNodeWithTag("swatch_WIDGET_BACKGROUND", useUnmergedTree = true).assertExists()

    // isDark toggle (8th editable property)
    composeTestRule.onNodeWithTag("is_dark_toggle", useUnmergedTree = true).assertExists()
  }

  @Test
  fun `theme studio wraps in overlay scaffold`() {
    composeTestRule.setContent {
      CompositionLocalProvider(LocalDashboardTheme provides containerTheme) {
        ThemeStudio(
          existingTheme = null,
          customThemeCount = 0,
          onAutoSave = {},
          onDelete = {},
          onClearPreview = {},
          onClose = {},
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.mainClock.advanceTimeBy(1000)
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("theme_studio", useUnmergedTree = true).assertExists()
  }

  @Test
  fun `editable title field exists`() {
    composeTestRule.setContent {
      CompositionLocalProvider(LocalDashboardTheme provides containerTheme) {
        ThemeStudio(
          existingTheme = existingTheme,
          customThemeCount = 1,
          onAutoSave = {},
          onDelete = {},
          onClearPreview = {},
          onClose = {},
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.mainClock.advanceTimeBy(1000)
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("editable_title", useUnmergedTree = true).assertExists()
  }

  @Test
  fun `undo button exists`() {
    composeTestRule.setContent {
      CompositionLocalProvider(LocalDashboardTheme provides containerTheme) {
        ThemeStudio(
          existingTheme = existingTheme,
          customThemeCount = 1,
          onAutoSave = {},
          onDelete = {},
          onClearPreview = {},
          onClose = {},
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.mainClock.advanceTimeBy(1000)
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("undo_button", useUnmergedTree = true).assertExists()
  }

  @Test
  fun `delete button shown for existing theme`() {
    composeTestRule.setContent {
      CompositionLocalProvider(LocalDashboardTheme provides containerTheme) {
        ThemeStudio(
          existingTheme = existingTheme,
          customThemeCount = 1,
          onAutoSave = {},
          onDelete = {},
          onClearPreview = {},
          onClose = {},
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.mainClock.advanceTimeBy(1000)
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("delete_button", useUnmergedTree = true).assertExists()
  }

  @Test
  fun `delete button hidden for new theme`() {
    composeTestRule.setContent {
      CompositionLocalProvider(LocalDashboardTheme provides containerTheme) {
        ThemeStudio(
          existingTheme = null,
          customThemeCount = 0,
          onAutoSave = {},
          onDelete = {},
          onClearPreview = {},
          onClose = {},
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.mainClock.advanceTimeBy(1000)
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("delete_button", useUnmergedTree = true).assertDoesNotExist()
  }

  @Test
  fun `swatch dimensions are 48dp container in source`() {
    val file =
      File(
        System.getProperty("user.dir"),
        "src/main/kotlin/app/dqxn/android/feature/settings/theme/ThemeSwatchRow.kt",
      )
    val content = file.readText()
    assertThat(content).contains(".size(48.dp)")
    assertThat(content).contains(".size(36.dp)")
    assertThat(content).doesNotContain(".size(40.dp)")
  }

  @Test
  fun `swatch selection uses highlightColor not accentColor`() {
    val file =
      File(
        System.getProperty("user.dir"),
        "src/main/kotlin/app/dqxn/android/feature/settings/theme/ThemeSwatchRow.kt",
      )
    val content = file.readText()
    assertThat(content).contains("theme.highlightColor")
    // Verify the border uses highlightColor
    assertThat(content).doesNotContain("if (isSelected) theme.accentColor")
  }

  @Test
  fun `gradient editing wired for background swatch`() {
    val file =
      File(
        System.getProperty("user.dir"),
        "src/main/kotlin/app/dqxn/android/feature/settings/theme/ThemeStudio.kt",
      )
    val content = file.readText()
    assertThat(content).contains("GradientTypeSelector")
    assertThat(content).contains("GradientStopRow")
    assertThat(content).doesNotContain("Gradient editing for")
  }

  @Test
  fun `editable title uses BasicTextField in source`() {
    val file =
      File(
        System.getProperty("user.dir"),
        "src/main/kotlin/app/dqxn/android/feature/settings/theme/ThemeStudio.kt",
      )
    val content = file.readText()
    assertThat(content).contains("BasicTextField")
  }
}
