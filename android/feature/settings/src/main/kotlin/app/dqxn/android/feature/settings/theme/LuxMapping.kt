package app.dqxn.android.feature.settings.theme

import kotlin.math.log10
import kotlin.math.pow

/** Maximum lux value for the mapping range. */
internal const val MAX_LUX: Float = 10_000f

/** Minimum lux value to avoid log(0). */
internal const val MIN_LUX: Float = 1f

/** log10(MAX_LUX) pre-computed for division in both directions. */
private val LOG_MAX_LUX: Float = log10(MAX_LUX)

/**
 * Maps a lux value to a normalized position [0, 1] using logarithmic scaling.
 *
 * Formula: position = log10(max(lux, MIN_LUX)) / log10(MAX_LUX)
 *
 * Values at or below [MIN_LUX] map to 0. Values at [MAX_LUX] map to 1.
 * Negative and sub-MIN_LUX values are clamped.
 *
 * @param lux Ambient light level in lux.
 * @return Position in [0, 1] range.
 */
internal fun luxToPosition(lux: Float): Float {
  val clamped = lux.coerceIn(MIN_LUX, MAX_LUX)
  return (log10(clamped) / LOG_MAX_LUX).coerceIn(0f, 1f)
}

/**
 * Maps a normalized position [0, 1] back to a lux value using inverse logarithmic scaling.
 *
 * Formula: lux = 10^(position * log10(MAX_LUX))
 *
 * Position 0 yields [MIN_LUX], position 1 yields [MAX_LUX].
 * Out-of-range positions are clamped.
 *
 * @param position Slider position in [0, 1] range.
 * @return Lux value in [MIN_LUX, MAX_LUX] range.
 */
internal fun positionToLux(position: Float): Float {
  val clamped = position.coerceIn(0f, 1f)
  val lux = 10f.pow(clamped * LOG_MAX_LUX)
  return lux.coerceIn(MIN_LUX, MAX_LUX)
}
