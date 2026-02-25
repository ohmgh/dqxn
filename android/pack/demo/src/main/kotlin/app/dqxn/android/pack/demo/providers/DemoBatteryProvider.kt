package app.dqxn.android.pack.demo.providers

import app.dqxn.android.pack.essentials.snapshots.BatterySnapshot
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
 * Deterministic battery provider for demo mode. Emits [BatterySnapshot] every 5 seconds with a
 * sawtooth drain pattern: 100% down to 1%, then repeats. Zero randomness -- same tick always
 * produces the same output.
 */
@DashboardDataProvider(
  localId = "demo-battery",
  displayName = "Demo Battery",
  description = "Deterministic simulated battery for demo mode",
)
@Singleton
public class DemoBatteryProvider @Inject constructor() : DataProvider<BatterySnapshot> {

  override val snapshotType: KClass<BatterySnapshot> = BatterySnapshot::class
  override val sourceId: String = "demo:battery"
  override val displayName: String = "Demo Battery"
  override val description: String = "Deterministic simulated battery for demo mode"
  override val dataType: String = DataTypes.BATTERY
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
          DataFieldSpec(name = "level", typeId = "Int", unit = "percent"),
          DataFieldSpec(name = "isCharging", typeId = "Boolean"),
          DataFieldSpec(name = "temperature", typeId = "Float", unit = "celsius"),
        ),
      stalenessThresholdMs = 10000L,
    )

  override fun provideState(): Flow<BatterySnapshot> = flow {
    var tick = 0
    while (true) {
      emit(
        BatterySnapshot(
          level = LEVEL_MAX - (tick % LEVEL_MAX),
          isCharging = false,
          temperature = TEMPERATURE_CELSIUS,
          timestamp = System.nanoTime(),
        )
      )
      delay(INTERVAL_MS)
      tick++
    }
  }

  internal companion object {
    const val LEVEL_MAX: Int = 100
    const val TEMPERATURE_CELSIUS: Float = 28.5f
    const val INTERVAL_MS: Long = 5000L
  }
}
