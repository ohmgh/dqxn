package app.dqxn.android.feature.dashboard.coordinator

import androidx.compose.runtime.Immutable
import app.dqxn.android.core.design.theme.BuiltInThemes
import app.dqxn.android.core.design.theme.ThemeAutoSwitchEngine
import app.dqxn.android.data.preferences.UserPreferencesRepository
import app.dqxn.android.sdk.contracts.theme.AutoSwitchMode
import app.dqxn.android.sdk.observability.log.DqxnLogger
import app.dqxn.android.sdk.observability.log.LogTags
import app.dqxn.android.sdk.observability.log.info
import app.dqxn.android.sdk.ui.theme.DashboardThemeDefinition
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Manages theme state for the dashboard with preview/revert cycle.
 *
 * Owns a [ThemeState] slice following decomposed state architecture. Observes
 * [ThemeAutoSwitchEngine.isDarkActive] and [UserPreferencesRepository] for theme selections.
 *
 * Key pattern from replication advisory section 3: preview theme is set BEFORE navigation to
 * prevent flash. [ThemeState.displayTheme] derivation is the single source for `LocalDashboardTheme`.
 */
public class ThemeCoordinator
@Inject
constructor(
  private val themeAutoSwitchEngine: ThemeAutoSwitchEngine,
  private val builtInThemes: BuiltInThemes,
  private val userPreferencesRepository: UserPreferencesRepository,
  private val logger: DqxnLogger,
) {

  private val _themeState =
    MutableStateFlow(
      ThemeState(
        currentTheme = builtInThemes.minimalist,
        darkTheme = builtInThemes.slate,
        lightTheme = builtInThemes.minimalist,
        autoSwitchMode = AutoSwitchMode.SYSTEM,
        previewTheme = null,
      )
    )

  /** Observable theme state. Collect from composables via `collectAsState()`. */
  public val themeState: StateFlow<ThemeState> = _themeState.asStateFlow()

  /**
   * Initializes theme observation on the provided [scope].
   *
   * Eagerly collects [ThemeAutoSwitchEngine.isDarkActive] to derive [ThemeState.currentTheme] from
   * the dark/light theme selection. Also collects user preference changes.
   */
  public fun initialize(scope: CoroutineScope) {
    // Observe auto-switch engine to derive currentTheme from dark/light selection
    scope.launch {
      themeAutoSwitchEngine.isDarkActive.collect { isDark ->
        _themeState.update { state ->
          val resolved = if (isDark) state.darkTheme else state.lightTheme
          state.copy(currentTheme = resolved)
        }
      }
    }

    // Observe user preference for auto-switch mode
    scope.launch {
      userPreferencesRepository.autoSwitchMode.collect { mode ->
        _themeState.update { it.copy(autoSwitchMode = mode) }
      }
    }

    // Observe light theme selection from preferences
    scope.launch {
      userPreferencesRepository.lightThemeId.collect { themeId ->
        val theme = builtInThemes.resolveById(themeId) ?: builtInThemes.minimalist
        _themeState.update { state ->
          val updated = state.copy(lightTheme = theme)
          // If not in dark mode, update current theme too
          if (!themeAutoSwitchEngine.isDarkActive.value) {
            updated.copy(currentTheme = theme)
          } else {
            updated
          }
        }
      }
    }

    // Observe dark theme selection from preferences
    scope.launch {
      userPreferencesRepository.darkThemeId.collect { themeId ->
        val theme = builtInThemes.resolveById(themeId) ?: builtInThemes.slate
        _themeState.update { state ->
          val updated = state.copy(darkTheme = theme)
          // If in dark mode, update current theme too
          if (themeAutoSwitchEngine.isDarkActive.value) {
            updated.copy(currentTheme = theme)
          } else {
            updated
          }
        }
      }
    }

    logger.info(LogTags.THEME) { "ThemeCoordinator initialized" }
  }

  /**
   * Sets the active theme by ID. Resolves from [BuiltInThemes] and persists selection to
   * [UserPreferencesRepository] based on current dark/light mode.
   */
  public suspend fun handleSetTheme(themeId: String) {
    val theme = builtInThemes.resolveById(themeId) ?: return

    _themeState.update { it.copy(currentTheme = theme) }

    // Persist to the appropriate slot based on whether the theme is dark or light
    if (theme.isDark) {
      userPreferencesRepository.setDarkThemeId(themeId)
    } else {
      userPreferencesRepository.setLightThemeId(themeId)
    }

    logger.info(LogTags.THEME) { "Theme set: $themeId (isDark=${theme.isDark})" }
  }

  /**
   * Sets or clears the preview theme. When set, [ThemeState.displayTheme] returns the preview
   * theme instead of the current theme. Pass `null` to clear (revert).
   *
   * Key pattern: set preview BEFORE navigation to prevent theme flash on transition.
   */
  public fun handlePreviewTheme(theme: DashboardThemeDefinition?) {
    _themeState.update { it.copy(previewTheme = theme) }

    if (theme != null) {
      logger.info(LogTags.THEME) { "Preview theme set: ${theme.themeId}" }
    } else {
      logger.info(LogTags.THEME) { "Preview theme cleared" }
    }
  }

  /**
   * Cycles through theme modes: LIGHT -> DARK -> SYSTEM -> SOLAR_AUTO -> ILLUMINANCE_AUTO -> LIGHT.
   *
   * Persists the new mode to [UserPreferencesRepository]. Implements F10.9 quick theme toggle.
   */
  public suspend fun handleCycleThemeMode() {
    val currentMode = _themeState.value.autoSwitchMode
    val nextMode =
      when (currentMode) {
        AutoSwitchMode.LIGHT -> AutoSwitchMode.DARK
        AutoSwitchMode.DARK -> AutoSwitchMode.SYSTEM
        AutoSwitchMode.SYSTEM -> AutoSwitchMode.SOLAR_AUTO
        AutoSwitchMode.SOLAR_AUTO -> AutoSwitchMode.ILLUMINANCE_AUTO
        AutoSwitchMode.ILLUMINANCE_AUTO -> AutoSwitchMode.LIGHT
      }

    _themeState.update { it.copy(autoSwitchMode = nextMode) }
    userPreferencesRepository.setAutoSwitchMode(nextMode)

    logger.info(LogTags.THEME) { "Theme mode cycled: $currentMode -> $nextMode" }
  }
}

/**
 * Theme state slice for the dashboard.
 *
 * [displayTheme] is the single source for `LocalDashboardTheme` -- returns [previewTheme] when
 * set, otherwise [currentTheme].
 */
@Immutable
public data class ThemeState(
  val currentTheme: DashboardThemeDefinition,
  val darkTheme: DashboardThemeDefinition,
  val lightTheme: DashboardThemeDefinition,
  val autoSwitchMode: AutoSwitchMode,
  val previewTheme: DashboardThemeDefinition?,
) {
  /** The theme to display. Preview takes priority over current. */
  val displayTheme: DashboardThemeDefinition
    get() = previewTheme ?: currentTheme
}
