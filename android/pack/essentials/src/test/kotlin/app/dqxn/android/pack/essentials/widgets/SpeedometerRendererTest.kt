package app.dqxn.android.pack.essentials.widgets

import app.dqxn.android.pack.essentials.snapshots.AccelerationSnapshot
import app.dqxn.android.pack.essentials.snapshots.SpeedLimitSnapshot
import app.dqxn.android.pack.essentials.snapshots.SpeedSnapshot
import app.dqxn.android.pack.essentials.widgets.speedometer.SpeedometerRenderer
import app.dqxn.android.sdk.contracts.testing.WidgetRendererContractTest
import app.dqxn.android.sdk.contracts.widget.WidgetData
import app.dqxn.android.sdk.contracts.widget.WidgetRenderer
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Speedometer widget tests. Extends [WidgetRendererContractTest] (JUnit4) for 14 inherited contract
 * assertions plus 13 widget-specific tests covering gauge max, arc angle, acceleration segments,
 * accessibility, and multi-slot independence.
 */
class SpeedometerRendererTest : WidgetRendererContractTest() {

  override fun createRenderer(): WidgetRenderer = SpeedometerRenderer()

  override fun createTestWidgetData(): WidgetData =
    WidgetData.Empty
      .withSlot(
        SpeedSnapshot::class,
        SpeedSnapshot(speedMps = 16.67f, accuracy = 1.0f, timestamp = 1000L),
      )
      .withSlot(
        AccelerationSnapshot::class,
        AccelerationSnapshot(acceleration = 2.5f, lateralAcceleration = null, timestamp = 1000L),
      )
      .withSlot(
        SpeedLimitSnapshot::class,
        SpeedLimitSnapshot(speedLimitKph = 60f, source = "user", timestamp = 1000L),
      )

  // --- Gauge max auto-scaling ---

  @Test
  fun `computeGaugeMax at 0 returns 60`() {
    assertThat(SpeedometerRenderer.computeGaugeMax(0f)).isEqualTo(60f)
  }

  @Test
  fun `computeGaugeMax at 60 returns 120`() {
    assertThat(SpeedometerRenderer.computeGaugeMax(60f)).isEqualTo(120f)
  }

  @Test
  fun `computeGaugeMax at 140 returns 300`() {
    assertThat(SpeedometerRenderer.computeGaugeMax(140f)).isEqualTo(300f)
  }

  @Test
  fun `computeGaugeMax at 250 returns 400`() {
    assertThat(SpeedometerRenderer.computeGaugeMax(250f)).isEqualTo(400f)
  }

  // --- Arc angle computation ---

  @Test
  fun `computeArcAngle at 0 speed returns 0`() {
    assertThat(SpeedometerRenderer.computeArcAngle(0f, 120f)).isEqualTo(0f)
  }

  @Test
  fun `computeArcAngle at half speed returns 135`() {
    assertThat(SpeedometerRenderer.computeArcAngle(60f, 120f)).isEqualTo(135f)
  }

  @Test
  fun `computeArcAngle at full speed returns 270`() {
    assertThat(SpeedometerRenderer.computeArcAngle(120f, 120f)).isEqualTo(270f)
  }

  // --- Acceleration segments ---

  @Test
  fun `computeAccelerationSegments at 0 returns 0`() {
    assertThat(SpeedometerRenderer.computeAccelerationSegments(0f, 10f)).isEqualTo(0)
  }

  @Test
  fun `computeAccelerationSegments at half returns 6`() {
    assertThat(SpeedometerRenderer.computeAccelerationSegments(5f, 10f)).isEqualTo(6)
  }

  @Test
  fun `computeAccelerationSegments at full returns 12`() {
    assertThat(SpeedometerRenderer.computeAccelerationSegments(10f, 10f)).isEqualTo(12)
  }

  // --- Accessibility ---

  @Test
  fun `accessibilityDescription with speed includes speed value`() {
    val renderer = SpeedometerRenderer()
    val data =
      WidgetData.Empty.withSlot(
        SpeedSnapshot::class,
        SpeedSnapshot(speedMps = 16.67f, accuracy = 1.0f, timestamp = 1000L),
      )
    val desc = renderer.accessibilityDescription(data)
    // 16.67 m/s * 3.6 = 60.012 -> rounds to 60
    assertThat(desc).contains("60")
  }

  @Test
  fun `accessibilityDescription with speed over limit includes Over limit`() {
    val renderer = SpeedometerRenderer()
    val data =
      WidgetData.Empty
        .withSlot(
          SpeedSnapshot::class,
          SpeedSnapshot(speedMps = 19.44f, accuracy = 1.0f, timestamp = 1000L),
        )
        .withSlot(
          SpeedLimitSnapshot::class,
          SpeedLimitSnapshot(speedLimitKph = 60f, source = "user", timestamp = 1000L),
        )
    val desc = renderer.accessibilityDescription(data)
    assertThat(desc).contains("Over limit")
  }

  // --- Multi-slot independence ---

  @Test
  fun `accessibilityDescription with speed-only data does not crash`() {
    val renderer = SpeedometerRenderer()
    val data =
      WidgetData.Empty.withSlot(
        SpeedSnapshot::class,
        SpeedSnapshot(speedMps = 16.67f, accuracy = 1.0f, timestamp = 1000L),
      )
    val desc = renderer.accessibilityDescription(data)
    assertThat(desc).contains("60")
    assertThat(desc).doesNotContain("Limit")
  }

  // --- Aspect ratio ---

  @Test
  fun `aspectRatio is 1f square`() {
    assertThat(SpeedometerRenderer().aspectRatio).isEqualTo(1f)
  }
}
