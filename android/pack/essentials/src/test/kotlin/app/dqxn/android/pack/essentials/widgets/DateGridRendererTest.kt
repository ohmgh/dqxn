package app.dqxn.android.pack.essentials.widgets

import app.dqxn.android.pack.essentials.snapshots.TimeSnapshot
import app.dqxn.android.pack.essentials.widgets.date.DateGridRenderer
import app.dqxn.android.sdk.contracts.testing.WidgetRendererContractTest
import app.dqxn.android.sdk.contracts.widget.WidgetData
import app.dqxn.android.sdk.contracts.widget.WidgetRenderer
import com.google.common.truth.Truth.assertThat
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Test

class DateGridRendererTest : WidgetRendererContractTest() {
  override fun createRenderer(): WidgetRenderer = DateGridRenderer()

  override fun createTestWidgetData(): WidgetData {
    val zdt =
      ZonedDateTime.of(LocalDateTime.of(2026, 1, 15, 12, 0, 0), ZoneId.of("America/New_York"))
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
  fun `accessibility contains date`() {
    val d = DateGridRenderer().accessibilityDescription(createTestWidgetData())
    assertThat(d).contains("January")
    assertThat(d).contains("15")
    assertThat(d).contains("2026")
  }

  @Test
  fun `accessibility no data`() {
    assertThat(DateGridRenderer().accessibilityDescription(WidgetData.Empty)).contains("no data")
  }
}
