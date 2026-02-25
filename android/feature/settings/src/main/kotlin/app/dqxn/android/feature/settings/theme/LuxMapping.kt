package app.dqxn.android.feature.settings.theme

/** Maximum lux value for the mapping range. */
internal const val MAX_LUX: Float = 10_000f

/** Minimum lux value to avoid log(0). */
internal const val MIN_LUX: Float = 1f

/**
 * Maps a lux value to a normalized position [0, 1] using logarithmic scaling.
 *
 * @param lux Ambient light level in lux.
 * @return Position in [0, 1] range.
 */
internal fun luxToPosition(lux: Float): Float {
  // Stub — returns dummy value
  return 0f
}

/**
 * Maps a normalized position [0, 1] back to a lux value using inverse logarithmic scaling.
 *
 * @param position Slider position in [0, 1] range.
 * @return Lux value in [MIN_LUX, MAX_LUX] range.
 */
internal fun positionToLux(position: Float): Float {
  // Stub — returns dummy value
  return 0f
}
