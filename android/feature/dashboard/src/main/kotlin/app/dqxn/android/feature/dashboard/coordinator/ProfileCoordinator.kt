package app.dqxn.android.feature.dashboard.coordinator

import androidx.compose.runtime.Immutable
import app.dqxn.android.data.layout.LayoutRepository
import app.dqxn.android.data.layout.ProfileSummary
import app.dqxn.android.sdk.observability.log.DqxnLogger
import app.dqxn.android.sdk.observability.log.LogTag
import app.dqxn.android.sdk.observability.log.info
import app.dqxn.android.sdk.observability.log.warn
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Profile info for the bottom bar and profile management UI.
 *
 * Maps from [ProfileSummary] with the addition of a default flag derived from position in the
 * profile list (first profile is default).
 */
@Immutable
public data class ProfileInfo(
  val id: String,
  val displayName: String,
  val isDefault: Boolean,
)

/**
 * Profile state slice for the dashboard. Tracks all profiles and the currently active profile.
 *
 * Per-profile independence (F1.30): each profile owns its own widget set via [LayoutRepository].
 * Switching profiles causes [LayoutCoordinator] to reload widgets for the new profile through
 * [LayoutRepository.activeProfileId] flow observation.
 */
@Immutable
public data class ProfileState(
  val profiles: ImmutableList<ProfileInfo> = persistentListOf(),
  val activeProfileId: String = "",
)

/**
 * Coordinator for profile CRUD with per-profile canvas independence.
 *
 * Owns the [ProfileState] slice following decomposed state architecture. Profile switching triggers
 * [LayoutRepository.switchProfile] which causes [LayoutCoordinator] to reload widgets for the new
 * profile through its active profile flow observation.
 *
 * New profile clones current dashboard layout (F1.30). Widget added to profile A is not visible in
 * profile B -- each profile owns independent widget sets.
 */
public class ProfileCoordinator
@Inject
constructor(
  private val layoutRepository: LayoutRepository,
  private val logger: DqxnLogger,
) {

  private val _profileState: MutableStateFlow<ProfileState> = MutableStateFlow(ProfileState())

  /** Observable profile state. Collect from composables for bottom bar profile icons. */
  public val profileState: StateFlow<ProfileState> = _profileState.asStateFlow()

  /**
   * Initialize the coordinator by observing [LayoutRepository.profiles] and
   * [LayoutRepository.activeProfileId]. Must be called once from ViewModel init.
   */
  public fun initialize(scope: CoroutineScope) {
    scope.launch {
      layoutRepository.profiles.collect { summaries ->
        _profileState.update { state ->
          state.copy(
            profiles =
              summaries
                .mapIndexed { index, summary ->
                  ProfileInfo(
                    id = summary.profileId,
                    displayName = summary.displayName,
                    isDefault = index == 0,
                  )
                }
                .toImmutableList()
          )
        }
        logger.info(TAG) { "Profiles loaded: ${summaries.size}" }
      }
    }

    scope.launch {
      layoutRepository.activeProfileId.collect { profileId ->
        _profileState.update { it.copy(activeProfileId = profileId) }
      }
    }

    logger.info(TAG) { "ProfileCoordinator initialized" }
  }

  /**
   * Switch to a different profile. Calls [LayoutRepository.switchProfile] which triggers
   * [LayoutCoordinator] to reload widgets for the target profile.
   */
  public suspend fun handleSwitchProfile(profileId: String) {
    val currentProfiles = _profileState.value.profiles
    if (currentProfiles.none { it.id == profileId }) {
      logger.warn(TAG) { "Cannot switch to unknown profile: $profileId" }
      return
    }

    layoutRepository.switchProfile(profileId)
    logger.info(TAG) { "Switched to profile: $profileId" }
  }

  /**
   * Create a new profile. If [cloneCurrentId] is provided, clones the source profile's layout
   * (F1.30: new profile clones current dashboard). Otherwise creates an empty profile.
   *
   * @return The new profile's ID.
   */
  public suspend fun handleCreateProfile(
    displayName: String,
    cloneCurrentId: String? = null,
  ): String {
    val newId =
      if (cloneCurrentId != null) {
        layoutRepository.cloneProfile(cloneCurrentId, displayName)
      } else {
        layoutRepository.createProfile(displayName)
      }

    logger.info(TAG) {
      if (cloneCurrentId != null) {
        "Profile created (cloned from $cloneCurrentId): $newId '$displayName'"
      } else {
        "Profile created (empty): $newId '$displayName'"
      }
    }

    return newId
  }

  /**
   * Delete a profile. Fails silently if trying to delete the default profile (first in list). If
   * deleting the currently active profile, switches to the default profile first.
   */
  public suspend fun handleDeleteProfile(profileId: String) {
    val state = _profileState.value
    val profile = state.profiles.find { it.id == profileId }

    if (profile == null) {
      logger.warn(TAG) { "Cannot delete unknown profile: $profileId" }
      return
    }

    if (profile.isDefault) {
      logger.warn(TAG) { "Cannot delete default profile: $profileId" }
      return
    }

    // If deleting the active profile, switch to default first
    if (state.activeProfileId == profileId) {
      val defaultProfile = state.profiles.first { it.isDefault }
      layoutRepository.switchProfile(defaultProfile.id)
      logger.info(TAG) { "Switched to default profile before deletion" }
    }

    layoutRepository.deleteProfile(profileId)
    logger.info(TAG) { "Profile deleted: $profileId" }
  }

  /**
   * Returns the current profile count. Used by bottom bar to determine whether to show profile
   * icons (2+ required per F1.9, F1.29).
   */
  public fun profileCount(): Int = _profileState.value.profiles.size

  private companion object {
    val TAG = LogTag("ProfileCoord")
  }
}
