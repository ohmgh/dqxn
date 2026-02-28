package app.dqxn.android.pack.essentials.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import app.dqxn.android.sdk.contracts.annotation.DashboardThemeProvider
import app.dqxn.android.sdk.contracts.theme.ThemeProvider
import app.dqxn.android.sdk.contracts.theme.ThemeSpec
import app.dqxn.android.sdk.contracts.widget.BackgroundStyle
import app.dqxn.android.sdk.ui.theme.DashboardThemeDefinition
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides 2 free themes for the essentials pack: Minimalist (light) and Slate (dark).
 *
 * Both themes have `requiredAnyEntitlement = null` (free tier, no gating). Registered via Hilt
 * multibinding as `Set<ThemeProvider>`. The shell aggregates all providers to build the complete
 * theme catalog available to the user.
 */
@DashboardThemeProvider
@Singleton
public class EssentialsThemeProvider @Inject constructor() : ThemeProvider {

  override val packId: String = "essentials"

  override fun getThemes(): List<ThemeSpec> = emptyList()

  internal companion object {
    /** Minimalist: clean light theme with subtle blue-gray tones. */
    val MINIMALIST_THEME =
      DashboardThemeDefinition(
        themeId = "essentials:minimalist",
        displayName = "Minimalist",
        isDark = false,
        packId = "essentials",
        requiredAnyEntitlement = null,
        primaryTextColor = Color(0xFF191C1E),
        secondaryTextColor = Color(0xFF5E6569),
        accentColor = Color(0xFF2563EB),
        highlightColor = Color(0xFFF472B6),
        widgetBorderColor = Color(0xFFBDBDBD),
        backgroundBrush =
          Brush.verticalGradient(
            colors = listOf(Color(0xFFF0F2F5), Color(0xFFE2E4E7)),
          ),
        widgetBackgroundBrush =
          Brush.linearGradient(
            colors = listOf(Color(0xFFFFFFFF), Color(0xFFF5F5F7)),
          ),
        errorColor = Color(0xFFD32F2F),
        warningColor = Color(0xFFF57C00),
        successColor = Color(0xFF388E3C),
        defaultBackgroundStyle = BackgroundStyle.SOLID,
        defaultHasGlowEffect = false,
      )

    /** Slate: dark theme with cool blue-gray tones. */
    val SLATE_THEME =
      DashboardThemeDefinition(
        themeId = "essentials:slate",
        displayName = "Slate",
        isDark = true,
        packId = "essentials",
        requiredAnyEntitlement = null,
        primaryTextColor = Color(0xFFECF0F1),
        secondaryTextColor = Color(0xB3BDC3C7.toInt()),
        accentColor = Color(0xFF3498DB),
        highlightColor = Color(0xFFFBBF24),
        widgetBorderColor = Color(0xFF95A5A6),
        backgroundBrush =
          Brush.verticalGradient(
            colors = listOf(Color(0xFF2C3E50), Color(0xFF1A252F)),
          ),
        widgetBackgroundBrush =
          Brush.linearGradient(
            colors = listOf(Color(0x25FFFFFF), Color(0x0AFFFFFF)),
          ),
        errorColor = Color(0xFFEF5350),
        warningColor = Color(0xFFFFB74D),
        successColor = Color(0xFF66BB6A),
        defaultBackgroundStyle = BackgroundStyle.SOLID,
        defaultHasGlowEffect = false,
      )
  }
}
