package app.dqxn.android.pack.essentials.widgets.speedlimit

import java.util.Locale
import java.util.TimeZone

/**
 * Timezone-based region detection for speed unit selection.
 *
 * Packs cannot depend on `:data` or `:core:*`, so this is a self-contained utility within the
 * essentials pack. Mirrors the old `core:widget-primitives:RegionDetector` with timezone-first
 * fallback to locale to "US" default.
 */
internal object RegionDetector {

  private const val FALLBACK_REGION = "US"
  private const val JAPAN_REGION = "JP"

  /** Countries using miles per hour. */
  val MPH_COUNTRIES: Set<String> =
    setOf(
      "US",
      "GB",
      "VG",
      "VI",
      "AS",
      "GU",
      "PR", // US territories, UK
      "BS",
      "BZ",
      "AG",
      "WS",
      "KN",
      "LC",
      "GD",
      "VC", // Caribbean
      "GI",
      "SH", // British overseas territories
    )

  /** Detect user's region code from timezone, falling back to locale, then "US". */
  fun detectRegion(): String {
    TIMEZONE_MAP[TimeZone.getDefault().id]?.let {
      return it
    }
    Locale.getDefault()
      .country
      .takeIf { it.isNotEmpty() }
      ?.let {
        return it
      }
    return FALLBACK_REGION
  }

  /** Whether the detected region uses MPH. */
  fun usesMph(): Boolean = detectRegion() in MPH_COUNTRIES

  /** Whether the detected region is Japan (blue speed limit digits). */
  fun isJapan(): Boolean = detectRegion() == JAPAN_REGION

  /** Resolves a [SpeedUnit] considering AUTO detection. Returns true if imperial (MPH). */
  fun resolveSpeedUnit(unit: SpeedUnit): Boolean =
    when (unit) {
      SpeedUnit.KPH -> false
      SpeedUnit.MPH -> true
      SpeedUnit.AUTO -> usesMph()
    }

  /** Resolves a [DigitColor] considering AUTO detection for Japan. */
  fun resolveUseBlueDigits(color: DigitColor): Boolean =
    when (color) {
      DigitColor.AUTO -> isJapan()
      DigitColor.BLACK -> false
      DigitColor.BLUE -> true
    }

  private val TIMEZONE_MAP: Map<String, String> =
    mapOf(
      "America/New_York" to "US",
      "America/Chicago" to "US",
      "America/Denver" to "US",
      "America/Los_Angeles" to "US",
      "America/Anchorage" to "US",
      "Pacific/Honolulu" to "US",
      "Europe/London" to "GB",
      "Europe/Dublin" to "IE",
      "Asia/Singapore" to "SG",
      "Asia/Tokyo" to "JP",
      "Asia/Kolkata" to "IN",
      "Australia/Sydney" to "AU",
      "Australia/Melbourne" to "AU",
      "Europe/Berlin" to "DE",
      "Europe/Paris" to "FR",
      "Europe/Rome" to "IT",
      "Europe/Madrid" to "ES",
      "Europe/Amsterdam" to "NL",
      "Europe/Zurich" to "CH",
      "Europe/Vienna" to "AT",
      "Europe/Brussels" to "BE",
      "Europe/Stockholm" to "SE",
      "Europe/Oslo" to "NO",
      "Europe/Helsinki" to "FI",
      "Europe/Copenhagen" to "DK",
      "Europe/Warsaw" to "PL",
      "Europe/Prague" to "CZ",
      "Europe/Budapest" to "HU",
      "Asia/Seoul" to "KR",
      "Asia/Shanghai" to "CN",
      "Asia/Hong_Kong" to "HK",
      "Asia/Taipei" to "TW",
      "Asia/Bangkok" to "TH",
      "Asia/Jakarta" to "ID",
      "Asia/Manila" to "PH",
      "Asia/Kuala_Lumpur" to "MY",
      "Pacific/Auckland" to "NZ",
      "America/Toronto" to "CA",
      "America/Vancouver" to "CA",
      "America/Mexico_City" to "MX",
      "America/Sao_Paulo" to "BR",
      "America/Argentina/Buenos_Aires" to "AR",
      "America/Bogota" to "CO",
      "America/Lima" to "PE",
      "America/Santiago" to "CL",
      "Africa/Johannesburg" to "ZA",
      "Africa/Lagos" to "NG",
      "Africa/Cairo" to "EG",
      "Asia/Dubai" to "AE",
      "Asia/Riyadh" to "SA",
      "Asia/Istanbul" to "TR",
      "Europe/Moscow" to "RU",
    )
}
