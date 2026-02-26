package app.dqxn.android.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.dqxn.android.core.design.theme.BuiltInThemes
import app.dqxn.android.data.preferences.UserPreferencesRepository
import app.dqxn.android.sdk.analytics.AnalyticsTracker
import app.dqxn.android.sdk.ui.theme.DashboardThemeDefinition
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel orchestrating the first-run onboarding flow.
 *
 * Exposes consent/completion state flows and provides actions for each onboarding step. Theme
 * selection persists to the appropriate light/dark slot based on the theme's [DashboardThemeDefinition.isDark]
 * property, matching the pattern in ThemeCoordinator.
 */
@HiltViewModel
public class OnboardingViewModel
@Inject
constructor(
  private val userPreferencesRepository: UserPreferencesRepository,
  private val analyticsTracker: AnalyticsTracker,
  private val builtInThemes: BuiltInThemes,
) : ViewModel() {

  /** Whether the user has completed onboarding. */
  public val hasCompletedOnboarding: Flow<Boolean> = userPreferencesRepository.hasCompletedOnboarding

  /** Current analytics consent state. */
  public val analyticsConsent: StateFlow<Boolean> =
    userPreferencesRepository.analyticsConsent.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5_000),
      initialValue = false,
    )

  /** Free themes available for selection during onboarding. */
  public val freeThemes: ImmutableList<DashboardThemeDefinition> = builtInThemes.freeThemes

  /**
   * Sets analytics consent.
   *
   * Ordering: if enabling, persist THEN enable tracker. If disabling, disable tracker THEN persist.
   * Prevents data collection during the window between state changes.
   */
  public fun setAnalyticsConsent(enabled: Boolean) {
    viewModelScope.launch {
      if (enabled) {
        userPreferencesRepository.setAnalyticsConsent(true)
        analyticsTracker.setEnabled(true)
      } else {
        analyticsTracker.setEnabled(false)
        userPreferencesRepository.setAnalyticsConsent(false)
      }
    }
  }

  /** Marks the first-launch disclaimer as seen. */
  public fun setHasSeenDisclaimer() {
    viewModelScope.launch { userPreferencesRepository.setHasSeenDisclaimer(true) }
  }

  /** Marks onboarding as completed, preventing re-show on next launch. */
  public suspend fun completeOnboarding() {
    userPreferencesRepository.setHasCompletedOnboarding(true)
  }

  /**
   * Persists the selected theme to the appropriate light/dark slot.
   *
   * Resolves the theme from [BuiltInThemes] and persists based on [DashboardThemeDefinition.isDark].
   * ThemeCoordinator monitors lightThemeId/darkThemeId flows and applies the theme on startup.
   */
  public fun selectTheme(themeId: String) {
    val theme = builtInThemes.resolveById(themeId) ?: return
    viewModelScope.launch {
      if (theme.isDark) {
        userPreferencesRepository.setDarkThemeId(themeId)
      } else {
        userPreferencesRepository.setLightThemeId(themeId)
      }
    }
  }
}
