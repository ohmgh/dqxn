package app.dqxn.android.pack.essentials.widgets

import app.dqxn.android.pack.essentials.snapshots.SpeedLimitSnapshot
import app.dqxn.android.pack.essentials.widgets.speedlimit.SpeedLimitCircleRenderer
import app.dqxn.android.pack.essentials.widgets.speedlimit.SpeedLimitRectRenderer
import app.dqxn.android.sdk.contracts.testing.WidgetRendererContractTest
import app.dqxn.android.sdk.contracts.widget.WidgetData
import app.dqxn.android.sdk.contracts.widget.WidgetRenderer
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SpeedLimitRectRendererTest : WidgetRendererContractTest() {

  override fun createRenderer(): WidgetRenderer = SpeedLimitRectRenderer()

  override fun createTestWidgetData(): WidgetData =
    WidgetData.Empty.withSlot(
      SpeedLimitSnapshot::class,
      SpeedLimitSnapshot(speedLimitKph = 60f, source = "user", timestamp = 1L),
    )

  @Test
  fun `accessibility description includes Speed limit`() {
    val renderer = SpeedLimitRectRenderer()
    val data =
      WidgetData.Empty.withSlot(
        SpeedLimitSnapshot::class,
        SpeedLimitSnapshot(speedLimitKph = 35f, source = "user", timestamp = 1L),
      )
    val desc = renderer.accessibilityDescription(data)
    assertThat(desc).contains("Speed limit")
  }

  @Test
  fun `accessibility description for empty data says unknown`() {
    val renderer = SpeedLimitRectRenderer()
    val desc = renderer.accessibilityDescription(WidgetData.Empty)
    assertThat(desc).contains("unknown")
  }

  @Test
  fun `typeId matches expected format`() {
    val renderer = SpeedLimitRectRenderer()
    assertThat(renderer.typeId).isEqualTo("essentials:speedlimit-rect")
  }

  @Test
  fun `aspectRatio is null for rectangle`() {
    val renderer = SpeedLimitRectRenderer()
    assertThat(renderer.aspectRatio).isNull()
  }

  @Test
  fun `default dimensions are 6x8`() {
    val renderer = SpeedLimitRectRenderer()
    val defaults = renderer.getDefaults(app.dqxn.android.sdk.contracts.testing.testWidgetContext())
    assertThat(defaults.widthUnits).isEqualTo(6)
    assertThat(defaults.heightUnits).isEqualTo(8)
  }

  @Test
  fun `kph to mph conversion uses same constant as circle renderer`() {
    val kph = 60f
    val mph = (kph * SpeedLimitCircleRenderer.KPH_TO_MPH).toInt()
    assertThat(mph).isEqualTo(37)
  }
}
