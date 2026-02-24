package app.dqxn.android.core.design.token

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Card size categories based on visual footprint.
 *
 * Size determines corner radius -- larger cards have more prominent rounding.
 * Ported verbatim from old codebase `DashboardThemeExtensions.kt`.
 *
 * @property cornerRadius The corner radius for this size category.
 */
public enum class CardSize(public val cornerRadius: Dp) {
  /**
   * Compact items: picker cells, chips, thumbnails, selection options.
   * Used when many cards appear together in grids or lists.
   */
  SMALL(8.dp),

  /**
   * Standard content cards: status, info, permission, settings.
   * The workhorse size for most card content.
   */
  MEDIUM(12.dp),

  /**
   * Hero displays: feature browser cards, prominent previews.
   * Used for cards that are the primary focus of a screen.
   */
  LARGE(16.dp),
}
