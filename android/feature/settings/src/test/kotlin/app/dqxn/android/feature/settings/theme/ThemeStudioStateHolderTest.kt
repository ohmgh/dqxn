package app.dqxn.android.feature.settings.theme

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import app.dqxn.android.sdk.ui.theme.DashboardThemeDefinition
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class ThemeStudioStateHolderTest {

  private val testTheme =
    DashboardThemeDefinition(
      themeId = "test-theme",
      displayName = "Test Theme",
      isDark = true,
      primaryTextColor = Color.White,
      secondaryTextColor = Color.Gray,
      accentColor = Color.Cyan,
      highlightColor = Color.Yellow,
      widgetBorderColor = Color.Red,
      backgroundBrush = Brush.verticalGradient(listOf(Color.Black, Color.DarkGray)),
      widgetBackgroundBrush = SolidColor(Color.DarkGray),
    )

  @Test
  fun `isDirty is false when no changes made`() {
    val holder = ThemeStudioStateHolder(testTheme)

    // Read isDirty within a snapshot to ensure derivedStateOf evaluates
    val dirty = Snapshot.withMutableSnapshot { holder.isDirty }

    assertThat(dirty).isFalse()
  }

  @Test
  fun `isDirty is true when color changed`() {
    val holder = ThemeStudioStateHolder(testTheme)

    Snapshot.withMutableSnapshot { holder.primaryTextColor = Color.Red }

    val dirty = Snapshot.withMutableSnapshot { holder.isDirty }
    assertThat(dirty).isTrue()
  }

  @Test
  fun `buildCustomTheme produces valid definition`() {
    val holder = ThemeStudioStateHolder(testTheme)

    Snapshot.withMutableSnapshot {
      holder.primaryTextColor = Color.Magenta
      holder.accentColor = Color.Green
      holder.isDark = false
    }

    val result = Snapshot.withMutableSnapshot { holder.buildCustomTheme("custom_123", "My Theme") }

    assertThat(result.themeId).isEqualTo("custom_123")
    assertThat(result.displayName).isEqualTo("My Theme")
    assertThat(result.primaryTextColor).isEqualTo(Color.Magenta)
    assertThat(result.accentColor).isEqualTo(Color.Green)
    assertThat(result.isDark).isFalse()
    // Unchanged fields preserved from initial
    assertThat(result.secondaryTextColor).isEqualTo(Color.Gray)
    assertThat(result.highlightColor).isEqualTo(Color.Yellow)
    assertThat(result.widgetBorderColor).isEqualTo(Color.Red)
  }

  @Test
  fun `null initialTheme uses defaults`() {
    val holder = ThemeStudioStateHolder(null)

    val snapshot =
      Snapshot.withMutableSnapshot {
        Triple(holder.primaryTextColor, holder.accentColor, holder.isDark)
      }

    assertThat(snapshot.first).isEqualTo(Color.White)
    assertThat(snapshot.second).isEqualTo(Color.Cyan)
    assertThat(snapshot.third).isTrue()

    // isDirty should be false with defaults
    val dirty = Snapshot.withMutableSnapshot { holder.isDirty }
    assertThat(dirty).isFalse()
  }

  @Test
  fun `buildCustomTheme uses provided themeId`() {
    val holder = ThemeStudioStateHolder(null)

    val result =
      Snapshot.withMutableSnapshot { holder.buildCustomTheme("custom_987654", "Studio Theme") }

    assertThat(result.themeId).isEqualTo("custom_987654")
    assertThat(result.displayName).isEqualTo("Studio Theme")
  }
}
