package app.dqxn.android.sdk.contracts.theme

import app.dqxn.android.sdk.contracts.entitlement.Gated

/**
 * Theme metadata interface. Extends [Gated] for entitlement-based access control.
 *
 * Metadata only -- no Compose types (`Brush`, `Color`). The full runtime type
 * `DashboardThemeDefinition` with Compose dependencies lives in `:sdk:ui` (Phase 3). Packs
 * implement [ThemeProvider] and return concrete `DashboardThemeDefinition` extending this
 * interface.
 */
public interface ThemeSpec : Gated {
  public val themeId: String
  public val displayName: String
  public val isDark: Boolean
  public val packId: String?
}
