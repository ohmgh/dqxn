package app.dqxn.android.sdk.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Dark slate fallback theme. Used when no theme provider is available. */
public val SlateTheme: DashboardThemeDefinition =
  DashboardThemeDefinition(
    themeId = "essentials:slate",
    displayName = "Slate",
    isDark = true,
    packId = "essentials",
    primaryTextColor = Color(0xFFE0E0E0),
    secondaryTextColor = Color(0xFF9E9E9E),
    accentColor = Color(0xFF4FC3F7),
    highlightColor = Color(0xFF81D4FA),
    widgetBorderColor = Color(0xFF37474F),
    backgroundBrush = Brush.verticalGradient(listOf(Color(0xFF1A1A2E), Color(0xFF16213E))),
    widgetBackgroundBrush = Brush.verticalGradient(listOf(Color(0xFF1E2A3A), Color(0xFF162032))),
    errorColor = Color(0xFFEF5350),
    warningColor = Color(0xFFFFB74D),
    successColor = Color(0xFF66BB6A),
  )

/** Light minimalist theme. */
public val MinimalistTheme: DashboardThemeDefinition =
  DashboardThemeDefinition(
    themeId = "essentials:minimalist",
    displayName = "Minimalist",
    isDark = false,
    packId = "essentials",
    primaryTextColor = Color(0xFF212121),
    secondaryTextColor = Color(0xFF757575),
    accentColor = Color(0xFF1976D2),
    highlightColor = Color(0xFF42A5F5),
    widgetBorderColor = Color(0xFFE0E0E0),
    backgroundBrush = Brush.verticalGradient(listOf(Color(0xFFF5F5F5), Color(0xFFEEEEEE))),
    widgetBackgroundBrush = Brush.verticalGradient(listOf(Color.White, Color(0xFFFAFAFA))),
    errorColor = Color(0xFFD32F2F),
    warningColor = Color(0xFFF57C00),
    successColor = Color(0xFF388E3C),
  )
