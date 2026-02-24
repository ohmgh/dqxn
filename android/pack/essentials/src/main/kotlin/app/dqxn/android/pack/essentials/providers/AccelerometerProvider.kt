package app.dqxn.android.pack.essentials.providers

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import app.dqxn.android.pack.essentials.snapshots.AccelerationSnapshot
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
 * Accelerometer data provider. Emits [AccelerationSnapshot] with gravity-removed acceleration.
 *
 * Strategy: tries [Sensor.TYPE_LINEAR_ACCELERATION] first (gravity pre-removed by Android). Falls
 * back to [Sensor.TYPE_ACCELEROMETER] with a low-pass gravity filter when linear acceleration
 * sensor is unavailable.
 */
@DashboardDataProvider(
  localId = "accelerometer",
  displayName = "Accelerometer",
  description = "Gravity-removed acceleration for speedometer arc",
)
@Singleton
class AccelerometerProvider
@Inject
constructor(
  private val sensorManager: SensorManager,
) : DataProvider<AccelerationSnapshot> {

  override val snapshotType: KClass<AccelerationSnapshot> = AccelerationSnapshot::class
  override val sourceId: String = "essentials:accelerometer"
  override val displayName: String = "Accelerometer"
  override val description: String = "Gravity-removed acceleration for speedometer arc"
  override val dataType: String = DataTypes.ACCELERATION
  override val priority: ProviderPriority = ProviderPriority.DEVICE_SENSOR
  override val subscriberTimeout: Duration = 5.seconds
  override val firstEmissionTimeout: Duration = 5.seconds
  override val isAvailable: Boolean = resolveAvailableSensor() != null
  override val requiredAnyEntitlement: Set<String>? = null
  override val connectionState: Flow<Boolean> = flowOf(isAvailable)
  override val connectionErrorDescription: Flow<String?> =
    flowOf(if (isAvailable) null else "No accelerometer sensor available")
  override val setupSchema: List<SetupPageDefinition> = emptyList()
  override val schema: DataSchema =
    DataSchema(
      fields =
        listOf(
          DataFieldSpec(
            name = "acceleration",
            typeId = "Float",
            unit = "m/s\u00B2",
            displayName = "Longitudinal Acceleration",
          ),
          DataFieldSpec(
            name = "lateralAcceleration",
            typeId = "Float",
            unit = "m/s\u00B2",
            displayName = "Lateral Acceleration",
          ),
        ),
      stalenessThresholdMs = 1000L,
    )

  override fun provideState(): Flow<AccelerationSnapshot> =
    callbackFlow {
        val linearSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        val useLinear = linearSensor != null
        val sensor =
          linearSensor ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        if (sensor == null) {
          close()
          return@callbackFlow
        }

        val gravity = FloatArray(3)

        val listener =
          object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
              val linearZ: Float
              val linearX: Float

              if (useLinear) {
                linearZ = event.values[2]
                linearX = event.values[0]
              } else {
                for (i in 0..2) {
                  gravity[i] = ALPHA * gravity[i] + (1f - ALPHA) * event.values[i]
                }
                linearZ = event.values[2] - gravity[2]
                linearX = event.values[0] - gravity[0]
              }

              trySend(
                AccelerationSnapshot(
                  acceleration = linearZ,
                  lateralAcceleration = linearX,
                  timestamp = SystemClock.elapsedRealtimeNanos(),
                )
              )
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
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

  private fun resolveAvailableSensor(): Sensor? =
    sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
      ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

  internal companion object {
    const val ALPHA: Float = 0.8f
    const val BATCH_LATENCY_US: Int = 100_000
  }
}
