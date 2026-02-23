package app.dqxn.android.sdk.contracts.theme

/**
 * Theme auto-switch modes for `ThemeAutoSwitchEngine`.
 * - [LIGHT] / [DARK] -- fixed mode, no auto-switching
 * - [SYSTEM] -- follows Android `uiMode` configuration
 * - [SOLAR_AUTO] -- switches based on solar position (sunrise/sunset)
 * - [ILLUMINANCE_AUTO] -- switches based on ambient light sensor
 */
public enum class AutoSwitchMode {
  LIGHT,
  DARK,
  SYSTEM,
  SOLAR_AUTO,
  ILLUMINANCE_AUTO,
}
