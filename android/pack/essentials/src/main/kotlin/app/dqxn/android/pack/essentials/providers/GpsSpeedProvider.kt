package app.dqxn.android.pack.essentials.providers

import android.Manifest
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import app.dqxn.android.pack.essentials.snapshots.SpeedSnapshot
import app.dqxn.android.sdk.contracts.annotation.DashboardDataProvider
import app.dqxn.android.sdk.contracts.provider.DataFieldSpec
import app.dqxn.android.sdk.contracts.provider.DataProvider
import app.dqxn.android.sdk.contracts.provider.DataSchema
import app.dqxn.android.sdk.contracts.provider.DataTypes
import app.dqxn.android.sdk.contracts.provider.ProviderPriority
import app.dqxn.android.sdk.contracts.setup.SetupDefinition
import app.dqxn.android.sdk.contracts.setup.SetupPageDefinition
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOf

/**
 * GPS speed data provider. Emits [SpeedSnapshot] from [LocationManager.GPS_PROVIDER].
 *
 * Speed extraction: prefers hardware-reported [Location.getSpeed] when [Location.hasSpeed] is true.
 * Falls back to computing speed from consecutive locations (distance / timeDelta). Computed speed
 * has `accuracy = null` to distinguish from hardware-reported accuracy.
 *
 * **NF-P1 CRITICAL:** No [Location] references are retained. Speed is extracted immediately and the
 * Location object is released. The transient `lastLocation` variable used for fallback speed
 * computation is overwritten on each update.
 */
@DashboardDataProvider(
  localId = "gps-speed",
  displayName = "GPS Speed",
  description = "Vehicle speed from GPS hardware",
)
@Singleton
class GpsSpeedProvider
@Inject
constructor(
  private val locationManager: LocationManager,
) : DataProvider<SpeedSnapshot> {

  override val snapshotType: KClass<SpeedSnapshot> = SpeedSnapshot::class
  override val sourceId: String = "essentials:gps-speed"
  override val displayName: String = "GPS Speed"
  override val description: String = "Vehicle speed from GPS hardware"
  override val dataType: String = DataTypes.SPEED
  override val priority: ProviderPriority = ProviderPriority.HARDWARE
  override val subscriberTimeout: Duration = 5.seconds
  override val firstEmissionTimeout: Duration = 10.seconds
  override val isAvailable: Boolean =
    locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
  override val requiredAnyEntitlement: Set<String>? = null
  override val schema: DataSchema =
    DataSchema(
      fields =
        listOf(
          DataFieldSpec(name = "speedMps", typeId = "Float", unit = "m/s"),
          DataFieldSpec(name = "accuracy", typeId = "Float", unit = "m/s"),
        ),
      stalenessThresholdMs = 3000L,
    )

  override val setupSchema: List<SetupPageDefinition> =
    listOf(
      SetupPageDefinition(
        id = "gps-speed-permissions",
        title = "GPS Permission",
        description = "Required for speed data",
        definitions =
          listOf(
            SetupDefinition.RuntimePermission(
              id = "gps-speed-location-permission",
              label = "Fine Location",
              description = "Access to GPS for speed measurement",
              permissions = listOf(Manifest.permission.ACCESS_FINE_LOCATION),
            )
          ),
      )
    )

  private val _connectionState: MutableStateFlow<Boolean> = MutableStateFlow(false)
  override val connectionState: Flow<Boolean> = _connectionState
  override val connectionErrorDescription: Flow<String?> = flowOf(null)

  override fun provideState(): Flow<SpeedSnapshot> =
    callbackFlow {
        var lastLocationTime: Long = 0L
        var lastLatitude: Double = 0.0
        var lastLongitude: Double = 0.0
        var hasLastLocation: Boolean = false

        val listener =
          object : LocationListener {
            override fun onLocationChanged(location: Location) {
              _connectionState.value = true

              val speedMps: Float
              val accuracy: Float?

              if (location.hasSpeed()) {
                speedMps = location.speed
                accuracy =
                  if (location.hasSpeedAccuracy()) location.speedAccuracyMetersPerSecond
                  else null
              } else if (hasLastLocation) {
                val timeDeltaS = (location.time - lastLocationTime) / 1000.0f
                if (timeDeltaS > 0f) {
                  val results = FloatArray(1)
                  Location.distanceBetween(
                    lastLatitude,
                    lastLongitude,
                    location.latitude,
                    location.longitude,
                    results,
                  )
                  speedMps = results[0] / timeDeltaS
                  accuracy = null
                } else {
                  speedMps = 0f
                  accuracy = null
                }
              } else {
                speedMps = 0f
                accuracy = null
              }

              lastLocationTime = location.time
              lastLatitude = location.latitude
              lastLongitude = location.longitude
              hasLastLocation = true

              trySend(
                SpeedSnapshot(
                  speedMps = speedMps,
                  accuracy = accuracy,
                  timestamp = System.nanoTime(),
                )
              )
            }

            override fun onProviderDisabled(provider: String) {
              _connectionState.value = false
            }

            override fun onProviderEnabled(provider: String) {}
          }

        try {
          locationManager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            MIN_TIME_MS,
            MIN_DISTANCE_M,
            listener,
            Looper.getMainLooper(),
          )
        } catch (e: SecurityException) {
          _connectionState.value = false
          close(e)
          return@callbackFlow
        }

        awaitClose {
          locationManager.removeUpdates(listener)
          _connectionState.value = false
        }
      }
      .conflate()

  internal companion object {
    const val MIN_TIME_MS: Long = 1000L
    const val MIN_DISTANCE_M: Float = 0f
  }
}
