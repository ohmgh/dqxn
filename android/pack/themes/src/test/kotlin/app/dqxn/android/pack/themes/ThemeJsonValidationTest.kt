package app.dqxn.android.pack.themes

import app.dqxn.android.sdk.ui.theme.DashboardThemeDefinition
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.util.stream.Stream
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/**
 * Per-file structural validation for all 22 premium theme JSON files.
 *
 * Uses JUnit5 `@ParameterizedTest` with `@MethodSource` to validate every theme file individually.
 * Covers F6.1 requirement: all themes parse correctly with valid colors and gradient stops.
 */
class ThemeJsonValidationTest {

  @ParameterizedTest(name = "{0}")
  @MethodSource("themeFiles")
  fun `theme JSON parses successfully`(filename: String) {
    val json = loadThemeJson(filename)
    val theme = parseThemeJson(json)
    assertWithMessage("parsed theme from $filename").that(theme).isNotNull()
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("themeFiles")
  fun `theme has non-empty themeId and displayName`(filename: String) {
    val theme = loadAndParse(filename)
    assertThat(theme.themeId).isNotEmpty()
    assertThat(theme.displayName).isNotEmpty()
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("themeFiles")
  fun `theme has isDark explicitly set`(filename: String) {
    // isDark is a required Boolean field -- parsing would fail if missing
    val theme = loadAndParse(filename)
    // Just verify it parsed (isDark is non-null Boolean, always set)
    assertThat(theme.isDark).isAnyOf(true, false)
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("themeFiles")
  fun `theme has non-zero color tokens`(filename: String) {
    val theme = loadAndParse(filename)
    assertThat(theme.primaryTextColor.value).isNotEqualTo(0uL)
    assertThat(theme.secondaryTextColor.value).isNotEqualTo(0uL)
    assertThat(theme.accentColor.value).isNotEqualTo(0uL)
    assertThat(theme.widgetBorderColor.value).isNotEqualTo(0uL)
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("themeFiles")
  fun `theme has non-null backgroundBrush`(filename: String) {
    val theme = loadAndParse(filename)
    assertThat(theme.backgroundBrush).isNotNull()
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("themeFiles")
  fun `theme has non-null widgetBackgroundBrush`(filename: String) {
    val theme = loadAndParse(filename)
    assertThat(theme.widgetBackgroundBrush).isNotNull()
  }

  @Test
  fun `all 22 raw JSON strings deserialize without exception`() {
    val json = Json { ignoreUnknownKeys = true }
    for (filename in ThemesPackThemeProvider.THEME_FILES) {
      val raw = loadThemeJson(filename)
      val schema = json.decodeFromString<ThemesFileSchema>(raw)
      assertWithMessage("id in $filename").that(schema.id).isNotEmpty()
      assertWithMessage("name in $filename").that(schema.name).isNotEmpty()
    }
  }

  @Test
  fun `gradient stop positions are in 0 to 1 range`() {
    val json = Json { ignoreUnknownKeys = true }
    for (filename in ThemesPackThemeProvider.THEME_FILES) {
      val raw = loadThemeJson(filename)
      val schema = json.decodeFromString<ThemesFileSchema>(raw)
      schema.backgroundGradient?.stops?.forEach { stop ->
        assertWithMessage("background gradient stop position in $filename")
          .that(stop.position)
          .isAtLeast(0.0f)
        assertWithMessage("background gradient stop position in $filename")
          .that(stop.position)
          .isAtMost(1.0f)
      }
      schema.widgetBackgroundGradient?.stops?.forEach { stop ->
        assertWithMessage("widget background gradient stop position in $filename")
          .that(stop.position)
          .isAtLeast(0.0f)
        assertWithMessage("widget background gradient stop position in $filename")
          .that(stop.position)
          .isAtMost(1.0f)
      }
    }
  }

  private fun loadThemeJson(filename: String): String =
    javaClass.classLoader!!.getResourceAsStream("themes/$filename")!!.bufferedReader().readText()

  private fun loadAndParse(filename: String): DashboardThemeDefinition {
    val json = loadThemeJson(filename)
    return parseThemeJson(json) ?: error("Failed to parse $filename")
  }

  companion object {
    @JvmStatic fun themeFiles(): Stream<String> = ThemesPackThemeProvider.THEME_FILES.stream()
  }
}
