package app.dqxn.android.feature.dashboard.test

import app.dqxn.android.data.layout.DashboardWidgetInstance
import app.dqxn.android.data.layout.GridPosition
import app.dqxn.android.data.layout.GridSize
import app.dqxn.android.data.layout.LayoutRepository
import app.dqxn.android.data.layout.ProfileSummary
import java.util.UUID
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
 */
public class FakeLayoutRepository : LayoutRepository {

  private data class ProfileData(
    val summary: ProfileSummary,
    val widgets: List<DashboardWidgetInstance>,
  )

  private val _profiles: MutableStateFlow<List<ProfileData>> =
    MutableStateFlow(
      listOf(
        ProfileData(
          summary = ProfileSummary(profileId = DEFAULT_PROFILE_ID, displayName = "Main", sortOrder = 0),
          widgets = emptyList(),
        )
      )
    )

  private val _activeProfileId: MutableStateFlow<String> = MutableStateFlow(DEFAULT_PROFILE_ID)

  override val profiles: Flow<ImmutableList<ProfileSummary>>
    get() = _profiles.map { list -> list.map { it.summary }.toImmutableList() }

  override val activeProfileId: Flow<String>
    get() = _activeProfileId

  override fun getActiveProfileWidgets(): Flow<ImmutableList<DashboardWidgetInstance>> =
    _profiles.map { profileList ->
      val activeId = _activeProfileId.value
      profileList.find { it.summary.profileId == activeId }?.widgets?.toImmutableList()
        ?: persistentListOf()
    }

  override suspend fun createProfile(displayName: String): String {
    val newId = UUID.randomUUID().toString()
    _profiles.update { list ->
      list +
        ProfileData(
          summary =
            ProfileSummary(profileId = newId, displayName = displayName, sortOrder = list.size),
          widgets = emptyList(),
        )
    }
    return newId
  }

  override suspend fun cloneProfile(sourceId: String, displayName: String): String {
    val newId = UUID.randomUUID().toString()
    _profiles.update { list ->
      val source = list.find { it.summary.profileId == sourceId }
      val clonedWidgets =
        source?.widgets?.map { it.copy(instanceId = UUID.randomUUID().toString()) } ?: emptyList()
      list +
        ProfileData(
          summary =
            ProfileSummary(profileId = newId, displayName = displayName, sortOrder = list.size),
          widgets = clonedWidgets,
        )
    }
    return newId
  }

  override suspend fun switchProfile(targetId: String) {
    _activeProfileId.value = targetId
    // Trigger re-emission of profiles to update widgets flow
    _profiles.update { it.toList() }
  }

  override suspend fun deleteProfile(id: String) {
    check(_profiles.value.size > 1) { "Cannot delete the last remaining profile" }
    _profiles.update { list -> list.filter { it.summary.profileId != id } }
    if (_activeProfileId.value == id) {
      _activeProfileId.value = _profiles.value.first().summary.profileId
    }
  }

  override suspend fun addWidget(widget: DashboardWidgetInstance) {
    val activeId = _activeProfileId.value
    _profiles.update { list ->
      list.map {
        if (it.summary.profileId == activeId) {
          it.copy(widgets = it.widgets + widget)
        } else {
          it
        }
      }
    }
  }

  override suspend fun removeWidget(instanceId: String) {
    val activeId = _activeProfileId.value
    _profiles.update { list ->
      list.map {
        if (it.summary.profileId == activeId) {
          it.copy(widgets = it.widgets.filter { w -> w.instanceId != instanceId })
        } else {
          it
        }
      }
    }
  }

  override suspend fun updateWidget(widget: DashboardWidgetInstance) {
    val activeId = _activeProfileId.value
    _profiles.update { list ->
      list.map {
        if (it.summary.profileId == activeId) {
          it.copy(
            widgets = it.widgets.map { w -> if (w.instanceId == widget.instanceId) widget else w }
          )
        } else {
          it
        }
      }
    }
  }

  override suspend fun updateWidgetPosition(instanceId: String, position: GridPosition) {
    val activeId = _activeProfileId.value
    _profiles.update { list ->
      list.map {
        if (it.summary.profileId == activeId) {
          it.copy(
            widgets =
              it.widgets.map { w ->
                if (w.instanceId == instanceId) w.copy(position = position) else w
              }
          )
        } else {
          it
        }
      }
    }
  }

  override suspend fun updateWidgetSize(instanceId: String, size: GridSize) {
    val activeId = _activeProfileId.value
    _profiles.update { list ->
      list.map {
        if (it.summary.profileId == activeId) {
          it.copy(
            widgets =
              it.widgets.map { w ->
                if (w.instanceId == instanceId) w.copy(size = size) else w
              }
          )
        } else {
          it
        }
      }
    }
  }

  /** Pre-populate widgets in the active profile for test setup. */
  public fun setWidgets(widgets: List<DashboardWidgetInstance>) {
    val activeId = _activeProfileId.value
    _profiles.update { list ->
      list.map {
        if (it.summary.profileId == activeId) it.copy(widgets = widgets)
        else it
      }
    }
  }

  public companion object {
    public const val DEFAULT_PROFILE_ID: String = "default-profile"
  }
}
