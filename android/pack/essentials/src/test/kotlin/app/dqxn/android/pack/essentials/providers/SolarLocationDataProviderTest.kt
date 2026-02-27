package app.dqxn.android.pack.essentials.providers

import android.Manifest
import android.content.Context
import android.location.Location
import android.os.Looper
import app.dqxn.android.pack.essentials.snapshots.SolarSnapshot
import app.dqxn.android.sdk.contracts.provider.DataProvider
import app.dqxn.android.sdk.contracts.provider.ProviderPriority
import app.dqxn.android.sdk.contracts.setup.SetupDefinition
import app.dqxn.android.sdk.contracts.testing.DataProviderContractTest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Test

class SolarLocationDataProviderTest : DataProviderContractTest() {

  private val mockContext: Context = mockk(relaxed = true)

  private val callbackSlot = slot<LocationCallback>()

  private val mockLocation: Location =
    mockk(relaxed = true) {
      every { latitude } returns 51.5074
      every { longitude } returns -0.1278
    }

  // Eagerly initialize the mock that auto-fires a location on requestLocationUpdates.
  // This must be initialized before the parent @BeforeEach calls createProvider().
  private val fusedClient: FusedLocationProviderClient =
    mockk<FusedLocationProviderClient>(relaxed = true).also { client ->
      every { client.requestLocationUpdates(any(), capture(callbackSlot), any<Looper>()) } answers
        {
          val callback = callbackSlot.captured
          val result =
            mockk<LocationResult>(relaxed = true) { every { lastLocation } returns mockLocation }
          callback.onLocationResult(result)
          mockk(relaxed = true) // Returns Task<Void>
        }
    }

  override fun createProvider(): DataProvider<*> =
    SolarLocationDataProvider(context = mockContext, fusedClient = fusedClient)

  @Test
  fun `sourceId follows convention`() {
    assertThat(createProvider().sourceId).isEqualTo("essentials:solar-location")
  }

  @Test
  fun `dataType is solar`() {
    assertThat(createProvider().dataType).isEqualTo("solar")
  }

  @Test
  fun `priority is HARDWARE`() {
    assertThat(createProvider().priority).isEqualTo(ProviderPriority.HARDWARE)
  }

  @Test
  fun `setupSchema requires ACCESS_COARSE_LOCATION`() {
    val allDefs = createProvider().setupSchema.flatMap { it.definitions }
    val perms = allDefs.filterIsInstance<SetupDefinition.RuntimePermission>()
    assertThat(perms).isNotEmpty()
    assertThat(perms.flatMap { it.permissions })
      .contains(Manifest.permission.ACCESS_COARSE_LOCATION)
  }

  @Test
  fun `isAvailable is true`() {
    assertThat(createProvider().isAvailable).isTrue()
  }

  @Test
  fun `snapshotType is SolarSnapshot`() {
    assertThat(createProvider().snapshotType).isEqualTo(SolarSnapshot::class)
  }

  @Test
  fun `no entitlement required`() {
    assertThat(createProvider().requiredAnyEntitlement).isNull()
  }
}
