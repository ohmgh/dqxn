package app.dqxn.android.pack.essentials.providers

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import app.dqxn.android.pack.essentials.snapshots.AmbientLightSnapshot
import app.dqxn.android.sdk.contracts.provider.DataProvider
import app.dqxn.android.sdk.contracts.testing.DataProviderContractTest
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AmbientLightDataProviderTest : DataProviderContractTest() {

  private val mockSensor: Sensor = mockk(relaxed = true)
  private val sensorManager: SensorManager =
    mockk(relaxed = true) {
      every { getDefaultSensor(Sensor.TYPE_LIGHT) } returns mockSensor
      every {
        registerListener(any<SensorEventListener>(), any<Sensor>(), any<Int>(), any<Int>())
      } answers
        {
          firstArg<SensorEventListener>().onSensorChanged(createLightEvent(300f))
          true
        }
    }

  override fun createProvider(): DataProvider<*> =
    AmbientLightDataProvider(sensorManager = sensorManager)

  @Test
  fun `classifies 10 lux as DARK`() {
    assertThat(AmbientLightDataProvider.classifyLux(10f))
      .isEqualTo(AmbientLightDataProvider.CATEGORY_DARK)
  }

  @Test
  fun `classifies 100 lux as DIM`() {
    assertThat(AmbientLightDataProvider.classifyLux(100f))
      .isEqualTo(AmbientLightDataProvider.CATEGORY_DIM)
  }

  @Test
  fun `classifies 300 lux as NORMAL`() {
    assertThat(AmbientLightDataProvider.classifyLux(300f))
      .isEqualTo(AmbientLightDataProvider.CATEGORY_NORMAL)
  }

  @Test
  fun `classifies 1000 lux as BRIGHT`() {
    assertThat(AmbientLightDataProvider.classifyLux(1000f))
      .isEqualTo(AmbientLightDataProvider.CATEGORY_BRIGHT)
  }

  @Test
  fun `boundary - 49_9 lux is DARK`() {
    assertThat(AmbientLightDataProvider.classifyLux(49.9f))
      .isEqualTo(AmbientLightDataProvider.CATEGORY_DARK)
  }

  @Test
  fun `boundary - 50 lux is DIM`() {
    assertThat(AmbientLightDataProvider.classifyLux(50f))
      .isEqualTo(AmbientLightDataProvider.CATEGORY_DIM)
  }

  @Test
  fun `boundary - 200 lux is NORMAL`() {
    assertThat(AmbientLightDataProvider.classifyLux(200f))
      .isEqualTo(AmbientLightDataProvider.CATEGORY_NORMAL)
  }

  @Test
  fun `boundary - 500 lux is BRIGHT`() {
    assertThat(AmbientLightDataProvider.classifyLux(500f))
      .isEqualTo(AmbientLightDataProvider.CATEGORY_BRIGHT)
  }

  @Test
  fun `emitted snapshot includes correct category for lux value`() = runTest {
    val listenerSlot = slot<SensorEventListener>()
    val sm: SensorManager =
      mockk(relaxed = true) {
        every { getDefaultSensor(Sensor.TYPE_LIGHT) } returns mockSensor
        every {
          registerListener(capture(listenerSlot), any<Sensor>(), any<Int>(), any<Int>())
        } returns true
      }
    val provider = AmbientLightDataProvider(sensorManager = sm)
    val emissions = mutableListOf<AmbientLightSnapshot>()
    val job = launch { provider.provideState().collect { emissions.add(it) } }
    delay(50)
    listenerSlot.captured.onSensorChanged(createLightEvent(25f))
    delay(50)
    assertThat(emissions.last().category).isEqualTo(AmbientLightDataProvider.CATEGORY_DARK)
    assertThat(emissions.last().lux).isEqualTo(25f)
    listenerSlot.captured.onSensorChanged(createLightEvent(750f))
    delay(50)
    assertThat(emissions.last().category).isEqualTo(AmbientLightDataProvider.CATEGORY_BRIGHT)
    job.cancel()
  }

  @Test
  fun `sensor batching uses 500ms max report latency`() = runTest {
    val sm: SensorManager =
      mockk(relaxed = true) {
        every { getDefaultSensor(Sensor.TYPE_LIGHT) } returns mockSensor
        every {
          registerListener(any<SensorEventListener>(), any<Sensor>(), any<Int>(), any<Int>())
        } answers
          {
            firstArg<SensorEventListener>().onSensorChanged(createLightEvent(100f))
            true
          }
      }
    val provider = AmbientLightDataProvider(sensorManager = sm)
    provider.provideState().first()
    verify {
      sm.registerListener(
        any(),
        any<Sensor>(),
        SensorManager.SENSOR_DELAY_NORMAL,
        AmbientLightDataProvider.BATCH_LATENCY_US
      )
    }
  }

  companion object {
    internal fun createLightEvent(lux: Float): SensorEvent {
      val event = mockk<SensorEvent>(relaxed = true)
      try {
        SensorEvent::class.java.getField("values").set(event, floatArrayOf(lux))
      } catch (_: Exception) {
        every { event.values } returns floatArrayOf(lux)
      }
      return event
    }
  }
}
