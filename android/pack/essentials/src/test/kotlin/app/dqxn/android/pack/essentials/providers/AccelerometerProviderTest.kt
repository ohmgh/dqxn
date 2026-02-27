package app.dqxn.android.pack.essentials.providers

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import app.dqxn.android.sdk.contracts.provider.DataProvider
import app.dqxn.android.sdk.contracts.testing.DataProviderContractTest
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccelerometerProviderTest : DataProviderContractTest() {

  private val linearSensor = mockk<Sensor>(relaxed = true)
  private val accelerometerSensor = mockk<Sensor>(relaxed = true)
  private val listenerSlot = slot<SensorEventListener>()

  private val sensorManager: SensorManager =
    mockk<SensorManager>(relaxed = true).also { mgr ->
      every { mgr.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION) } returns linearSensor
      every { mgr.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) } returns accelerometerSensor
      every {
        mgr.registerListener(capture(listenerSlot), any<Sensor>(), any<Int>(), any<Int>())
      } answers
        {
          // Auto-fire a sensor event so contract tests that call provideState().first() succeed.
          simulateSensorEvent(listenerSlot.captured, floatArrayOf(0.5f, 0.1f, 0.3f))
          true
        }
    }

  override fun createProvider(): DataProvider<*> = AccelerometerProvider(sensorManager)

  // --- Accelerometer-specific: sensor selection ---

  @Test
  fun `prefers LINEAR_ACCELERATION when available`() = runTest {
    val provider = AccelerometerProvider(sensorManager)
    val job = launch { provider.provideState().first() }
    advanceUntilIdle()

    if (listenerSlot.isCaptured) {
      // Simulate sensor event
      simulateSensorEvent(listenerSlot.captured, floatArrayOf(0.1f, 0.2f, 0.3f))
    }

    job.join()

    verify {
      sensorManager.registerListener(
        any<SensorEventListener>(),
        linearSensor,
        SensorManager.SENSOR_DELAY_UI,
        AccelerometerProvider.BATCH_LATENCY_US,
      )
    }
  }

  @Test
  fun `falls back to TYPE_ACCELEROMETER when linear unavailable`() = runTest {
    val fallbackManager =
      mockk<SensorManager>(relaxed = true) {
        every { getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION) } returns null
        every { getDefaultSensor(Sensor.TYPE_ACCELEROMETER) } returns accelerometerSensor
        every {
          registerListener(capture(listenerSlot), any<Sensor>(), any<Int>(), any<Int>())
        } returns true
      }

    val provider = AccelerometerProvider(fallbackManager)
    val job = launch { provider.provideState().first() }
    advanceUntilIdle()

    if (listenerSlot.isCaptured) {
      simulateSensorEvent(listenerSlot.captured, floatArrayOf(0f, 0f, 9.8f))
    }

    job.join()

    verify {
      fallbackManager.registerListener(
        any<SensorEventListener>(),
        accelerometerSensor,
        SensorManager.SENSOR_DELAY_UI,
        AccelerometerProvider.BATCH_LATENCY_US,
      )
    }
  }

  @Test
  fun `sensor batching uses 100ms max report latency`() {
    assertThat(AccelerometerProvider.BATCH_LATENCY_US).isEqualTo(100_000)
  }

  @Test
  fun `isAvailable false when no sensor available`() {
    val noSensorManager =
      mockk<SensorManager>(relaxed = true) {
        every { getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION) } returns null
        every { getDefaultSensor(Sensor.TYPE_ACCELEROMETER) } returns null
      }
    val provider = AccelerometerProvider(noSensorManager)
    assertThat(provider.isAvailable).isFalse()
  }

  @Test
  fun `gravity filter alpha is 0_8`() {
    assertThat(AccelerometerProvider.ALPHA).isWithin(0.001f).of(0.8f)
  }

  // --- Helper ---

  private fun simulateSensorEvent(listener: SensorEventListener, values: FloatArray) {
    val event = mockk<SensorEvent>(relaxed = true)
    // SensorEvent.values is a public field, not a getter - use reflection
    val valuesField = SensorEvent::class.java.getField("values")
    valuesField.set(event, values)
    listener.onSensorChanged(event)
  }
}
