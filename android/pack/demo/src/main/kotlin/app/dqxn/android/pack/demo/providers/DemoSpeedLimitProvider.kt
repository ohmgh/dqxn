package app.dqxn.android.pack.demo.providers

import app.dqxn.android.pack.essentials.snapshots.SpeedLimitSnapshot
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
 * Deterministic speed limit provider for demo mode. Emits [SpeedLimitSnapshot] every 10 seconds,
 * cycling through common speed limits (50, 60, 80, 90, 110 km/h). Zero randomness -- same tick
 * always produces the same output.
 */
@DashboardDataProvider(
  localId = "demo-speed-limit",
  displayName = "Demo Speed Limit",
  description = "Deterministic simulated speed limit for demo mode",
)
@Singleton
public class DemoSpeedLimitProvider @Inject constructor() : DataProvider<SpeedLimitSnapshot> {

  override val snapshotType: KClass<SpeedLimitSnapshot> = SpeedLimitSnapshot::class
  override val sourceId: String = "demo:speed-limit"
  override val displayName: String = "Demo Speed Limit"
  override val description: String = "Deterministic simulated speed limit for demo mode"
  override val dataType: String = DataTypes.SPEED_LIMIT
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
          DataFieldSpec(name = "speedLimitKph", typeId = "Float", unit = "km/h"),
          DataFieldSpec(name = "source", typeId = "String"),
        ),
      stalenessThresholdMs = 15000L,
    )

  override fun provideState(): Flow<SpeedLimitSnapshot> = flow {
    var tick = 0
    while (true) {
      emit(
        SpeedLimitSnapshot(
          speedLimitKph = LIMITS[tick % LIMITS.size],
          source = SOURCE,
          timestamp = System.nanoTime(),
        )
      )
      delay(INTERVAL_MS)
      tick++
    }
  }

  internal companion object {
    val LIMITS: FloatArray = floatArrayOf(50f, 60f, 80f, 90f, 110f)
    const val SOURCE: String = "simulated"
    const val INTERVAL_MS: Long = 10000L
  }
}
