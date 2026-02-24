package app.dqxn.android.pack.essentials.providers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import app.dqxn.android.pack.essentials.snapshots.TimeSnapshot
import app.dqxn.android.sdk.contracts.provider.DataProvider
import app.dqxn.android.sdk.contracts.testing.DataProviderContractTest
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.TimeZone
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TimeDataProviderTest : DataProviderContractTest() {

  private val mockContext: Context = mockk(relaxed = true) {
    every { registerReceiver(any<BroadcastReceiver>(), any<IntentFilter>()) } returns null
    every { unregisterReceiver(any()) } returns Unit
  }

  override fun createProvider(): DataProvider<*> = TimeDataProvider(context = mockContext)

  @Test
  fun `emits TimeSnapshot with valid epoch and timezone`() = runTest {
    val provider = TimeDataProvider(context = mockContext)
    val snapshot = provider.provideState().first()
    assertThat(snapshot).isInstanceOf(TimeSnapshot::class.java)
    assertThat(snapshot.epochMillis).isGreaterThan(0L)
    assertThat(snapshot.zoneId).isEqualTo(TimeZone.getDefault().id)
    assertThat(snapshot.timestamp).isGreaterThan(0L)
  }

  @Test
  fun `registers timezone change broadcast receiver`() = runTest {
    val provider = TimeDataProvider(context = mockContext)
    provider.provideState().first()
    verify { mockContext.registerReceiver(any<BroadcastReceiver>(), any<IntentFilter>()) }
  }
}
