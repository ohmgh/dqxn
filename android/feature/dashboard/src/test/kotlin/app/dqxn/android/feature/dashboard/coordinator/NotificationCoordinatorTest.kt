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
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
  fun `safe mode active shows CRITICAL banner`() = runTest(UnconfinedTestDispatcher()) {
    val coordinator = createCoordinator()
    coordinator.initialize(backgroundScope)

    safeModeActive.value = true

    val banners = coordinator.activeBanners.value
    assertThat(banners).hasSize(1)
    assertThat(banners.first().id).isEqualTo("safe_mode")
    assertThat(banners.first().priority).isEqualTo(NotificationPriority.CRITICAL)
    assertThat(banners.first().message).contains("repeated crashes")
  }

  @Test
  fun `safe mode inactive dismisses banner`() = runTest(UnconfinedTestDispatcher()) {
    val coordinator = createCoordinator()
    coordinator.initialize(backgroundScope)

    safeModeActive.value = true
    assertThat(coordinator.activeBanners.value).hasSize(1)

    safeModeActive.value = false
    assertThat(coordinator.activeBanners.value).isEmpty()
  }

  @Test
  fun `low storage shows HIGH banner`() = runTest(UnconfinedTestDispatcher()) {
    val coordinator = createCoordinator()
    coordinator.initialize(backgroundScope)

    storageIsLow.value = true

    val banners = coordinator.activeBanners.value
    assertThat(banners).hasSize(1)
    assertThat(banners.first().id).isEqualTo("low_storage")
    assertThat(banners.first().priority).isEqualTo(NotificationPriority.HIGH)
    assertThat(banners.first().message).contains("running low")
  }

  @Test
  fun `layout save failure shows HIGH banner with specific message`() = runTest(UnconfinedTestDispatcher()) {
    val coordinator = createCoordinator()
    coordinator.initialize(backgroundScope)

    coordinator.reportLayoutSaveFailure()

    val banners = coordinator.activeBanners.value
    assertThat(banners).hasSize(1)
    assertThat(banners.first().id).isEqualTo("layout_save_failed")
    assertThat(banners.first().priority).isEqualTo(NotificationPriority.HIGH)
    assertThat(banners.first().message).contains("Unable to save layout")
  }

  @Test
  fun `banner priority ordering CRITICAL before HIGH before NORMAL`() = runTest(UnconfinedTestDispatcher()) {
    val coordinator = createCoordinator()
    coordinator.initialize(backgroundScope)

    // Add banners in reverse priority order
    coordinator.emitConnectionStatus("SensorX", connected = false) // NORMAL
    storageIsLow.value = true // HIGH
    safeModeActive.value = true // CRITICAL

    val banners = coordinator.activeBanners.value
    assertThat(banners).hasSize(3)
    assertThat(banners[0].priority).isEqualTo(NotificationPriority.CRITICAL)
    assertThat(banners[1].priority).isEqualTo(NotificationPriority.HIGH)
    assertThat(banners[2].priority).isEqualTo(NotificationPriority.NORMAL)
  }

  @Test
  fun `condition-keyed banners same id updates in-place no duplicate`() = runTest(UnconfinedTestDispatcher()) {
    val coordinator = createCoordinator()
    coordinator.initialize(backgroundScope)

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
  }

  @Test
  fun `re-derivation on recreation all condition banners re-derived from current singleton state`() =
    runTest(UnconfinedTestDispatcher()) {
      // Set safe mode active BEFORE creating coordinator (simulates process death recovery)
      safeModeActive.value = true
      storageIsLow.value = true

      val coordinator = createCoordinator()
      coordinator.initialize(backgroundScope)

      // Banners should be present immediately from singleton state, no explicit events needed
      val banners = coordinator.activeBanners.value
      assertThat(banners).hasSize(2)
      assertThat(banners.any { it.id == "safe_mode" }).isTrue()
      assertThat(banners.any { it.id == "low_storage" }).isTrue()
    }

  @Test
  fun `showToast delivers to channel exactly once`() = runTest(UnconfinedTestDispatcher()) {
    val coordinator = createCoordinator()
    coordinator.initialize(backgroundScope)

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
  }

  @Test
  fun `connection status disconnect shows NORMAL banner connect dismisses`() =
    runTest(UnconfinedTestDispatcher()) {
      val coordinator = createCoordinator()
      coordinator.initialize(backgroundScope)

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
    }

  @Test
  fun `safe mode banner has VIBRATE alert profile`() = runTest(UnconfinedTestDispatcher()) {
    val coordinator = createCoordinator()
    coordinator.initialize(backgroundScope)

    safeModeActive.value = true

    val banner = coordinator.activeBanners.value.first()
    assertThat(banner.alertProfile).isNotNull()
    assertThat(banner.alertProfile!!.mode).isEqualTo(AlertMode.VIBRATE)
  }

  @Test
  fun `clearLayoutSaveFailure dismisses the save failure banner`() =
    runTest(UnconfinedTestDispatcher()) {
      val coordinator = createCoordinator()
      coordinator.initialize(backgroundScope)

      coordinator.reportLayoutSaveFailure()
      assertThat(coordinator.activeBanners.value).hasSize(1)

      coordinator.clearLayoutSaveFailure()
      assertThat(coordinator.activeBanners.value).isEmpty()
    }
}
