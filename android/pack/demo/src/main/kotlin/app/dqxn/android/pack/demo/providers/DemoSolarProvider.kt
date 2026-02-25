package app.dqxn.android.pack.demo.providers

import app.dqxn.android.pack.essentials.snapshots.SolarSnapshot
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
 * Deterministic solar provider for demo mode. Emits [SolarSnapshot] every 30 seconds with fixed
 * Singapore coordinates. Zero randomness -- same tick always produces the same output.
 */
@DashboardDataProvider(
  localId = "demo-solar",
  displayName = "Demo Solar",
  description = "Deterministic simulated solar data for demo mode",
)
@Singleton
public class DemoSolarProvider @Inject constructor() : DataProvider<SolarSnapshot> {

  override val snapshotType: KClass<SolarSnapshot> = SolarSnapshot::class
  override val sourceId: String = "demo:solar"
  override val displayName: String = "Demo Solar"
  override val description: String = "Deterministic simulated solar data for demo mode"
  override val dataType: String = DataTypes.SOLAR
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
          DataFieldSpec(name = "sunriseEpochMillis", typeId = "Long", unit = "ms"),
          DataFieldSpec(name = "sunsetEpochMillis", typeId = "Long", unit = "ms"),
          DataFieldSpec(name = "solarNoonEpochMillis", typeId = "Long", unit = "ms"),
          DataFieldSpec(name = "isDaytime", typeId = "Boolean"),
          DataFieldSpec(name = "sourceMode", typeId = "String"),
        ),
      stalenessThresholdMs = 60000L,
    )

  override fun provideState(): Flow<SolarSnapshot> = flow {
    var tick = 0
    while (true) {
      @Suppress("unused") val ignored = tick // tick unused but kept for pattern consistency
      emit(
        SolarSnapshot(
          sunriseEpochMillis = BASE_EPOCH + SUNRISE_OFFSET_MS,
          sunsetEpochMillis = BASE_EPOCH + SUNSET_OFFSET_MS,
          solarNoonEpochMillis = BASE_EPOCH + SOLAR_NOON_OFFSET_MS,
          isDaytime = true,
          sourceMode = SOURCE_MODE,
          timestamp = System.nanoTime(),
        )
      )
      delay(INTERVAL_MS)
      tick++
    }
  }

  internal companion object {
    /** 2025-01-01T00:00:00Z (same base epoch as DemoTimeProvider). */
    const val BASE_EPOCH: Long = 1735689600000L
    /** 06:45 SGT = midnight UTC + 6h45m = 24_300_000ms. */
    const val SUNRISE_OFFSET_MS: Long = 24_300_000L
    /** 19:00 SGT = midnight UTC + 19h = 68_400_000ms. */
    const val SUNSET_OFFSET_MS: Long = 68_400_000L
    /** 12:52 SGT = midnight UTC + 12h52m = 46_320_000ms. */
    const val SOLAR_NOON_OFFSET_MS: Long = 46_320_000L
    const val SOURCE_MODE: String = "simulated"
    const val INTERVAL_MS: Long = 30000L
  }
}
