package app.dqxn.android.app.accessibility

import androidx.compose.ui.graphics.Color
import app.dqxn.android.app.accessibility.WcagContrastChecker.alphaComposite
import app.dqxn.android.app.accessibility.WcagContrastChecker.relativeLuminance
import app.dqxn.android.core.design.theme.ThemeFileSchema
import app.dqxn.android.sdk.ui.theme.MinimalistTheme
import app.dqxn.android.sdk.ui.theme.SlateTheme
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * Programmatic WCAG AA contrast audit across all 24 themes.
 *
 * Verifies:
 * - primaryTextColor vs every background gradient stop: >= 4.5:1 (normal text)
 * - primaryTextColor vs every widget background gradient stop (alpha-composited): >= 4.5:1
 * - accentColor vs every background gradient stop: >= 3.0:1 (large text -- accent used for display
 *   values)
 *
 * Covers 2 free themes (Slate, Minimalist) and 22 premium themes from JSON files.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ThemeContrastAuditTest {

  /** Parsed theme data used by all contrast checks. */
  private data class ThemeColors(
    val themeId: String,
    val primaryTextColor: Color,
    val secondaryTextColor: Color,
    val accentColor: Color,
    val errorColor: Color,
    val warningColor: Color,
    val successColor: Color,
    val backgroundGradientStops: List<Color>,
    val widgetBackgroundGradientStops: List<Color>,
  )

  private lateinit var allThemes: List<ThemeColors>
  private val json = Json { ignoreUnknownKeys = true }

  @BeforeAll
  fun loadAllThemes() {
    val themes = mutableListOf<ThemeColors>()

    // 2 free themes
    themes += extractFreeThemeColors(SlateTheme)
    themes += extractFreeThemeColors(MinimalistTheme)

    // 22 premium themes from JSON files
    val themesDir = findThemesDirectory()
    val jsonFiles =
      themesDir.listFiles { f -> f.name.endsWith(".theme.json") }
        ?: error("No theme JSON files found in $themesDir")

    for (file in jsonFiles.sortedBy { it.name }) {
      val schema = json.decodeFromString<ThemeFileSchema>(file.readText())
      themes += extractJsonThemeColors(schema)
    }

    allThemes = themes

    // Sanity check: exactly 24 themes
    assertWithMessage("Expected 24 themes (2 free + 22 premium)")
      .that(allThemes.size)
      .isEqualTo(24)
  }

  // --- Contrast checks ---

  @Test
  fun `primaryText vs background gradient stops meets AA normal text`() {
    val violations = mutableListOf<String>()

    for (theme in allThemes) {
      for ((i, bgStop) in theme.backgroundGradientStops.withIndex()) {
        val ratio = WcagContrastChecker.contrastRatio(theme.primaryTextColor, bgStop)
        if (ratio < WcagContrastChecker.AA_NORMAL_TEXT) {
          violations +=
            "Theme '${theme.themeId}': primaryText vs bg stop $i = " +
              "${String.format("%.2f", ratio)}:1 (need 4.5:1)"
        }
      }
    }

    assertWithMessage(
      "WCAG AA normal-text violations (primaryText vs background):\n" +
        violations.joinToString("\n") { "  - $it" }
    )
      .that(violations)
      .isEmpty()
  }

  @Test
  fun `primaryText vs widget background gradient stops meets AA normal text`() {
    val violations = mutableListOf<String>()

    for (theme in allThemes) {
      for ((i, widgetStop) in theme.widgetBackgroundGradientStops.withIndex()) {
        if (widgetStop.alpha < 1f) {
          // Semi-transparent widget background -- composite over each bg stop
          for ((j, bgStop) in theme.backgroundGradientStops.withIndex()) {
            val composited = alphaComposite(widgetStop, bgStop)
            val ratio = WcagContrastChecker.contrastRatio(theme.primaryTextColor, composited)
            if (ratio < WcagContrastChecker.AA_NORMAL_TEXT) {
              violations +=
                "Theme '${theme.themeId}': primaryText vs widget$i+bg$j = " +
                  "${String.format("%.2f", ratio)}:1 (need 4.5:1)"
            }
          }
        } else {
          val ratio = WcagContrastChecker.contrastRatio(theme.primaryTextColor, widgetStop)
          if (ratio < WcagContrastChecker.AA_NORMAL_TEXT) {
            violations +=
              "Theme '${theme.themeId}': primaryText vs widget$i = " +
                "${String.format("%.2f", ratio)}:1 (need 4.5:1)"
          }
        }
      }
    }

    assertWithMessage(
      "WCAG AA normal-text violations (primaryText vs widget background):\n" +
        violations.joinToString("\n") { "  - $it" }
    )
      .that(violations)
      .isEmpty()
  }

  @Test
  fun `accentColor vs background gradient stops meets AA large text`() {
    val violations = mutableListOf<String>()

    for (theme in allThemes) {
      for ((i, bgStop) in theme.backgroundGradientStops.withIndex()) {
        val ratio = WcagContrastChecker.contrastRatio(theme.accentColor, bgStop)
        if (ratio < WcagContrastChecker.AA_LARGE_TEXT) {
          violations +=
            "Theme '${theme.themeId}': accent vs bg stop $i = " +
              "${String.format("%.2f", ratio)}:1 (need 3.0:1)"
        }
      }
    }

    assertWithMessage(
      "WCAG AA large-text violations (accent vs background):\n" +
        violations.joinToString("\n") { "  - $it" }
    )
      .that(violations)
      .isEmpty()
  }

  // --- Helpers ---

  /**
   * Locates the themes directory by searching upward from the current working directory. The test
   * executes from `android/` or a subdirectory, so we search for
   * `pack/themes/src/main/resources/themes`.
   */
  private fun findThemesDirectory(): File {
    // Try relative to project root (android/)
    val candidates =
      listOf(
        File("pack/themes/src/main/resources/themes"),
        File("../pack/themes/src/main/resources/themes"),
        File(System.getProperty("user.dir"), "pack/themes/src/main/resources/themes"),
      )
    return candidates.firstOrNull { it.isDirectory }
      ?: error(
        "Could not find themes directory. Tried: ${candidates.map { it.absolutePath }}. " +
          "CWD: ${System.getProperty("user.dir")}"
      )
  }

  /**
   * Extracts color data from a free theme (Slate or Minimalist) which are defined as
   * [DashboardThemeDefinition] objects with inline Brush gradients.
   *
   * Since the free themes use `Brush.verticalGradient(listOf(...))`, the gradient stops are the
   * constructor params. We extract them from the known definitions.
   */
  private fun extractFreeThemeColors(
    theme: app.dqxn.android.sdk.ui.theme.DashboardThemeDefinition,
  ): ThemeColors {
    // Free themes have known gradient stops -- extract from the theme definition
    val bgStops: List<Color>
    val widgetBgStops: List<Color>

    when (theme.themeId) {
      "slate" -> {
        bgStops = listOf(Color(0xFF1A1A2E), Color(0xFF16213E))
        widgetBgStops = listOf(Color(0xFF1E1E32), Color(0xFF1A1A2E))
      }
      "minimalist" -> {
        bgStops = listOf(Color(0xFFF5F5F5), Color(0xFFEEEEEE))
        widgetBgStops = listOf(Color.White, Color(0xFFFAFAFA))
      }
      else -> error("Unknown free theme: ${theme.themeId}")
    }

    return ThemeColors(
      themeId = theme.themeId,
      primaryTextColor = theme.primaryTextColor,
      secondaryTextColor = theme.secondaryTextColor,
      accentColor = theme.accentColor,
      errorColor = theme.errorColor,
      warningColor = theme.warningColor,
      successColor = theme.successColor,
      backgroundGradientStops = bgStops,
      widgetBackgroundGradientStops = widgetBgStops,
    )
  }

  /** Extracts color data from a premium theme JSON schema. */
  private fun extractJsonThemeColors(schema: ThemeFileSchema): ThemeColors {
    val colors = schema.colors
    val bgStops =
      schema.backgroundGradient?.stops?.map { WcagContrastChecker.parseHexColor(it.color) }
        ?: listOf(
          WcagContrastChecker.parseHexColor(colors.background),
          WcagContrastChecker.parseHexColor(colors.surface),
        )
    val widgetBgStops =
      schema.widgetBackgroundGradient?.stops?.map { WcagContrastChecker.parseHexColor(it.color) }
        ?: listOf(
          WcagContrastChecker.parseHexColor(colors.surface),
          WcagContrastChecker.parseHexColor(colors.background),
        )

    return ThemeColors(
      themeId = schema.id,
      primaryTextColor = WcagContrastChecker.parseHexColor(colors.primary),
      secondaryTextColor = WcagContrastChecker.parseHexColor(colors.secondary),
      accentColor = WcagContrastChecker.parseHexColor(colors.accent),
      errorColor =
        colors.error?.let { WcagContrastChecker.parseHexColor(it) } ?: Color(0xFFEF5350),
      warningColor =
        colors.warning?.let { WcagContrastChecker.parseHexColor(it) } ?: Color(0xFFFFB74D),
      successColor =
        colors.success?.let { WcagContrastChecker.parseHexColor(it) } ?: Color(0xFF66BB6A),
      backgroundGradientStops = bgStops,
      widgetBackgroundGradientStops = widgetBgStops,
    )
  }
}
