package app.dqxn.android.pack.demo.providers

import app.dqxn.android.pack.essentials.snapshots.TimeSnapshot
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
 * Deterministic time provider for demo mode. Emits [TimeSnapshot] every 1 second with a fixed base
 * epoch advancing by 1 second per tick. Zero randomness -- same tick always produces the same
 * output.
 */
@DashboardDataProvider(
  localId = "demo-time",
  displayName = "Demo Time",
  description = "Deterministic simulated clock for demo mode",
)
@Singleton
public class DemoTimeProvider @Inject constructor() : DataProvider<TimeSnapshot> {

  override val snapshotType: KClass<TimeSnapshot> = TimeSnapshot::class
  override val sourceId: String = "demo:time"
  override val displayName: String = "Demo Time"
  override val description: String = "Deterministic simulated clock for demo mode"
  override val dataType: String = DataTypes.TIME
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
          DataFieldSpec(name = "epochMillis", typeId = "Long", unit = "ms"),
          DataFieldSpec(name = "zoneId", typeId = "String"),
        ),
      stalenessThresholdMs = 2000L,
    )

  override fun provideState(): Flow<TimeSnapshot> = flow {
    var tick = 0
    while (true) {
      emit(
        TimeSnapshot(
          epochMillis = BASE_EPOCH + (tick * 1000L),
          zoneId = ZONE_ID,
          timestamp = System.nanoTime(),
        )
      )
      delay(INTERVAL_MS)
      tick++
    }
  }

  internal companion object {
    /** 2025-01-01T00:00:00Z */
    const val BASE_EPOCH: Long = 1735689600000L
    const val ZONE_ID: String = "Asia/Singapore"
    const val INTERVAL_MS: Long = 1000L
  }
}
