package app.dqxn.android.sdk.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Static CompositionLocal for the active dashboard theme.
 *
 * Static because theme changes are infrequent -- forces full recomposition on theme switch, which
 * is the correct behavior (all widget colors change simultaneously).
 */
public val LocalDashboardTheme:
  androidx.compose.runtime.ProvidableCompositionLocal<DashboardThemeDefinition> =
  staticCompositionLocalOf {
    SlateTheme
  }
