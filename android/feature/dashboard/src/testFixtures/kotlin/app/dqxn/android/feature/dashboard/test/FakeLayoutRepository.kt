package app.dqxn.android.feature.dashboard.test

import app.dqxn.android.data.layout.DashboardWidgetInstance
import app.dqxn.android.data.layout.GridPosition
import app.dqxn.android.data.layout.GridSize
import app.dqxn.android.data.layout.LayoutRepository
import app.dqxn.android.data.layout.ProfileSummary
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory [LayoutRepository] for testing. All operations are immediate (no debounce). Backs
 * profile and widget state with [MutableStateFlow] so collectors see updates instantly.
 *
 * Uses a [Versioned] wrapper around the profile list to force [MutableStateFlow] re-emission
 * when the structural content is unchanged but the active profile changed (profile switching).
 */
public class FakeLayoutRepository : LayoutRepository {

  private data class ProfileData(
    val summary: ProfileSummary,
    val widgets: List<DashboardWidgetInstance>,
  )

  /**
   * Versioned wrapper to force [MutableStateFlow] re-emission. [MutableStateFlow] uses structural
   * equality to suppress duplicate emissions. When `switchProfile` is called, the profile list
   * content may be unchanged -- only the active profile ID changes. Including a monotonically
   * increasing [version] guarantees `equals()` returns false, forcing re-emission so
   * [getActiveProfileWidgets] re-reads the active profile ID.
   */
  private data class Versioned<T>(val data: T, val version: Long)

  private val versionCounter: AtomicLong = AtomicLong(0L)

  private fun nextVersion(): Long = versionCounter.incrementAndGet()

  private val _profiles: MutableStateFlow<Versioned<List<ProfileData>>> =
    MutableStateFlow(
      Versioned(
        data = listOf(
          ProfileData(
            summary = ProfileSummary(profileId = DEFAULT_PROFILE_ID, displayName = "Main", sortOrder = 0),
            widgets = emptyList(),
          )
        ),
        version = 0L,
      )
    )

  private val _activeProfileId: MutableStateFlow<String> = MutableStateFlow(DEFAULT_PROFILE_ID)

  override val profiles: Flow<ImmutableList<ProfileSummary>>
    get() = _profiles.map { versioned -> versioned.data.map { it.summary }.toImmutableList() }

  override val activeProfileId: Flow<String>
    get() = _activeProfileId

  override fun getActiveProfileWidgets(): Flow<ImmutableList<DashboardWidgetInstance>> =
    _profiles.map { versioned ->
      val activeId = _activeProfileId.value
      versioned.data.find { it.summary.profileId == activeId }?.widgets?.toImmutableList()
        ?: persistentListOf()
    }

  override suspend fun createProfile(displayName: String): String {
    val newId = UUID.randomUUID().toString()
    _profiles.update { versioned ->
      Versioned(
        data = versioned.data + ProfileData(
          summary = ProfileSummary(profileId = newId, displayName = displayName, sortOrder = versioned.data.size),
          widgets = emptyList(),
        ),
        version = nextVersion(),
      )
    }
    return newId
  }

  override suspend fun cloneProfile(sourceId: String, displayName: String): String {
    val newId = UUID.randomUUID().toString()
    _profiles.update { versioned ->
      val source = versioned.data.find { it.summary.profileId == sourceId }
      val clonedWidgets =
        source?.widgets?.map { it.copy(instanceId = UUID.randomUUID().toString()) } ?: emptyList()
      Versioned(
        data = versioned.data + ProfileData(
          summary = ProfileSummary(profileId = newId, displayName = displayName, sortOrder = versioned.data.size),
          widgets = clonedWidgets,
        ),
        version = nextVersion(),
      )
    }
    return newId
  }

  override suspend fun switchProfile(targetId: String) {
    _activeProfileId.value = targetId
    // Force re-emission of _profiles so getActiveProfileWidgets() re-reads _activeProfileId
    _profiles.update { versioned -> Versioned(versioned.data, nextVersion()) }
  }

  override suspend fun deleteProfile(id: String) {
    check(_profiles.value.data.size > 1) { "Cannot delete the last remaining profile" }
    _profiles.update { versioned ->
      Versioned(
        data = versioned.data.filter { it.summary.profileId != id },
        version = nextVersion(),
      )
    }
    if (_activeProfileId.value == id) {
      _activeProfileId.value = _profiles.value.data.first().summary.profileId
    }
  }

  override suspend fun addWidget(widget: DashboardWidgetInstance) {
    val activeId = _activeProfileId.value
    _profiles.update { versioned ->
      Versioned(
        data = versioned.data.map {
          if (it.summary.profileId == activeId) {
            it.copy(widgets = it.widgets + widget)
          } else {
            it
          }
        },
        version = nextVersion(),
      )
    }
  }

  override suspend fun removeWidget(instanceId: String) {
    val activeId = _activeProfileId.value
    _profiles.update { versioned ->
      Versioned(
        data = versioned.data.map {
          if (it.summary.profileId == activeId) {
            it.copy(widgets = it.widgets.filter { w -> w.instanceId != instanceId })
          } else {
            it
          }
        },
        version = nextVersion(),
      )
    }
  }

  override suspend fun updateWidget(widget: DashboardWidgetInstance) {
    val activeId = _activeProfileId.value
    _profiles.update { versioned ->
      Versioned(
        data = versioned.data.map {
          if (it.summary.profileId == activeId) {
            it.copy(
              widgets = it.widgets.map { w -> if (w.instanceId == widget.instanceId) widget else w }
            )
          } else {
            it
          }
        },
        version = nextVersion(),
      )
    }
  }

  override suspend fun updateWidgetPosition(instanceId: String, position: GridPosition) {
    val activeId = _activeProfileId.value
    _profiles.update { versioned ->
      Versioned(
        data = versioned.data.map {
          if (it.summary.profileId == activeId) {
            it.copy(
              widgets = it.widgets.map { w ->
                if (w.instanceId == instanceId) w.copy(position = position) else w
              }
            )
          } else {
            it
          }
        },
        version = nextVersion(),
      )
    }
  }

  override suspend fun updateWidgetSize(instanceId: String, size: GridSize) {
    val activeId = _activeProfileId.value
    _profiles.update { versioned ->
      Versioned(
        data = versioned.data.map {
          if (it.summary.profileId == activeId) {
            it.copy(
              widgets = it.widgets.map { w ->
                if (w.instanceId == instanceId) w.copy(size = size) else w
              }
            )
          } else {
            it
          }
        },
        version = nextVersion(),
      )
    }
  }

  override suspend fun clearAll() {
    _profiles.value =
      Versioned(
        data = listOf(
          ProfileData(
            summary = ProfileSummary(profileId = DEFAULT_PROFILE_ID, displayName = "Main", sortOrder = 0),
            widgets = emptyList(),
          )
        ),
        version = nextVersion(),
      )
    _activeProfileId.value = DEFAULT_PROFILE_ID
  }

  /** Pre-populate widgets in the active profile for test setup. */
  public fun setWidgets(widgets: List<DashboardWidgetInstance>) {
    val activeId = _activeProfileId.value
    _profiles.update { versioned ->
      Versioned(
        data = versioned.data.map {
          if (it.summary.profileId == activeId) it.copy(widgets = widgets) else it
        },
        version = nextVersion(),
      )
    }
  }

  public companion object {
    public const val DEFAULT_PROFILE_ID: String = "default-profile"
  }
}
