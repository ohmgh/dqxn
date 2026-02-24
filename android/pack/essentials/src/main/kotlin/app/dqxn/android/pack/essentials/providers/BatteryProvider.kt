package app.dqxn.android.pack.essentials.providers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.SystemClock
import app.dqxn.android.pack.essentials.snapshots.BatterySnapshot
import app.dqxn.android.sdk.contracts.annotation.DashboardDataProvider
import app.dqxn.android.sdk.contracts.provider.DataFieldSpec
import app.dqxn.android.sdk.contracts.provider.DataProvider
import app.dqxn.android.sdk.contracts.provider.DataSchema
import app.dqxn.android.sdk.contracts.provider.DataTypes
import app.dqxn.android.sdk.contracts.provider.ProviderPriority
import app.dqxn.android.sdk.contracts.setup.SetupPageDefinition
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf

/**
 * Battery data provider. Emits [BatterySnapshot] with level (0-100), charging state, and
 * temperature in Celsius.
 *
 * Uses `ACTION_BATTERY_CHANGED` sticky broadcast — first emission is immediate upon registration.
 * This is a greenfield provider with no old codebase equivalent.
 */
@DashboardDataProvider(
  localId = "battery",
  displayName = "Battery",
  description = "Battery level, charging state, and temperature",
)
@Singleton
internal class BatteryProvider
@Inject
constructor(
  @param:ApplicationContext private val context: Context,
) : DataProvider<BatterySnapshot> {

  override val snapshotType: KClass<BatterySnapshot> = BatterySnapshot::class
  override val sourceId: String = "essentials:battery"
  override val displayName: String = "Battery"
  override val description: String = "Battery level, charging state, and temperature"
  override val dataType: String = DataTypes.BATTERY
  override val priority: ProviderPriority = ProviderPriority.DEVICE_SENSOR
  override val subscriberTimeout: Duration = 30.seconds
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
          DataFieldSpec(name = "level", typeId = "Int", unit = "%"),
          DataFieldSpec(name = "isCharging", typeId = "Boolean"),
          DataFieldSpec(name = "temperature", typeId = "Float", unit = "C"),
        ),
      stalenessThresholdMs = 60_000L,
    )

  override fun provideState(): Flow<BatterySnapshot> = callbackFlow {
    val receiver =
      object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
          val snapshot = extractBatterySnapshot(intent)
          if (snapshot != null) {
            trySend(snapshot)
          }
        }
      }

    // Register for battery changes — sticky broadcast delivers immediately
    context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

    awaitClose { context.unregisterReceiver(receiver) }
  }

  internal companion object {
    internal fun extractBatterySnapshot(intent: Intent): BatterySnapshot? {
      val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
      val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
      if (level < 0 || scale <= 0) return null

      val percentage = level * 100 / scale

      val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
      val isCharging =
        status == BatteryManager.BATTERY_STATUS_CHARGING ||
          status == BatteryManager.BATTERY_STATUS_FULL

      val rawTemperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
      val temperature = if (rawTemperature > 0) rawTemperature / 10f else null

      return BatterySnapshot(
        level = percentage,
        isCharging = isCharging,
        temperature = temperature,
        timestamp = SystemClock.elapsedRealtimeNanos(),
      )
    }
  }
}
