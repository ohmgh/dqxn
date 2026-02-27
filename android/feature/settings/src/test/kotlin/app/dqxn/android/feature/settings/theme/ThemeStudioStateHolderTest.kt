package app.dqxn.android.feature.settings.theme

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import app.dqxn.android.sdk.ui.theme.DashboardThemeDefinition
import app.dqxn.android.sdk.ui.theme.GradientType
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
      widgetBackgroundBrush =
        Brush.verticalGradient(listOf(Color.DarkGray, Color.Black)),
    )

  @Test
  fun `isDirty is false when no changes made`() {
    val holder = ThemeStudioStateHolder(testTheme)
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
    val result = Snapshot.withMutableSnapshot { holder.buildCustomTheme("custom_123") }
    assertThat(result.themeId).isEqualTo("custom_123")
    assertThat(result.displayName).isEqualTo("Test Theme")
    assertThat(result.primaryTextColor).isEqualTo(Color.Magenta)
    assertThat(result.accentColor).isEqualTo(Color.Green)
    assertThat(result.isDark).isFalse()
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
    val dirty = Snapshot.withMutableSnapshot { holder.isDirty }
    assertThat(dirty).isFalse()
  }

  @Test
  fun `buildCustomTheme uses provided themeId`() {
    val holder = ThemeStudioStateHolder(null)
    val result = Snapshot.withMutableSnapshot { holder.buildCustomTheme("custom_987654") }
    assertThat(result.themeId).isEqualTo("custom_987654")
    assertThat(result.displayName).isEqualTo("Custom Theme")
  }

  @Test
  fun `displayName initialized from existing theme`() {
    val holder = ThemeStudioStateHolder(existingTheme)
    val name = Snapshot.withMutableSnapshot { holder.displayName }
    assertThat(name).isEqualTo("My Custom")
  }

  @Test
  fun `displayName defaults to Custom Theme for new`() {
    val holder = ThemeStudioStateHolder(null)
    val name = Snapshot.withMutableSnapshot { holder.displayName }
    assertThat(name).isEqualTo("Custom Theme")
  }

  @Test
  fun `isDirty includes displayName change`() {
    val holder = ThemeStudioStateHolder(existingTheme)
    val dirtyBefore = Snapshot.withMutableSnapshot { holder.isDirty }
    assertThat(dirtyBefore).isFalse()
    Snapshot.withMutableSnapshot { holder.displayName = "Renamed" }
    val dirtyAfter = Snapshot.withMutableSnapshot { holder.isDirty }
    assertThat(dirtyAfter).isTrue()
  }

  @Test
  fun `reset restores all properties to initial`() {
    val holder = ThemeStudioStateHolder(existingTheme)
    Snapshot.withMutableSnapshot {
      holder.displayName = "Changed"
      holder.accentColor = Color.Red
      holder.isDark = !holder.isDark
    }
    val dirtyAfterChange = Snapshot.withMutableSnapshot { holder.isDirty }
    assertThat(dirtyAfterChange).isTrue()
    Snapshot.withMutableSnapshot { holder.reset() }
    val dirtyAfterReset = Snapshot.withMutableSnapshot { holder.isDirty }
    assertThat(dirtyAfterReset).isFalse()
    val name = Snapshot.withMutableSnapshot { holder.displayName }
    assertThat(name).isEqualTo("My Custom")
  }

  @Test
  fun `buildCustomTheme uses mutable displayName`() {
    val holder = ThemeStudioStateHolder(null)
    Snapshot.withMutableSnapshot { holder.displayName = "Sunset Glow" }
    val theme = Snapshot.withMutableSnapshot { holder.buildCustomTheme("test_id") }
    assertThat(theme.displayName).isEqualTo("Sunset Glow")
  }

  @Test
  fun `isDirty includes gradient type change`() {
    val holder = ThemeStudioStateHolder(existingTheme)
    val dirtyBefore = Snapshot.withMutableSnapshot { holder.isDirty }
    assertThat(dirtyBefore).isFalse()
    Snapshot.withMutableSnapshot {
      holder.backgroundGradientType = GradientType.RADIAL
    }
    val dirtyAfter = Snapshot.withMutableSnapshot { holder.isDirty }
    assertThat(dirtyAfter).isTrue()
  }
}
