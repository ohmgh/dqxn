package app.dqxn.android.pack.themes

import app.dqxn.android.sdk.contracts.theme.ThemeProvider
import app.dqxn.android.sdk.contracts.theme.ThemeSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides 22 premium themes loaded from JSON resource files in this pack.
 *
 * Every returned theme has `requiredAnyEntitlement = setOf("themes")`, gating Theme Studio access
 * (F6.2) and ensuring SOLAR_AUTO/ILLUMINANCE_AUTO modes (F6.3/F6.4) only resolve between
 * entitled theme selections. The gating chain is complete at the theme level -- mode activation
 * itself needs no separate entitlement check because modes only pick between user-selected
 * light/dark themes, which are already gated at selection time.
 */
@Singleton
public class ThemesPackThemeProvider @Inject constructor() : ThemeProvider {

  override val packId: String = "themes"

  override fun getThemes(): List<ThemeSpec> = loadedThemes

  private val loadedThemes: List<ThemeSpec> by lazy {
    THEME_FILES.mapNotNull { filename ->
      val json =
        javaClass.classLoader!!.getResourceAsStream("themes/$filename")
          ?.bufferedReader()
          ?.readText()
          ?: return@mapNotNull null
      parseThemeJson(json)?.copy(requiredAnyEntitlement = setOf("themes"))
    }
  }

  internal companion object {
    /** All 22 premium theme JSON filenames. */
    val THEME_FILES: List<String> =
      listOf(
        "arctic.theme.json",
        "aurora.theme.json",
        "carbon.theme.json",
        "cloud.theme.json",
        "cyberpunk.theme.json",
        "ember.theme.json",
        "expressive.theme.json",
        "forest.theme.json",
        "lavender.theme.json",
        "midnight_purple.theme.json",
        "mint.theme.json",
        "mocha.theme.json",
        "neon_pink.theme.json",
        "ocean_breeze.theme.json",
        "peach.theme.json",
        "rose.theme.json",
        "sage.theme.json",
        "sand.theme.json",
        "sky.theme.json",
        "sunset_glow.theme.json",
        "tron.theme.json",
        "void.theme.json",
      )
  }
}
