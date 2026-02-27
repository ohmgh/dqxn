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

  override fun getThemes(): List<ThemeSpec> = listOf(MINIMALIST_THEME, SLATE_THEME)

  internal companion object {
    /** Minimalist: clean light theme with subtle blues and warm grays. */
    val MINIMALIST_THEME =
      DashboardThemeDefinition(
        themeId = "essentials:minimalist",
        displayName = "Minimalist",
        isDark = false,
        packId = "essentials",
        requiredAnyEntitlement = null,
        primaryTextColor = Color(0xFF212121),
        secondaryTextColor = Color(0xFF757575),
        accentColor = Color(0xFF1976D2),
        highlightColor = Color(0xFF42A5F5),
        widgetBorderColor = Color(0xFFE0E0E0),
        backgroundBrush =
          Brush.verticalGradient(
            colors = listOf(Color(0xFFF5F5F5), Color(0xFFEEEEEE)),
          ),
        widgetBackgroundBrush =
          Brush.verticalGradient(
            colors = listOf(Color.White, Color(0xFFFAFAFA)),
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
        primaryTextColor = Color(0xFFE0E0E0),
        secondaryTextColor = Color(0xFF9E9E9E),
        accentColor = Color(0xFF4FC3F7),
        highlightColor = Color(0xFF81D4FA),
        widgetBorderColor = Color(0xFF37474F),
        backgroundBrush =
          Brush.verticalGradient(
            colors = listOf(Color(0xFF1A1A2E), Color(0xFF16213E)),
          ),
        widgetBackgroundBrush =
          Brush.verticalGradient(
            colors = listOf(Color(0xFF1E2A3A), Color(0xFF162032)),
          ),
        errorColor = Color(0xFFEF5350),
        warningColor = Color(0xFFFFB74D),
        successColor = Color(0xFF66BB6A),
        defaultBackgroundStyle = BackgroundStyle.SOLID,
        defaultHasGlowEffect = false,
      )
  }
}
