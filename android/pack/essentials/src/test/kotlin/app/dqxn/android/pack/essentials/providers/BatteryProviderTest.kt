package app.dqxn.android.pack.essentials.providers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import app.dqxn.android.pack.essentials.snapshots.BatterySnapshot
import app.dqxn.android.sdk.contracts.provider.DataProvider
import app.dqxn.android.sdk.contracts.testing.DataProviderContractTest
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BatteryProviderTest : DataProviderContractTest() {

  private val mockContext: Context = mockk(relaxed = true) {
    every { registerReceiver(any<BroadcastReceiver>(), any<IntentFilter>()) } answers {
      val receiver = firstArg<BroadcastReceiver>()
      receiver.onReceive(this@mockk, createBatteryIntent(level = 75, scale = 100, status = BatteryManager.BATTERY_STATUS_DISCHARGING, temperature = 285))
      createBatteryIntent()
    }
  }

  override fun createProvider(): DataProvider<*> = BatteryProvider(context = mockContext)

  @Test fun `battery level 75 of 100`() { assertThat(BatteryProvider.extractBatterySnapshot(createBatteryIntent(level = 75, scale = 100))!!.level).isEqualTo(75) }
  @Test fun `battery level 50 of 200`() { assertThat(BatteryProvider.extractBatterySnapshot(createBatteryIntent(level = 50, scale = 200))!!.level).isEqualTo(25) }
  @Test fun `charging - charging status`() { assertThat(BatteryProvider.extractBatterySnapshot(createBatteryIntent(status = BatteryManager.BATTERY_STATUS_CHARGING))!!.isCharging).isTrue() }
  @Test fun `charging - full status`() { assertThat(BatteryProvider.extractBatterySnapshot(createBatteryIntent(status = BatteryManager.BATTERY_STATUS_FULL))!!.isCharging).isTrue() }
  @Test fun `charging - discharging status`() { assertThat(BatteryProvider.extractBatterySnapshot(createBatteryIntent(status = BatteryManager.BATTERY_STATUS_DISCHARGING))!!.isCharging).isFalse() }
  @Test fun `temperature 285 tenths to 28_5 celsius`() { assertThat(BatteryProvider.extractBatterySnapshot(createBatteryIntent(temperature = 285))!!.temperature).isEqualTo(28.5f) }
  @Test fun `temperature null when zero`() { assertThat(BatteryProvider.extractBatterySnapshot(createBatteryIntent(temperature = 0))!!.temperature).isNull() }
  @Test fun `returns null for invalid level`() { assertThat(BatteryProvider.extractBatterySnapshot(createBatteryIntent(level = -1))).isNull() }
  @Test fun `returns null for zero scale`() { assertThat(BatteryProvider.extractBatterySnapshot(createBatteryIntent(scale = 0))).isNull() }

  @Test
  fun `sticky broadcast delivers first emission immediately`() = runTest {
    val ctx: Context = mockk(relaxed = true) {
      every { registerReceiver(any<BroadcastReceiver>(), any<IntentFilter>()) } answers {
        firstArg<BroadcastReceiver>().onReceive(this@mockk, createBatteryIntent()); createBatteryIntent()
      }
    }
    val snapshot = BatteryProvider(context = ctx).provideState().first()
    assertThat(snapshot).isInstanceOf(BatterySnapshot::class.java)
    assertThat(snapshot.level).isEqualTo(75)
  }

  @Test
  fun `subsequent broadcasts update the flow`() = runTest {
    val receiverSlot = slot<BroadcastReceiver>()
    val ctx: Context = mockk(relaxed = true) {
      every { registerReceiver(capture(receiverSlot), any<IntentFilter>()) } answers {
        firstArg<BroadcastReceiver>().onReceive(this@mockk, createBatteryIntent(level = 50, scale = 100)); createBatteryIntent()
      }
    }
    val emissions = mutableListOf<BatterySnapshot>()
    val job = launch { BatteryProvider(context = ctx).provideState().collect { emissions.add(it) } }
    delay(50)
    assertThat(emissions).hasSize(1)
    assertThat(emissions[0].level).isEqualTo(50)
    receiverSlot.captured.onReceive(ctx, createBatteryIntent(level = 80, scale = 100, status = BatteryManager.BATTERY_STATUS_CHARGING))
    delay(50)
    assertThat(emissions).hasSize(2)
    assertThat(emissions[1].level).isEqualTo(80)
    assertThat(emissions[1].isCharging).isTrue()
    job.cancel()
  }

  companion object {
    internal fun createBatteryIntent(level: Int = 75, scale: Int = 100, status: Int = BatteryManager.BATTERY_STATUS_DISCHARGING, temperature: Int = 285): Intent = mockk<Intent>(relaxed = true) {
      every { getIntExtra(BatteryManager.EXTRA_LEVEL, -1) } returns level
      every { getIntExtra(BatteryManager.EXTRA_SCALE, -1) } returns scale
      every { getIntExtra(BatteryManager.EXTRA_STATUS, -1) } returns status
      every { getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) } returns temperature
    }
  }
}
