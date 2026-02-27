package app.dqxn.android.pack.essentials.providers

import java.util.Locale
import java.util.TimeZone

/**
 * Timezone-first MPH country detection for speed unit display.
 *
 * 3-step fallback chain:
 * 1. Timezone ID -> country code via [TimezoneCountryMap]
 * 2. [Locale.getDefault] country
 * 3. Fallback to "US"
 *
 * Used by speedometer, speed limit circle, and speed limit rectangle widgets when `speedUnit=AUTO`.
 */
internal object RegionDetector {

  /** Speed unit for display in speed-related widgets. */
  enum class SpeedUnit {
    KPH,
    MPH,
  }

  /**
   * Countries/territories that use miles per hour.
   *
   * Includes US + territories (AS, VI, GU, MP, PR), UK, Myanmar, Liberia, and Caribbean island
   * nations that drive on the left but use mph.
   */
  val MPH_COUNTRIES: Set<String> =
    setOf(
      "US",
      "GB",
      "MM",
      "LR",
      // US territories
      "AS",
      "VI",
      "GU",
      "MP",
      "PR",
      // Other mph users
      "WS",
      "BS",
      "BZ",
      "KY",
      "FK",
      // Caribbean
      "AG",
      "DM",
      "GD",
      "KN",
      "LC",
      "VC",
      "MS",
      "TC",
      "VG",
      "AI",
    )

  /**
   * Detect the speed unit to display based on the device's timezone and locale.
   *
   * Returns [SpeedUnit.MPH] if the detected country is in [MPH_COUNTRIES], otherwise
   * [SpeedUnit.KPH].
   */
  fun detectSpeedUnit(): SpeedUnit {
    val country = detectCountry()
    return if (country in MPH_COUNTRIES) SpeedUnit.MPH else SpeedUnit.KPH
  }

  /** Convenience: `true` if the detected region uses metric speed (KPH). */
  fun isMetric(): Boolean = detectSpeedUnit() == SpeedUnit.KPH

  /**
   * Detect the ISO 3166-1 alpha-2 country code via 3-step fallback:
   * 1. Timezone -> country via [TimezoneCountryMap]
   * 2. Locale default country
   * 3. "US" fallback
   */
  internal fun detectCountry(): String {
    // Step 1: timezone -> country
    val timezoneId = TimeZone.getDefault().id
    val tzCountry = TimezoneCountryMap.getCountryCode(timezoneId)
    if (tzCountry != null) return tzCountry

    // Step 2: locale country
    val localeCountry = Locale.getDefault().country
    if (localeCountry.isNotBlank()) return localeCountry

    // Step 3: fallback
    return "US"
  }
}
