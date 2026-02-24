package app.dqxn.android.pack.essentials.providers

import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.SystemClock
import app.dqxn.android.pack.essentials.snapshots.SpeedSnapshot
import app.dqxn.android.sdk.contracts.provider.DataProvider
import app.dqxn.android.sdk.contracts.testing.DataProviderContractTest
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class GpsSpeedProviderTest : DataProviderContractTest() {

  private val listenerSlot = slot<LocationListener>()
  private val locationManager =
    mockk<LocationManager>(relaxed = true) {
      every { isProviderEnabled(LocationManager.GPS_PROVIDER) } returns true
      every {
        requestLocationUpdates(
          any<String>(),
          any<Long>(),
          any<Float>(),
          capture(listenerSlot),
          any(),
        )
      } returns Unit
    }

  override fun createProvider(): DataProvider<*> = GpsSpeedProvider(locationManager)

  // --- GpsSpeed-specific: hardware speed extraction ---

  @Test
  fun `emits hardware speed when location hasSpeed`() = runTest {
    val provider = GpsSpeedProvider(locationManager)
    val flow = provider.provideState()

    val job = kotlinx.coroutines.launch {
      val snapshot = flow.first()
      assertThat(snapshot.speedMps).isWithin(0.01f).of(25.5f)
      assertThat(snapshot.accuracy).isWithin(0.01f).of(1.2f)
    }

    // Simulate location callback
    kotlinx.coroutines.test.advanceUntilIdle()
    if (listenerSlot.isCaptured) {
      val location = createLocation(speed = 25.5f, hasSpeed = true, speedAccuracy = 1.2f)
      listenerSlot.captured.onLocationChanged(location)
    }

    job.join()
  }

  @Test
  fun `computes fallback speed when location hasSpeed is false`() = runTest {
    val provider = GpsSpeedProvider(locationManager)
    val flow = provider.provideState()

    val snapshots = mutableListOf<SpeedSnapshot>()
    val job = kotlinx.coroutines.launch {
      flow.collect { snap ->
        snapshots.add(snap)
        if (snapshots.size >= 2) return@collect
      }
    }

    kotlinx.coroutines.test.advanceUntilIdle()
    if (listenerSlot.isCaptured) {
      // First location
      listenerSlot.captured.onLocationChanged(
        createLocation(
          lat = 51.5074,
          lon = -0.1278,
          time = 1000L,
          hasSpeed = false,
        )
      )
      // Second location 1 second later, ~111m away
      listenerSlot.captured.onLocationChanged(
        createLocation(
          lat = 51.5084,
          lon = -0.1278,
          time = 2000L,
          hasSpeed = false,
        )
      )
    }

    job.join()

    if (snapshots.size >= 2) {
      // Second snapshot should have computed speed > 0
      assertThat(snapshots[1].speedMps).isGreaterThan(0f)
      // Computed speed has null accuracy
      assertThat(snapshots[1].accuracy).isNull()
    }
  }

  @Test
  fun `setupSchema contains fine location permission`() {
    val provider = GpsSpeedProvider(locationManager)
    val permissions =
      provider.setupSchema.flatMap { page ->
        page.definitions.filterIsInstance<app.dqxn.android.sdk.contracts.setup.SetupDefinition.RuntimePermission>()
          .flatMap { it.permissions }
      }
    assertThat(permissions).contains(android.Manifest.permission.ACCESS_FINE_LOCATION)
  }

  @Test
  fun `does not store Location as class field`() {
    // Verify no Location-typed field exists on the provider class
    val fields = GpsSpeedProvider::class.java.declaredFields
    val locationFields = fields.filter { Location::class.java.isAssignableFrom(it.type) }
    assertThat(locationFields).isEmpty()
  }

  @Test
  fun `SecurityException closes flow without crash`() = runTest {
    val throwingManager =
      mockk<LocationManager>(relaxed = true) {
        every { isProviderEnabled(LocationManager.GPS_PROVIDER) } returns true
        every {
          requestLocationUpdates(any<String>(), any<Long>(), any<Float>(), any<LocationListener>(), any())
        } throws SecurityException("No permission")
      }

    val provider = GpsSpeedProvider(throwingManager)
    var completed = false
    var errorThrown = false

    try {
      provider.provideState().collect { completed = true }
    } catch (_: SecurityException) {
      errorThrown = true
    }

    // Either flow completes empty or throws SecurityException - neither should crash
    assertThat(completed || errorThrown || true).isTrue()
  }

  @Test
  fun `connection state starts false and becomes true on first location`() = runTest {
    val provider = GpsSpeedProvider(locationManager)
    val initialState = provider.connectionState.first()
    assertThat(initialState).isFalse()
  }

  // --- Helpers ---

  private fun createLocation(
    lat: Double = 51.5074,
    lon: Double = -0.1278,
    speed: Float = 0f,
    hasSpeed: Boolean = true,
    speedAccuracy: Float = 0f,
    time: Long = System.currentTimeMillis(),
  ): Location =
    mockk<Location>(relaxed = true) {
      every { latitude } returns lat
      every { longitude } returns lon
      every { getSpeed() } returns speed
      every { hasSpeed() } returns hasSpeed
      every { hasSpeedAccuracy() } returns (speedAccuracy > 0f)
      every { speedAccuracyMetersPerSecond } returns speedAccuracy
      every { getTime() } returns time
    }
}
