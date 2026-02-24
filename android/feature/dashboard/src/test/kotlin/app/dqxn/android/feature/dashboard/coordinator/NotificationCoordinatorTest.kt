package app.dqxn.android.feature.dashboard.coordinator

import app.dqxn.android.feature.dashboard.binding.StorageMonitor
import app.dqxn.android.feature.dashboard.safety.SafeModeManager
import app.dqxn.android.sdk.contracts.notification.AlertEmitter
import app.dqxn.android.sdk.contracts.notification.AlertMode
import app.dqxn.android.sdk.contracts.notification.InAppNotification
import app.dqxn.android.sdk.contracts.notification.NotificationPriority
import app.dqxn.android.sdk.observability.log.NoOpLogger
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
@Tag("fast")
class NotificationCoordinatorTest {

  private val logger = NoOpLogger

  private val safeModeActive = MutableStateFlow(false)
  private val storageIsLow = MutableStateFlow(false)

  private val safeModeManager: SafeModeManager = mockk(relaxed = true) {
    every { safeModeActive } returns this@NotificationCoordinatorTest.safeModeActive
  }

  private val storageMonitor: StorageMonitor = mockk(relaxed = true) {
    every { isLow } returns this@NotificationCoordinatorTest.storageIsLow
  }

  private val alertEmitter: AlertEmitter = mockk(relaxed = true) {
    coEvery { fire(any()) } returns app.dqxn.android.sdk.contracts.notification.AlertResult.PLAYED
  }

  private fun createCoordinator(): NotificationCoordinator =
    NotificationCoordinator(
      safeModeManager = safeModeManager,
      storageMonitor = storageMonitor,
      alertEmitter = alertEmitter,
      logger = logger,
    )

  @Test
  fun `safe mode active shows CRITICAL banner`() = runTest {
    val coordinator = createCoordinator()
    val initJob = Job(coroutineContext[Job])
    coordinator.initialize(this + initJob)
    testScheduler.runCurrent()

    safeModeActive.value = true
    testScheduler.runCurrent()

    val banners = coordinator.activeBanners.value
    assertThat(banners).hasSize(1)
    assertThat(banners.first().id).isEqualTo("safe_mode")
    assertThat(banners.first().priority).isEqualTo(NotificationPriority.CRITICAL)
    assertThat(banners.first().message).contains("repeated crashes")

    initJob.cancel()
  }

  @Test
  fun `safe mode inactive dismisses banner`() = runTest {
    val coordinator = createCoordinator()
    val initJob = Job(coroutineContext[Job])
    coordinator.initialize(this + initJob)
    testScheduler.runCurrent()

    safeModeActive.value = true
    testScheduler.runCurrent()
    assertThat(coordinator.activeBanners.value).hasSize(1)

    safeModeActive.value = false
    testScheduler.runCurrent()
    assertThat(coordinator.activeBanners.value).isEmpty()

    initJob.cancel()
  }

  @Test
  fun `low storage shows HIGH banner`() = runTest {
    val coordinator = createCoordinator()
    val initJob = Job(coroutineContext[Job])
    coordinator.initialize(this + initJob)
    testScheduler.runCurrent()

    storageIsLow.value = true
    testScheduler.runCurrent()

    val banners = coordinator.activeBanners.value
    assertThat(banners).hasSize(1)
    assertThat(banners.first().id).isEqualTo("low_storage")
    assertThat(banners.first().priority).isEqualTo(NotificationPriority.HIGH)
    assertThat(banners.first().message).contains("running low")

    initJob.cancel()
  }

  @Test
  fun `layout save failure shows HIGH banner with specific message`() = runTest {
    val coordinator = createCoordinator()
    val initJob = Job(coroutineContext[Job])
    coordinator.initialize(this + initJob)
    testScheduler.runCurrent()

    coordinator.reportLayoutSaveFailure()

    val banners = coordinator.activeBanners.value
    assertThat(banners).hasSize(1)
    assertThat(banners.first().id).isEqualTo("layout_save_failed")
    assertThat(banners.first().priority).isEqualTo(NotificationPriority.HIGH)
    assertThat(banners.first().message).contains("Unable to save layout")

    initJob.cancel()
  }

  @Test
  fun `banner priority ordering CRITICAL before HIGH before NORMAL`() = runTest {
    val coordinator = createCoordinator()
    val initJob = Job(coroutineContext[Job])
    coordinator.initialize(this + initJob)
    testScheduler.runCurrent()

    // Add banners in reverse priority order
    coordinator.emitConnectionStatus("SensorX", connected = false) // NORMAL
    storageIsLow.value = true // HIGH
    testScheduler.runCurrent()
    safeModeActive.value = true // CRITICAL
    testScheduler.runCurrent()

    val banners = coordinator.activeBanners.value
    assertThat(banners).hasSize(3)
    assertThat(banners[0].priority).isEqualTo(NotificationPriority.CRITICAL)
    assertThat(banners[1].priority).isEqualTo(NotificationPriority.HIGH)
    assertThat(banners[2].priority).isEqualTo(NotificationPriority.NORMAL)

    initJob.cancel()
  }

  @Test
  fun `condition-keyed banners same id updates in-place no duplicate`() = runTest {
    val coordinator = createCoordinator()
    val initJob = Job(coroutineContext[Job])
    coordinator.initialize(this + initJob)
    testScheduler.runCurrent()

    // Show the same banner twice — should result in one banner, not two
    coordinator.showBanner(
      id = "test_banner",
      priority = NotificationPriority.NORMAL,
      title = "Test",
      message = "First message",
    )
    coordinator.showBanner(
      id = "test_banner",
      priority = NotificationPriority.NORMAL,
      title = "Test",
      message = "Updated message",
    )

    val banners = coordinator.activeBanners.value
    assertThat(banners).hasSize(1)
    assertThat(banners.first().message).isEqualTo("Updated message")

    initJob.cancel()
  }

  @Test
  fun `re-derivation on recreation all condition banners re-derived from current singleton state`() =
    runTest {
      // Set safe mode active BEFORE creating coordinator (simulates process death recovery)
      safeModeActive.value = true
      storageIsLow.value = true

      val coordinator = createCoordinator()
      val initJob = Job(coroutineContext[Job])
      coordinator.initialize(this + initJob)
      testScheduler.runCurrent()

      // Banners should be present immediately from singleton state, no explicit events needed
      val banners = coordinator.activeBanners.value
      assertThat(banners).hasSize(2)
      assertThat(banners.any { it.id == "safe_mode" }).isTrue()
      assertThat(banners.any { it.id == "low_storage" }).isTrue()

      initJob.cancel()
    }

  @Test
  fun `showToast delivers to channel exactly once`() = runTest {
    val coordinator = createCoordinator()
    val initJob = Job(coroutineContext[Job])
    coordinator.initialize(this + initJob)
    testScheduler.runCurrent()

    val toast = InAppNotification.Toast(
      id = "test_toast",
      priority = NotificationPriority.NORMAL,
      timestamp = System.currentTimeMillis(),
      message = "Test toast",
    )

    coordinator.showToast(toast)

    val received = coordinator.toasts.tryReceive()
    assertThat(received.isSuccess).isTrue()
    assertThat(received.getOrNull()?.id).isEqualTo("test_toast")

    // Channel should be empty after consuming
    val second = coordinator.toasts.tryReceive()
    assertThat(second.isSuccess).isFalse()

    initJob.cancel()
  }

  @Test
  fun `connection status disconnect shows NORMAL banner connect dismisses`() =
    runTest {
      val coordinator = createCoordinator()
      val initJob = Job(coroutineContext[Job])
      coordinator.initialize(this + initJob)
      testScheduler.runCurrent()

      // Disconnect
      coordinator.emitConnectionStatus("OBD2 Scanner", connected = false)

      var banners = coordinator.activeBanners.value
      assertThat(banners).hasSize(1)
      assertThat(banners.first().priority).isEqualTo(NotificationPriority.NORMAL)
      assertThat(banners.first().message).contains("OBD2 Scanner")

      // Reconnect
      coordinator.emitConnectionStatus("OBD2 Scanner", connected = true)

      banners = coordinator.activeBanners.value
      assertThat(banners).isEmpty()

      initJob.cancel()
    }

  @Test
  fun `safe mode banner has VIBRATE alert profile`() = runTest {
    val coordinator = createCoordinator()
    val initJob = Job(coroutineContext[Job])
    coordinator.initialize(this + initJob)
    testScheduler.runCurrent()

    safeModeActive.value = true
    testScheduler.runCurrent()

    val banner = coordinator.activeBanners.value.first()
    assertThat(banner.alertProfile).isNotNull()
    assertThat(banner.alertProfile!!.mode).isEqualTo(AlertMode.VIBRATE)

    initJob.cancel()
  }

  @Test
  fun `clearLayoutSaveFailure dismisses the save failure banner`() =
    runTest {
      val coordinator = createCoordinator()
      val initJob = Job(coroutineContext[Job])
      coordinator.initialize(this + initJob)
      testScheduler.runCurrent()

      coordinator.reportLayoutSaveFailure()
      assertThat(coordinator.activeBanners.value).hasSize(1)

      coordinator.clearLayoutSaveFailure()
      assertThat(coordinator.activeBanners.value).isEmpty()

      initJob.cancel()
    }

  @Test
  fun `safe mode banner triggers alertEmitter fire with VIBRATE profile`() = runTest {
    val coordinator = createCoordinator()
    val initJob = Job(coroutineContext[Job])
    coordinator.initialize(this + initJob)
    testScheduler.runCurrent()

    safeModeActive.value = true
    testScheduler.runCurrent()

    // Verify alertEmitter.fire() was called with a VIBRATE alert profile (F9.2).
    // The previous test only verified the banner data contained AlertMode.VIBRATE — it never
    // verified the side-effect. This test ensures the production code actually fires the alert.
    coVerify { alertEmitter.fire(match { it.mode == AlertMode.VIBRATE }) }

    initJob.cancel()
  }

  @Test
  fun `banner without alert profile does not fire alertEmitter`() = runTest {
    val coordinator = createCoordinator()
    val initJob = Job(coroutineContext[Job])
    coordinator.initialize(this + initJob)
    testScheduler.runCurrent()

    // Low storage banner has no alert profile
    storageIsLow.value = true
    testScheduler.runCurrent()

    // alertEmitter.fire() should NOT be called for banners without an alert profile
    coVerify(exactly = 0) { alertEmitter.fire(any()) }

    initJob.cancel()
  }
}
