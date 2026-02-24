package app.dqxn.android.feature.dashboard.binding

import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
@Tag("fast")
class StorageMonitorTest {

  @Test
  fun `isLow starts as false`() {
    val monitor = createMonitor(isLowStorage = false)
    assertThat(monitor.isLow.value).isFalse()
  }

  @Test
  fun `startMonitoring detects low storage immediately`() = runTest {
    val monitor = createMonitor(isLowStorage = true)
    monitor.startMonitoring(backgroundScope)
    advanceTimeBy(1)

    assertThat(monitor.isLow.value).isTrue()
  }

  @Test
  fun `startMonitoring detects normal storage`() = runTest {
    val monitor = createMonitor(isLowStorage = false)
    monitor.startMonitoring(backgroundScope)
    advanceTimeBy(1)

    assertThat(monitor.isLow.value).isFalse()
  }

  @Test
  fun `polling detects storage change from normal to low`() = runTest {
    var isLow = false
    val monitor = createMonitor(storageChecker = { isLow })
    monitor.startMonitoring(backgroundScope)
    advanceTimeBy(1)

    assertThat(monitor.isLow.value).isFalse()

    // Storage drops
    isLow = true
    advanceTimeBy(StorageMonitor.POLL_INTERVAL_MS + 1)

    assertThat(monitor.isLow.value).isTrue()
  }

  @Test
  fun `polling detects storage recovery from low to normal`() = runTest {
    var isLow = true
    val monitor = createMonitor(storageChecker = { isLow })
    monitor.startMonitoring(backgroundScope)
    advanceTimeBy(1)

    assertThat(monitor.isLow.value).isTrue()

    // Storage recovers
    isLow = false
    advanceTimeBy(StorageMonitor.POLL_INTERVAL_MS + 1)

    assertThat(monitor.isLow.value).isFalse()
  }

  // -- Helpers --

  private fun createMonitor(
    isLowStorage: Boolean = false,
  ): StorageMonitor = createMonitor(storageChecker = { isLowStorage })

  private fun createMonitor(
    storageChecker: () -> Boolean,
  ): StorageMonitor {
    val context: android.content.Context = mockk(relaxed = true)
    return StorageMonitor(context = context).also {
      it.storageChecker = storageChecker
    }
  }
}
