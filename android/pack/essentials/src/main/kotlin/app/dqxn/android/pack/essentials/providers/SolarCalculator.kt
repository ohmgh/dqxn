package app.dqxn.android.pack.essentials.providers

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Solar position calculator using the Meeus/NOAA algorithm.
 *
 * Based on equations from "Astronomical Algorithms" by Jean Meeus (1991, 2nd ed. 1998),
 * as implemented in the NOAA Solar Calculator spreadsheet. Accurate to +/-1 minute
 * for locations between +/-72 degrees latitude.
 *
 * Reference: https://gml.noaa.gov/grad/solcalc/calcdetails.html
 */
internal object SolarCalculator {

  data class SolarResult(
    val sunriseEpochMillis: Long,
    val sunsetEpochMillis: Long,
    val solarNoonEpochMillis: Long,
    val isDaytime: Boolean,
  )

  fun calculate(
    latitude: Double,
    longitude: Double,
    date: LocalDate,
    zoneId: ZoneId,
  ): SolarResult {
    // Derive timezone offset from the calculation date, not current time.
    // This avoids DST mismatch when the date being calculated is in a different
    // DST period than the current moment.
    val dateZoned = date.atStartOfDay(zoneId)
    val timezoneOffset = dateZoned.offset.totalSeconds / 3600.0

    // Julian Century from J2000.0 epoch
    val jd = toJulianDay(date.year, date.monthValue, date.dayOfMonth)
    val T = (jd - 2451545.0) / 36525.0

    // Geometric mean longitude of the sun (degrees)
    val L0 = normalizeDegrees(280.46646 + 36000.76983 * T + 0.0003032 * T * T)

    // Mean anomaly of the sun (degrees)
    val M = normalizeDegrees(357.52911 + 35999.05029 * T - 0.0001537 * T * T)
    val mRad = Math.toRadians(M)

    // Eccentricity of Earth's orbit
    val e = 0.016708634 - 0.000042037 * T - 0.0000001267 * T * T

    // Equation of center (degrees)
    val C =
      (1.914602 - 0.004817 * T - 0.000014 * T * T) * sin(mRad) +
        (0.019993 - 0.000101 * T) * sin(2 * mRad) +
        0.000289 * sin(3 * mRad)

    // Sun true longitude (degrees)
    val sunTrueLon = L0 + C

    // Sun apparent longitude (degrees) -- corrected for nutation and aberration
    val omega = 125.04 - 1934.136 * T
    val omegaRad = Math.toRadians(omega)
    val sunAppLon = sunTrueLon - 0.00569 - 0.00478 * sin(omegaRad)
    val sunAppLonRad = Math.toRadians(sunAppLon)

    // Mean obliquity of the ecliptic (degrees)
    val meanObliquity =
      23.439291 - 0.013004 * T - 0.00000164 * T * T + 0.000000504 * T * T * T

    // Corrected obliquity (degrees)
    val obliquity = meanObliquity + 0.00256 * cos(omegaRad)
    val obliquityRad = Math.toRadians(obliquity)

    // Solar declination (radians)
    val decl = asin(sin(obliquityRad) * sin(sunAppLonRad))

    // Equation of time (minutes)
    val y = tan(obliquityRad / 2).let { it * it }
    val l0Rad = Math.toRadians(L0)
    val eqTime =
      229.18 *
        (y * sin(2 * l0Rad) -
          2 * e * sin(mRad) +
          4 * e * y * sin(mRad) * cos(2 * l0Rad) -
          0.5 * y * y * sin(4 * l0Rad) -
          1.25 * e * e * sin(2 * mRad))

    // Hour angle for sunrise/sunset (degrees)
    val latRad = Math.toRadians(latitude)
    val zenith = Math.toRadians(90.833) // Official sunrise/sunset zenith
    val rawCosHa =
      cos(zenith) / (cos(latRad) * cos(decl)) - tan(latRad) * tan(decl)

    // Polar edge case: cosHa outside [-1, 1] means no sunrise or no sunset
    val polarNoSunrise = rawCosHa > 1.0 // polar night
    val polarNoSunset = rawCosHa < -1.0 // midnight sun

    val cosHa = rawCosHa.coerceIn(-1.0, 1.0)
    val ha = Math.toDegrees(acos(cosHa))

    // Solar noon, sunrise, sunset (minutes from midnight local time)
    val solarNoonMin = 720 - 4 * longitude - eqTime + timezoneOffset * 60

    val sunriseEpoch: Long
    val sunsetEpoch: Long

    if (polarNoSunrise || polarNoSunset) {
      sunriseEpoch =
        if (polarNoSunrise) Long.MIN_VALUE
        else minutesToLocalTime(
          (solarNoonMin - ha * 4).coerceIn(0.0, 1439.0),
          date,
          zoneId,
        )
      sunsetEpoch =
        if (polarNoSunset) Long.MIN_VALUE
        else minutesToLocalTime(
          (solarNoonMin + ha * 4).coerceIn(0.0, 1439.0),
          date,
          zoneId,
        )
    } else {
      sunriseEpoch = minutesToLocalTime(
        (solarNoonMin - ha * 4).coerceIn(0.0, 1439.0),
        date,
        zoneId,
      )
      sunsetEpoch = minutesToLocalTime(
        (solarNoonMin + ha * 4).coerceIn(0.0, 1439.0),
        date,
        zoneId,
      )
    }

    val noonEpoch = minutesToLocalTime(
      solarNoonMin.coerceIn(0.0, 1439.0),
      date,
      zoneId,
    )

    // Determine isDaytime from current time relative to sunrise/sunset
    val nowEpoch = ZonedDateTime.now(zoneId).toInstant().toEpochMilli()
    val isDaytime = when {
      polarNoSunrise -> false // polar night -- always dark
      polarNoSunset -> true // midnight sun -- always light
      sunriseEpoch != Long.MIN_VALUE && sunsetEpoch != Long.MIN_VALUE ->
        nowEpoch in sunriseEpoch..sunsetEpoch
      else -> false
    }

    return SolarResult(
      sunriseEpochMillis = sunriseEpoch,
      sunsetEpochMillis = sunsetEpoch,
      solarNoonEpochMillis = noonEpoch,
      isDaytime = isDaytime,
    )
  }

  /**
   * Convert UTC minutes-of-day to epoch millis in the given timezone.
   * Guards against negative values and values >= 1440.
   */
  internal fun minutesToLocalTime(
    minutes: Double,
    date: LocalDate,
    zone: ZoneId,
  ): Long {
    val floored = minutes.toInt().coerceIn(0, 1439)
    val hour = floored / 60
    val minute = floored % 60
    return date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
  }

  /**
   * Convert calendar date to Julian Day number.
   * Valid for Gregorian calendar dates (1582-10-15 onward).
   */
  internal fun toJulianDay(year: Int, month: Int, day: Int): Double {
    var y = year
    var m = month
    if (m <= 2) {
      y -= 1
      m += 12
    }
    val A = y / 100
    val B = 2 - A + A / 4
    return (365.25 * (y + 4716)).toInt() +
      (30.6001 * (m + 1)).toInt() +
      day + B - 1524.5
  }

  private fun normalizeDegrees(degrees: Double): Double {
    var result = degrees % 360.0
    if (result < 0) result += 360.0
    return result
  }
}
