package app.dqxn.android.feature.settings

import app.dqxn.android.sdk.contracts.settings.DateFormatOption
import app.dqxn.android.sdk.contracts.setup.InstructionAction

/**
 * Navigation events for sub-navigation within the settings overlay.
 *
 * Each event carries the [settingKey] that triggered it, plus the current/selected value for
 * pre-populating the destination picker.
 */
public sealed interface SettingNavigation {
  /** Navigate to the timezone picker for the given setting. */
  public data class ToTimezonePicker(
    val settingKey: String,
    val currentValue: String?,
  ) : SettingNavigation

  /** Navigate to the date format picker for the given setting. */
  public data class ToDateFormatPicker(
    val settingKey: String,
    val currentValue: DateFormatOption,
  ) : SettingNavigation

  /** Navigate to the app picker for the given setting. */
  public data class ToAppPicker(
    val settingKey: String,
    val currentPackage: String?,
  ) : SettingNavigation

  /** Execute an instruction action (open system settings, URL, or app). */
  public data class OnInstructionAction(
    val settingKey: String,
    val action: InstructionAction,
  ) : SettingNavigation
}
