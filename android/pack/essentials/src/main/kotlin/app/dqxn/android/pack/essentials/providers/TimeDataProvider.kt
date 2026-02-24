package app.dqxn.android.pack.essentials.providers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import app.dqxn.android.pack.essentials.snapshots.TimeSnapshot
import app.dqxn.android.sdk.contracts.annotation.DashboardDataProvider
import app.dqxn.android.sdk.contracts.provider.DataFieldSpec
import app.dqxn.android.sdk.contracts.provider.DataProvider
import app.dqxn.android.sdk.contracts.provider.DataSchema
import app.dqxn.android.sdk.contracts.provider.DataTypes
import app.dqxn.android.sdk.contracts.provider.ProviderPriority
import app.dqxn.android.sdk.contracts.setup.SetupPageDefinition
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.isActive

/**
 * System clock data provider. Emits [TimeSnapshot] every 1 second containing epoch millis, IANA
 * timezone ID, and monotonic timestamp.
 *
 * Reacts to `ACTION_TIMEZONE_CHANGED` broadcasts to update the zone ID immediately.
 */
@DashboardDataProvider(
  localId = "time",
  displayName = "Time",
  description = "System clock with timezone tracking",
)
@Singleton
internal class TimeDataProvider
@Inject
constructor(
  @param:ApplicationContext private val context: Context,
) : DataProvider<TimeSnapshot> {

  override val snapshotType: KClass<TimeSnapshot> = TimeSnapshot::class
  override val sourceId: String = "essentials:time"
  override val displayName: String = "Time"
  override val description: String = "System clock with timezone tracking"
  override val dataType: String = DataTypes.TIME
  override val priority: ProviderPriority = ProviderPriority.DEVICE_SENSOR
  override val subscriberTimeout: Duration = 1.seconds
  override val firstEmissionTimeout: Duration = 2.seconds
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

  override fun provideState(): Flow<TimeSnapshot> = callbackFlow {
    var currentZoneId = TimeZone.getDefault().id

    val receiver =
      object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
          if (intent.action == Intent.ACTION_TIMEZONE_CHANGED) {
            currentZoneId = TimeZone.getDefault().id
          }
        }
      }

    context.registerReceiver(receiver, IntentFilter(Intent.ACTION_TIMEZONE_CHANGED))

    while (isActive) {
      send(
        TimeSnapshot(
          epochMillis = System.currentTimeMillis(),
          zoneId = currentZoneId,
          timestamp = SystemClock.elapsedRealtimeNanos(),
        )
      )
      delay(1000)
    }

    awaitClose { context.unregisterReceiver(receiver) }
  }
}
