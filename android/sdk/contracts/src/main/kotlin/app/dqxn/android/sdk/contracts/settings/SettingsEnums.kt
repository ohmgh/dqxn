package app.dqxn.android.sdk.contracts.settings

/** Widget size option for settings sliders/pickers. */
public enum class SizeOption {
  SMALL,
  MEDIUM,
  LARGE,
  EXTRA_LARGE,
}

/** Returns the size multiplier for this option. */
public fun SizeOption.toMultiplier(): Float =
  when (this) {
    SizeOption.SMALL -> 0.75f
    SizeOption.MEDIUM -> 1.0f
    SizeOption.LARGE -> 1.25f
    SizeOption.EXTRA_LARGE -> 1.5f
  }

/** Position of the timezone display within a widget. */
public enum class TimezonePosition {
  TOP,
  BOTTOM,
  HIDDEN,
}

/** Layout option for date display widgets. */
public enum class DateLayoutOption {
  SIMPLE,
  STACK,
  GRID,
}

/** Common date format patterns. */
public enum class DateFormatOption(public val pattern: String) {
  /** "Mon, Jan 1" */
  DAY_MONTH_SHORT("EEE, MMM d"),

  /** "Monday, January 1" */
  DAY_MONTH_LONG("EEEE, MMMM d"),

  /** "01/01/2026" */
  NUMERIC_MDY("MM/dd/yyyy"),

  /** "01-01-2026" */
  NUMERIC_MDY_DASH("MM-dd-yyyy"),

  /** "2026/01/01" */
  NUMERIC_YMD("yyyy/MM/dd"),

  /** "2026-01-01" */
  ISO_DATE("yyyy-MM-dd"),

  /** "1 Jan 2026" */
  DAY_MONTH_YEAR("d MMM yyyy"),

  /** "January 1, 2026" */
  MONTH_DAY_YEAR("MMMM d, yyyy"),
}

/** Layout mode for info card widgets. */
public enum class InfoCardLayoutMode {
  STANDARD,
  COMPACT,
  WIDE,
}

/** Type of sound for [SettingDefinition.SoundPickerSetting]. */
public enum class SoundType {
  NOTIFICATION,
  ALARM,
  RINGTONE,
}
