package app.dqxn.android.pack.essentials.widgets

import app.dqxn.android.pack.essentials.snapshots.AmbientLightSnapshot
import app.dqxn.android.pack.essentials.widgets.ambientlight.AmbientLightRenderer
import app.dqxn.android.sdk.contracts.testing.WidgetRendererContractTest
import app.dqxn.android.sdk.contracts.widget.WidgetData
import app.dqxn.android.sdk.contracts.widget.WidgetRenderer
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AmbientLightRendererTest : WidgetRendererContractTest() {

  override fun createRenderer(): WidgetRenderer = AmbientLightRenderer()

  override fun createTestWidgetData(): WidgetData =
    WidgetData.Empty.withSlot(
      AmbientLightSnapshot::class,
      AmbientLightSnapshot(lux = 350f, category = "NORMAL", timestamp = 1L),
    )

  @Test
  fun `accessibilityDescription includes lux value and category`() {
    val data = createTestWidgetData()
    val description = createRenderer().accessibilityDescription(data)
    assertThat(description).contains("350")
    assertThat(description).contains("lux")
    assertThat(description).contains("Normal")
  }

  @Test
  fun `accessibilityDescription formats BRIGHT category`() {
    val data =
      WidgetData.Empty.withSlot(
        AmbientLightSnapshot::class,
        AmbientLightSnapshot(lux = 1000f, category = "BRIGHT", timestamp = 1L),
      )
    val description = createRenderer().accessibilityDescription(data)
    assertThat(description).contains("Bright")
  }

  @Test
  fun `accessibilityDescription formats DARK category`() {
    val data =
      WidgetData.Empty.withSlot(
        AmbientLightSnapshot::class,
        AmbientLightSnapshot(lux = 5f, category = "DARK", timestamp = 1L),
      )
    val description = createRenderer().accessibilityDescription(data)
    assertThat(description).contains("Dark")
  }

  @Test
  fun `accessibilityDescription formats VERY_BRIGHT category`() {
    val data =
      WidgetData.Empty.withSlot(
        AmbientLightSnapshot::class,
        AmbientLightSnapshot(lux = 50000f, category = "VERY_BRIGHT", timestamp = 1L),
      )
    val description = createRenderer().accessibilityDescription(data)
    assertThat(description).contains("Very Bright")
  }

  @Test
  fun `accessibilityDescription shows no data when snapshot is null`() {
    val description = createRenderer().accessibilityDescription(WidgetData.Empty)
    assertThat(description).contains("No data")
  }
}
