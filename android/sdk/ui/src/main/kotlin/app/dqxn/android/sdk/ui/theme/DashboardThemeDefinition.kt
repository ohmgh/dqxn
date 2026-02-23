package app.dqxn.android.sdk.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import app.dqxn.android.sdk.contracts.theme.ThemeSpec
import app.dqxn.android.sdk.contracts.widget.BackgroundStyle

/**
 * Full runtime theme type with Compose-dependent color/brush fields.
 *
 * 6 core color tokens (from replication-advisory section 5): [primaryTextColor],
 * [secondaryTextColor], [accentColor], [highlightColor], [widgetBorderColor], plus
 * [backgroundBrush] and [widgetBackgroundBrush].
 *
 * 3 semantic tokens: [errorColor], [warningColor], [successColor].
 *
 * Lives in `:sdk:ui` (not `:core:design`) because packs implement `ThemeProvider` returning this
 * type, and packs depend only on `:sdk:*`.
 */
@Immutable
public data class DashboardThemeDefinition(
  // -- ThemeSpec metadata --
  override val themeId: String,
  override val displayName: String,
  override val isDark: Boolean = true,
  override val packId: String? = null,
  override val requiredAnyEntitlement: Set<String>? = null,
  // -- 6 core color tokens --
  val primaryTextColor: Color,
  val secondaryTextColor: Color,
  val accentColor: Color,
  val highlightColor: Color = accentColor,
  val widgetBorderColor: Color,
  val backgroundBrush: Brush,
  val widgetBackgroundBrush: Brush,
  // -- 3 semantic tokens --
  val errorColor: Color = Color(0xFFEF5350),
  val warningColor: Color = Color(0xFFFFB74D),
  val successColor: Color = Color(0xFF66BB6A),
  // -- Defaults --
  val defaultBackgroundStyle: BackgroundStyle = BackgroundStyle.SOLID,
  val defaultHasGlowEffect: Boolean = false,
  // -- Serializable gradient specs for persistence/transport --
  val backgroundGradientSpec: GradientSpec? = null,
  val widgetBackgroundGradientSpec: GradientSpec? = null,
) : ThemeSpec {

  public companion object {
    /** Full emphasis -- primary text, active elements. */
    public const val EMPHASIS_HIGH: Float = 1.0f

    /** Medium emphasis -- secondary text, subtitles. */
    public const val EMPHASIS_MEDIUM: Float = 0.7f

    /** Low emphasis -- hints, placeholders, tertiary labels. */
    public const val EMPHASIS_LOW: Float = 0.4f

    /** Disabled state -- dimmed surfaces and text. */
    public const val EMPHASIS_DISABLED: Float = 0.12f
  }
}
