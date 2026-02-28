package app.dqxn.android.pack.essentials.theme

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tests for [EssentialsThemeProvider].
 *
 * Free themes (Minimalist, Slate) are now defined as built-in themes in `:sdk:ui` (DefaultTheme.kt)
 * with `packId = "essentials"`. The provider returns an empty list to avoid duplicates — the shell
 * aggregates built-in themes separately from pack-provided themes.
 */
class EssentialsThemeProviderTest {

  private val provider = EssentialsThemeProvider()

  @Test
  fun `packId is essentials`() {
    assertThat(provider.packId).isEqualTo("essentials")
  }

  @Test
  fun `getThemes returns empty list -- free themes are built-in`() {
    assertThat(provider.getThemes()).isEmpty()
  }

  @Test
  fun `companion themes still have correct metadata`() {
    val minimalist = EssentialsThemeProvider.MINIMALIST_THEME
    assertThat(minimalist.themeId).isEqualTo("essentials:minimalist")
    assertThat(minimalist.displayName).isEqualTo("Minimalist")
    assertThat(minimalist.isDark).isFalse()
    assertThat(minimalist.packId).isEqualTo("essentials")

    val slate = EssentialsThemeProvider.SLATE_THEME
    assertThat(slate.themeId).isEqualTo("essentials:slate")
    assertThat(slate.displayName).isEqualTo("Slate")
    assertThat(slate.isDark).isTrue()
    assertThat(slate.packId).isEqualTo("essentials")
  }

  @Test
  fun `companion themes are free tier (no entitlement required)`() {
    assertThat(EssentialsThemeProvider.MINIMALIST_THEME.requiredAnyEntitlement).isNull()
    assertThat(EssentialsThemeProvider.SLATE_THEME.requiredAnyEntitlement).isNull()
  }

  @Test
  fun `companion theme IDs are unique`() {
    val ids = listOf(
      EssentialsThemeProvider.MINIMALIST_THEME.themeId,
      EssentialsThemeProvider.SLATE_THEME.themeId,
    )
    assertThat(ids).containsNoDuplicates()
  }

  @Test
  fun `companion themes have non-empty display names`() {
    assertThat(EssentialsThemeProvider.MINIMALIST_THEME.displayName).isNotEmpty()
    assertThat(EssentialsThemeProvider.SLATE_THEME.displayName).isNotEmpty()
  }

  @Test
  fun `companion themes reference essentials packId`() {
    assertThat(EssentialsThemeProvider.MINIMALIST_THEME.packId).isEqualTo("essentials")
    assertThat(EssentialsThemeProvider.SLATE_THEME.packId).isEqualTo("essentials")
  }
}
