package app.dqxn.android.pack.essentials.providers

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import app.dqxn.android.pack.essentials.snapshots.SolarSnapshot
import app.dqxn.android.sdk.contracts.annotation.DashboardDataProvider
import app.dqxn.android.sdk.contracts.provider.DataFieldSpec
import app.dqxn.android.sdk.contracts.provider.DataProvider
import app.dqxn.android.sdk.contracts.provider.DataSchema
import app.dqxn.android.sdk.contracts.provider.ProviderPriority
import app.dqxn.android.sdk.contracts.setup.SetupDefinition
import app.dqxn.android.sdk.contracts.setup.SetupPageDefinition
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf

/**
 * Data provider for solar times using passive GPS location.
 *
 * Requires [Manifest.permission.ACCESS_COARSE_LOCATION] permission. Uses
 * [FusedLocationProviderClient] with [Priority.PRIORITY_PASSIVE] to piggyback on other apps' GPS
 * requests -- no additional battery drain.
 *
 * Recalculates sunrise/sunset on each location update (interval: 30 minutes).
 */
@DashboardDataProvider(
  localId = "solar-location",
  displayName = "Solar (Location)",
  description = "Sunrise and sunset times based on GPS location",
)
@Singleton
public class SolarLocationDataProvider
@Inject
constructor(
  @param:ApplicationContext private val context: Context,
  private val fusedClient: FusedLocationProviderClient,
) : DataProvider<SolarSnapshot> {

  override val sourceId: String = "essentials:solar-location"
  override val displayName: String = "Solar (Location)"
  override val description: String = "Sunrise and sunset times based on GPS location"
  override val dataType: String = "solar"
  override val priority: ProviderPriority = ProviderPriority.HARDWARE
  override val snapshotType: KClass<SolarSnapshot> = SolarSnapshot::class
  override val requiredAnyEntitlement: Set<String>? = null

  override val schema: DataSchema =
    DataSchema(
      fields =
        listOf(
          DataFieldSpec(name = "sunriseEpochMillis", typeId = "long"),
          DataFieldSpec(name = "sunsetEpochMillis", typeId = "long"),
          DataFieldSpec(name = "solarNoonEpochMillis", typeId = "long"),
          DataFieldSpec(name = "isDaytime", typeId = "boolean"),
          DataFieldSpec(name = "sourceMode", typeId = "string"),
        ),
      stalenessThresholdMs = 3_600_000L, // 1 hour
    )

  override val setupSchema: List<SetupPageDefinition> =
    listOf(
      SetupPageDefinition(
        id = "permissions",
        title = "Permissions",
        description = "Location access enables accurate sunrise and sunset times for your area",
        definitions =
          listOf(
            SetupDefinition.RuntimePermission(
              id = "location",
              label = "Location Access",
              description = "Required for accurate sunrise/sunset times based on your location",
              permissions = listOf(Manifest.permission.ACCESS_COARSE_LOCATION),
            ),
          ),
      ),
    )

  override val subscriberTimeout: kotlin.time.Duration = 30.seconds
  override val firstEmissionTimeout: kotlin.time.Duration = 5.minutes
  override val isAvailable: Boolean = true
  override val connectionState: Flow<Boolean> = flowOf(true)
  override val connectionErrorDescription: Flow<String?> = flowOf(null)

  @SuppressLint("MissingPermission")
  override fun provideState(): Flow<SolarSnapshot> = callbackFlow {
    val locationRequest =
      LocationRequest.Builder(
          Priority.PRIORITY_PASSIVE,
          30 * 60 * 1000L, // 30 minutes interval
        )
        .build()

    val callback =
      object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
          val location = result.lastLocation ?: return
          val zoneId = ZoneId.systemDefault()
          val date = LocalDate.now(zoneId)
          val solar =
            SolarCalculator.calculate(
              location.latitude,
              location.longitude,
              date,
              zoneId,
            )
          trySend(
            SolarSnapshot(
              sunriseEpochMillis = solar.sunriseEpochMillis,
              sunsetEpochMillis = solar.sunsetEpochMillis,
              solarNoonEpochMillis = solar.solarNoonEpochMillis,
              isDaytime = solar.isDaytime,
              sourceMode = "location",
              timestamp = System.currentTimeMillis(),
            ),
          )
        }
      }

    fusedClient.requestLocationUpdates(
      locationRequest,
      callback,
      Looper.getMainLooper(),
    )

    awaitClose { fusedClient.removeLocationUpdates(callback) }
  }
}
