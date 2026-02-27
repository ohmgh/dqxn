package app.dqxn.android.app.lifecycle

import android.app.Activity
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.common.truth.Truth.assertThat
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Unit tests for [AppUpdateCoordinator].
 *
 * Uses MockK-based approach since FakeAppUpdateManager requires real Android Context (incompatible
 * with JUnit5 pure JVM tests).
 */
@DisplayName("AppUpdateCoordinator")
class AppUpdateCoordinatorTest {

  private val appUpdateManager = mockk<AppUpdateManager>(relaxed = true)
  private val activity = mockk<Activity>(relaxed = true)
  private val appUpdateInfo = mockk<AppUpdateInfo>(relaxed = true)

  private lateinit var coordinator: AppUpdateCoordinator

  @BeforeEach
  fun setup() {
    coordinator = AppUpdateCoordinator(appUpdateManager)
  }

  private fun setupUpdateAvailable(priority: Int) {
    val task = mockk<Task<AppUpdateInfo>>()
    every { appUpdateManager.appUpdateInfo } returns task

    val listenerSlot = slot<OnSuccessListener<AppUpdateInfo>>()
    every { task.addOnSuccessListener(capture(listenerSlot)) } answers
      {
        listenerSlot.captured.onSuccess(appUpdateInfo)
        task
      }

    every { appUpdateInfo.updateAvailability() } returns UpdateAvailability.UPDATE_AVAILABLE
    every { appUpdateInfo.updatePriority() } returns priority
    every { appUpdateInfo.isUpdateTypeAllowed(any<Int>()) } returns true
    every {
      appUpdateManager.startUpdateFlowForResult(any(), any<Activity>(), any(), any())
    } returns true
  }

  private fun setupNoUpdateAvailable() {
    val task = mockk<Task<AppUpdateInfo>>()
    every { appUpdateManager.appUpdateInfo } returns task

    val listenerSlot = slot<OnSuccessListener<AppUpdateInfo>>()
    every { task.addOnSuccessListener(capture(listenerSlot)) } answers
      {
        listenerSlot.captured.onSuccess(appUpdateInfo)
        task
      }

    every { appUpdateInfo.updateAvailability() } returns UpdateAvailability.UPDATE_NOT_AVAILABLE
  }

  @Test
  @DisplayName("triggers IMMEDIATE flow when update priority >= 4")
  fun `immediate update triggered for critical priority`() {
    setupUpdateAvailable(priority = 4)

    coordinator.checkForUpdate(activity)

    val optionsSlot = slot<AppUpdateOptions>()
    verify {
      appUpdateManager.startUpdateFlowForResult(
        appUpdateInfo,
        activity,
        capture(optionsSlot),
        any(),
      )
    }
    assertThat(optionsSlot.captured.appUpdateType()).isEqualTo(AppUpdateType.IMMEDIATE)
  }

  @Test
  @DisplayName("triggers IMMEDIATE flow when update priority is 5")
  fun `immediate update triggered for highest priority`() {
    setupUpdateAvailable(priority = 5)

    coordinator.checkForUpdate(activity)

    val optionsSlot = slot<AppUpdateOptions>()
    verify {
      appUpdateManager.startUpdateFlowForResult(
        appUpdateInfo,
        activity,
        capture(optionsSlot),
        any(),
      )
    }
    assertThat(optionsSlot.captured.appUpdateType()).isEqualTo(AppUpdateType.IMMEDIATE)
  }

  @Test
  @DisplayName("triggers FLEXIBLE flow when update priority < 4")
  fun `flexible update triggered for non-critical priority`() {
    setupUpdateAvailable(priority = 2)

    coordinator.checkForUpdate(activity)

    val optionsSlot = slot<AppUpdateOptions>()
    verify {
      appUpdateManager.startUpdateFlowForResult(
        appUpdateInfo,
        activity,
        capture(optionsSlot),
        any(),
      )
    }
    assertThat(optionsSlot.captured.appUpdateType()).isEqualTo(AppUpdateType.FLEXIBLE)
  }

  @Test
  @DisplayName("does not start update flow when no update available")
  fun `no update flow when update not available`() {
    setupNoUpdateAvailable()

    coordinator.checkForUpdate(activity)

    verify(exactly = 0) {
      appUpdateManager.startUpdateFlowForResult(any(), any<Activity>(), any(), any())
    }
  }

  @Test
  @DisplayName("does not start update flow when update type not allowed")
  fun `no update flow when type not allowed`() {
    setupUpdateAvailable(priority = 4)
    every { appUpdateInfo.isUpdateTypeAllowed(any<Int>()) } returns false

    coordinator.checkForUpdate(activity)

    verify(exactly = 0) {
      appUpdateManager.startUpdateFlowForResult(any(), any<Activity>(), any(), any())
    }
  }

  @Test
  @DisplayName("registers install state listener on checkForUpdate")
  fun `registers listener on check`() {
    setupNoUpdateAvailable()

    coordinator.checkForUpdate(activity)

    verify { appUpdateManager.registerListener(any()) }
  }

  @Test
  @DisplayName("unregisters listener on unregisterListener call")
  fun `unregisters listener`() {
    coordinator.unregisterListener()

    verify { appUpdateManager.unregisterListener(any()) }
  }

  @Test
  @DisplayName("completeUpdate called when install status is DOWNLOADED")
  fun `complete update on downloaded status`() {
    // Capture the install state listener
    val listenerSlot = slot<InstallStateUpdatedListener>()
    every { appUpdateManager.registerListener(capture(listenerSlot)) } just Runs

    setupNoUpdateAvailable()
    coordinator.checkForUpdate(activity)

    // Simulate DOWNLOADED state
    val installState = mockk<com.google.android.play.core.install.InstallState>(relaxed = true)
    every { installState.installStatus() } returns
      com.google.android.play.core.install.model.InstallStatus.DOWNLOADED

    listenerSlot.captured.onStateUpdate(installState)

    verify { appUpdateManager.completeUpdate() }
  }
}
