package app.dqxn.android.pack.demo.providers

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
import kotlin.math.PI
import kotlin.math.sin
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

/**
 * Deterministic acceleration provider for demo mode. Emits [AccelerationSnapshot] every 200ms.
 * Lateral: gentle sine sway. Longitudinal: square wave acceleration/deceleration. Zero randomness
 * -- same tick always produces the same output.
 */
@DashboardDataProvider(
  localId = "demo-acceleration",
  displayName = "Demo Acceleration",
  description = "Deterministic simulated acceleration for demo mode",
)
@Singleton
public class DemoAccelerationProvider @Inject constructor() : DataProvider<AccelerationSnapshot> {

  override val snapshotType: KClass<AccelerationSnapshot> = AccelerationSnapshot::class
  override val sourceId: String = "demo:acceleration"
  override val displayName: String = "Demo Acceleration"
  override val description: String = "Deterministic simulated acceleration for demo mode"
  override val dataType: String = DataTypes.ACCELERATION
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
          DataFieldSpec(name = "acceleration", typeId = "Float", unit = "G"),
          DataFieldSpec(name = "lateralAcceleration", typeId = "Float", unit = "G"),
        ),
      stalenessThresholdMs = 500L,
    )

  override fun provideState(): Flow<AccelerationSnapshot> = flow {
    var tick = 0
    while (true) {
      val lateralG = LATERAL_AMPLITUDE * sin(tick * PI / LATERAL_HALF_CYCLE).toFloat()
      val longitudinalG =
        if (tick % LONGITUDINAL_CYCLE < LONGITUDINAL_HALF_CYCLE) {
          LONGITUDINAL_G
        } else {
          -LONGITUDINAL_G
        }
      emit(
        AccelerationSnapshot(
          acceleration = longitudinalG,
          lateralAcceleration = lateralG,
          timestamp = System.nanoTime(),
        )
      )
      delay(INTERVAL_MS)
      tick++
    }
  }

  internal companion object {
    const val LATERAL_AMPLITUDE: Float = 0.3f
    const val LATERAL_HALF_CYCLE: Int = 24
    const val LONGITUDINAL_CYCLE: Int = 48
    const val LONGITUDINAL_HALF_CYCLE: Int = 24
    const val LONGITUDINAL_G: Float = 0.15f
    const val INTERVAL_MS: Long = 200L
  }
}
