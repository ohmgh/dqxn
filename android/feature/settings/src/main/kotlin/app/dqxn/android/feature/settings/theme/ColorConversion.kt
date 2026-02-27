package app.dqxn.android.feature.settings.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Converts a [Color] to HSL representation.
 *
 * Handles achromatic grays (delta == 0 -> S=0, H=0) and hue wrap at 360.
 *
 * @return FloatArray of [hue (0-360), saturation (0-1), lightness (0-1)]
 */
internal fun colorToHsl(color: Color): FloatArray {
  val r = color.red
  val g = color.green
  val b = color.blue

  val max = maxOf(r, g, b)
  val min = minOf(r, g, b)
  val delta = max - min

  val lightness = (max + min) / 2f

  if (delta < 1e-6f) {
    // Achromatic — no saturation, hue is undefined (use 0)
    return floatArrayOf(0f, 0f, lightness)
  }

  val saturation =
    if (lightness <= 0.5f) {
      delta / (max + min)
    } else {
      delta / (2f - max - min)
    }

  val hue =
    when (max) {
      r -> {
        val segment = (g - b) / delta
        // Wrap negative hue into 0-360 range
        if (segment < 0f) (segment + 6f) * 60f else segment * 60f
      }
      g -> ((b - r) / delta + 2f) * 60f
      else -> ((r - g) / delta + 4f) * 60f
    }

  return floatArrayOf(hue, saturation, lightness)
}

/**
 * Converts HSL values back to a [Color].
 *
 * @param hsl FloatArray of [hue (0-360), saturation (0-1), lightness (0-1)]
 */
internal fun hslToColor(hsl: FloatArray): Color {
  val h = hsl[0]
  val s = hsl[1]
  val l = hsl[2]

  if (abs(s) < 1e-6f) {
    // Achromatic
    return Color(l, l, l)
  }

  val q = if (l < 0.5f) l * (1f + s) else l + s - l * s
  val p = 2f * l - q

  fun hueToRgb(t: Float): Float {
    val wrapped =
      when {
        t < 0f -> t + 1f
        t > 1f -> t - 1f
        else -> t
      }
    return when {
      wrapped < 1f / 6f -> p + (q - p) * 6f * wrapped
      wrapped < 1f / 2f -> q
      wrapped < 2f / 3f -> p + (q - p) * (2f / 3f - wrapped) * 6f
      else -> p
    }
  }

  val hNorm = h / 360f
  val r = hueToRgb(hNorm + 1f / 3f)
  val g = hueToRgb(hNorm)
  val b = hueToRgb(hNorm - 1f / 3f)

  return Color(r, g, b)
}

/**
 * Returns a copy of [color] with its HSL lightness replaced by [targetLightness].
 *
 * Preserves hue, saturation, and alpha. [targetLightness] is clamped to [0, 1].
 */
internal fun adjustLightness(color: Color, targetLightness: Float): Color {
  val hsl = colorToHsl(color)
  hsl[2] = targetLightness.coerceIn(0f, 1f)
  return hslToColor(hsl).copy(alpha = color.alpha)
}

/** Converts a [Color] to hex string in #AARRGGBB format. */
internal fun colorToHex(color: Color): String {
  val a = (color.alpha * 255f).roundToInt().coerceIn(0, 255)
  val r = (color.red * 255f).roundToInt().coerceIn(0, 255)
  val g = (color.green * 255f).roundToInt().coerceIn(0, 255)
  val b = (color.blue * 255f).roundToInt().coerceIn(0, 255)
  return "#%02X%02X%02X%02X".format(a, r, g, b)
}

/**
 * Parses a hex color string to [Color].
 *
 * Supports 6-digit (#RRGGBB) and 8-digit (#AARRGGBB) formats. Returns null for invalid input.
 */
internal fun parseHexToColor(hex: String): Color? {
  if (hex.isEmpty() || hex[0] != '#') return null

  val digits = hex.substring(1)
  // Validate hex characters
  if (!digits.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null

  return when (digits.length) {
    6 -> {
      // #RRGGBB — full alpha
      val value = digits.toLongOrNull(16) ?: return null
      val r = ((value shr 16) and 0xFF).toInt()
      val g = ((value shr 8) and 0xFF).toInt()
      val b = (value and 0xFF).toInt()
      Color(r, g, b, 255)
    }
    8 -> {
      // #AARRGGBB
      val value = digits.toLongOrNull(16) ?: return null
      val a = ((value shr 24) and 0xFF).toInt()
      val r = ((value shr 16) and 0xFF).toInt()
      val g = ((value shr 8) and 0xFF).toInt()
      val b = (value and 0xFF).toInt()
      Color(r, g, b, a)
    }
    else -> null
  }
}
