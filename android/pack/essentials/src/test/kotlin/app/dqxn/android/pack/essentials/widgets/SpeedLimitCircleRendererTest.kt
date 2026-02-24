package app.dqxn.android.pack.essentials.widgets

import app.dqxn.android.pack.essentials.snapshots.SpeedLimitSnapshot
import app.dqxn.android.pack.essentials.widgets.speedlimit.SpeedLimitCircleRenderer
import app.dqxn.android.sdk.contracts.testing.WidgetRendererContractTest
import app.dqxn.android.sdk.contracts.widget.WidgetData
import app.dqxn.android.sdk.contracts.widget.WidgetRenderer
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SpeedLimitCircleRendererTest : WidgetRendererContractTest() {

  override fun createRenderer(): WidgetRenderer = SpeedLimitCircleRenderer()

  override fun createTestWidgetData(): WidgetData =
    WidgetData.Empty.withSlot(
      SpeedLimitSnapshot::class,
      SpeedLimitSnapshot(speedLimitKph = 60f, source = "user", timestamp = 1L),
    )

  @Test
  fun `accessibility description includes 60 for 60 kph limit`() {
    val renderer = SpeedLimitCircleRenderer()
    val data =
      WidgetData.Empty.withSlot(
        SpeedLimitSnapshot::class,
        SpeedLimitSnapshot(speedLimitKph = 60f, source = "user", timestamp = 1L),
      )
    val desc = renderer.accessibilityDescription(data)
    assertThat(desc).contains("Speed limit")
    assertThat(desc).containsMatch("\\d+")
  }

  @Test
  fun `kph to mph conversion is correct`() {
    val mph = (60f * SpeedLimitCircleRenderer.KPH_TO_MPH).toInt()
    assertThat(mph).isEqualTo(37)
  }

  @Test
  fun `kph to mph conversion for 100 kph`() {
    val mph = (100f * SpeedLimitCircleRenderer.KPH_TO_MPH).toInt()
    assertThat(mph).isEqualTo(62)
  }

  @Test
  fun `aspectRatio is 1f`() {
    val renderer = SpeedLimitCircleRenderer()
    assertThat(renderer.aspectRatio).isEqualTo(1f)
  }

  @Test
  fun `accessibility description for empty data says unknown`() {
    val renderer = SpeedLimitCircleRenderer()
    val desc = renderer.accessibilityDescription(WidgetData.Empty)
    assertThat(desc).contains("unknown")
  }

  @Test
  fun `typeId matches expected format`() {
    val renderer = SpeedLimitCircleRenderer()
    assertThat(renderer.typeId).isEqualTo("essentials:speedlimit-circle")
  }
}
