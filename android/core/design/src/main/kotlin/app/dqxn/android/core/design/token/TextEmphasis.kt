package app.dqxn.android.core.design.token

/**
 * Alpha values for text emphasis levels. Apply via `color.copy(alpha = TextEmphasis.Medium)`.
 *
 * Ported verbatim from old codebase `DashboardThemeExtensions.kt`.
 *
 * Note: Old codebase has a known inconsistency where `OverlayTitleBar` uses inline `alpha = 0.6f`
 * instead of [Medium] (0.7f). Callers should always use these constants rather than inline literals.
 */
public object TextEmphasis {
  /** Primary text -- full opacity. */
  public const val High: Float = 1.0f

  /** Secondary text -- descriptions, subtitles. */
  public const val Medium: Float = 0.7f

  /** Disabled text -- inactive items. */
  public const val Disabled: Float = 0.4f

  /** Pressed overlay -- interactive feedback. */
  public const val Pressed: Float = 0.12f
}
