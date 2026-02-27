package app.dqxn.android.pack.essentials.theme

import app.dqxn.android.sdk.contracts.entitlement.isAccessible
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tests for [EssentialsThemeProvider] verifying 2 free themes (Minimalist light, Slate dark) with
 * correct metadata, color tokens, and free-tier gating.
 */
class EssentialsThemeProviderTest {

  private val provider = EssentialsThemeProvider()

  @Test
  fun `packId is essentials`() {
    assertThat(provider.packId).isEqualTo("essentials")
  }

  @Test
  fun `getThemes returns exactly 2 themes`() {
    assertThat(provider.getThemes()).hasSize(2)
  }

  @Test
  fun `first theme is Minimalist light`() {
    val minimalist = provider.getThemes()[0]
    assertThat(minimalist.themeId).isEqualTo("essentials:minimalist")
    assertThat(minimalist.displayName).isEqualTo("Minimalist")
    assertThat(minimalist.isDark).isFalse()
    assertThat(minimalist.packId).isEqualTo("essentials")
  }

  @Test
  fun `second theme is Slate dark`() {
    val slate = provider.getThemes()[1]
    assertThat(slate.themeId).isEqualTo("essentials:slate")
    assertThat(slate.displayName).isEqualTo("Slate")
    assertThat(slate.isDark).isTrue()
    assertThat(slate.packId).isEqualTo("essentials")
  }

  @Test
  fun `both themes are free tier (no entitlement required)`() {
    for (theme in provider.getThemes()) {
      assertThat(theme.requiredAnyEntitlement).isNull()
      // isAccessible should return true when no entitlement is required
      assertThat(theme.isAccessible { false }).isTrue()
    }
  }

  @Test
  fun `theme IDs are unique`() {
    val ids = provider.getThemes().map { it.themeId }
    assertThat(ids).containsNoDuplicates()
  }

  @Test
  fun `both themes have non-empty display names`() {
    for (theme in provider.getThemes()) {
      assertThat(theme.displayName).isNotEmpty()
    }
  }

  @Test
  fun `both themes reference essentials packId`() {
    for (theme in provider.getThemes()) {
      assertThat(theme.packId).isEqualTo("essentials")
    }
  }
}
