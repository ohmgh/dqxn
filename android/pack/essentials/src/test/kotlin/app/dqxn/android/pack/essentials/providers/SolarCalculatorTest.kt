package app.dqxn.android.pack.essentials.providers

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.jupiter.api.Test

class SolarCalculatorTest {

  companion object {
    private const val LONDON_LAT = 51.5074
    private const val LONDON_LON = -0.1278
    private const val SINGAPORE_LAT = 1.3521
    private const val SINGAPORE_LON = 103.8198
    private const val TROMSO_LAT = 69.65
    private const val TROMSO_LON = 18.95
    private val UTC: ZoneId = ZoneOffset.UTC
  }

  @Test
  fun `toJulianDay at J2000 epoch returns correct value`() {
    val jd = SolarCalculator.toJulianDay(2000, 1, 1)
    assertThat(jd).isWithin(0.01).of(2451544.5)
  }

  @Test
  fun `toJulianDay for known 2025 date`() {
    val jd = SolarCalculator.toJulianDay(2025, 6, 21)
    assertThat(jd).isWithin(1.0).of(2460846.5)
  }

  @Test
  fun `London summer solstice 2025 sunrise within 5 minutes of NOAA`() {
    val date = LocalDate.of(2025, 6, 21)
    val result = SolarCalculator.calculate(LONDON_LAT, LONDON_LON, date, UTC)
    assertThat(result.sunriseEpochMillis).isNotEqualTo(Long.MIN_VALUE)
    assertThat(result.sunsetEpochMillis).isNotEqualTo(Long.MIN_VALUE)
    val sunriseMin = epochToMinutesOfDayUtc(result.sunriseEpochMillis)
    val sunsetMin = epochToMinutesOfDayUtc(result.sunsetEpochMillis)
    // NOAA: sunrise ~03:43 UTC, sunset ~20:21 UTC (London summer solstice in UTC zone)
    assertThat(sunriseMin.toDouble()).isWithin(5.0).of(223.0)
    assertThat(sunsetMin.toDouble()).isWithin(5.0).of(1221.0)
  }

  @Test
  fun `London winter solstice 2025 sunrise within 2 minutes of NOAA`() {
    val date = LocalDate.of(2025, 12, 21)
    val result = SolarCalculator.calculate(LONDON_LAT, LONDON_LON, date, UTC)
    assertThat(result.sunriseEpochMillis).isNotEqualTo(Long.MIN_VALUE)
    assertThat(result.sunsetEpochMillis).isNotEqualTo(Long.MIN_VALUE)
    val sunriseMin = epochToMinutesOfDayUtc(result.sunriseEpochMillis)
    val sunsetMin = epochToMinutesOfDayUtc(result.sunsetEpochMillis)
    assertThat(sunriseMin.toDouble()).isWithin(2.0).of(483.0)
    assertThat(sunsetMin.toDouble()).isWithin(2.0).of(951.0)
  }

  @Test
  fun `Singapore equinox 2025 sunset within 5 minutes of NOAA`() {
    val date = LocalDate.of(2025, 3, 20)
    val result = SolarCalculator.calculate(SINGAPORE_LAT, SINGAPORE_LON, date, UTC)
    assertThat(result.sunsetEpochMillis).isNotEqualTo(Long.MIN_VALUE)
    val sunsetMin = epochToMinutesOfDayUtc(result.sunsetEpochMillis)
    assertThat(sunsetMin.toDouble()).isWithin(5.0).of(671.0)
  }

  @Test
  fun `equatorial location always has sunrise and sunset`() {
    val dates = listOf(
      LocalDate.of(2025, 3, 20),
      LocalDate.of(2025, 6, 21),
      LocalDate.of(2025, 12, 21),
    )
    for (date in dates) {
      val result = SolarCalculator.calculate(0.0, 0.0, date, UTC)
      assertThat(result.sunriseEpochMillis).isNotEqualTo(Long.MIN_VALUE)
      assertThat(result.sunsetEpochMillis).isNotEqualTo(Long.MIN_VALUE)
    }
  }

  @Test
  fun `minutesToLocalTime at 0 minutes returns midnight`() {
    val date = LocalDate.of(2025, 6, 21)
    val result = SolarCalculator.minutesToLocalTime(0.0, date, UTC)
    assertThat(epochToMinutesOfDayUtc(result)).isEqualTo(0)
  }

  @Test
  fun `minutesToLocalTime at 1439 minutes returns 23h59`() {
    val date = LocalDate.of(2025, 6, 21)
    val result = SolarCalculator.minutesToLocalTime(1439.0, date, UTC)
    assertThat(epochToMinutesOfDayUtc(result)).isEqualTo(1439)
  }

  @Test
  fun `minutesToLocalTime fractional minutes truncates to whole`() {
    val date = LocalDate.of(2025, 6, 21)
    val result = SolarCalculator.minutesToLocalTime(720.7, date, UTC)
    assertThat(epochToMinutesOfDayUtc(result)).isEqualTo(720)
  }

  @Test
  fun `minutesToLocalTime negative clamps to 0`() {
    val date = LocalDate.of(2025, 6, 21)
    val result = SolarCalculator.minutesToLocalTime(-10.0, date, UTC)
    assertThat(epochToMinutesOfDayUtc(result)).isEqualTo(0)
  }

  @Test
  fun `Tromso summer solstice midnight sun`() {
    val date = LocalDate.of(2025, 6, 21)
    val result = SolarCalculator.calculate(TROMSO_LAT, TROMSO_LON, date, UTC)
    assertThat(result.isDaytime).isTrue()
    assertThat(result.solarNoonEpochMillis).isNotEqualTo(Long.MIN_VALUE)
  }

  @Test
  fun `Tromso winter solstice polar night`() {
    val date = LocalDate.of(2025, 12, 21)
    val result = SolarCalculator.calculate(TROMSO_LAT, TROMSO_LON, date, UTC)
    assertThat(result.isDaytime).isFalse()
    assertThat(result.solarNoonEpochMillis).isNotEqualTo(Long.MIN_VALUE)
  }

  @Test
  fun `sunrise before noon before sunset`() {
    val date = LocalDate.of(2025, 6, 21)
    val result = SolarCalculator.calculate(LONDON_LAT, LONDON_LON, date, UTC)
    assertThat(result.sunriseEpochMillis).isLessThan(result.solarNoonEpochMillis)
    assertThat(result.solarNoonEpochMillis).isLessThan(result.sunsetEpochMillis)
  }

  private fun epochToMinutesOfDayUtc(epochMillis: Long): Int {
    val instant = java.time.Instant.ofEpochMilli(epochMillis)
    val utcTime = instant.atZone(UTC).toLocalTime()
    return utcTime.hour * 60 + utcTime.minute
  }
}
