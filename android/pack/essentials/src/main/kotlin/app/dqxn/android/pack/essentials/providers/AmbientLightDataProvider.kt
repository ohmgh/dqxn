package app.dqxn.android.pack.essentials.providers

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import app.dqxn.android.pack.essentials.snapshots.AmbientLightSnapshot
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
 * Ambient light data provider using the light sensor. Emits [AmbientLightSnapshot] with lux value
 * and category classification (DARK, DIM, NORMAL, BRIGHT).
 *
 * Sensor batching: 500ms max report latency per NF14. Flow is conflated — latest value wins.
 */
@DashboardDataProvider(
  localId = "ambient-light",
  displayName = "Ambient Light",
  description = "Ambient light level with category classification",
)
@Singleton
public class AmbientLightDataProvider
@Inject
constructor(
  private val sensorManager: SensorManager,
) : DataProvider<AmbientLightSnapshot> {

  override val snapshotType: KClass<AmbientLightSnapshot> = AmbientLightSnapshot::class
  override val sourceId: String = "essentials:ambient-light"
  override val displayName: String = "Ambient Light"
  override val description: String = "Ambient light level with category classification"
  override val dataType: String = DataTypes.AMBIENT_LIGHT
  override val priority: ProviderPriority = ProviderPriority.DEVICE_SENSOR
  override val subscriberTimeout: Duration = 5.seconds
  override val firstEmissionTimeout: Duration = 5.seconds
  override val isAvailable: Boolean = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT) != null
  override val requiredAnyEntitlement: Set<String>? = null
  override val connectionState: Flow<Boolean> = flowOf(isAvailable)
  override val connectionErrorDescription: Flow<String?> =
    flowOf(if (isAvailable) null else "Light sensor not available")
  override val setupSchema: List<SetupPageDefinition> = emptyList()
  override val schema: DataSchema =
    DataSchema(
      fields =
        listOf(
          DataFieldSpec(name = "lux", typeId = "Float", unit = "lux"),
          DataFieldSpec(name = "category", typeId = "String"),
        ),
      stalenessThresholdMs = 5000L,
    )

  override fun provideState(): Flow<AmbientLightSnapshot> =
    callbackFlow {
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        if (sensor == null) {
          close()
          return@callbackFlow
        }

        val listener =
          object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
              val lux = event.values[0]
              trySend(
                AmbientLightSnapshot(
                  lux = lux,
                  category = classifyLux(lux),
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
          SensorManager.SENSOR_DELAY_NORMAL,
          BATCH_LATENCY_US,
        )

        awaitClose { sensorManager.unregisterListener(listener) }
      }
      .conflate()

  internal companion object {
    /** NF14 sensor batching: 500ms max report latency for ambient light. */
    const val BATCH_LATENCY_US: Int = 500_000

    /** Light level category thresholds. */
    const val DARK_THRESHOLD: Float = 50f
    const val DIM_THRESHOLD: Float = 200f
    const val NORMAL_THRESHOLD: Float = 500f

    const val CATEGORY_DARK: String = "DARK"
    const val CATEGORY_DIM: String = "DIM"
    const val CATEGORY_NORMAL: String = "NORMAL"
    const val CATEGORY_BRIGHT: String = "BRIGHT"

    internal fun classifyLux(lux: Float): String =
      when {
        lux < DARK_THRESHOLD -> CATEGORY_DARK
        lux < DIM_THRESHOLD -> CATEGORY_DIM
        lux < NORMAL_THRESHOLD -> CATEGORY_NORMAL
        else -> CATEGORY_BRIGHT
      }
  }
}
