package app.dqxn.android.data.preferences

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/** Typed [androidx.datastore.preferences.core.Preferences.Key] constants for user preferences. */
public object PreferenceKeys {
  public val AUTO_SWITCH_MODE = stringPreferencesKey("auto_switch_mode")
  public val LIGHT_THEME_ID = stringPreferencesKey("light_theme_id")
  public val DARK_THEME_ID = stringPreferencesKey("dark_theme_id")
  public val ILLUMINANCE_THRESHOLD = floatPreferencesKey("illuminance_threshold")
  public val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
  public val ORIENTATION_LOCK = stringPreferencesKey("orientation_lock")
  public val SHOW_STATUS_BAR = booleanPreferencesKey("show_status_bar")
  public val ANALYTICS_CONSENT = booleanPreferencesKey("analytics_consent")
  public val HAS_COMPLETED_ONBOARDING = booleanPreferencesKey("has_completed_onboarding")
  public val HAS_SEEN_DISCLAIMER = booleanPreferencesKey("has_seen_disclaimer")
  public val SESSION_COUNT = intPreferencesKey("session_count")
  public val LAST_REVIEW_PROMPT_TIMESTAMP = longPreferencesKey("last_review_prompt_timestamp")

  /** Returns a dynamic key for per-tip seen state. */
  public fun tipSeenKey(
    tipKey: String
  ): androidx.datastore.preferences.core.Preferences.Key<Boolean> =
    booleanPreferencesKey("tip_seen_$tipKey")
}
