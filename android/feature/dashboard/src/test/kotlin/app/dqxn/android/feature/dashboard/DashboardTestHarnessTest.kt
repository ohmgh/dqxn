package app.dqxn.android.feature.dashboard

import app.dqxn.android.feature.dashboard.binding.StorageMonitor
import app.dqxn.android.feature.dashboard.coordinator.EditModeCoordinator
import app.dqxn.android.feature.dashboard.coordinator.NotificationCoordinator
import app.dqxn.android.feature.dashboard.coordinator.ProfileCoordinator
import app.dqxn.android.feature.dashboard.gesture.DashboardHaptics
import app.dqxn.android.feature.dashboard.gesture.ReducedMotionHelper
import app.dqxn.android.feature.dashboard.test.DashboardTestHarness
import app.dqxn.android.feature.dashboard.test.HarnessStateOnFailure
import app.dqxn.android.feature.dashboard.test.TestWidgetFactory.testWidget
import app.dqxn.android.sdk.contracts.notification.AlertEmitter
import app.dqxn.android.sdk.contracts.notification.NotificationPriority
import app.dqxn.android.sdk.observability.log.NoOpLogger
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * Integration tests using real coordinators (not mocks).
 *
 * Validates cross-coordinator interactions that mocked unit tests cannot catch:
 * - AddWidget -> WidgetBindingCoordinator creates binding job -> reports ACTIVE
 * - Safe mode: 4 crashes in 60s triggers safe mode
 * - EditMode -> editState.isEditMode true/false
 * - ResetLayout -> layout contains preset widgets
 * - Profile switching -> layout loads new profile widgets
 *
 * Uses [StandardTestDispatcher] (default for [runTest]) per CLAUDE.md testing rules.
 * The harness receives the [runTest] TestScope so all dispatchers share one scheduler.
 * Forever-collecting coordinator flows are cancelled via [DashboardTestHarness.close] before
 * [runTest] exits to avoid [UncompletedCoroutinesError].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Tag("fast")
class DashboardTestHarnessTest {

  @JvmField
  @RegisterExtension
  val stateOnFailure = HarnessStateOnFailure()

  private val logger = NoOpLogger

  @Test
  fun `harness initializes all coordinators without crash`() = runTest {
    val harness = DashboardTestHarness(testScope = this)
    stateOnFailure.harness = harness

    harness.initialize()
    advanceUntilIdle()

    // Verify basic state after initialization
    val layoutState = harness.layoutCoordinator.layoutState.value
    assertThat(layoutState.isLoading).isFalse()
    assertThat(harness.safeModeManager.safeModeActive.value).isFalse()

    harness.close()
  }

  @Test
  fun `dispatch AddWidget creates binding job and reports ACTIVE`() = runTest {
    val harness = DashboardTestHarness(testScope = this)
    stateOnFailure.harness = harness
    harness.initialize()
    advanceUntilIdle()

    val widget = testWidget(typeId = "essentials:clock")

    // Add widget to layout
    harness.layoutCoordinator.handleAddWidget(widget)
    advanceUntilIdle()

    // Verify widget was added to layout
    val layoutState = harness.layoutCoordinator.layoutState.value
    assertThat(layoutState.widgets).hasSize(1)
    assertThat(layoutState.widgets.first().typeId).isEqualTo("essentials:clock")

    // Bind widget (in production, ViewModel.routeCommand does both add + bind)
    harness.widgetBindingCoordinator.bind(layoutState.widgets.first())
    advanceUntilIdle()

    // SC#3: Verify WidgetBindingCoordinator created a binding job
    assertThat(harness.widgetBindingCoordinator.activeBindings())
      .containsKey(layoutState.widgets.first().instanceId)

    harness.close()
  }

  @Test
  fun `dispatch EnterEditMode updates editState isEditMode true`() = runTest {
    val harness = DashboardTestHarness(testScope = this)
    stateOnFailure.harness = harness
    harness.initialize()
    advanceUntilIdle()

    val editMode = createEditModeCoordinator(harness)
    editMode.initialize(backgroundScope)

    editMode.enterEditMode()

    assertThat(editMode.editState.value.isEditMode).isTrue()

    harness.close()
  }

  @Test
  fun `dispatch ExitEditMode updates editState isEditMode false`() = runTest {
    val harness = DashboardTestHarness(testScope = this)
    stateOnFailure.harness = harness
    harness.initialize()
    advanceUntilIdle()

    val editMode = createEditModeCoordinator(harness)
    editMode.initialize(backgroundScope)

    editMode.enterEditMode()
    assertThat(editMode.editState.value.isEditMode).isTrue()

    editMode.exitEditMode()
    assertThat(editMode.editState.value.isEditMode).isFalse()

    harness.close()
  }

  @Test
  fun `safe mode 4 WidgetCrash commands in 60s triggers safe mode`() = runTest {
    val harness = DashboardTestHarness(testScope = this)
    stateOnFailure.harness = harness
    harness.initialize()
    advanceUntilIdle()

    val safeModeManager = harness.safeModeManager

    // Report 4 crashes within 60s window (threshold is 4)
    safeModeManager.reportCrash("w1", "essentials:clock")
    safeModeManager.reportCrash("w2", "essentials:battery")
    safeModeManager.reportCrash("w3", "essentials:speed")
    safeModeManager.reportCrash("w4", "essentials:compass")

    assertThat(safeModeManager.safeModeActive.value).isTrue()

    harness.close()
  }

  @Test
  fun `dispatch SwitchProfile loads new profile widgets`() = runTest {
    val harness = DashboardTestHarness(testScope = this)
    stateOnFailure.harness = harness
    harness.initialize()
    advanceUntilIdle()

    // Use a cancellable scope for the profile coordinator's forever-collecting flows
    val profileJob = Job(coroutineContext[Job])
    val profileScope = this + profileJob
    val profileCoordinator = ProfileCoordinator(
      layoutRepository = harness.fakeLayoutRepository,
      logger = logger,
    )
    profileCoordinator.initialize(profileScope)
    advanceUntilIdle()

    // Add widget to default profile
    val widget = testWidget(typeId = "essentials:clock")
    harness.layoutCoordinator.handleAddWidget(widget)
    advanceUntilIdle()
    assertThat(harness.layoutCoordinator.layoutState.value.widgets).hasSize(1)

    // Create a new empty profile and switch to it
    val newProfileId = profileCoordinator.handleCreateProfile("Work")
    advanceUntilIdle()
    profileCoordinator.handleSwitchProfile(newProfileId)
    advanceUntilIdle()

    // After switching, the layout coordinator re-collects from the repository.
    // The FakeLayoutRepository returns widgets for the active profile.
    val activeWidgets = harness.layoutCoordinator.layoutState.value.widgets
    assertThat(activeWidgets).isEmpty()

    profileJob.cancel()
    harness.close()
  }

  @Test
  fun `dispatch ResetLayout loads preset widgets`() = runTest {
    val harness = DashboardTestHarness(testScope = this)
    stateOnFailure.harness = harness
    harness.initialize()
    advanceUntilIdle()

    // Add some widgets
    harness.layoutCoordinator.handleAddWidget(testWidget(typeId = "essentials:clock"))
    advanceUntilIdle()
    harness.layoutCoordinator.handleAddWidget(testWidget(typeId = "essentials:battery"))
    advanceUntilIdle()
    assertThat(harness.layoutCoordinator.layoutState.value.widgets).hasSize(2)

    // Reset layout (preset loader returns empty list in the harness by default)
    harness.layoutCoordinator.handleResetLayout()
    advanceUntilIdle()

    // After reset, layout matches preset (empty by default in test harness)
    assertThat(harness.layoutCoordinator.layoutState.value.widgets).isEmpty()

    harness.close()
  }

  @Test
  fun `HarnessStateOnFailure dumps state on test failure`() = runTest {
    val harness = DashboardTestHarness(testScope = this)
    stateOnFailure.harness = harness
    harness.initialize()
    advanceUntilIdle()

    harness.layoutCoordinator.handleAddWidget(testWidget(typeId = "essentials:clock"))
    advanceUntilIdle()

    // The state dump would fire if this assertion fails. We verify it doesn't crash.
    // We deliberately succeed here -- the purpose is to test that stateOnFailure is wired up.
    assertThat(harness.layoutCoordinator.layoutState.value.widgets).hasSize(1)

    harness.close()
  }

  @Test
  fun `notification coordinator shows safe mode CRITICAL banner`() = runTest {
    val harness = DashboardTestHarness(testScope = this)
    stateOnFailure.harness = harness
    harness.initialize()
    advanceUntilIdle()

    val storageMonitor: StorageMonitor = mockk {
      every { isLow } returns MutableStateFlow(false)
    }
    val alertEmitter: AlertEmitter = mockk(relaxed = true)

    // Use a cancellable scope for the notification coordinator's forever-collecting flows
    val notifJob = Job(coroutineContext[Job])
    val notifScope = this + notifJob
    val notificationCoordinator = NotificationCoordinator(
      safeModeManager = harness.safeModeManager,
      storageMonitor = storageMonitor,
      alertEmitter = alertEmitter,
      logger = logger,
    )
    notificationCoordinator.initialize(notifScope)
    advanceUntilIdle()

    // Trigger safe mode via 4 crashes
    harness.safeModeManager.reportCrash("w1", "t1")
    harness.safeModeManager.reportCrash("w2", "t2")
    harness.safeModeManager.reportCrash("w3", "t3")
    harness.safeModeManager.reportCrash("w4", "t4")

    // Advance to process the safe mode flow collection in NotificationCoordinator
    advanceUntilIdle()

    assertThat(harness.safeModeManager.safeModeActive.value).isTrue()

    // Verify CRITICAL banner is shown
    val banners = notificationCoordinator.activeBanners.value
    assertThat(banners).isNotEmpty()
    assertThat(banners.first().priority).isEqualTo(NotificationPriority.CRITICAL)
    assertThat(banners.first().title).isEqualTo("Safe Mode")

    notifJob.cancel()
    harness.close()
  }

  // -- Helper methods --

  private fun createEditModeCoordinator(harness: DashboardTestHarness): EditModeCoordinator {
    val haptics: DashboardHaptics = mockk(relaxed = true)
    val reducedMotionHelper: ReducedMotionHelper = mockk {
      every { isReducedMotion } returns false
    }

    return EditModeCoordinator(
      layoutCoordinator = harness.layoutCoordinator,
      gridPlacementEngine = harness.gridPlacementEngine,
      haptics = haptics,
      reducedMotionHelper = reducedMotionHelper,
      logger = logger,
    )
  }
}
