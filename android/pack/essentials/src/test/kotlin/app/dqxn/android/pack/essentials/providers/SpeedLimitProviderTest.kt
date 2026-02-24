package app.dqxn.android.pack.essentials.providers

import app.dqxn.android.sdk.contracts.provider.DataProvider
import app.dqxn.android.sdk.contracts.settings.ProviderSettingsStore
import app.dqxn.android.sdk.contracts.testing.DataProviderContractTest
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SpeedLimitProviderTest : DataProviderContractTest() {

  private val settingFlow = MutableStateFlow<Any?>(60f)
  private val settingsStore =
    mockk<ProviderSettingsStore>(relaxed = true) {
      every { getSetting("essentials", "speed-limit", "value") } returns settingFlow
    }

  override fun createProvider(): DataProvider<*> = SpeedLimitProvider(settingsStore)

  // --- SpeedLimit-specific: settings-driven emission ---

  @Test
  fun `emits configured speed limit from settings`() = runTest {
    settingFlow.value = 60f
    val provider = SpeedLimitProvider(settingsStore)
    val snapshot = provider.provideState().first()
    assertThat(snapshot.speedLimitKph).isWithin(0.01f).of(60f)
    assertThat(snapshot.source).isEqualTo("user")
  }

  @Test
  fun `emits default 0 when setting is absent`() = runTest {
    settingFlow.value = null
    val provider = SpeedLimitProvider(settingsStore)
    val snapshot = provider.provideState().first()
    assertThat(snapshot.speedLimitKph).isWithin(0.01f).of(0f)
  }

  @Test
  fun `reads from correct settings path`() = runTest {
    val provider = SpeedLimitProvider(settingsStore)
    provider.provideState().first()
    verify { settingsStore.getSetting("essentials", "speed-limit", "value") }
  }

  @Test
  fun `emits updated value when setting changes`() = runTest {
    settingFlow.value = 60f
    val provider = SpeedLimitProvider(settingsStore)

    val snapshots = mutableListOf<app.dqxn.android.pack.essentials.snapshots.SpeedLimitSnapshot>()
    val job = launch {
      provider.provideState().collect { snap ->
        snapshots.add(snap)
        if (snapshots.size >= 2) return@collect
      }
    }

    advanceUntilIdle()
    settingFlow.value = 100f
    advanceUntilIdle()

    job.join()

    assertThat(snapshots).hasSize(2)
    assertThat(snapshots[0].speedLimitKph).isWithin(0.01f).of(60f)
    assertThat(snapshots[1].speedLimitKph).isWithin(0.01f).of(100f)
  }

  @Test
  fun `handles integer setting value`() = runTest {
    settingFlow.value = 80
    val provider = SpeedLimitProvider(settingsStore)
    val snapshot = provider.provideState().first()
    assertThat(snapshot.speedLimitKph).isWithin(0.01f).of(80f)
  }

  @Test
  fun `priority is SIMULATED`() {
    val provider = SpeedLimitProvider(settingsStore)
    assertThat(provider.priority)
      .isEqualTo(app.dqxn.android.sdk.contracts.provider.ProviderPriority.SIMULATED)
  }
}
