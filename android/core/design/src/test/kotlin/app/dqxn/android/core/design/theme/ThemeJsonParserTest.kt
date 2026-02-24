package app.dqxn.android.core.design.theme

import androidx.compose.ui.graphics.Color
import app.dqxn.android.sdk.observability.log.NoOpLogger
import app.dqxn.android.sdk.ui.theme.GradientType
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ThemeJsonParserTest {

  private lateinit var parser: ThemeJsonParser

  @BeforeEach
  fun setUp() {
    parser = ThemeJsonParser(Json { ignoreUnknownKeys = true }, NoOpLogger)
  }

  @Nested
  @DisplayName("parse single theme")
  inner class ParseSingle {

    @Test
    fun `valid JSON produces DashboardThemeDefinition with correct colors`() {
      val json =
        """
        {
          "id": "test-dark",
          "name": "Test Dark",
          "isDark": true,
          "colors": {
            "primary": "#FFFFFF",
            "secondary": "#B0B0B0",
            "accent": "#4FC3F7",
            "background": "#1A1A2E",
            "surface": "#16213E",
            "onSurface": "#424242"
          }
        }
        """
          .trimIndent()

      val result = parser.parse(json)

      assertThat(result).isNotNull()
      assertThat(result!!.themeId).isEqualTo("test-dark")
      assertThat(result.displayName).isEqualTo("Test Dark")
      assertThat(result.isDark).isTrue()
      assertThat(result.primaryTextColor).isEqualTo(Color.White)
      assertThat(result.secondaryTextColor).isEqualTo(Color(0xFFB0B0B0))
      assertThat(result.accentColor).isEqualTo(Color(0xFF4FC3F7))
      assertThat(result.widgetBorderColor).isEqualTo(Color(0xFF424242))
    }

    @Test
    fun `JSON with gradients produces correct GradientSpec types`() {
      val json =
        """
        {
          "id": "gradient-theme",
          "name": "Gradient Theme",
          "isDark": true,
          "colors": {
            "primary": "#FFFFFF",
            "secondary": "#CCCCCC",
            "accent": "#FF5722",
            "background": "#000000",
            "surface": "#111111",
            "onSurface": "#333333"
          },
          "backgroundGradient": {
            "type": "VERTICAL",
            "stops": [
              { "color": "#FF000000", "position": 0.0 },
              { "color": "#FF111111", "position": 1.0 }
            ]
          },
          "widgetBackgroundGradient": {
            "type": "RADIAL",
            "stops": [
              { "color": "#FF222222", "position": 0.0 },
              { "color": "#FF333333", "position": 1.0 }
            ]
          }
        }
        """
          .trimIndent()

      val result = parser.parse(json)

      assertThat(result).isNotNull()
      assertThat(result!!.backgroundGradientSpec).isNotNull()
      assertThat(result.backgroundGradientSpec!!.type).isEqualTo(GradientType.VERTICAL)
      assertThat(result.backgroundGradientSpec!!.stops).hasSize(2)

      assertThat(result.widgetBackgroundGradientSpec).isNotNull()
      assertThat(result.widgetBackgroundGradientSpec!!.type).isEqualTo(GradientType.RADIAL)
    }

    @Test
    fun `malformed JSON returns null`() {
      val result = parser.parse("{ not valid json }")
      assertThat(result).isNull()
    }

    @Test
    fun `missing optional fields uses defaults`() {
      val json =
        """
        {
          "id": "minimal",
          "name": "Minimal",
          "isDark": false,
          "colors": {
            "primary": "#212121",
            "secondary": "#757575",
            "accent": "#1976D2",
            "background": "#F5F5F5",
            "surface": "#EEEEEE",
            "onSurface": "#E0E0E0"
          }
        }
        """
          .trimIndent()

      val result = parser.parse(json)

      assertThat(result).isNotNull()
      // No error/warning/success in JSON → defaults from DashboardThemeDefinition
      assertThat(result!!.errorColor).isEqualTo(Color(0xFFEF5350))
      assertThat(result.warningColor).isEqualTo(Color(0xFFFFB74D))
      assertThat(result.successColor).isEqualTo(Color(0xFF66BB6A))
      assertThat(result.backgroundGradientSpec).isNull()
      assertThat(result.widgetBackgroundGradientSpec).isNull()
    }

    @Test
    fun `color parsing - RRGGBB format`() {
      val json =
        """
        {
          "id": "red-test",
          "name": "Red Test",
          "isDark": true,
          "colors": {
            "primary": "#FF0000",
            "secondary": "#00FF00",
            "accent": "#0000FF",
            "background": "#000000",
            "surface": "#111111",
            "onSurface": "#222222"
          }
        }
        """
          .trimIndent()

      val result = parser.parse(json)

      assertThat(result).isNotNull()
      assertThat(result!!.primaryTextColor).isEqualTo(Color.Red)
      assertThat(result.secondaryTextColor).isEqualTo(Color.Green)
      assertThat(result.accentColor).isEqualTo(Color.Blue)
    }

    @Test
    fun `color parsing - AARRGGBB format with alpha`() {
      val json =
        """
        {
          "id": "alpha-test",
          "name": "Alpha Test",
          "isDark": true,
          "colors": {
            "primary": "#80FF0000",
            "secondary": "#CCCCCC",
            "accent": "#FF5722",
            "background": "#000000",
            "surface": "#111111",
            "onSurface": "#222222"
          }
        }
        """
          .trimIndent()

      val result = parser.parse(json)

      assertThat(result).isNotNull()
      // #80FF0000 = semi-transparent red (alpha ~0.5)
      val primary = result!!.primaryTextColor
      assertThat(primary.red).isWithin(0.01f).of(1.0f)
      assertThat(primary.green).isWithin(0.01f).of(0.0f)
      assertThat(primary.blue).isWithin(0.01f).of(0.0f)
      assertThat(primary.alpha).isWithin(0.02f).of(0.502f)
    }

    @Test
    fun `custom error, warning, success colors override defaults`() {
      val json =
        """
        {
          "id": "custom-semantic",
          "name": "Custom Semantic",
          "isDark": true,
          "colors": {
            "primary": "#FFFFFF",
            "secondary": "#CCCCCC",
            "accent": "#FF5722",
            "background": "#000000",
            "surface": "#111111",
            "onSurface": "#222222",
            "error": "#D32F2F",
            "warning": "#F57C00",
            "success": "#388E3C"
          }
        }
        """
          .trimIndent()

      val result = parser.parse(json)

      assertThat(result).isNotNull()
      assertThat(result!!.errorColor).isEqualTo(Color(0xFFD32F2F))
      assertThat(result.warningColor).isEqualTo(Color(0xFFF57C00))
      assertThat(result.successColor).isEqualTo(Color(0xFF388E3C))
    }
  }

  @Nested
  @DisplayName("parseAll")
  inner class ParseAll {

    @Test
    fun `parses array of themes`() {
      val json =
        """
        [
          {
            "id": "theme1",
            "name": "Theme 1",
            "isDark": true,
            "colors": {
              "primary": "#FFFFFF",
              "secondary": "#CCCCCC",
              "accent": "#FF5722",
              "background": "#000000",
              "surface": "#111111",
              "onSurface": "#222222"
            }
          },
          {
            "id": "theme2",
            "name": "Theme 2",
            "isDark": false,
            "colors": {
              "primary": "#212121",
              "secondary": "#757575",
              "accent": "#1976D2",
              "background": "#F5F5F5",
              "surface": "#EEEEEE",
              "onSurface": "#E0E0E0"
            }
          }
        ]
        """
          .trimIndent()

      val results = parser.parseAll(json)

      assertThat(results).hasSize(2)
      assertThat(results[0].themeId).isEqualTo("theme1")
      assertThat(results[1].themeId).isEqualTo("theme2")
    }

    @Test
    fun `malformed array returns empty list`() {
      val results = parser.parseAll("not json")
      assertThat(results).isEmpty()
    }
  }
}
