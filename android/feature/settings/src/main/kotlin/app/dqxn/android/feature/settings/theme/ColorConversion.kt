package app.dqxn.android.feature.settings.theme

import androidx.compose.ui.graphics.Color

/**
 * Converts a [Color] to HSL representation.
 *
 * @return FloatArray of [hue (0-360), saturation (0-1), lightness (0-1)]
 */
internal fun colorToHsl(color: Color): FloatArray {
  // Stub — returns dummy values
  return floatArrayOf(0f, 0f, 0f)
}

/**
 * Converts HSL values back to a [Color].
 *
 * @param hsl FloatArray of [hue (0-360), saturation (0-1), lightness (0-1)]
 */
internal fun hslToColor(hsl: FloatArray): Color {
  // Stub — returns dummy value
  return Color.Unspecified
}

/**
 * Converts a [Color] to hex string in #AARRGGBB format.
 */
internal fun colorToHex(color: Color): String {
  // Stub — returns dummy value
  return ""
}

/**
 * Parses a hex color string to [Color].
 *
 * Supports 6-digit (#RRGGBB) and 8-digit (#AARRGGBB) formats.
 * Returns null for invalid input.
 */
internal fun parseHexToColor(hex: String): Color? {
  // Stub — returns dummy value
  return null
}
