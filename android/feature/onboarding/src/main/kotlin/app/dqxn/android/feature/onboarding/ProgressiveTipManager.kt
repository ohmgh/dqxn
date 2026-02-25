package app.dqxn.android.feature.onboarding

import app.dqxn.android.data.preferences.UserPreferencesRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Tracks progressive tip display state. Each tip is identified by a string key and persisted via
 * [UserPreferencesRepository]. Tips that have been dismissed are never shown again.
 */
@Singleton
public class ProgressiveTipManager
@Inject
constructor(
  private val userPreferencesRepository: UserPreferencesRepository,
) {

  public companion object {
    public const val TIP_FIRST_LAUNCH: String = "first_launch"
    public const val TIP_EDIT_MODE: String = "edit_mode"
    public const val TIP_WIDGET_FOCUS: String = "widget_focus"
    public const val TIP_WIDGET_SETTINGS: String = "widget_settings"
  }

  /** Returns a [Flow] that emits `true` when the tip should be shown (i.e., has NOT been seen). */
  public fun shouldShowTip(tipKey: String): Flow<Boolean> =
    userPreferencesRepository.hasSeenTip(tipKey).map { seen -> !seen }

  /** Dismisses the tip identified by [tipKey], persisting the decision. */
  public suspend fun dismissTip(tipKey: String) {
    userPreferencesRepository.markTipSeen(tipKey)
  }
}
