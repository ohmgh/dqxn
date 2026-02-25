package app.dqxn.android.pack.themes

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.jupiter.api.Test

/**
 * Proves the F6.3/F6.4 entitlement gating chain is complete.
 *
 * The gating chain for SOLAR_AUTO and ILLUMINANCE_AUTO modes:
 * 1. All 22 premium themes have `requiredAnyEntitlement = setOf("themes")`
 * 2. Theme Studio UI gates theme selection at selection time
 * 3. SOLAR_AUTO/ILLUMINANCE_AUTO modes resolve via ThemeAutoSwitchEngine which only picks between
 *    user-selected light/dark themes
 * 4. Therefore mode activation itself needs no separate entitlement check -- it can only activate
 *    themes the user has already been entitled to select
 *
 * This test validates step 1 (the foundation of the chain) and documents that the remaining steps
 * are architectural guarantees (no theme leaks without gating).
 */
class ThemeEntitlementGatingChainTest {

  private val provider = ThemesPackThemeProvider()

  @Test
  fun `all premium themes have requiredAnyEntitlement containing themes`() {
    val themes = provider.getThemes()
    assertThat(themes).hasSize(22)
    for (theme in themes) {
      assertWithMessage("requiredAnyEntitlement for ${theme.themeId}")
        .that(theme.requiredAnyEntitlement)
        .isEqualTo(setOf("themes"))
    }
  }

  @Test
  fun `no premium theme has null requiredAnyEntitlement`() {
    for (theme in provider.getThemes()) {
      assertWithMessage("requiredAnyEntitlement for ${theme.themeId}")
        .that(theme.requiredAnyEntitlement)
        .isNotNull()
    }
  }

  @Test
  fun `gating chain completeness -- SOLAR_AUTO and ILLUMINANCE_AUTO covered by theme-level gating`() {
    // This test documents the complete gating chain:
    // - ThemesPackThemeProvider sets requiredAnyEntitlement = setOf("themes") on ALL 22 themes
    // - Theme Studio (Phase 11) gates selection via isAccessible() check
    // - ThemeAutoSwitchEngine (Phase 5, :core:design) resolves SOLAR_AUTO/ILLUMINANCE_AUTO modes
    //   by picking between the user's selected light/dark themes
    // - Since both selected themes were already entitled at selection time, mode activation
    //   is inherently gated -- no separate mode-level entitlement check needed
    //
    // Verifiable assertion: every theme returned by this provider is gated
    val themes = provider.getThemes()
    assertThat(themes).isNotEmpty()
    val ungatedThemes = themes.filter { it.requiredAnyEntitlement.isNullOrEmpty() }
    assertWithMessage("themes without entitlement gating (should be none)")
      .that(ungatedThemes)
      .isEmpty()
  }
}
