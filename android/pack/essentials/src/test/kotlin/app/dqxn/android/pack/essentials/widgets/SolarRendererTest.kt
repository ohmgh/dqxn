package app.dqxn.android.pack.essentials.widgets

import app.dqxn.android.pack.essentials.snapshots.SolarSnapshot
import app.dqxn.android.pack.essentials.widgets.solar.SolarRenderer
import app.dqxn.android.sdk.contracts.testing.WidgetRendererContractTest
import app.dqxn.android.sdk.contracts.widget.WidgetData
import app.dqxn.android.sdk.contracts.widget.WidgetRenderer
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SolarRendererTest : WidgetRendererContractTest() {

  override fun createRenderer(): WidgetRenderer = SolarRenderer()

  override fun createTestWidgetData(): WidgetData =
    WidgetData.Empty.withSlot(
      SolarSnapshot::class,
      SolarSnapshot(
        sunriseEpochMillis = 1700000000000L,
        sunsetEpochMillis = 1700040000000L,
        solarNoonEpochMillis = 1700020000000L,
        isDaytime = true,
        sourceMode = "timezone",
        timestamp = 1L,
      ),
    )

  @Test
  fun `computeSunPosition returns 0 at sunrise`() {
    val sunrise = 1700000000000L
    val sunset = 1700040000000L
    val result = SolarRenderer.computeSunPosition(sunrise, sunrise, sunset)
    assertThat(result).isWithin(0.001f).of(0.0f)
  }

  @Test
  fun `computeSunPosition returns 0_5 at solar noon`() {
    val sunrise = 1700000000000L
    val sunset = 1700040000000L
    val noon = (sunrise + sunset) / 2
    val result = SolarRenderer.computeSunPosition(noon, sunrise, sunset)
    assertThat(result).isWithin(0.001f).of(0.5f)
  }

  @Test
  fun `computeSunPosition returns 1 at sunset`() {
    val sunrise = 1700000000000L
    val sunset = 1700040000000L
    val result = SolarRenderer.computeSunPosition(sunset, sunrise, sunset)
    assertThat(result).isWithin(0.001f).of(1.0f)
  }

  @Test
  fun `computeSunPosition clamps below sunrise to 0`() {
    val sunrise = 1700000000000L
    val sunset = 1700040000000L
    val beforeSunrise = sunrise - 3600000L
    val result = SolarRenderer.computeSunPosition(beforeSunrise, sunrise, sunset)
    assertThat(result).isWithin(0.001f).of(0.0f)
  }

  @Test
  fun `computeSunPosition clamps above sunset to 1`() {
    val sunrise = 1700000000000L
    val sunset = 1700040000000L
    val afterSunset = sunset + 3600000L
    val result = SolarRenderer.computeSunPosition(afterSunset, sunrise, sunset)
    assertThat(result).isWithin(0.001f).of(1.0f)
  }

  @Test
  fun `computeSunPosition returns 0 for zero-length day`() {
    val time = 1700000000000L
    val result = SolarRenderer.computeSunPosition(time, time, time)
    assertThat(result).isWithin(0.001f).of(0.0f)
  }

  @Test
  fun `computeArcAngle at position 0 returns start angle`() {
    val result = SolarRenderer.computeArcAngle(0.0f, 90f, 360f)
    assertThat(result).isWithin(0.001f).of(90f)
  }

  @Test
  fun `computeArcAngle at position 1 returns start plus sweep`() {
    val result = SolarRenderer.computeArcAngle(1.0f, 90f, 360f)
    assertThat(result).isWithin(0.001f).of(450f)
  }

  @Test
  fun `computeArcAngle at position 0_5 returns midpoint`() {
    val result = SolarRenderer.computeArcAngle(0.5f, 90f, 360f)
    assertThat(result).isWithin(0.001f).of(270f)
  }

  @Test
  fun `computeArcAngle with custom start and sweep`() {
    val result = SolarRenderer.computeArcAngle(0.25f, 0f, 180f)
    assertThat(result).isWithin(0.001f).of(45f)
  }

  @Test
  fun `formatCountdown with hours and minutes`() {
    val result = SolarRenderer.formatCountdown(8100000L)
    assertThat(result).isEqualTo("2h 15m")
  }

  @Test
  fun `formatCountdown with 2 hours 0 minutes`() {
    val result = SolarRenderer.formatCountdown(7200000L)
    assertThat(result).isEqualTo("2h 0m")
  }

  @Test
  fun `formatCountdown with minutes only`() {
    val result = SolarRenderer.formatCountdown(2700000L)
    assertThat(result).isEqualTo("45m")
  }

  @Test
  fun `formatCountdown with zero`() {
    val result = SolarRenderer.formatCountdown(0L)
    assertThat(result).isEqualTo("0m")
  }

  @Test
  fun `formatCountdown with 1 minute`() {
    val result = SolarRenderer.formatCountdown(60000L)
    assertThat(result).isEqualTo("1m")
  }

  @Test
  fun `formatCountdown clamps negative to 0m`() {
    val result = SolarRenderer.formatCountdown(-5000L)
    assertThat(result).isEqualTo("0m")
  }

  @Test
  fun `accessibility description with no data returns no data available`() {
    val renderer = SolarRenderer()
    val desc = renderer.accessibilityDescription(WidgetData.Empty)
    assertThat(desc).contains("no data")
  }

  @Test
  fun `accessibility description with solar data includes sunrise or sunset`() {
    val renderer = SolarRenderer()
    val data = createTestWidgetData()
    val desc = renderer.accessibilityDescription(data)
    assertThat(desc).containsMatch("sunrise|sunset|Solar")
  }

  @Test
  fun `compatibleSnapshots contains SolarSnapshot`() {
    val renderer = SolarRenderer()
    assertThat(renderer.compatibleSnapshots).containsExactly(SolarSnapshot::class)
  }

  @Test
  fun `supportsTap is false`() {
    val renderer = SolarRenderer()
    assertThat(renderer.supportsTap).isFalse()
  }
}
