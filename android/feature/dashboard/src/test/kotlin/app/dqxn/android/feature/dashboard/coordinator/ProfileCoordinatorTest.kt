package app.dqxn.android.feature.dashboard.coordinator

import app.dqxn.android.feature.dashboard.test.FakeLayoutRepository
import app.dqxn.android.feature.dashboard.test.TestWidgetFactory.testWidget
import app.dqxn.android.sdk.observability.log.NoOpLogger
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
@Tag("fast")
class ProfileCoordinatorTest {

  private val logger = NoOpLogger

  @Test
  fun `initialize loads profiles from repository`() = runTest(UnconfinedTestDispatcher()) {
    val repo = FakeLayoutRepository()
    val coordinator = ProfileCoordinator(layoutRepository = repo, logger = logger)

    coordinator.initialize(backgroundScope)

    val state = coordinator.profileState.value
    assertThat(state.profiles).hasSize(1)
    assertThat(state.profiles.first().displayName).isEqualTo("Main")
    assertThat(state.profiles.first().isDefault).isTrue()
    assertThat(state.activeProfileId).isEqualTo(FakeLayoutRepository.DEFAULT_PROFILE_ID)
  }

  @Test
  fun `handleSwitchProfile updates activeProfileId`() = runTest(UnconfinedTestDispatcher()) {
    val repo = FakeLayoutRepository()
    val coordinator = ProfileCoordinator(layoutRepository = repo, logger = logger)

    coordinator.initialize(backgroundScope)

    // Create a second profile to switch to
    val secondId = repo.createProfile("Work")
    // Let profiles flow re-emit
    coordinator.initialize(backgroundScope)

    coordinator.handleSwitchProfile(secondId)

    assertThat(coordinator.profileState.value.activeProfileId).isEqualTo(secondId)
  }

  @Test
  fun `handleCreateProfile creates new profile in repository`() = runTest(UnconfinedTestDispatcher()) {
    val repo = FakeLayoutRepository()
    val coordinator = ProfileCoordinator(layoutRepository = repo, logger = logger)

    coordinator.initialize(backgroundScope)

    val newId = coordinator.handleCreateProfile("Work")

    val state = coordinator.profileState.value
    assertThat(state.profiles).hasSize(2)
    assertThat(state.profiles.any { it.id == newId && it.displayName == "Work" }).isTrue()
  }

  @Test
  fun `handleCreateProfile with clone duplicates current layout`() = runTest(UnconfinedTestDispatcher()) {
    val repo = FakeLayoutRepository()
    val coordinator = ProfileCoordinator(layoutRepository = repo, logger = logger)

    // Add widgets to the default profile
    val widget1 = testWidget(typeId = "essentials:clock", col = 0, row = 0)
    val widget2 = testWidget(typeId = "essentials:battery", col = 4, row = 0)
    repo.setWidgets(listOf(widget1, widget2))

    coordinator.initialize(backgroundScope)

    // Clone the default profile
    val clonedId = coordinator.handleCreateProfile(
      displayName = "Cloned",
      cloneCurrentId = FakeLayoutRepository.DEFAULT_PROFILE_ID,
    )

    // Switch to the cloned profile to verify widgets were copied
    repo.switchProfile(clonedId)

    val clonedList = repo.getActiveProfileWidgets().first()

    // Cloned widgets should have same typeIds but different instanceIds
    assertThat(clonedList).hasSize(2)
    assertThat(clonedList.map { it.typeId })
      .containsExactly("essentials:clock", "essentials:battery")
    // Instance IDs should be different (deep copy, not shared reference)
    assertThat(clonedList.map { it.instanceId })
      .containsNoneOf(widget1.instanceId, widget2.instanceId)
  }

  @Test
  fun `handleDeleteProfile removes profile`() = runTest(UnconfinedTestDispatcher()) {
    val repo = FakeLayoutRepository()
    val coordinator = ProfileCoordinator(layoutRepository = repo, logger = logger)

    coordinator.initialize(backgroundScope)

    // Create a second profile and then delete it
    val secondId = coordinator.handleCreateProfile("Work")
    assertThat(coordinator.profileState.value.profiles).hasSize(2)

    coordinator.handleDeleteProfile(secondId)

    assertThat(coordinator.profileState.value.profiles).hasSize(1)
    assertThat(coordinator.profileState.value.profiles.none { it.id == secondId }).isTrue()
  }

  @Test
  fun `handleDeleteProfile on default profile does nothing`() = runTest(UnconfinedTestDispatcher()) {
    val repo = FakeLayoutRepository()
    val coordinator = ProfileCoordinator(layoutRepository = repo, logger = logger)

    coordinator.initialize(backgroundScope)

    // Create a second profile so we have 2 total
    coordinator.handleCreateProfile("Work")
    assertThat(coordinator.profileState.value.profiles).hasSize(2)

    // Try to delete the default profile
    val defaultId = coordinator.profileState.value.profiles.first { it.isDefault }.id
    coordinator.handleDeleteProfile(defaultId)

    // Should still have 2 profiles
    assertThat(coordinator.profileState.value.profiles).hasSize(2)
  }

  @Test
  fun `handleDeleteProfile on active profile switches to default`() = runTest(UnconfinedTestDispatcher()) {
    val repo = FakeLayoutRepository()
    val coordinator = ProfileCoordinator(layoutRepository = repo, logger = logger)

    coordinator.initialize(backgroundScope)

    // Create a second profile and switch to it
    val secondId = coordinator.handleCreateProfile("Work")
    coordinator.handleSwitchProfile(secondId)
    assertThat(coordinator.profileState.value.activeProfileId).isEqualTo(secondId)

    // Delete the active (non-default) profile
    coordinator.handleDeleteProfile(secondId)

    // Should switch back to default
    assertThat(coordinator.profileState.value.activeProfileId)
      .isEqualTo(FakeLayoutRepository.DEFAULT_PROFILE_ID)
  }

  @Test
  fun `per-profile independence widget added to A not in B`() = runTest(UnconfinedTestDispatcher()) {
    val repo = FakeLayoutRepository()
    val coordinator = ProfileCoordinator(layoutRepository = repo, logger = logger)

    coordinator.initialize(backgroundScope)

    // Add a widget to profile A (default)
    val widget = testWidget(typeId = "essentials:clock", col = 0, row = 0)
    repo.addWidget(widget)

    // Create profile B and switch to it
    val profileBId = coordinator.handleCreateProfile("ProfileB")
    coordinator.handleSwitchProfile(profileBId)

    // Widgets in profile B should be empty (B was created empty, not cloned)
    val profileBWidgets = repo.getActiveProfileWidgets().first()
    assertThat(profileBWidgets).isEmpty()
  }

  @Test
  fun `profileCount returns correct count for bottom bar logic`() = runTest(UnconfinedTestDispatcher()) {
    val repo = FakeLayoutRepository()
    val coordinator = ProfileCoordinator(layoutRepository = repo, logger = logger)

    coordinator.initialize(backgroundScope)
    assertThat(coordinator.profileCount()).isEqualTo(1)

    coordinator.handleCreateProfile("Work")
    assertThat(coordinator.profileCount()).isEqualTo(2)

    coordinator.handleCreateProfile("Play")
    assertThat(coordinator.profileCount()).isEqualTo(3)
  }
}
