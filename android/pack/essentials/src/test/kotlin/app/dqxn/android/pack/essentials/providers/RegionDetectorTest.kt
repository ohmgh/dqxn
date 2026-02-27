package app.dqxn.android.pack.essentials.providers

import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import java.util.Locale
import java.util.TimeZone
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RegionDetectorTest {

  private var originalTimeZone: TimeZone? = null
  private var originalLocale: Locale? = null

  @BeforeEach
  fun setUp() {
    originalTimeZone = TimeZone.getDefault()
    originalLocale = Locale.getDefault()
  }

  @AfterEach
  fun tearDown() {
    originalTimeZone?.let { TimeZone.setDefault(it) }
    originalLocale?.let { Locale.setDefault(it) }
    unmockkAll()
  }

  @Test
  fun `America_New_York resolves to MPH via US`() {
    TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
    assertThat(RegionDetector.detectSpeedUnit()).isEqualTo(RegionDetector.SpeedUnit.MPH)
  }

  @Test
  fun `Europe_London resolves to MPH via GB`() {
    TimeZone.setDefault(TimeZone.getTimeZone("Europe/London"))
    assertThat(RegionDetector.detectSpeedUnit()).isEqualTo(RegionDetector.SpeedUnit.MPH)
  }

  @Test
  fun `Asia_Singapore resolves to KPH`() {
    TimeZone.setDefault(TimeZone.getTimeZone("Asia/Singapore"))
    assertThat(RegionDetector.detectSpeedUnit()).isEqualTo(RegionDetector.SpeedUnit.KPH)
  }

  @Test
  fun `Asia_Tokyo resolves to KPH`() {
    TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
    assertThat(RegionDetector.detectSpeedUnit()).isEqualTo(RegionDetector.SpeedUnit.KPH)
  }

  @Test
  fun `Europe_Paris resolves to KPH`() {
    TimeZone.setDefault(TimeZone.getTimeZone("Europe/Paris"))
    assertThat(RegionDetector.detectSpeedUnit()).isEqualTo(RegionDetector.SpeedUnit.KPH)
  }

  @Test
  fun `Australia_Sydney resolves to KPH`() {
    TimeZone.setDefault(TimeZone.getTimeZone("Australia/Sydney"))
    assertThat(RegionDetector.detectSpeedUnit()).isEqualTo(RegionDetector.SpeedUnit.KPH)
  }

  @Test
  fun `Africa_Monrovia resolves to MPH via LR`() {
    TimeZone.setDefault(TimeZone.getTimeZone("Africa/Monrovia"))
    assertThat(RegionDetector.detectSpeedUnit()).isEqualTo(RegionDetector.SpeedUnit.MPH)
  }

  @Test
  fun `falls back to locale when timezone lookup fails`() {
    TimeZone.setDefault(TimeZone.getTimeZone("Etc/UTC"))
    Locale.setDefault(Locale("en", "GB"))
    mockkObject(TimezoneCountryMap) {
      every { TimezoneCountryMap.getCountryCode("Etc/UTC") } returns null
      assertThat(RegionDetector.detectSpeedUnit()).isEqualTo(RegionDetector.SpeedUnit.MPH)
    }
  }

  @Test
  fun `falls back to locale for unknown timezone`() {
    TimeZone.setDefault(TimeZone.getTimeZone("Etc/Unknown"))
    Locale.setDefault(Locale("de", "DE"))
    mockkObject(TimezoneCountryMap) {
      every { TimezoneCountryMap.getCountryCode(any()) } returns null
      assertThat(RegionDetector.detectSpeedUnit()).isEqualTo(RegionDetector.SpeedUnit.KPH)
    }
  }

  @Test
  fun `falls back to US when both timezone and locale fail`() {
    TimeZone.setDefault(TimeZone.getTimeZone("Etc/Unknown"))
    Locale.setDefault(Locale("", ""))
    mockkObject(TimezoneCountryMap) {
      every { TimezoneCountryMap.getCountryCode(any()) } returns null
      assertThat(RegionDetector.detectSpeedUnit()).isEqualTo(RegionDetector.SpeedUnit.MPH)
    }
  }

  @Test
  fun `isMetric true for KPH countries`() {
    TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
    assertThat(RegionDetector.isMetric()).isTrue()
  }

  @Test
  fun `isMetric false for MPH countries`() {
    TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
    assertThat(RegionDetector.isMetric()).isFalse()
  }

  @Test
  fun `MPH_COUNTRIES has 24 entries`() {
    assertThat(RegionDetector.MPH_COUNTRIES).hasSize(24)
  }

  @Test
  fun `detectCountry uses timezone first`() {
    TimeZone.setDefault(TimeZone.getTimeZone("America/Chicago"))
    Locale.setDefault(Locale("ja", "JP"))
    assertThat(RegionDetector.detectCountry()).isEqualTo("US")
  }

  @Test
  fun `detectCountry timezone alias resolution`() {
    TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Apia"))
    assertThat(RegionDetector.detectCountry()).isEqualTo("WS")
  }
}
