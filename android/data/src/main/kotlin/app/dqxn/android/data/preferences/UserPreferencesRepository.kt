package app.dqxn.android.data.preferences

import app.dqxn.android.sdk.contracts.theme.AutoSwitchMode
import kotlinx.coroutines.flow.Flow

/**
 * Interface for user preferences (theme mode, orientation, etc.). Backed by Preferences DataStore.
 * Each property is a hot flow that emits the current value and updates on change.
 */
public interface UserPreferencesRepository {

  /** Current theme auto-switch mode. Defaults to [AutoSwitchMode.SYSTEM]. */
  public val autoSwitchMode: Flow<AutoSwitchMode>

  /** Theme ID applied when in light mode. Defaults to "minimalist". */
  public val lightThemeId: Flow<String>

  /** Theme ID applied when in dark mode. Defaults to "slate". */
  public val darkThemeId: Flow<String>

  /** Ambient light threshold (lux) for [AutoSwitchMode.ILLUMINANCE_AUTO]. Defaults to 100f. */
  public val illuminanceThreshold: Flow<Float>

  /** Whether to keep the screen on while the dashboard is visible. Defaults to true. */
  public val keepScreenOn: Flow<Boolean>

  /** Orientation lock: "landscape", "portrait", or "auto". Defaults to "auto". */
  public val orientationLock: Flow<String>

  /** Whether to show the system status bar. Defaults to false (hidden). */
  public val showStatusBar: Flow<Boolean>

  public suspend fun setAutoSwitchMode(mode: AutoSwitchMode)

  public suspend fun setLightThemeId(id: String)

  public suspend fun setDarkThemeId(id: String)

  public suspend fun setIlluminanceThreshold(threshold: Float)

  public suspend fun setKeepScreenOn(enabled: Boolean)

  public suspend fun setOrientationLock(lock: String)

  public suspend fun setShowStatusBar(visible: Boolean)
}
