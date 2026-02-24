package app.dqxn.android.pack.essentials.providers

import android.os.SystemClock
import app.dqxn.android.pack.essentials.snapshots.SpeedLimitSnapshot
import app.dqxn.android.sdk.contracts.annotation.DashboardDataProvider
import app.dqxn.android.sdk.contracts.provider.DataFieldSpec
import app.dqxn.android.sdk.contracts.provider.DataProvider
import app.dqxn.android.sdk.contracts.provider.DataSchema
import app.dqxn.android.sdk.contracts.provider.DataTypes
import app.dqxn.android.sdk.contracts.provider.ProviderPriority
import app.dqxn.android.sdk.contracts.settings.ProviderSettingsStore
import app.dqxn.android.sdk.contracts.setup.SetupPageDefinition
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * User-configured static speed limit provider. Reads the speed limit value from
 * [ProviderSettingsStore] under the `essentials:speed-limit:value` key.
 */
@DashboardDataProvider(
  localId = "speed-limit",
  displayName = "Speed Limit",
  description = "User-configured speed limit value",
)
@Singleton
class SpeedLimitProvider
@Inject
constructor(
  private val settingsStore: ProviderSettingsStore,
) : DataProvider<SpeedLimitSnapshot> {

  override val snapshotType: KClass<SpeedLimitSnapshot> = SpeedLimitSnapshot::class
  override val sourceId: String = "essentials:speed-limit"
  override val displayName: String = "Speed Limit"
  override val description: String = "User-configured speed limit value"
  override val dataType: String = DataTypes.SPEED_LIMIT
  override val priority: ProviderPriority = ProviderPriority.SIMULATED
  override val subscriberTimeout: Duration = 5.seconds
  override val firstEmissionTimeout: Duration = 5.seconds
  override val isAvailable: Boolean = true
  override val requiredAnyEntitlement: Set<String>? = null
  override val connectionState: Flow<Boolean> = flowOf(true)
  override val connectionErrorDescription: Flow<String?> = flowOf(null)
  override val setupSchema: List<SetupPageDefinition> = emptyList()
  override val schema: DataSchema =
    DataSchema(
      fields =
        listOf(
          DataFieldSpec(
            name = "speedLimitKph",
            typeId = "Float",
            unit = "km/h",
            displayName = "Speed Limit",
          ),
          DataFieldSpec(name = "source", typeId = "String"),
        ),
      stalenessThresholdMs = Long.MAX_VALUE,
    )

  override fun provideState(): Flow<SpeedLimitSnapshot> =
    settingsStore.getSetting(PACK_ID, PROVIDER_ID, SETTING_KEY).map { value ->
      SpeedLimitSnapshot(
        speedLimitKph = (value as? Number)?.toFloat() ?: 0f,
        source = "user",
        timestamp = SystemClock.elapsedRealtimeNanos(),
      )
    }

  internal companion object {
    const val PACK_ID: String = "essentials"
    const val PROVIDER_ID: String = "speed-limit"
    const val SETTING_KEY: String = "value"
  }
}
