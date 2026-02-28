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
    primaryTextColor = Color(0xFFECF0F1),
    secondaryTextColor = Color(0xB3BDC3C7.toInt()),
    accentColor = Color(0xFF3498DB),
    highlightColor = Color(0xFFFBBF24),
    widgetBorderColor = Color(0xFF95A5A6),
    backgroundBrush = Brush.verticalGradient(listOf(Color(0xFF2C3E50), Color(0xFF1A252F))),
    widgetBackgroundBrush = Brush.linearGradient(listOf(Color(0x25FFFFFF), Color(0x0AFFFFFF))),
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
    primaryTextColor = Color(0xFF191C1E),
    secondaryTextColor = Color(0xFF5E6569),
    accentColor = Color(0xFF2563EB),
    highlightColor = Color(0xFFF472B6),
    widgetBorderColor = Color(0xFFBDBDBD),
    backgroundBrush = Brush.verticalGradient(listOf(Color(0xFFF0F2F5), Color(0xFFE2E4E7))),
    widgetBackgroundBrush = Brush.linearGradient(listOf(Color(0xFFFFFFFF), Color(0xFFF5F5F7))),
    errorColor = Color(0xFFD32F2F),
    warningColor = Color(0xFFF57C00),
    successColor = Color(0xFF388E3C),
  )
