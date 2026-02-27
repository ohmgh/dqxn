package app.dqxn.android.pack.demo.providers

import app.dqxn.android.pack.essentials.snapshots.AccelerationSnapshot
import app.dqxn.android.pack.essentials.snapshots.AmbientLightSnapshot
import app.dqxn.android.pack.essentials.snapshots.BatterySnapshot
import app.dqxn.android.pack.essentials.snapshots.OrientationSnapshot
import app.dqxn.android.pack.essentials.snapshots.SolarSnapshot
import app.dqxn.android.pack.essentials.snapshots.SpeedLimitSnapshot
import app.dqxn.android.pack.essentials.snapshots.SpeedSnapshot
import app.dqxn.android.pack.essentials.snapshots.TimeSnapshot
import app.dqxn.android.sdk.contracts.provider.DataProvider
import app.dqxn.android.sdk.contracts.provider.DataSnapshot
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Determinism verification for all 8 demo providers. Same tick must produce the same data values
 * from independent instances. Timestamps (System.nanoTime()) are excluded from comparison.
 */
class DemoDeterminismTest {

  private val emissionCount = 10

  // ---- Per-provider determinism tests ----

  @Test
  fun `DemoTimeProvider produces identical values across instances`() = runTest {
    assertDeterministic(
      provider1 = DemoTimeProvider(),
      provider2 = DemoTimeProvider(),
    ) { it: DataSnapshot ->
      (it as TimeSnapshot).let { s -> s.copy(timestamp = 0L) }
    }
  }

  @Test
  fun `DemoSpeedProvider produces identical values across instances`() = runTest {
    assertDeterministic(
      provider1 = DemoSpeedProvider(),
      provider2 = DemoSpeedProvider(),
    ) { it: DataSnapshot ->
      (it as SpeedSnapshot).let { s -> s.copy(timestamp = 0L) }
    }
  }

  @Test
  fun `DemoOrientationProvider produces identical values across instances`() = runTest {
    assertDeterministic(
      provider1 = DemoOrientationProvider(),
      provider2 = DemoOrientationProvider(),
    ) { it: DataSnapshot ->
      (it as OrientationSnapshot).let { s -> s.copy(timestamp = 0L) }
    }
  }

  @Test
  fun `DemoBatteryProvider produces identical values across instances`() = runTest {
    assertDeterministic(
      provider1 = DemoBatteryProvider(),
      provider2 = DemoBatteryProvider(),
    ) { it: DataSnapshot ->
      (it as BatterySnapshot).let { s -> s.copy(timestamp = 0L) }
    }
  }

  @Test
  fun `DemoAmbientLightProvider produces identical values across instances`() = runTest {
    assertDeterministic(
      provider1 = DemoAmbientLightProvider(),
      provider2 = DemoAmbientLightProvider(),
    ) { it: DataSnapshot ->
      (it as AmbientLightSnapshot).let { s -> s.copy(timestamp = 0L) }
    }
  }

  @Test
  fun `DemoAccelerationProvider produces identical values across instances`() = runTest {
    assertDeterministic(
      provider1 = DemoAccelerationProvider(),
      provider2 = DemoAccelerationProvider(),
    ) { it: DataSnapshot ->
      (it as AccelerationSnapshot).let { s -> s.copy(timestamp = 0L) }
    }
  }

  @Test
  fun `DemoSolarProvider produces identical values across instances`() = runTest {
    assertDeterministic(
      provider1 = DemoSolarProvider(),
      provider2 = DemoSolarProvider(),
    ) { it: DataSnapshot ->
      (it as SolarSnapshot).let { s -> s.copy(timestamp = 0L) }
    }
  }

  @Test
  fun `DemoSpeedLimitProvider produces identical values across instances`() = runTest {
    assertDeterministic(
      provider1 = DemoSpeedLimitProvider(),
      provider2 = DemoSpeedLimitProvider(),
    ) { it: DataSnapshot ->
      (it as SpeedLimitSnapshot).let { s -> s.copy(timestamp = 0L) }
    }
  }

  // ---- Known-value assertions ----

  @Test
  fun `DemoSpeedProvider known values at specific ticks`() = runTest {
    val provider = DemoSpeedProvider()
    val emissions = provider.provideState().take(25).toList()

    // Tick 0: 0 km/h -> 0 m/s
    assertWithMessage("tick 0 speedMps").that(emissions[0].speedMps).isEqualTo(0f)

    // Tick 12: halfway up ramp -> 70 km/h -> ~19.44 m/s
    val expectedTick12 = (DemoSpeedProvider.PEAK_SPEED_KMH * 12.0 / 24.0 / 3.6).toFloat()
    assertWithMessage("tick 12 speedMps").that(emissions[12].speedMps).isEqualTo(expectedTick12)

    // Tick 24: peak of descending ramp -> 140 km/h -> ~38.89 m/s
    val expectedTick24 =
      (DemoSpeedProvider.PEAK_SPEED_KMH * (DemoSpeedProvider.CYCLE_LENGTH - 24) /
          DemoSpeedProvider.HALF_CYCLE /
          3.6)
        .toFloat()
    assertWithMessage("tick 24 speedMps").that(emissions[24].speedMps).isEqualTo(expectedTick24)
  }

  @Test
  fun `DemoAmbientLightProvider known values at specific ticks`() = runTest {
    val provider = DemoAmbientLightProvider()
    val emissions = provider.provideState().take(31).toList()

    // Tick 0: sin(0) = 0 -> lux = 500
    assertWithMessage("tick 0 lux").that(emissions[0].lux).isEqualTo(500.0f)

    // Tick 15: sin(PI/2) = 1 -> lux = 999
    val expectedTick15 =
      DemoAmbientLightProvider.LUX_MIDPOINT +
        DemoAmbientLightProvider.LUX_AMPLITUDE * sin(15.0 * PI / 30.0).toFloat()
    assertWithMessage("tick 15 lux").that(emissions[15].lux).isEqualTo(expectedTick15)

    // Tick 30: sin(PI) ~ 0 -> lux ~ 500
    val expectedTick30 =
      DemoAmbientLightProvider.LUX_MIDPOINT +
        DemoAmbientLightProvider.LUX_AMPLITUDE * sin(30.0 * PI / 30.0).toFloat()
    assertWithMessage("tick 30 lux").that(emissions[30].lux).isEqualTo(expectedTick30)
  }

  @Test
  fun `DemoSpeedLimitProvider cycles through known limits`() = runTest {
    val provider = DemoSpeedLimitProvider()
    val emissions = provider.provideState().take(6).toList()

    assertWithMessage("tick 0").that(emissions[0].speedLimitKph).isEqualTo(50f)
    assertWithMessage("tick 1").that(emissions[1].speedLimitKph).isEqualTo(60f)
    assertWithMessage("tick 2").that(emissions[2].speedLimitKph).isEqualTo(80f)
    assertWithMessage("tick 3").that(emissions[3].speedLimitKph).isEqualTo(90f)
    assertWithMessage("tick 4").that(emissions[4].speedLimitKph).isEqualTo(110f)
    assertWithMessage("tick 5 wraps").that(emissions[5].speedLimitKph).isEqualTo(50f)
  }

  // ---- Helper ----

  private suspend fun assertDeterministic(
    provider1: DataProvider<*>,
    provider2: DataProvider<*>,
    stripTimestamp: (DataSnapshot) -> DataSnapshot,
  ) {
    val values1 = provider1.provideState().take(emissionCount).toList().map(stripTimestamp)
    val values2 = provider2.provideState().take(emissionCount).toList().map(stripTimestamp)
    assertThat(values1).isEqualTo(values2)
  }
}
