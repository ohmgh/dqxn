package app.dqxn.android.core.design.token

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Spacing constants for dashboard UI layout. All values follow the Material 3 4dp grid.
 *
 * Ported verbatim from old codebase `DashboardThemeExtensions.kt` — converted from
 * `DashboardThemeDefinition` extension properties to a standalone object so they are accessible
 * from `:core:design` without coupling to `:sdk:ui`.
 */
public object DashboardSpacing {

  // T-shirt sizes
  /** 4.dp -- hairline gaps, icon insets. */
  public val SpaceXXS: Dp = 4.dp

  /** 8.dp -- tight gaps between related items. */
  public val SpaceXS: Dp = 8.dp

  /** 12.dp -- default item-to-item gap. */
  public val SpaceS: Dp = 12.dp

  /** 16.dp -- standard content padding. */
  public val SpaceM: Dp = 16.dp

  /** 24.dp -- section-level spacing. */
  public val SpaceL: Dp = 24.dp

  /** 32.dp -- large structural gaps. */
  public val SpaceXL: Dp = 32.dp

  /** 48.dp -- extra-large structural gaps. */
  public val SpaceXXL: Dp = 48.dp

  // Semantic aliases
  /** 16.dp -- horizontal inset from screen edge. */
  public val ScreenEdgePadding: Dp = 16.dp

  /** 16.dp -- vertical gap between distinct sections. */
  public val SectionGap: Dp = 16.dp

  /** 12.dp -- vertical gap between items in a list. */
  public val ItemGap: Dp = 12.dp

  /** 8.dp -- vertical gap between items within the same logical group. */
  public val InGroupGap: Dp = 8.dp

  /** 8.dp -- horizontal gap between adjacent buttons. */
  public val ButtonGap: Dp = 8.dp

  /** 8.dp -- horizontal gap between an icon and its text label. */
  public val IconTextGap: Dp = 8.dp

  /** 8.dp -- vertical gap between a label and its input field. */
  public val LabelInputGap: Dp = 8.dp

  /** 16.dp -- internal padding inside card surfaces. */
  public val CardInternalPadding: Dp = 16.dp

  /** 16.dp -- horizontal indent for nested content. */
  public val NestedIndent: Dp = 16.dp

  /** 48.dp -- minimum touch target size (accessibility). */
  public val MinTouchTarget: Dp = 48.dp
}
