package app.dqxn.android.pack.essentials.providers

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import app.dqxn.android.pack.essentials.snapshots.OrientationSnapshot
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
class OrientationDataProviderTest : DataProviderContractTest() {

  private val mockSensor: Sensor = mockk(relaxed = true)

  private val sensorManager: SensorManager = mockk(relaxed = true) {
    every { getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) } returns mockSensor
    every {
      registerListener(any<SensorEventListener>(), any<Sensor>(), any<Int>(), any<Int>())
    } answers {
      val listener = firstArg<SensorEventListener>()
      repeat(OrientationDataProvider.WARM_UP_EVENTS + 3) {
        listener.onSensorChanged(createSensorEvent(floatArrayOf(0f, 0f, 0f, 1f, 0f)))
      }
      true
    }
  }

  override fun createProvider(): DataProvider<*> =
    OrientationDataProvider(sensorManager = sensorManager)

  @Test
  fun `bearing is normalized to 0-360 range`() = runTest {
    val provider = OrientationDataProvider(sensorManager = sensorManager)
    val snapshot = provider.provideState().first()
    assertThat(snapshot.bearing).isAtLeast(0f)
    assertThat(snapshot.bearing).isLessThan(360f)
  }

  @Test
  fun `warm-up skips first events`() = runTest {
    val listenerSlot = slot<SensorEventListener>()
    val sm: SensorManager = mockk(relaxed = true) {
      every { getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) } returns mockSensor
      every {
        registerListener(capture(listenerSlot), any<Sensor>(), any<Int>(), any<Int>())
      } returns true
    }
    val provider = OrientationDataProvider(sensorManager = sm)
    val emissions = mutableListOf<OrientationSnapshot>()
    val job = launch { provider.provideState().collect { emissions.add(it) } }
    delay(50)
    val listener = listenerSlot.captured
    repeat(OrientationDataProvider.WARM_UP_EVENTS) {
      listener.onSensorChanged(createSensorEvent(floatArrayOf(0f, 0f, 0f, 1f, 0f)))
    }
    delay(50)
    assertThat(emissions).isEmpty()
    listener.onSensorChanged(createSensorEvent(floatArrayOf(0f, 0f, 0f, 1f, 0f)))
    delay(50)
    assertThat(emissions).hasSize(1)
    job.cancel()
  }

  @Test
  fun `sensor batching uses 200ms max report latency`() = runTest {
    val sm: SensorManager = mockk(relaxed = true) {
      every { getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) } returns mockSensor
      every {
        registerListener(any<SensorEventListener>(), any<Sensor>(), any<Int>(), any<Int>())
      } answers {
        val listener = firstArg<SensorEventListener>()
        repeat(OrientationDataProvider.WARM_UP_EVENTS + 1) {
          listener.onSensorChanged(createSensorEvent(floatArrayOf(0f, 0f, 0f, 1f, 0f)))
        }
        true
      }
    }
    val provider = OrientationDataProvider(sensorManager = sm)
    provider.provideState().first()
    verify {
      sm.registerListener(any(), any<Sensor>(), SensorManager.SENSOR_DELAY_UI, OrientationDataProvider.BATCH_LATENCY_US)
    }
  }

  companion object {
    internal fun createSensorEvent(values: FloatArray): SensorEvent {
      val event = mockk<SensorEvent>(relaxed = true)
      try {
        SensorEvent::class.java.getField("values").set(event, values)
      } catch (_: Exception) {
        every { event.values } returns values
      }
      return event
    }
  }
}
