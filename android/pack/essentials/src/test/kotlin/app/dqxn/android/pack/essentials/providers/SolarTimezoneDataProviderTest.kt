package app.dqxn.android.pack.essentials.providers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import app.dqxn.android.pack.essentials.snapshots.SolarSnapshot
import app.dqxn.android.sdk.contracts.provider.DataProvider
import app.dqxn.android.sdk.contracts.provider.ProviderPriority
import app.dqxn.android.sdk.contracts.testing.DataProviderContractTest
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class SolarTimezoneDataProviderTest : DataProviderContractTest() {

  private val receiverSlot = slot<BroadcastReceiver>()
  private val mockContext: Context =
    mockk<Context>(relaxed = true).also { ctx ->
      every { ctx.registerReceiver(capture(receiverSlot), any<IntentFilter>()) } returns mockk()
    }

  override fun createProvider(): DataProvider<*> = SolarTimezoneDataProvider(context = mockContext)

  @Test
  fun `sourceId follows convention`() {
    assertThat(createProvider().sourceId).isEqualTo("essentials:solar-timezone")
  }

  @Test
  fun `dataType is solar`() {
    assertThat(createProvider().dataType).isEqualTo("solar")
  }

  @Test
  fun `priority is DEVICE_SENSOR`() {
    assertThat(createProvider().priority).isEqualTo(ProviderPriority.DEVICE_SENSOR)
  }

  @Test
  fun `setupSchema is empty`() {
    assertThat(createProvider().setupSchema).isEmpty()
  }

  @Test
  fun `isAvailable is always true`() {
    assertThat(createProvider().isAvailable).isTrue()
  }

  @Test
  fun `snapshotType is SolarSnapshot`() {
    assertThat(createProvider().snapshotType).isEqualTo(SolarSnapshot::class)
  }

  @Test
  fun `provideState emits timezone sourceMode`() = runTest {
    val snapshot = createProvider().provideState().first() as SolarSnapshot
    assertThat(snapshot.sourceMode).isEqualTo("timezone")
    assertThat(snapshot.timestamp).isGreaterThan(0L)
  }

  @Test
  fun `timezone change broadcast does not crash`() = runTest {
    val provider = createProvider()
    provider.provideState().first()
    if (receiverSlot.isCaptured) {
      receiverSlot.captured.onReceive(mockContext, Intent(Intent.ACTION_TIMEZONE_CHANGED))
    }
  }
}
