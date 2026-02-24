package app.dqxn.android.data.preferences

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
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
}
