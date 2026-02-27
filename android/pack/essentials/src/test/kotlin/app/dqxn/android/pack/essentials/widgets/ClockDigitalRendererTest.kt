package app.dqxn.android.pack.essentials.widgets

import app.dqxn.android.pack.essentials.snapshots.TimeSnapshot
import app.dqxn.android.pack.essentials.widgets.clock.ClockDigitalRenderer
import app.dqxn.android.sdk.contracts.testing.WidgetRendererContractTest
import app.dqxn.android.sdk.contracts.testing.testWidgetContext
import app.dqxn.android.sdk.contracts.widget.WidgetData
import app.dqxn.android.sdk.contracts.widget.WidgetRenderer
import com.google.common.truth.Truth.assertThat
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Test

class ClockDigitalRendererTest : WidgetRendererContractTest() {
  override fun createRenderer(): WidgetRenderer = ClockDigitalRenderer()

  override fun createTestWidgetData(): WidgetData {
    val zdt =
      ZonedDateTime.of(LocalDateTime.of(2026, 1, 15, 14, 30, 45), ZoneId.of("America/New_York"))
    return WidgetData.Empty.withSlot(
      TimeSnapshot::class,
      TimeSnapshot(
        epochMillis = zdt.toInstant().toEpochMilli(),
        zoneId = "America/New_York",
        timestamp = zdt.toInstant().toEpochMilli()
      )
    )
  }

  @Test
  fun `accessibilityDescription includes formatted time`() {
    assertThat(ClockDigitalRenderer().accessibilityDescription(createTestWidgetData()))
      .contains("14:30")
  }

  @Test
  fun `accessibilityDescription returns no data for empty`() {
    assertThat(ClockDigitalRenderer().accessibilityDescription(WidgetData.Empty))
      .contains("no data")
  }

  @Test
  fun `getDefaults wider with showSeconds`() {
    val r = ClockDigitalRenderer()
    val base = r.getDefaultsWithSettings(testWidgetContext(), mapOf("showSeconds" to false))
    val wide = r.getDefaultsWithSettings(testWidgetContext(), mapOf("showSeconds" to true))
    assertThat(wide.widthUnits).isEqualTo(base.widthUnits + 2)
  }

  @Test
  fun `12h format shows 2_30`() {
    val desc =
      ClockDigitalRenderer()
        .accessibilityDescriptionWithSettings(
          createTestWidgetData(),
          mapOf("use24HourFormat" to false, "showSeconds" to false)
        )
    assertThat(desc).contains("2:30")
    assertThat(desc).doesNotContain("14:30")
  }
}
