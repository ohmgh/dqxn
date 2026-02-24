package app.dqxn.android.pack.essentials.providers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import app.dqxn.android.pack.essentials.snapshots.SolarSnapshot
import app.dqxn.android.sdk.contracts.annotation.DashboardDataProvider
import app.dqxn.android.sdk.contracts.provider.DataFieldSpec
import app.dqxn.android.sdk.contracts.provider.DataProvider
import app.dqxn.android.sdk.contracts.provider.DataSchema
import app.dqxn.android.sdk.contracts.provider.ProviderPriority
import app.dqxn.android.sdk.contracts.setup.SetupPageDefinition
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart

/**
 * Data provider for solar times using timezone-based coordinates.
 *
 * Always works without any permissions. Estimates coordinates from the device timezone
 * using [IanaTimezoneCoordinates], which provides sunrise/sunset accuracy within ~30 minutes
 * for most locations.
 *
 * Recalculates on timezone change (via [Intent.ACTION_TIMEZONE_CHANGED] broadcast) and at
 * midnight for daily refresh.
 */
@DashboardDataProvider(
  localId = "solar-timezone",
  displayName = "Solar (Timezone)",
  description = "Sunrise and sunset times based on timezone location",
)
@Singleton
internal class SolarTimezoneDataProvider @Inject constructor(
  @param:ApplicationContext private val context: Context,
) : DataProvider<SolarSnapshot> {

  override val sourceId: String = "essentials:solar-timezone"
  override val displayName: String = "Solar (Timezone)"
  override val description: String = "Sunrise and sunset times based on timezone location"
  override val dataType: String = "solar"
  override val priority: ProviderPriority = ProviderPriority.DEVICE_SENSOR
  override val snapshotType: KClass<SolarSnapshot> = SolarSnapshot::class
  override val requiredAnyEntitlement: Set<String>? = null

  override val schema: DataSchema = DataSchema(
    fields = listOf(
      DataFieldSpec(name = "sunriseEpochMillis", typeId = "long"),
      DataFieldSpec(name = "sunsetEpochMillis", typeId = "long"),
      DataFieldSpec(name = "solarNoonEpochMillis", typeId = "long"),
      DataFieldSpec(name = "isDaytime", typeId = "boolean"),
      DataFieldSpec(name = "sourceMode", typeId = "string"),
    ),
    stalenessThresholdMs = 3_600_000L, // 1 hour
  )

  override val setupSchema: List<SetupPageDefinition> = emptyList()
  override val subscriberTimeout: kotlin.time.Duration = 30.seconds
  override val firstEmissionTimeout: kotlin.time.Duration = 5.seconds
  override val isAvailable: Boolean = true
  override val connectionState: Flow<Boolean> = flowOf(true)
  override val connectionErrorDescription: Flow<String?> = flowOf(null)

  @OptIn(ExperimentalCoroutinesApi::class)
  override fun provideState(): Flow<SolarSnapshot> {
    // Timezone change events via broadcast receiver
    val timezoneChanges = callbackFlow {
      val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
          trySend(Unit)
        }
      }
      val filter = IntentFilter(Intent.ACTION_TIMEZONE_CHANGED)
      context.registerReceiver(receiver, filter)
      awaitClose { context.unregisterReceiver(receiver) }
    }

    // Combine: initial emit + timezone changes -> recalculate
    return timezoneChanges
      .map { getTimezoneCoordinates() }
      .onStart { emit(getTimezoneCoordinates()) }
      .flatMapLatest { coords ->
        flow {
          while (true) {
            val zoneId = ZoneId.systemDefault()
            val date = LocalDate.now(zoneId)
            val result = SolarCalculator.calculate(
              coords.first, coords.second, date, zoneId,
            )

            emit(
              SolarSnapshot(
                sunriseEpochMillis = result.sunriseEpochMillis,
                sunsetEpochMillis = result.sunsetEpochMillis,
                solarNoonEpochMillis = result.solarNoonEpochMillis,
                isDaytime = result.isDaytime,
                sourceMode = "timezone",
                timestamp = System.currentTimeMillis(),
              ),
            )

            // Delay until midnight for next day's recalculation
            val delayMs = computeDelayUntilMidnight(zoneId)
            delay(delayMs)
          }
        }
      }
  }

  private fun getTimezoneCoordinates(): Pair<Double, Double> {
    val tz = TimeZone.getDefault()
    IanaTimezoneCoordinates.getCoordinates(tz.id)?.let { return it }
    // Fallback: equatorial (0, 0) -- always has sunrise/sunset
    return 0.0 to 0.0
  }

  private fun computeDelayUntilMidnight(zoneId: ZoneId): Long {
    val now = ZonedDateTime.now(zoneId)
    val currentTime = now.toLocalTime()
    val bufferMs = 60_000L // 60s buffer past midnight
    return Duration.between(currentTime, LocalTime.MAX).toMillis() + 1 + bufferMs
  }
}
