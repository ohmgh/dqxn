package app.dqxn.android.pack.themes

import app.dqxn.android.sdk.contracts.entitlement.isAccessible
import app.dqxn.android.sdk.ui.theme.DashboardThemeDefinition
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Tests for [ThemesPackThemeProvider] verifying 22 premium themes are loaded from JSON resources
 * with correct metadata and entitlement gating.
 */
class ThemesPackThemeProviderTest {

  private val provider = ThemesPackThemeProvider()

  @Test
  fun `packId is themes`() {
    assertThat(provider.packId).isEqualTo("themes")
  }

  @Test
  fun `getThemes returns exactly 22 themes`() {
    assertThat(provider.getThemes()).hasSize(22)
  }

  @Test
  fun `all themes have requiredAnyEntitlement containing themes`() {
    for (theme in provider.getThemes()) {
      assertThat(theme.requiredAnyEntitlement).isEqualTo(setOf("themes"))
    }
  }

  @Test
  fun `all themes have packId themes`() {
    for (theme in provider.getThemes()) {
      val definition = theme as DashboardThemeDefinition
      assertThat(definition.packId).isEqualTo("themes")
    }
  }

  @Test
  fun `all theme IDs start with themes colon prefix`() {
    for (theme in provider.getThemes()) {
      assertThat(theme.themeId).startsWith("themes:")
    }
  }

  @Test
  fun `no duplicate theme IDs`() {
    val ids = provider.getThemes().map { it.themeId }
    assertThat(ids).containsNoDuplicates()
  }

  @Test
  fun `each theme has non-empty displayName`() {
    for (theme in provider.getThemes()) {
      assertThat(theme.displayName).isNotEmpty()
    }
  }

  @Test
  fun `mix of dark and light themes present`() {
    val themes = provider.getThemes()
    val darkCount = themes.count { it.isDark }
    val lightCount = themes.count { !it.isDark }
    assertThat(darkCount).isGreaterThan(0)
    assertThat(lightCount).isGreaterThan(0)
  }

  @Test
  fun `all themes are gated -- not accessible without themes entitlement`() {
    for (theme in provider.getThemes()) {
      assertThat(theme.isAccessible { false }).isFalse()
    }
  }

  @Test
  fun `all themes are accessible with themes entitlement`() {
    for (theme in provider.getThemes()) {
      assertThat(theme.isAccessible { it == "themes" }).isTrue()
    }
  }
}
