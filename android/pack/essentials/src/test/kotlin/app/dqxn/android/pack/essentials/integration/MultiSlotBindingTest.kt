package app.dqxn.android.pack.essentials.integration

import app.dqxn.android.pack.essentials.snapshots.AccelerationSnapshot
import app.dqxn.android.pack.essentials.snapshots.SpeedLimitSnapshot
import app.dqxn.android.pack.essentials.snapshots.SpeedSnapshot
import app.dqxn.android.pack.essentials.widgets.speedometer.SpeedometerRenderer
import app.dqxn.android.sdk.contracts.widget.WidgetData
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Proves the multi-slot [WidgetData] accumulation model used by merge()+scan() binding.
 *
 * The core invariant: each slot update via [WidgetData.withSlot] preserves all existing slots.
 * This means speed data is immediately available to the SpeedometerRenderer without waiting for
 * acceleration or speed limit providers to emit -- the exact behavior merge()+scan() provides
 * over combine() which would block until all sources emit.
 *
 * This is the Phase 8 proof that independent slot arrival works correctly for the
 * SpeedometerRenderer's 3-provider binding.
 */
class MultiSlotBindingTest {

  private val renderer = SpeedometerRenderer()

  private val speedSnapshot = SpeedSnapshot(
    speedMps = 27.78f, // ~100 km/h
    accuracy = 1.0f,
    timestamp = 1000L,
  )

  private val accelSnapshot = AccelerationSnapshot(
    acceleration = 2.5f,
    lateralAcceleration = 0.3f,
    timestamp = 2000L,
  )

  private val speedLimitSnapshot = SpeedLimitSnapshot(
    speedLimitKph = 80f,
    source = "user",
    timestamp = 3000L,
  )

  @Test
  fun `empty WidgetData has no snapshots`() {
    val data = WidgetData.Empty
    assertThat(data.snapshot<SpeedSnapshot>()).isNull()
    assertThat(data.snapshot<AccelerationSnapshot>()).isNull()
    assertThat(data.snapshot<SpeedLimitSnapshot>()).isNull()
    assertThat(data.hasData()).isFalse()
  }

  @Test
  fun `speed slot available immediately without acceleration or limit`() {
    val data = WidgetData.Empty
      .withSlot(SpeedSnapshot::class, speedSnapshot)

    assertThat(data.snapshot<SpeedSnapshot>()).isNotNull()
    assertThat(data.snapshot<SpeedSnapshot>()!!.speedMps).isEqualTo(27.78f)
    assertThat(data.snapshot<AccelerationSnapshot>()).isNull()
    assertThat(data.snapshot<SpeedLimitSnapshot>()).isNull()
    assertThat(data.hasData()).isTrue()
  }

  @Test
  fun `adding acceleration preserves existing speed slot`() {
    val data = WidgetData.Empty
      .withSlot(SpeedSnapshot::class, speedSnapshot)
      .withSlot(AccelerationSnapshot::class, accelSnapshot)

    assertThat(data.snapshot<SpeedSnapshot>()).isNotNull()
    assertThat(data.snapshot<SpeedSnapshot>()!!.speedMps).isEqualTo(27.78f)
    assertThat(data.snapshot<AccelerationSnapshot>()).isNotNull()
    assertThat(data.snapshot<AccelerationSnapshot>()!!.acceleration).isEqualTo(2.5f)
    assertThat(data.snapshot<SpeedLimitSnapshot>()).isNull()
  }

  @Test
  fun `all three slots populated after progressive accumulation`() {
    val data = WidgetData.Empty
      .withSlot(SpeedSnapshot::class, speedSnapshot)
      .withSlot(AccelerationSnapshot::class, accelSnapshot)
      .withSlot(SpeedLimitSnapshot::class, speedLimitSnapshot)

    assertThat(data.snapshot<SpeedSnapshot>()).isNotNull()
    assertThat(data.snapshot<AccelerationSnapshot>()).isNotNull()
    assertThat(data.snapshot<SpeedLimitSnapshot>()).isNotNull()
    assertThat(data.snapshots).hasSize(3)
  }

  @Test
  fun `speedometer renders with partial data - speed only, no crash`() {
    val data = WidgetData.Empty
      .withSlot(SpeedSnapshot::class, speedSnapshot)

    val description = renderer.accessibilityDescription(data)
    assertThat(description).contains("100") // ~100 km/h from 27.78 m/s
    assertThat(description).doesNotContain("Limit")
  }

  @Test
  fun `speedometer renders with all slots - shows over limit warning`() {
    val data = WidgetData.Empty
      .withSlot(SpeedSnapshot::class, speedSnapshot) // ~100 km/h
      .withSlot(AccelerationSnapshot::class, accelSnapshot)
      .withSlot(SpeedLimitSnapshot::class, speedLimitSnapshot) // 80 km/h limit

    val description = renderer.accessibilityDescription(data)
    assertThat(description).contains("100") // speed value
    assertThat(description).contains("80") // limit value
    assertThat(description).contains("Over limit") // 100 > 80
  }

  @Test
  fun `speedometer with no data returns fallback description`() {
    val description = renderer.accessibilityDescription(WidgetData.Empty)
    assertThat(description).contains("No data")
  }

  @Test
  fun `slot update replaces previous value for same type`() {
    val updatedSpeed = SpeedSnapshot(speedMps = 13.89f, accuracy = 0.5f, timestamp = 4000L) // ~50 km/h
    val data = WidgetData.Empty
      .withSlot(SpeedSnapshot::class, speedSnapshot) // 100 km/h
      .withSlot(AccelerationSnapshot::class, accelSnapshot)
      .withSlot(SpeedSnapshot::class, updatedSpeed) // Replace with 50 km/h

    assertThat(data.snapshot<SpeedSnapshot>()!!.speedMps).isEqualTo(13.89f)
    // Acceleration slot must still be present
    assertThat(data.snapshot<AccelerationSnapshot>()).isNotNull()
    assertThat(data.snapshots).hasSize(2)
  }

  @Test
  fun `slot addition order does not matter - acceleration first then speed`() {
    val data = WidgetData.Empty
      .withSlot(AccelerationSnapshot::class, accelSnapshot)
      .withSlot(SpeedSnapshot::class, speedSnapshot)

    assertThat(data.snapshot<SpeedSnapshot>()).isNotNull()
    assertThat(data.snapshot<AccelerationSnapshot>()).isNotNull()

    val description = renderer.accessibilityDescription(data)
    assertThat(description).contains("100")
  }

  @Test
  fun `timestamp updates to latest slot addition`() {
    val data0 = WidgetData.Empty
    assertThat(data0.timestamp).isEqualTo(0L)

    val data1 = data0.withSlot(SpeedSnapshot::class, speedSnapshot)
    assertThat(data1.timestamp).isEqualTo(1000L)

    val data2 = data1.withSlot(AccelerationSnapshot::class, accelSnapshot)
    assertThat(data2.timestamp).isEqualTo(2000L)

    val data3 = data2.withSlot(SpeedLimitSnapshot::class, speedLimitSnapshot)
    assertThat(data3.timestamp).isEqualTo(3000L)
  }
}
