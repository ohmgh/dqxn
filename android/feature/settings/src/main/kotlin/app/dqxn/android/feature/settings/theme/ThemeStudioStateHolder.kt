package app.dqxn.android.feature.settings.theme

import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import app.dqxn.android.sdk.ui.theme.DashboardThemeDefinition

/**
 * Decomposed state holder for custom theme editing in Theme Studio.
 *
 * Each color property is individually mutable, avoiding a god-composable with a single
 * monolithic state class. [isDirty] is derived via [derivedStateOf] to track whether any field
 * has changed from the saved (initial) values.
 *
 * Max 12 custom themes is enforced at the caller level (ThemeSelector), not here.
 *
 * @param initialTheme The theme being edited, or null for a new custom theme with defaults.
 */
@Stable
public class ThemeStudioStateHolder(initialTheme: DashboardThemeDefinition?) {

  // -- 6 core color tokens (mutable for editing) --
  public var primaryTextColor: Color by mutableStateOf(initialTheme?.primaryTextColor ?: Color.White)
  public var secondaryTextColor: Color by
    mutableStateOf(initialTheme?.secondaryTextColor ?: Color.White.copy(alpha = 0.7f))
  public var accentColor: Color by mutableStateOf(initialTheme?.accentColor ?: Color.Cyan)
  public var highlightColor: Color by mutableStateOf(initialTheme?.highlightColor ?: Color.Cyan)
  public var widgetBorderColor: Color by
    mutableStateOf(initialTheme?.widgetBorderColor ?: Color.White.copy(alpha = 0.2f))
  public var isDark: Boolean by mutableStateOf(initialTheme?.isDark ?: true)

  // Brush state -- SolidColor for default, preserves initial for dirty tracking
  public var backgroundBrush: Brush by
    mutableStateOf(initialTheme?.backgroundBrush ?: SolidColor(Color.Black))
  public var widgetBackgroundBrush: Brush by
    mutableStateOf(initialTheme?.widgetBackgroundBrush ?: SolidColor(Color(0xFF1A1A1A)))

  // -- Saved state for isDirty comparison --
  private val savedPrimaryTextColor: Color = initialTheme?.primaryTextColor ?: Color.White
  private val savedSecondaryTextColor: Color =
    initialTheme?.secondaryTextColor ?: Color.White.copy(alpha = 0.7f)
  private val savedAccentColor: Color = initialTheme?.accentColor ?: Color.Cyan
  private val savedHighlightColor: Color = initialTheme?.highlightColor ?: Color.Cyan
  private val savedWidgetBorderColor: Color =
    initialTheme?.widgetBorderColor ?: Color.White.copy(alpha = 0.2f)
  private val savedIsDark: Boolean = initialTheme?.isDark ?: true

  /**
   * Whether any editable field has changed from the saved state.
   *
   * Brush comparison is excluded because [Brush] does not override equals meaningfully
   * (gradient brushes are stateless lambda wrappers). Color + isDark changes are sufficient
   * to detect user edits. Brush changes are always coupled with color picker interactions.
   */
  public val isDirty: Boolean by derivedStateOf {
    primaryTextColor != savedPrimaryTextColor ||
      secondaryTextColor != savedSecondaryTextColor ||
      accentColor != savedAccentColor ||
      highlightColor != savedHighlightColor ||
      widgetBorderColor != savedWidgetBorderColor ||
      isDark != savedIsDark
  }

  /**
   * Builds a [DashboardThemeDefinition] from the current editing state.
   *
   * @param themeId Unique ID for the custom theme (typically "custom_{timestamp}").
   * @param displayName Human-readable name for the theme.
   */
  public fun buildCustomTheme(
    themeId: String,
    displayName: String = "Custom Theme",
  ): DashboardThemeDefinition =
    DashboardThemeDefinition(
      themeId = themeId,
      displayName = displayName,
      isDark = isDark,
      primaryTextColor = primaryTextColor,
      secondaryTextColor = secondaryTextColor,
      accentColor = accentColor,
      highlightColor = highlightColor,
      widgetBorderColor = widgetBorderColor,
      backgroundBrush = backgroundBrush,
      widgetBackgroundBrush = widgetBackgroundBrush,
    )
}
