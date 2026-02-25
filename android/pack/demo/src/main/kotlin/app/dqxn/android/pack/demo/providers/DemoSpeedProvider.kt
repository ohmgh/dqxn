package app.dqxn.android.pack.demo.providers

import app.dqxn.android.pack.essentials.snapshots.SpeedSnapshot
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

/**
 * Deterministic speed provider for demo mode. Emits [SpeedSnapshot] every 200ms with a triangle
 * wave pattern: ramp up to 140 km/h over 24 ticks, then ramp down over 24 ticks. Zero randomness
 * -- same tick always produces the same output.
 */
@DashboardDataProvider(
  localId = "demo-speed",
  displayName = "Demo Speed",
  description = "Deterministic simulated speed for demo mode",
)
@Singleton
public class DemoSpeedProvider @Inject constructor() : DataProvider<SpeedSnapshot> {

  override val snapshotType: KClass<SpeedSnapshot> = SpeedSnapshot::class
  override val sourceId: String = "demo:speed"
  override val displayName: String = "Demo Speed"
  override val description: String = "Deterministic simulated speed for demo mode"
  override val dataType: String = DataTypes.SPEED
  override val priority: ProviderPriority = ProviderPriority.SIMULATED
  override val subscriberTimeout: Duration = 2.seconds
  override val firstEmissionTimeout: Duration = 3.seconds
  override val isAvailable: Boolean = true
  override val requiredAnyEntitlement: Set<String>? = null
  override val connectionState: Flow<Boolean> = flowOf(true)
  override val connectionErrorDescription: Flow<String?> = flowOf(null)
  override val setupSchema: List<SetupPageDefinition> = emptyList()
  override val schema: DataSchema =
    DataSchema(
      fields =
        listOf(
          DataFieldSpec(name = "speedMps", typeId = "Float", unit = "m/s"),
          DataFieldSpec(name = "accuracy", typeId = "Float"),
        ),
      stalenessThresholdMs = 500L,
    )

  override fun provideState(): Flow<SpeedSnapshot> = flow {
    var tick = 0
    while (true) {
      val phase = tick % CYCLE_LENGTH
      val speedKmh =
        if (phase < HALF_CYCLE) {
          PEAK_SPEED_KMH * phase / HALF_CYCLE
        } else {
          PEAK_SPEED_KMH * (CYCLE_LENGTH - phase) / HALF_CYCLE
        }
      val speedMps = (speedKmh / KMH_TO_MPS_DIVISOR).toFloat()
      emit(
        SpeedSnapshot(
          speedMps = speedMps,
          accuracy = 1.0f,
          timestamp = System.nanoTime(),
        )
      )
      delay(INTERVAL_MS)
      tick++
    }
  }

  internal companion object {
    const val PEAK_SPEED_KMH: Double = 140.0
    const val CYCLE_LENGTH: Int = 48
    const val HALF_CYCLE: Int = 24
    const val KMH_TO_MPS_DIVISOR: Double = 3.6
    const val INTERVAL_MS: Long = 200L
  }
}
