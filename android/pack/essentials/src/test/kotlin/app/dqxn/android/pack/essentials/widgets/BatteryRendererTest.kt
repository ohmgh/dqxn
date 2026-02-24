package app.dqxn.android.pack.essentials.widgets

import app.dqxn.android.pack.essentials.snapshots.BatterySnapshot
import app.dqxn.android.pack.essentials.widgets.battery.BatteryRenderer
import app.dqxn.android.sdk.contracts.testing.WidgetRendererContractTest
import app.dqxn.android.sdk.contracts.widget.WidgetData
import app.dqxn.android.sdk.contracts.widget.WidgetRenderer
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BatteryRendererTest : WidgetRendererContractTest() {

  override fun createRenderer(): WidgetRenderer = BatteryRenderer()

  override fun createTestWidgetData(): WidgetData =
    WidgetData.Empty.withSlot(
      BatterySnapshot::class,
      BatterySnapshot(level = 75, isCharging = true, temperature = 28.5f, timestamp = 1L),
    )

  @Test
  fun `accessibilityDescription includes battery level percentage`() {
    val data = createTestWidgetData()
    val description = createRenderer().accessibilityDescription(data)
    assertThat(description).contains("75")
    assertThat(description).contains("%")
  }

  @Test
  fun `accessibilityDescription includes Charging when isCharging is true`() {
    val data =
      WidgetData.Empty.withSlot(
        BatterySnapshot::class,
        BatterySnapshot(level = 75, isCharging = true, temperature = null, timestamp = 1L),
      )
    val description = createRenderer().accessibilityDescription(data)
    assertThat(description).contains("Charging")
  }

  @Test
  fun `accessibilityDescription excludes Charging when isCharging is false`() {
    val data =
      WidgetData.Empty.withSlot(
        BatterySnapshot::class,
        BatterySnapshot(level = 45, isCharging = false, temperature = null, timestamp = 1L),
      )
    val description = createRenderer().accessibilityDescription(data)
    assertThat(description).doesNotContain("Charging")
  }

  @Test
  fun `accessibilityDescription includes temperature when available`() {
    val data =
      WidgetData.Empty.withSlot(
        BatterySnapshot::class,
        BatterySnapshot(level = 50, isCharging = false, temperature = 32.0f, timestamp = 1L),
      )
    val description = createRenderer().accessibilityDescription(data)
    assertThat(description).contains("\u00B0C")
  }

  @Test
  fun `accessibilityDescription excludes temperature when null`() {
    val data =
      WidgetData.Empty.withSlot(
        BatterySnapshot::class,
        BatterySnapshot(level = 50, isCharging = false, temperature = null, timestamp = 1L),
      )
    val description = createRenderer().accessibilityDescription(data)
    assertThat(description).doesNotContain("\u00B0C")
  }

  @Test
  fun `accessibilityDescription handles empty widget data`() {
    val description = createRenderer().accessibilityDescription(WidgetData.Empty)
    assertThat(description).contains("No data")
  }
}
