package app.dqxn.android.sdk.contracts.widget

import app.dqxn.android.sdk.contracts.provider.DataSnapshot
import com.google.common.truth.Truth.assertThat
import net.jqwik.api.Arbitraries
import net.jqwik.api.Arbitrary
import net.jqwik.api.ForAll
import net.jqwik.api.Property
import net.jqwik.api.Provide
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("fast")
class WidgetDataTest {

  private data class TestSpeedSnapshot(
    override val timestamp: Long,
    val speedKmh: Float,
  ) : DataSnapshot

  private data class TestBatterySnapshot(
    override val timestamp: Long,
    val level: Int,
  ) : DataSnapshot

  private data class TestTimeSnapshot(
    override val timestamp: Long,
    val epochMs: Long,
  ) : DataSnapshot

  @Test
  fun `snapshot returns typed value for matching KClass`() {
    val speed = TestSpeedSnapshot(timestamp = 100L, speedKmh = 60.5f)
    val data = WidgetData.Empty.withSlot(TestSpeedSnapshot::class, speed)

    val result: TestSpeedSnapshot? = data.snapshot<TestSpeedSnapshot>()

    assertThat(result).isNotNull()
    assertThat(result!!.speedKmh).isEqualTo(60.5f)
    assertThat(result.timestamp).isEqualTo(100L)
  }

  @Test
  fun `snapshot returns null for missing KClass`() {
    val result: TestSpeedSnapshot? = WidgetData.Empty.snapshot<TestSpeedSnapshot>()

    assertThat(result).isNull()
  }

  @Test
  fun `snapshot returns null for wrong KClass`() {
    val speed = TestSpeedSnapshot(timestamp = 100L, speedKmh = 60.5f)
    val data = WidgetData.Empty.withSlot(TestSpeedSnapshot::class, speed)

    val result: TestBatterySnapshot? = data.snapshot<TestBatterySnapshot>()

    assertThat(result).isNull()
  }

  @Test
  fun `withSlot adds new slot without removing existing`() {
    val speed = TestSpeedSnapshot(timestamp = 100L, speedKmh = 60.5f)
    val battery = TestBatterySnapshot(timestamp = 200L, level = 85)

    val data =
      WidgetData.Empty.withSlot(TestSpeedSnapshot::class, speed)
        .withSlot(TestBatterySnapshot::class, battery)

    assertThat(data.snapshot<TestSpeedSnapshot>()).isEqualTo(speed)
    assertThat(data.snapshot<TestBatterySnapshot>()).isEqualTo(battery)
  }

  @Test
  fun `withSlot replaces existing slot of same KClass`() {
    val speed1 = TestSpeedSnapshot(timestamp = 100L, speedKmh = 60.5f)
    val speed2 = TestSpeedSnapshot(timestamp = 200L, speedKmh = 120.0f)

    val data =
      WidgetData.Empty.withSlot(TestSpeedSnapshot::class, speed1)
        .withSlot(TestSpeedSnapshot::class, speed2)

    val result = data.snapshot<TestSpeedSnapshot>()
    assertThat(result).isEqualTo(speed2)
    assertThat(result!!.speedKmh).isEqualTo(120.0f)
  }

  @Test
  fun `hasData false for Empty, true with any slot`() {
    assertThat(WidgetData.Empty.hasData()).isFalse()

    val speed = TestSpeedSnapshot(timestamp = 100L, speedKmh = 60.5f)
    val data = WidgetData.Empty.withSlot(TestSpeedSnapshot::class, speed)
    assertThat(data.hasData()).isTrue()
  }

  @Test
  fun `Empty and Unavailable are distinct`() {
    assertThat(WidgetData.Empty.timestamp).isEqualTo(0L)
    assertThat(WidgetData.Unavailable.timestamp).isEqualTo(-1L)
    assertThat(WidgetData.Empty).isNotEqualTo(WidgetData.Unavailable)
  }

  @Property
  fun `withSlot accumulation is order-independent for distinct KClasses`(
    @ForAll("timestamps") ts1: Long,
    @ForAll("timestamps") ts2: Long,
    @ForAll("timestamps") ts3: Long,
  ) {
    val speed = TestSpeedSnapshot(timestamp = ts1, speedKmh = 60.0f)
    val battery = TestBatterySnapshot(timestamp = ts2, level = 50)
    val time = TestTimeSnapshot(timestamp = ts3, epochMs = System.currentTimeMillis())

    // Apply in one order
    val data1 =
      WidgetData.Empty.withSlot(TestSpeedSnapshot::class, speed)
        .withSlot(TestBatterySnapshot::class, battery)
        .withSlot(TestTimeSnapshot::class, time)

    // Apply in reverse order
    val data2 =
      WidgetData.Empty.withSlot(TestTimeSnapshot::class, time)
        .withSlot(TestBatterySnapshot::class, battery)
        .withSlot(TestSpeedSnapshot::class, speed)

    // All 3 retrievable in both cases
    assertThat(data1.snapshot<TestSpeedSnapshot>()).isEqualTo(speed)
    assertThat(data1.snapshot<TestBatterySnapshot>()).isEqualTo(battery)
    assertThat(data1.snapshot<TestTimeSnapshot>()).isEqualTo(time)

    assertThat(data2.snapshot<TestSpeedSnapshot>()).isEqualTo(speed)
    assertThat(data2.snapshot<TestBatterySnapshot>()).isEqualTo(battery)
    assertThat(data2.snapshot<TestTimeSnapshot>()).isEqualTo(time)
  }

  @Property
  fun `withSlot is idempotent for same KClass - last write wins`(
    @ForAll("timestamps") ts1: Long,
    @ForAll("timestamps") ts2: Long,
  ) {
    val speed1 = TestSpeedSnapshot(timestamp = ts1, speedKmh = 60.0f)
    val speed2 = TestSpeedSnapshot(timestamp = ts2, speedKmh = 120.0f)

    val data =
      WidgetData.Empty.withSlot(TestSpeedSnapshot::class, speed1)
        .withSlot(TestSpeedSnapshot::class, speed2)

    assertThat(data.snapshot<TestSpeedSnapshot>()).isEqualTo(speed2)
    // Only one slot for the same KClass
    assertThat(data.snapshots.size).isEqualTo(1)
  }

  @Provide fun timestamps(): Arbitrary<Long> = Arbitraries.longs().between(1, Long.MAX_VALUE)
}
