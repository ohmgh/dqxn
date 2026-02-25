package app.dqxn.android.feature.settings.theme

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.math.abs

@DisplayName("ColorConversion")
internal class ColorConversionTest {

  // +-1/255 tolerance for round-trip accuracy
  private val channelTolerance = 1f / 255f

  @Nested
  @DisplayName("colorToHsl")
  inner class ColorToHslTests {

    @Test
    fun `black produces H=0, S=0, L=0`() {
      val hsl = colorToHsl(Color.Black)
      assertWithMessage("hue").that(hsl[0]).isWithin(0.01f).of(0f)
      assertWithMessage("saturation").that(hsl[1]).isWithin(0.01f).of(0f)
      assertWithMessage("lightness").that(hsl[2]).isWithin(0.01f).of(0f)
    }

    @Test
    fun `white produces H=0, S=0, L=1`() {
      val hsl = colorToHsl(Color.White)
      assertWithMessage("hue").that(hsl[0]).isWithin(0.01f).of(0f)
      assertWithMessage("saturation").that(hsl[1]).isWithin(0.01f).of(0f)
      assertWithMessage("lightness").that(hsl[2]).isWithin(0.01f).of(1f)
    }

    @Test
    fun `pure red produces H=0, S=1, L=0_5`() {
      val hsl = colorToHsl(Color.Red)
      assertWithMessage("hue").that(hsl[0]).isWithin(0.01f).of(0f)
      assertWithMessage("saturation").that(hsl[1]).isWithin(0.01f).of(1f)
      assertWithMessage("lightness").that(hsl[2]).isWithin(0.01f).of(0.5f)
    }

    @Test
    fun `pure green produces H=120, S=1, L=0_5`() {
      val hsl = colorToHsl(Color.Green)
      assertWithMessage("hue").that(hsl[0]).isWithin(0.01f).of(120f)
      assertWithMessage("saturation").that(hsl[1]).isWithin(0.01f).of(1f)
      assertWithMessage("lightness").that(hsl[2]).isWithin(0.01f).of(0.5f)
    }

    @Test
    fun `pure blue produces H=240, S=1, L=0_5`() {
      val hsl = colorToHsl(Color.Blue)
      assertWithMessage("hue").that(hsl[0]).isWithin(0.01f).of(240f)
      assertWithMessage("saturation").that(hsl[1]).isWithin(0.01f).of(1f)
      assertWithMessage("lightness").that(hsl[2]).isWithin(0.01f).of(0.5f)
    }

    @Test
    fun `achromatic gray produces H=0, S=0, L=0_5`() {
      val gray = Color(0.5f, 0.5f, 0.5f)
      val hsl = colorToHsl(gray)
      assertWithMessage("hue").that(hsl[0]).isWithin(0.01f).of(0f)
      assertWithMessage("saturation").that(hsl[1]).isWithin(0.01f).of(0f)
      assertWithMessage("lightness").that(hsl[2]).isWithin(0.01f).of(0.5f)
    }

    @Test
    fun `yellow produces H=60, S=1, L=0_5`() {
      val hsl = colorToHsl(Color.Yellow)
      assertWithMessage("hue").that(hsl[0]).isWithin(0.01f).of(60f)
      assertWithMessage("saturation").that(hsl[1]).isWithin(0.01f).of(1f)
      assertWithMessage("lightness").that(hsl[2]).isWithin(0.01f).of(0.5f)
    }

    @Test
    fun `cyan produces H=180, S=1, L=0_5`() {
      val hsl = colorToHsl(Color.Cyan)
      assertWithMessage("hue").that(hsl[0]).isWithin(0.01f).of(180f)
      assertWithMessage("saturation").that(hsl[1]).isWithin(0.01f).of(1f)
      assertWithMessage("lightness").that(hsl[2]).isWithin(0.01f).of(0.5f)
    }

    @Test
    fun `magenta produces H=300, S=1, L=0_5`() {
      val hsl = colorToHsl(Color.Magenta)
      assertWithMessage("hue").that(hsl[0]).isWithin(0.01f).of(300f)
      assertWithMessage("saturation").that(hsl[1]).isWithin(0.01f).of(1f)
      assertWithMessage("lightness").that(hsl[2]).isWithin(0.01f).of(0.5f)
    }
  }

  @Nested
  @DisplayName("colorToHex")
  inner class ColorToHexTests {

    @Test
    fun `white returns FFFFFFFF`() {
      assertThat(colorToHex(Color.White)).isEqualTo("#FFFFFFFF")
    }

    @Test
    fun `black returns FF000000`() {
      assertThat(colorToHex(Color.Black)).isEqualTo("#FF000000")
    }

    @Test
    fun `transparent red returns 80FF0000`() {
      val transparentRed = Color(1f, 0f, 0f, 0.5f)
      // Alpha 0.5 = 0x80 (128/255 rounds to 0x80)
      val hex = colorToHex(transparentRed)
      assertWithMessage("transparent red hex")
        .that(hex)
        .matches("#[0-9A-F]{8}")
      // Verify alpha byte is approximately 0x80 (allow rounding)
      val alpha = hex.substring(1, 3).toInt(16)
      assertWithMessage("alpha channel").that(alpha).isIn(0x7E..0x81)
      // Verify RGB is FF0000
      assertWithMessage("rgb portion").that(hex.substring(3)).isEqualTo("FF0000")
    }
  }

  @Nested
  @DisplayName("parseHexToColor")
  inner class ParseHexToColorTests {

    @Test
    fun `parses 6-digit hex FF0000 as red`() {
      val color = parseHexToColor("#FF0000")
      assertThat(color).isNotNull()
      assertWithMessage("red").that(color!!.red).isWithin(channelTolerance).of(1f)
      assertWithMessage("green").that(color.green).isWithin(channelTolerance).of(0f)
      assertWithMessage("blue").that(color.blue).isWithin(channelTolerance).of(0f)
      assertWithMessage("alpha").that(color.alpha).isWithin(channelTolerance).of(1f)
    }

    @Test
    fun `parses 8-digit hex 80FF0000 as semi-transparent red`() {
      val color = parseHexToColor("#80FF0000")
      assertThat(color).isNotNull()
      assertWithMessage("red").that(color!!.red).isWithin(channelTolerance).of(1f)
      assertWithMessage("green").that(color.green).isWithin(channelTolerance).of(0f)
      assertWithMessage("blue").that(color.blue).isWithin(channelTolerance).of(0f)
      // 0x80 = 128, 128/255 ~= 0.502
      assertWithMessage("alpha").that(color.alpha).isWithin(0.01f).of(128f / 255f)
    }

    @Test
    fun `returns null for invalid string`() {
      assertThat(parseHexToColor("#invalid")).isNull()
    }

    @Test
    fun `returns null for bad hex chars`() {
      assertThat(parseHexToColor("#GG0000")).isNull()
    }

    @Test
    fun `returns null for empty string`() {
      assertThat(parseHexToColor("")).isNull()
    }

    @Test
    fun `returns null for 5-digit hex`() {
      assertThat(parseHexToColor("#12345")).isNull()
    }
  }

  @Nested
  @DisplayName("Round-trip tests")
  inner class RoundTripTests {

    @Test
    fun `parseHexToColor of colorToHex round-trips within tolerance`() {
      val colors = listOf(
        Color.Red,
        Color.Green,
        Color.Blue,
        Color.White,
        Color.Black,
        Color.Yellow,
        Color.Cyan,
        Color.Magenta,
        Color(0.2f, 0.4f, 0.6f, 1f),
        Color(0.8f, 0.3f, 0.1f, 0.7f),
        Color(0.5f, 0.5f, 0.5f, 1f),
        Color(0.1f, 0.9f, 0.5f, 0.3f),
      )

      for (original in colors) {
        val hex = colorToHex(original)
        val parsed = parseHexToColor(hex)
        assertWithMessage("parseHexToColor should return non-null for $hex")
          .that(parsed).isNotNull()
        assertWithMessage("red channel for $hex")
          .that(abs(parsed!!.red - original.red)).isLessThan(channelTolerance)
        assertWithMessage("green channel for $hex")
          .that(abs(parsed.green - original.green)).isLessThan(channelTolerance)
        assertWithMessage("blue channel for $hex")
          .that(abs(parsed.blue - original.blue)).isLessThan(channelTolerance)
        assertWithMessage("alpha channel for $hex")
          .that(abs(parsed.alpha - original.alpha)).isLessThan(channelTolerance)
      }
    }

    @Test
    fun `hslToColor of colorToHsl round-trips for primary colors`() {
      val colors = listOf(
        Color.Red to "red",
        Color.Green to "green",
        Color.Blue to "blue",
        Color.Yellow to "yellow",
        Color.Cyan to "cyan",
        Color.Magenta to "magenta",
        Color.White to "white",
        Color.Black to "black",
        Color(0.5f, 0.5f, 0.5f) to "gray",
      )

      for ((original, name) in colors) {
        val hsl = colorToHsl(original)
        val result = hslToColor(hsl)
        assertWithMessage("$name red channel")
          .that(abs(result.red - original.red)).isLessThan(channelTolerance)
        assertWithMessage("$name green channel")
          .that(abs(result.green - original.green)).isLessThan(channelTolerance)
        assertWithMessage("$name blue channel")
          .that(abs(result.blue - original.blue)).isLessThan(channelTolerance)
      }
    }
  }
}
