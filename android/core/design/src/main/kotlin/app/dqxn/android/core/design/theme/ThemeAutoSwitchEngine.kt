package app.dqxn.android.core.design.theme

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import app.dqxn.android.sdk.common.di.ApplicationScope
import app.dqxn.android.sdk.contracts.theme.AutoSwitchMode
import app.dqxn.android.sdk.observability.log.DqxnLogger
import app.dqxn.android.sdk.observability.log.LogTags
import app.dqxn.android.sdk.observability.log.info
import app.dqxn.android.sdk.ui.theme.DashboardThemeDefinition
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Theme auto-switch engine implementing 5 modes from [AutoSwitchMode].
 *
 * Uses late-binding [StateFlow] inputs rather than depending on `:data` directly. Preference flows
 * are bound via [bindPreferences] (called from `:app` DI assembly). Sensor flows are bound via
 * [bindIlluminance] and [bindSolarDaytime] (Phase 8 providers).
 *
 * When [AutoSwitchMode.SOLAR_AUTO] or [AutoSwitchMode.ILLUMINANCE_AUTO] has no bound sensor input,
 * the engine falls back to [AutoSwitchMode.SYSTEM] behavior.
 *
 * Uses [SharingStarted.Eagerly] so [isDarkActive] has a value immediately at cold start (no suspend
 * required).
 */
@Singleton
public class ThemeAutoSwitchEngine
@Inject
constructor(
  @ApplicationContext private val context: Context,
  @ApplicationScope private val scope: CoroutineScope,
  private val logger: DqxnLogger,
  private val builtInThemes: BuiltInThemes,
) {

  // Internal mutable state for preference binding
  private val _autoSwitchMode = MutableStateFlow(AutoSwitchMode.SYSTEM)
  private val _lightThemeId = MutableStateFlow("minimalist")
  private val _darkThemeId = MutableStateFlow("slate")
  private val _illuminanceThreshold = MutableStateFlow(200f)

  // Late-binding sensor inputs -- null means no provider bound
  private val _illuminanceFlow = MutableStateFlow<Float?>(null)
  private val _solarIsDaytimeFlow = MutableStateFlow<Boolean?>(null)

  // System dark mode reactive flow
  private val _systemDarkMode = MutableStateFlow(isSystemInDarkMode())

  init {
    registerConfigurationCallback()
  }

  /**
   * Binds preference flows from `:app` DI assembly. Call site: `:app` module invokes this after
   * constructing both `ThemeAutoSwitchEngine` and `UserPreferencesRepository`.
   *
   * Each source flow is collected on [scope] and updates the corresponding internal
   * [MutableStateFlow].
   */
  public fun bindPreferences(
    autoSwitchMode: StateFlow<AutoSwitchMode>,
    lightThemeId: StateFlow<String>,
    darkThemeId: StateFlow<String>,
    illuminanceThreshold: StateFlow<Float>,
  ) {
    scope.launch { autoSwitchMode.collect { _autoSwitchMode.value = it } }
    scope.launch { lightThemeId.collect { _lightThemeId.value = it } }
    scope.launch { darkThemeId.collect { _darkThemeId.value = it } }
    scope.launch { illuminanceThreshold.collect { _illuminanceThreshold.value = it } }
    logger.info(LogTags.THEME) { "Preferences bound to ThemeAutoSwitchEngine" }
  }

  /**
   * Binds an illuminance sensor flow. Used by light sensor data providers (Phase 8). Once bound,
   * [AutoSwitchMode.ILLUMINANCE_AUTO] uses real sensor data instead of falling back to system dark
   * mode.
   */
  public fun bindIlluminance(source: StateFlow<Float>) {
    scope.launch { source.collect { _illuminanceFlow.value = it } }
    logger.info(LogTags.THEME) { "Illuminance sensor bound to ThemeAutoSwitchEngine" }
  }

  /**
   * Binds a solar daytime flow. Used by solar position providers (Phase 8). Once bound,
   * [AutoSwitchMode.SOLAR_AUTO] uses real solar data instead of falling back to system dark mode.
   */
  public fun bindSolarDaytime(source: StateFlow<Boolean>) {
    scope.launch { source.collect { _solarIsDaytimeFlow.value = it } }
    logger.info(LogTags.THEME) { "Solar daytime sensor bound to ThemeAutoSwitchEngine" }
  }

  /**
   * Whether dark mode is currently active, based on the selected [AutoSwitchMode] and available
   * sensor inputs. Falls back to system dark mode when sensor inputs are unbound.
   *
   * Uses [SharingStarted.Eagerly] -- value available immediately at cold start.
   */
  public val isDarkActive: StateFlow<Boolean> =
    combine(
        _autoSwitchMode,
        _systemDarkMode,
        _solarIsDaytimeFlow,
        _illuminanceFlow,
        _illuminanceThreshold,
      ) { mode, systemDark, solarDaytime, lux, threshold ->
        when (mode) {
          AutoSwitchMode.LIGHT -> false
          AutoSwitchMode.DARK -> true
          AutoSwitchMode.SYSTEM -> systemDark
          AutoSwitchMode.SOLAR_AUTO -> solarDaytime?.not() ?: systemDark
          AutoSwitchMode.ILLUMINANCE_AUTO -> lux?.let { it < threshold } ?: systemDark
        }
      }
      .distinctUntilChanged()
      .stateIn(scope, SharingStarted.Eagerly, isSystemInDarkMode())

  /**
   * The currently active theme, resolved by combining [isDarkActive] with the light/dark theme ID
   * selections.
   *
   * Uses [SharingStarted.Eagerly] -- value available immediately at cold start.
   */
  public val activeTheme: StateFlow<DashboardThemeDefinition> =
    combine(isDarkActive, _lightThemeId, _darkThemeId) { isDark, lightId, darkId ->
        val themeId = if (isDark) darkId else lightId
        builtInThemes.resolveById(themeId)
          ?: if (isDark) builtInThemes.slate else builtInThemes.minimalist
      }
      .distinctUntilChanged()
      .stateIn(scope, SharingStarted.Eagerly, resolveInitialTheme())

  private fun isSystemInDarkMode(): Boolean {
    val nightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
    return nightMode == Configuration.UI_MODE_NIGHT_YES
  }

  private fun resolveInitialTheme(): DashboardThemeDefinition =
    if (isSystemInDarkMode()) builtInThemes.slate else builtInThemes.minimalist

  private fun registerConfigurationCallback() {
    context.registerComponentCallbacks(
      object : ComponentCallbacks2 {
        override fun onConfigurationChanged(newConfig: Configuration) {
          val nightMode = newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK
          _systemDarkMode.value = nightMode == Configuration.UI_MODE_NIGHT_YES
        }

        override fun onLowMemory() {
          // Not used.
        }

        @Deprecated("Deprecated in API 34", ReplaceWith(""))
        override fun onTrimMemory(level: Int) {
          // Not used.
        }
      }
    )
  }
}
