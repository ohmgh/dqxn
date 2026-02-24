package app.dqxn.android.pack.essentials.providers

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import app.dqxn.android.pack.essentials.snapshots.OrientationSnapshot
import app.dqxn.android.sdk.contracts.annotation.DashboardDataProvider
import app.dqxn.android.sdk.contracts.provider.DataFieldSpec
import app.dqxn.android.sdk.contracts.provider.DataProvider
import app.dqxn.android.sdk.contracts.provider.DataSchema
import app.dqxn.android.sdk.contracts.provider.DataTypes
import app.dqxn.android.sdk.contracts.provider.ProviderPriority
import app.dqxn.android.sdk.contracts.setup.SetupPageDefinition
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOf

/**
 * Orientation data provider using rotation vector sensor. Emits [OrientationSnapshot] with bearing
 * (0-360), pitch, and roll in degrees.
 *
 * Sensor batching: 200ms max report latency per NF14. First 10 events are skipped as warm-up
 * (calibration noise). Flow is conflated — latest value wins for display.
 */
@DashboardDataProvider(
  localId = "orientation",
  displayName = "Orientation",
  description = "Compass heading, pitch, and roll from rotation vector sensor",
)
@Singleton
public class OrientationDataProvider
@Inject
constructor(
  private val sensorManager: SensorManager,
) : DataProvider<OrientationSnapshot> {

  override val snapshotType: KClass<OrientationSnapshot> = OrientationSnapshot::class
  override val sourceId: String = "essentials:orientation"
  override val displayName: String = "Orientation"
  override val description: String = "Compass heading, pitch, and roll from rotation vector sensor"
  override val dataType: String = DataTypes.ORIENTATION
  override val priority: ProviderPriority = ProviderPriority.DEVICE_SENSOR
  override val subscriberTimeout: Duration = 5.seconds
  override val firstEmissionTimeout: Duration = 5.seconds
  override val isAvailable: Boolean =
    sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null
  override val requiredAnyEntitlement: Set<String>? = null
  override val connectionState: Flow<Boolean> = flowOf(isAvailable)
  override val connectionErrorDescription: Flow<String?> =
    flowOf(if (isAvailable) null else "Rotation vector sensor not available")
  override val setupSchema: List<SetupPageDefinition> = emptyList()
  override val schema: DataSchema =
    DataSchema(
      fields =
        listOf(
          DataFieldSpec(name = "bearing", typeId = "Float", unit = "degrees"),
          DataFieldSpec(name = "pitch", typeId = "Float", unit = "degrees"),
          DataFieldSpec(name = "roll", typeId = "Float", unit = "degrees"),
        ),
      stalenessThresholdMs = 1000L,
    )

  override fun provideState(): Flow<OrientationSnapshot> =
    callbackFlow {
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (sensor == null) {
          close()
          return@callbackFlow
        }

        var eventCount = 0
        val rotationMatrix = FloatArray(9)
        val orientation = FloatArray(3)

        val listener =
          object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
              eventCount++
              // Skip first 10 events (warm-up / calibration noise)
              if (eventCount <= WARM_UP_EVENTS) return

              SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
              SensorManager.getOrientation(rotationMatrix, orientation)

              val bearing =
                (Math.toDegrees(orientation[0].toDouble()).toFloat() + 360f) % 360f
              val pitch = Math.toDegrees(orientation[1].toDouble()).toFloat()
              val roll = Math.toDegrees(orientation[2].toDouble()).toFloat()

              trySend(
                OrientationSnapshot(
                  bearing = bearing,
                  pitch = pitch,
                  roll = roll,
                  timestamp = System.nanoTime(),
                )
              )
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
              // No-op
            }
          }

        sensorManager.registerListener(
          listener,
          sensor,
          SensorManager.SENSOR_DELAY_UI,
          BATCH_LATENCY_US,
        )

        awaitClose { sensorManager.unregisterListener(listener) }
      }
      .conflate()

  internal companion object {
    /** Number of initial sensor events to skip during warm-up. */
    const val WARM_UP_EVENTS: Int = 10

    /** NF14 sensor batching: 200ms max report latency for orientation. */
    const val BATCH_LATENCY_US: Int = 200_000
  }
}
