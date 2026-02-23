package app.dqxn.android.sdk.ui.layout

import app.dqxn.android.sdk.contracts.settings.InfoCardLayoutMode
import app.dqxn.android.sdk.contracts.settings.SizeOption

/** Safe parsing of [InfoCardLayoutMode] from a string value, with fallback to [STANDARD]. */
public fun parseLayoutMode(value: String): InfoCardLayoutMode =
  try {
    InfoCardLayoutMode.valueOf(value)
  } catch (_: IllegalArgumentException) {
    InfoCardLayoutMode.STANDARD
  }

/** Safe parsing of [SizeOption] from a string value, with fallback to [MEDIUM]. */
public fun parseSizeOption(value: String): SizeOption =
  try {
    SizeOption.valueOf(value)
  } catch (_: IllegalArgumentException) {
    SizeOption.MEDIUM
  }
