package app.dqxn.android.feature.dashboard.coordinator

import app.dqxn.android.feature.dashboard.test.FakeLayoutRepository
import app.dqxn.android.feature.dashboard.test.TestWidgetFactory.testWidget
import app.dqxn.android.sdk.observability.log.NoOpLogger
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
@Tag("fast")
class ProfileCoordinatorTest {

  private val logger = NoOpLogger

  @Test
  fun `initialize loads profiles from repository`() = runTest {
    val repo = FakeLayoutRepository()
    val coordinator = ProfileCoordinator(layoutRepository = repo, logger = logger)

    val initJob = Job(coroutineContext[Job])
    coordinator.initialize(this + initJob)
    testScheduler.runCurrent()

    val state = coordinator.profileState.value
    assertThat(state.profiles).hasSize(1)
    assertThat(state.profiles.first().displayName).isEqualTo("Main")
    assertThat(state.profiles.first().isDefault).isTrue()
    assertThat(state.activeProfileId).isEqualTo(FakeLayoutRepository.DEFAULT_PROFILE_ID)

    initJob.cancel()
  }

  @Test
  fun `handleSwitchProfile updates activeProfileId`() = runTest {
    val repo = FakeLayoutRepository()
    val coordinator = ProfileCoordinator(layoutRepository = repo, logger = logger)

    val initJob = Job(coroutineContext[Job])
    coordinator.initialize(this + initJob)
    testScheduler.runCurrent()

    // Create a second profile to switch to
    val secondId = repo.createProfile("Work")
    testScheduler.runCurrent()

    coordinator.handleSwitchProfile(secondId)
    testScheduler.runCurrent()

    assertThat(coordinator.profileState.value.activeProfileId).isEqualTo(secondId)

    initJob.cancel()
  }

  @Test
  fun `handleCreateProfile creates new profile in repository`() = runTest {
    val repo = FakeLayoutRepository()
    val coordinator = ProfileCoordinator(layoutRepository = repo, logger = logger)

    val initJob = Job(coroutineContext[Job])
    coordinator.initialize(this + initJob)
    testScheduler.runCurrent()

    val newId = coordinator.handleCreateProfile("Work")
    testScheduler.runCurrent()

    val state = coordinator.profileState.value
    assertThat(state.profiles).hasSize(2)
    assertThat(state.profiles.any { it.id == newId && it.displayName == "Work" }).isTrue()

    initJob.cancel()
  }

  @Test
  fun `handleCreateProfile with clone duplicates current layout`() = runTest {
    val repo = FakeLayoutRepository()
    val coordinator = ProfileCoordinator(layoutRepository = repo, logger = logger)

    // Add widgets to the default profile
    val widget1 = testWidget(typeId = "essentials:clock", col = 0, row = 0)
    val widget2 = testWidget(typeId = "essentials:battery", col = 4, row = 0)
    repo.setWidgets(listOf(widget1, widget2))

    val initJob = Job(coroutineContext[Job])
    coordinator.initialize(this + initJob)
    testScheduler.runCurrent()

    // Clone the default profile
    val clonedId = coordinator.handleCreateProfile(
      displayName = "Cloned",
      cloneCurrentId = FakeLayoutRepository.DEFAULT_PROFILE_ID,
    )
    testScheduler.runCurrent()

    // Switch to the cloned profile to verify widgets were copied
    repo.switchProfile(clonedId)
    testScheduler.runCurrent()

    val clonedList = repo.getActiveProfileWidgets().first()

    // Cloned widgets should have same typeIds but different instanceIds
    assertThat(clonedList).hasSize(2)
    assertThat(clonedList.map { it.typeId })
      .containsExactly("essentials:clock", "essentials:battery")
    // Instance IDs should be different (deep copy, not shared reference)
    assertThat(clonedList.map { it.instanceId })
      .containsNoneOf(widget1.instanceId, widget2.instanceId)

    initJob.cancel()
  }

  @Test
  fun `handleDeleteProfile removes profile`() = runTest {
    val repo = FakeLayoutRepository()
    val coordinator = ProfileCoordinator(layoutRepository = repo, logger = logger)

    val initJob = Job(coroutineContext[Job])
    coordinator.initialize(this + initJob)
    testScheduler.runCurrent()

    // Create a second profile and then delete it
    val secondId = coordinator.handleCreateProfile("Work")
    testScheduler.runCurrent()
    assertThat(coordinator.profileState.value.profiles).hasSize(2)

    coordinator.handleDeleteProfile(secondId)
    testScheduler.runCurrent()

    assertThat(coordinator.profileState.value.profiles).hasSize(1)
    assertThat(coordinator.profileState.value.profiles.none { it.id == secondId }).isTrue()

    initJob.cancel()
  }

  @Test
  fun `handleDeleteProfile on default profile does nothing`() = runTest {
    val repo = FakeLayoutRepository()
    val coordinator = ProfileCoordinator(layoutRepository = repo, logger = logger)

    val initJob = Job(coroutineContext[Job])
    coordinator.initialize(this + initJob)
    testScheduler.runCurrent()

    // Create a second profile so we have 2 total
    coordinator.handleCreateProfile("Work")
    testScheduler.runCurrent()
    assertThat(coordinator.profileState.value.profiles).hasSize(2)

    // Try to delete the default profile
    val defaultId = coordinator.profileState.value.profiles.first { it.isDefault }.id
    coordinator.handleDeleteProfile(defaultId)
    testScheduler.runCurrent()

    // Should still have 2 profiles
    assertThat(coordinator.profileState.value.profiles).hasSize(2)

    initJob.cancel()
  }

  @Test
  fun `handleDeleteProfile on active profile switches to default`() = runTest {
    val repo = FakeLayoutRepository()
    val coordinator = ProfileCoordinator(layoutRepository = repo, logger = logger)

    val initJob = Job(coroutineContext[Job])
    coordinator.initialize(this + initJob)
    testScheduler.runCurrent()

    // Create a second profile and switch to it
    val secondId = coordinator.handleCreateProfile("Work")
    testScheduler.runCurrent()
    coordinator.handleSwitchProfile(secondId)
    testScheduler.runCurrent()
    assertThat(coordinator.profileState.value.activeProfileId).isEqualTo(secondId)

    // Delete the active (non-default) profile
    coordinator.handleDeleteProfile(secondId)
    testScheduler.runCurrent()

    // Should switch back to default
    assertThat(coordinator.profileState.value.activeProfileId)
      .isEqualTo(FakeLayoutRepository.DEFAULT_PROFILE_ID)

    initJob.cancel()
  }

  @Test
  fun `per-profile independence widget added to A not in B`() = runTest {
    val repo = FakeLayoutRepository()
    val coordinator = ProfileCoordinator(layoutRepository = repo, logger = logger)

    val initJob = Job(coroutineContext[Job])
    coordinator.initialize(this + initJob)
    testScheduler.runCurrent()

    // Add a widget to profile A (default)
    val widget = testWidget(typeId = "essentials:clock", col = 0, row = 0)
    repo.addWidget(widget)
    testScheduler.runCurrent()

    // Create profile B and switch to it
    val profileBId = coordinator.handleCreateProfile("ProfileB")
    testScheduler.runCurrent()
    coordinator.handleSwitchProfile(profileBId)
    testScheduler.runCurrent()

    // Widgets in profile B should be empty (B was created empty, not cloned)
    val profileBWidgets = repo.getActiveProfileWidgets().first()
    assertThat(profileBWidgets).isEmpty()

    initJob.cancel()
  }

  @Test
  fun `profileCount returns correct count for bottom bar logic`() = runTest {
    val repo = FakeLayoutRepository()
    val coordinator = ProfileCoordinator(layoutRepository = repo, logger = logger)

    val initJob = Job(coroutineContext[Job])
    coordinator.initialize(this + initJob)
    testScheduler.runCurrent()
    assertThat(coordinator.profileCount()).isEqualTo(1)

    coordinator.handleCreateProfile("Work")
    testScheduler.runCurrent()
    assertThat(coordinator.profileCount()).isEqualTo(2)

    coordinator.handleCreateProfile("Play")
    testScheduler.runCurrent()
    assertThat(coordinator.profileCount()).isEqualTo(3)

    initJob.cancel()
  }
}
