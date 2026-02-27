package app.dqxn.android.app.accessibility

import androidx.compose.ui.graphics.Color

/**
 * WCAG 2.1 contrast ratio utilities for programmatic accessibility auditing.
 *
 * Implements the relative luminance and contrast ratio algorithms per
 * [WCAG 2.1 specification](https://www.w3.org/TR/WCAG21/#dfn-relative-luminance).
 *
 * Test utility only -- lives in test source set.
 */
public object WcagContrastChecker {

  /** WCAG AA minimum contrast ratio for normal text (< 18pt / < 14pt bold). */
  public const val AA_NORMAL_TEXT: Double = 4.5

  /** WCAG AA minimum contrast ratio for large text (>= 18pt / >= 14pt bold). */
  public const val AA_LARGE_TEXT: Double = 3.0

  /**
   * Linearizes a single sRGB channel value per WCAG 2.1 spec.
   *
   * If [channel] <= 0.04045, returns `channel / 12.92`. Otherwise returns `((channel + 0.055) /
   * 1.055) ^ 2.4`.
   */
  public fun linearize(channel: Float): Double {
    val c = channel.toDouble()
    return if (c <= 0.04045) {
      c / 12.92
    } else {
      Math.pow((c + 0.055) / 1.055, 2.4)
    }
  }

  /**
   * Computes relative luminance of a [Color] per WCAG 2.1.
   *
   * Formula: `L = 0.2126 * R_lin + 0.7152 * G_lin + 0.0722 * B_lin` where R_lin, G_lin, B_lin are
   * the linearized sRGB channel values.
   */
  public fun Color.relativeLuminance(): Double {
    val rLin = linearize(red)
    val gLin = linearize(green)
    val bLin = linearize(blue)
    return 0.2126 * rLin + 0.7152 * gLin + 0.0722 * bLin
  }

  /**
   * Computes WCAG 2.1 contrast ratio between [foreground] and [background].
   *
   * Formula: `(L_lighter + 0.05) / (L_darker + 0.05)` where L values are relative luminances.
   * Returns a value in the range [1.0, 21.0].
   */
  public fun contrastRatio(foreground: Color, background: Color): Double {
    val fgLum = foreground.relativeLuminance()
    val bgLum = background.relativeLuminance()
    val lighter = maxOf(fgLum, bgLum)
    val darker = minOf(fgLum, bgLum)
    return (lighter + 0.05) / (darker + 0.05)
  }

  /**
   * Checks whether the contrast ratio between [foreground] and [background] meets WCAG AA.
   *
   * @param isLargeText If true, uses the 3.0:1 threshold (large text). Otherwise uses 4.5:1 (normal
   *   text).
   */
  public fun meetsAA(
    foreground: Color,
    background: Color,
    isLargeText: Boolean = false,
  ): Boolean {
    val threshold = if (isLargeText) AA_LARGE_TEXT else AA_NORMAL_TEXT
    return contrastRatio(foreground, background) >= threshold
  }

  /**
   * Parses a hex color string to a Compose [Color].
   *
   * Supports `#RRGGBB` (6-char) and `#AARRGGBB` (8-char) formats.
   */
  public fun parseHexColor(hex: String): Color {
    val stripped = hex.removePrefix("#")
    val colorLong =
      when (stripped.length) {
        6 -> {
          // #RRGGBB -> full alpha
          val rgb = stripped.toLong(16)
          0xFF000000L or rgb
        }
        8 -> {
          // #AARRGGBB
          stripped.toLong(16)
        }
        else -> throw IllegalArgumentException("Invalid color format: $hex")
      }
    return Color(colorLong.toInt())
  }

  /**
   * Alpha-composites [foreground] over [background] per channel.
   *
   * Formula per channel: `result = fg * fg.alpha + bg * (1 - fg.alpha)`
   *
   * Returns an opaque color representing the visual result.
   */
  public fun alphaComposite(foreground: Color, background: Color): Color {
    val a = foreground.alpha
    return Color(
      red = foreground.red * a + background.red * (1f - a),
      green = foreground.green * a + background.green * (1f - a),
      blue = foreground.blue * a + background.blue * (1f - a),
      alpha = 1f,
    )
  }
}
