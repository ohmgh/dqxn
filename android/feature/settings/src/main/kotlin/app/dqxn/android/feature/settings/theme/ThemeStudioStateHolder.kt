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
import app.dqxn.android.sdk.ui.theme.GradientStop
import app.dqxn.android.sdk.ui.theme.GradientType
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * Decomposed state holder for custom theme editing in Theme Studio.
 *
 * Each color property is individually mutable, avoiding a god-composable with a single monolithic
 * state class. [isDirty] is derived via [derivedStateOf] to track whether any field has changed
 * from the saved (initial) values.
 *
 * Max 12 custom themes is enforced at the caller level (ThemeSelector), not here.
 *
 * @param initialTheme The theme being edited, or null for a new custom theme with defaults.
 */
@Stable
public class ThemeStudioStateHolder(initialTheme: DashboardThemeDefinition?) {

  // -- Editable display name --
  public var displayName: String by
    mutableStateOf(initialTheme?.displayName ?: "Custom Theme")

  // -- 6 core color tokens (mutable for editing) --
  public var primaryTextColor: Color by
    mutableStateOf(initialTheme?.primaryTextColor ?: Color.White)
  public var secondaryTextColor: Color by
    mutableStateOf(initialTheme?.secondaryTextColor ?: Color.White.copy(alpha = 0.7f))
  public var accentColor: Color by mutableStateOf(initialTheme?.accentColor ?: Color.Cyan)
  public var highlightColor: Color by mutableStateOf(initialTheme?.highlightColor ?: Color.Cyan)
  public var widgetBorderColor: Color by
    mutableStateOf(initialTheme?.widgetBorderColor ?: Color.White.copy(alpha = 0.2f))
  public var isDark: Boolean by mutableStateOf(initialTheme?.isDark ?: true)

  // -- Gradient state for background --
  public var backgroundGradientType: GradientType by
    mutableStateOf(
      initialTheme?.let { extractGradientType(it.backgroundBrush) } ?: GradientType.VERTICAL,
    )
  public var backgroundStops: ImmutableList<GradientStop> by
    mutableStateOf(
      initialTheme?.let { extractGradientStops(it.backgroundBrush) }
        ?: persistentListOf(
          GradientStop(color = Color.Black.value.toLong(), position = 0f),
          GradientStop(color = Color.DarkGray.value.toLong(), position = 1f),
        ),
    )

  // -- Gradient state for widget background --
  public var widgetBackgroundGradientType: GradientType by
    mutableStateOf(
      initialTheme?.let { extractGradientType(it.widgetBackgroundBrush) }
        ?: GradientType.VERTICAL,
    )
  public var widgetBackgroundStops: ImmutableList<GradientStop> by
    mutableStateOf(
      initialTheme?.let { extractGradientStops(it.widgetBackgroundBrush) }
        ?: persistentListOf(
          GradientStop(color = Color(0xFF1A1A1A).value.toLong(), position = 0f),
          GradientStop(color = Color.Black.value.toLong(), position = 1f),
        ),
    )

  // -- Saved state for isDirty comparison --
  private val savedDisplayName: String = initialTheme?.displayName ?: "Custom Theme"
  private val savedPrimaryTextColor: Color = initialTheme?.primaryTextColor ?: Color.White
  private val savedSecondaryTextColor: Color =
    initialTheme?.secondaryTextColor ?: Color.White.copy(alpha = 0.7f)
  private val savedAccentColor: Color = initialTheme?.accentColor ?: Color.Cyan
  private val savedHighlightColor: Color = initialTheme?.highlightColor ?: Color.Cyan
  private val savedWidgetBorderColor: Color =
    initialTheme?.widgetBorderColor ?: Color.White.copy(alpha = 0.2f)
  private val savedIsDark: Boolean = initialTheme?.isDark ?: true
  private val savedBackgroundGradientType: GradientType =
    initialTheme?.let { extractGradientType(it.backgroundBrush) } ?: GradientType.VERTICAL
  private val savedBackgroundStops: ImmutableList<GradientStop> =
    initialTheme?.let { extractGradientStops(it.backgroundBrush) }
      ?: persistentListOf(
        GradientStop(color = Color.Black.value.toLong(), position = 0f),
        GradientStop(color = Color.DarkGray.value.toLong(), position = 1f),
      )
  private val savedWidgetBackgroundGradientType: GradientType =
    initialTheme?.let { extractGradientType(it.widgetBackgroundBrush) }
      ?: GradientType.VERTICAL
  private val savedWidgetBackgroundStops: ImmutableList<GradientStop> =
    initialTheme?.let { extractGradientStops(it.widgetBackgroundBrush) }
      ?: persistentListOf(
        GradientStop(color = Color(0xFF1A1A1A).value.toLong(), position = 0f),
        GradientStop(color = Color.Black.value.toLong(), position = 1f),
      )

  /**
   * Whether any editable field has changed from the saved state.
   *
   * Includes displayName, all color tokens, isDark, and gradient state (type + stops).
   */
  public val isDirty: Boolean by derivedStateOf {
    displayName != savedDisplayName ||
      primaryTextColor != savedPrimaryTextColor ||
      secondaryTextColor != savedSecondaryTextColor ||
      accentColor != savedAccentColor ||
      highlightColor != savedHighlightColor ||
      widgetBorderColor != savedWidgetBorderColor ||
      isDark != savedIsDark ||
      backgroundGradientType != savedBackgroundGradientType ||
      backgroundStops != savedBackgroundStops ||
      widgetBackgroundGradientType != savedWidgetBackgroundGradientType ||
      widgetBackgroundStops != savedWidgetBackgroundStops
  }

  /**
   * Builds a [DashboardThemeDefinition] from the current editing state.
   *
   * Uses the mutable [displayName] property and derives brush from gradient state.
   *
   * @param themeId Unique ID for the custom theme (typically "custom_{timestamp}").
   */
  public fun buildCustomTheme(themeId: String): DashboardThemeDefinition =
    DashboardThemeDefinition(
      themeId = themeId,
      displayName = displayName,
      isDark = isDark,
      primaryTextColor = primaryTextColor,
      secondaryTextColor = secondaryTextColor,
      accentColor = accentColor,
      highlightColor = highlightColor,
      widgetBorderColor = widgetBorderColor,
      backgroundBrush = buildBrush(backgroundGradientType, backgroundStops),
      widgetBackgroundBrush =
        buildBrush(widgetBackgroundGradientType, widgetBackgroundStops),
    )

  /** Resets all mutable properties to their saved/initial values. */
  public fun reset() {
    displayName = savedDisplayName
    primaryTextColor = savedPrimaryTextColor
    secondaryTextColor = savedSecondaryTextColor
    accentColor = savedAccentColor
    highlightColor = savedHighlightColor
    widgetBorderColor = savedWidgetBorderColor
    isDark = savedIsDark
    backgroundGradientType = savedBackgroundGradientType
    backgroundStops = savedBackgroundStops
    widgetBackgroundGradientType = savedWidgetBackgroundGradientType
    widgetBackgroundStops = savedWidgetBackgroundStops
  }

  private companion object {
    fun extractGradientType(
      @Suppress("UNUSED_PARAMETER") brush: Brush,
    ): GradientType = GradientType.VERTICAL

    fun extractGradientStops(
      @Suppress("UNUSED_PARAMETER") brush: Brush,
    ): ImmutableList<GradientStop> =
      persistentListOf(
        GradientStop(color = Color.Black.value.toLong(), position = 0f),
        GradientStop(color = Color.DarkGray.value.toLong(), position = 1f),
      )
  }
}

/** Converts gradient type + stops to a Compose [Brush]. */
private fun buildBrush(type: GradientType, stops: ImmutableList<GradientStop>): Brush {
  if (stops.size < 2) {
    return SolidColor(
      Color(stops.firstOrNull()?.color?.toULong() ?: Color.Black.value),
    )
  }
  val colorStops =
    stops
      .sortedBy { it.position }
      .map { it.position to Color(it.color.toULong()) }
      .toTypedArray()
  return when (type) {
    GradientType.VERTICAL -> Brush.verticalGradient(colorStops = colorStops)
    GradientType.HORIZONTAL -> Brush.horizontalGradient(colorStops = colorStops)
    GradientType.LINEAR -> Brush.linearGradient(colorStops = colorStops)
    GradientType.RADIAL -> Brush.radialGradient(colorStops = colorStops)
    GradientType.SWEEP -> Brush.sweepGradient(colorStops = colorStops)
  }
}
