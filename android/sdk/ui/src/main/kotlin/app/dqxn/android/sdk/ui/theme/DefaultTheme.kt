package app.dqxn.android.sdk.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Dark slate fallback theme. Used when no theme provider is available. */
public val SlateTheme: DashboardThemeDefinition =
  DashboardThemeDefinition(
    themeId = "slate",
    displayName = "Slate",
    isDark = true,
    primaryTextColor = Color.White,
    secondaryTextColor = Color(0xFFB0B0B0),
    accentColor = Color(0xFF4FC3F7),
    highlightColor = Color(0xFF4FC3F7),
    widgetBorderColor = Color(0xFF424242),
    backgroundBrush = Brush.verticalGradient(listOf(Color(0xFF1A1A2E), Color(0xFF16213E))),
    widgetBackgroundBrush = Brush.verticalGradient(listOf(Color(0xFF1E1E32), Color(0xFF1A1A2E))),
    errorColor = Color(0xFFEF5350),
    warningColor = Color(0xFFFFB74D),
    successColor = Color(0xFF66BB6A),
  )

/** Light minimalist theme. */
public val MinimalistTheme: DashboardThemeDefinition =
  DashboardThemeDefinition(
    themeId = "minimalist",
    displayName = "Minimalist",
    isDark = false,
    primaryTextColor = Color(0xFF212121),
    secondaryTextColor = Color(0xFF757575),
    accentColor = Color(0xFF1976D2),
    highlightColor = Color(0xFF1976D2),
    widgetBorderColor = Color(0xFFE0E0E0),
    backgroundBrush = Brush.verticalGradient(listOf(Color(0xFFF5F5F5), Color(0xFFEEEEEE))),
    widgetBackgroundBrush = Brush.verticalGradient(listOf(Color.White, Color(0xFFFAFAFA))),
    errorColor = Color(0xFFD32F2F),
    warningColor = Color(0xFFF57C00),
    successColor = Color(0xFF388E3C),
  )
