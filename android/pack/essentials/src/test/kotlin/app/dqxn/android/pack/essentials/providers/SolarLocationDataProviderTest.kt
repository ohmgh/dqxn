package app.dqxn.android.pack.essentials.providers

import android.Manifest
import android.content.Context
import app.dqxn.android.pack.essentials.snapshots.SolarSnapshot
import app.dqxn.android.sdk.contracts.provider.ProviderPriority
import app.dqxn.android.sdk.contracts.setup.SetupDefinition
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import org.junit.jupiter.api.Test

class SolarLocationDataProviderTest {

  private val mockContext: Context = mockk(relaxed = true)

  private fun createProvider(): SolarLocationDataProvider =
    SolarLocationDataProvider(context = mockContext)

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
    assertThat(perms.flatMap { it.permissions }).contains(Manifest.permission.ACCESS_COARSE_LOCATION)
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
