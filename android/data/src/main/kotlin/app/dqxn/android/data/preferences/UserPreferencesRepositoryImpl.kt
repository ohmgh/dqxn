package app.dqxn.android.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import app.dqxn.android.data.di.UserPreferences
import app.dqxn.android.sdk.contracts.theme.AutoSwitchMode
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Preferences DataStore-backed implementation of [UserPreferencesRepository]. Each property reads
 * from the store with a sensible default; setters call [DataStore.edit].
 */
@Singleton
public class UserPreferencesRepositoryImpl
@Inject
constructor(
  @param:UserPreferences private val dataStore: DataStore<Preferences>,
) : UserPreferencesRepository {

  override val autoSwitchMode: Flow<AutoSwitchMode> =
    dataStore.data.map { prefs ->
      val name = prefs[PreferenceKeys.AUTO_SWITCH_MODE]
      if (name != null) {
        runCatching { AutoSwitchMode.valueOf(name) }.getOrDefault(DEFAULT_AUTO_SWITCH_MODE)
      } else {
        DEFAULT_AUTO_SWITCH_MODE
      }
    }

  override val lightThemeId: Flow<String> =
    dataStore.data.map { prefs -> prefs[PreferenceKeys.LIGHT_THEME_ID] ?: DEFAULT_LIGHT_THEME }

  override val darkThemeId: Flow<String> =
    dataStore.data.map { prefs -> prefs[PreferenceKeys.DARK_THEME_ID] ?: DEFAULT_DARK_THEME }

  override val illuminanceThreshold: Flow<Float> =
    dataStore.data.map { prefs ->
      prefs[PreferenceKeys.ILLUMINANCE_THRESHOLD] ?: DEFAULT_ILLUMINANCE_THRESHOLD
    }

  override val keepScreenOn: Flow<Boolean> =
    dataStore.data.map { prefs -> prefs[PreferenceKeys.KEEP_SCREEN_ON] ?: DEFAULT_KEEP_SCREEN_ON }

  override val orientationLock: Flow<String> =
    dataStore.data.map { prefs ->
      prefs[PreferenceKeys.ORIENTATION_LOCK] ?: DEFAULT_ORIENTATION_LOCK
    }

  override val showStatusBar: Flow<Boolean> =
    dataStore.data.map { prefs -> prefs[PreferenceKeys.SHOW_STATUS_BAR] ?: DEFAULT_SHOW_STATUS_BAR }

  override suspend fun setAutoSwitchMode(mode: AutoSwitchMode) {
    dataStore.edit { prefs -> prefs[PreferenceKeys.AUTO_SWITCH_MODE] = mode.name }
  }

  override suspend fun setLightThemeId(id: String) {
    dataStore.edit { prefs -> prefs[PreferenceKeys.LIGHT_THEME_ID] = id }
  }

  override suspend fun setDarkThemeId(id: String) {
    dataStore.edit { prefs -> prefs[PreferenceKeys.DARK_THEME_ID] = id }
  }

  override suspend fun setIlluminanceThreshold(threshold: Float) {
    dataStore.edit { prefs -> prefs[PreferenceKeys.ILLUMINANCE_THRESHOLD] = threshold }
  }

  override suspend fun setKeepScreenOn(enabled: Boolean) {
    dataStore.edit { prefs -> prefs[PreferenceKeys.KEEP_SCREEN_ON] = enabled }
  }

  override suspend fun setOrientationLock(lock: String) {
    dataStore.edit { prefs -> prefs[PreferenceKeys.ORIENTATION_LOCK] = lock }
  }

  override suspend fun setShowStatusBar(visible: Boolean) {
    dataStore.edit { prefs -> prefs[PreferenceKeys.SHOW_STATUS_BAR] = visible }
  }

  override val analyticsConsent: Flow<Boolean> =
    dataStore.data.map { prefs ->
      prefs[PreferenceKeys.ANALYTICS_CONSENT] ?: DEFAULT_ANALYTICS_CONSENT
    }

  override suspend fun setAnalyticsConsent(enabled: Boolean) {
    dataStore.edit { prefs -> prefs[PreferenceKeys.ANALYTICS_CONSENT] = enabled }
  }

  override val hasCompletedOnboarding: Flow<Boolean> =
    dataStore.data.map { prefs ->
      prefs[PreferenceKeys.HAS_COMPLETED_ONBOARDING] ?: DEFAULT_HAS_COMPLETED_ONBOARDING
    }

  override val hasSeenDisclaimer: Flow<Boolean> =
    dataStore.data.map { prefs ->
      prefs[PreferenceKeys.HAS_SEEN_DISCLAIMER] ?: DEFAULT_HAS_SEEN_DISCLAIMER
    }

  override suspend fun setHasCompletedOnboarding(completed: Boolean) {
    dataStore.edit { prefs -> prefs[PreferenceKeys.HAS_COMPLETED_ONBOARDING] = completed }
  }

  override suspend fun setHasSeenDisclaimer(seen: Boolean) {
    dataStore.edit { prefs -> prefs[PreferenceKeys.HAS_SEEN_DISCLAIMER] = seen }
  }

  override val sessionCount: Flow<Int> =
    dataStore.data.map { prefs -> prefs[PreferenceKeys.SESSION_COUNT] ?: DEFAULT_SESSION_COUNT }

  override suspend fun incrementSessionCount() {
    dataStore.edit { prefs ->
      val current = prefs[PreferenceKeys.SESSION_COUNT] ?: DEFAULT_SESSION_COUNT
      prefs[PreferenceKeys.SESSION_COUNT] = current + 1
    }
  }

  override val lastReviewPromptTimestamp: Flow<Long> =
    dataStore.data.map { prefs ->
      prefs[PreferenceKeys.LAST_REVIEW_PROMPT_TIMESTAMP] ?: DEFAULT_LAST_REVIEW_PROMPT_TIMESTAMP
    }

  override suspend fun setLastReviewPromptTimestamp(timestamp: Long) {
    dataStore.edit { prefs -> prefs[PreferenceKeys.LAST_REVIEW_PROMPT_TIMESTAMP] = timestamp }
  }

  override fun hasSeenTip(tipKey: String): Flow<Boolean> =
    dataStore.data.map { prefs -> prefs[PreferenceKeys.tipSeenKey(tipKey)] ?: false }

  override suspend fun markTipSeen(tipKey: String) {
    dataStore.edit { prefs -> prefs[PreferenceKeys.tipSeenKey(tipKey)] = true }
  }

  override suspend fun clearAll() {
    dataStore.edit { it.clear() }
  }

  private companion object {
    val DEFAULT_AUTO_SWITCH_MODE = AutoSwitchMode.SYSTEM
    const val DEFAULT_LIGHT_THEME = "essentials:minimalist"
    const val DEFAULT_DARK_THEME = "essentials:slate"
    const val DEFAULT_ILLUMINANCE_THRESHOLD = 100f
    const val DEFAULT_KEEP_SCREEN_ON = true
    const val DEFAULT_ORIENTATION_LOCK = "auto"
    const val DEFAULT_SHOW_STATUS_BAR = false
    const val DEFAULT_ANALYTICS_CONSENT = false
    const val DEFAULT_HAS_COMPLETED_ONBOARDING = false
    const val DEFAULT_HAS_SEEN_DISCLAIMER = false
    const val DEFAULT_SESSION_COUNT = 0
    const val DEFAULT_LAST_REVIEW_PROMPT_TIMESTAMP = 0L
  }
}
