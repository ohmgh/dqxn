package app.dqxn.android.data.layout

import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.Flow

/** Lightweight summary of a dashboard profile for listing. */
public data class ProfileSummary(
  val profileId: String,
  val displayName: String,
  val sortOrder: Int,
)

/**
 * Interface for dashboard layout persistence with profile CRUD. All mutating operations are
 * debounced at 500ms before writing to DataStore, so rapid mutations batch into a single write.
 */
public interface LayoutRepository {

  /** All profile summaries, ordered by [ProfileSummary.sortOrder]. */
  public val profiles: Flow<ImmutableList<ProfileSummary>>

  /** Currently active profile ID. */
  public val activeProfileId: Flow<String>

  /** Widgets in the currently active profile. */
  public fun getActiveProfileWidgets(): Flow<ImmutableList<DashboardWidgetInstance>>

  /** Create a new empty profile. Returns the new profile's ID. */
  public suspend fun createProfile(displayName: String): String

  /**
   * Deep-copy a profile including all widgets. Each widget in the clone gets a new UUID.
   * Returns the new profile's ID.
   */
  public suspend fun cloneProfile(sourceId: String, displayName: String): String

  /** Switch the active profile. */
  public suspend fun switchProfile(targetId: String)

  /**
   * Delete a profile.
   * @throws IllegalStateException if this is the last remaining profile.
   */
  public suspend fun deleteProfile(id: String)

  /** Add a widget to the active profile. */
  public suspend fun addWidget(widget: DashboardWidgetInstance)

  /** Remove a widget from the active profile by instance ID. */
  public suspend fun removeWidget(instanceId: String)

  /** Replace a widget in the active profile (matched by instanceId). */
  public suspend fun updateWidget(widget: DashboardWidgetInstance)

  /** Update only the position of a widget. */
  public suspend fun updateWidgetPosition(instanceId: String, position: GridPosition)

  /** Update only the size of a widget. */
  public suspend fun updateWidgetSize(instanceId: String, size: GridSize)
}
