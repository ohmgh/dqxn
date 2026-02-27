package app.dqxn.android.pack.essentials.widgets

import app.dqxn.android.pack.essentials.snapshots.TimeSnapshot
import app.dqxn.android.pack.essentials.widgets.clock.ClockAnalogRenderer
import app.dqxn.android.sdk.contracts.testing.WidgetRendererContractTest
import app.dqxn.android.sdk.contracts.widget.WidgetData
import app.dqxn.android.sdk.contracts.widget.WidgetRenderer
import com.google.common.truth.Truth.assertThat
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Test

class ClockAnalogRendererTest : WidgetRendererContractTest() {
  override fun createRenderer(): WidgetRenderer = ClockAnalogRenderer()

  override fun createTestWidgetData(): WidgetData {
    val zdt = ZonedDateTime.of(LocalDateTime.of(2026, 1, 15, 15, 15, 0), ZoneId.of("UTC"))
    return WidgetData.Empty.withSlot(
      TimeSnapshot::class,
      TimeSnapshot(
        epochMillis = zdt.toInstant().toEpochMilli(),
        zoneId = "UTC",
        timestamp = zdt.toInstant().toEpochMilli()
      )
    )
  }

  @Test
  fun `hour 3 is 90 degrees`() {
    assertThat(ClockAnalogRenderer.computeHourHandAngle(3, 0, 0)).isEqualTo(90f)
  }

  @Test
  fun `hour 12 is 0 degrees`() {
    assertThat(ClockAnalogRenderer.computeHourHandAngle(12, 0, 0)).isEqualTo(0f)
  }

  @Test
  fun `hour 6 is 180 degrees`() {
    assertThat(ClockAnalogRenderer.computeHourHandAngle(6, 0, 0)).isEqualTo(180f)
  }

  @Test
  fun `hour 6_30 is 195 degrees`() {
    assertThat(ClockAnalogRenderer.computeHourHandAngle(6, 30, 0)).isEqualTo(195f)
  }

  @Test
  fun `minute 15 is 90 degrees`() {
    assertThat(ClockAnalogRenderer.computeMinuteHandAngle(15, 0)).isEqualTo(90f)
  }

  @Test
  fun `minute 30 is 180 degrees`() {
    assertThat(ClockAnalogRenderer.computeMinuteHandAngle(30, 0)).isEqualTo(180f)
  }

  @Test
  fun `second 15 is 90 degrees`() {
    assertThat(ClockAnalogRenderer.computeSecondHandAngle(15)).isEqualTo(90f)
  }

  @Test
  fun `aspect ratio is 1f`() {
    assertThat(ClockAnalogRenderer().aspectRatio).isEqualTo(1f)
  }

  @Test
  fun `accessibility includes time`() {
    assertThat(ClockAnalogRenderer().accessibilityDescription(createTestWidgetData()))
      .contains("3:15")
  }
}
