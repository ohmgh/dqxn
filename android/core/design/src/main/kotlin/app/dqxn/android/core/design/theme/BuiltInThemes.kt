package app.dqxn.android.core.design.theme

import android.content.Context
import app.dqxn.android.sdk.ui.theme.DashboardThemeDefinition
import app.dqxn.android.sdk.ui.theme.MinimalistTheme
import app.dqxn.android.sdk.ui.theme.SlateTheme
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * Registry of built-in themes.
 *
 * Provides references to the two free themes (Slate and Minimalist) defined in `:sdk:ui`, and a
 * method to load bundled premium theme JSON files from `assets/themes/`.
 *
 * Free themes are always listed first. Premium theme files are populated in Phase 9.
 */
@Singleton
public class BuiltInThemes @Inject constructor(private val parser: ThemeJsonParser) {

  /** Dark slate fallback theme. */
  public val slate: DashboardThemeDefinition = SlateTheme

  /** Light minimalist theme. */
  public val minimalist: DashboardThemeDefinition = MinimalistTheme

  /** All free built-in themes (always available regardless of entitlement). */
  public val freeThemes: ImmutableList<DashboardThemeDefinition> =
    persistentListOf(slate, minimalist)

  /** Resolves a theme by ID from the free built-in set. Returns null if not found. */
  public fun resolveById(themeId: String): DashboardThemeDefinition? =
    when (themeId) {
      slate.themeId -> slate
      minimalist.themeId -> minimalist
      else -> null
    }

  /**
   * Loads bundled premium theme JSON files from `assets/themes/` directory. Returns an empty list
   * if no theme files exist (Phase 9 populates these). Free themes are NOT included in this list --
   * use [freeThemes] for those.
   */
  public fun loadBundledThemes(context: Context): ImmutableList<DashboardThemeDefinition> {
    val assetManager = context.assets
    val themeFiles =
      try {
        assetManager.list("themes")?.filter { it.endsWith(".json") } ?: emptyList()
      } catch (_: Exception) {
        emptyList()
      }

    if (themeFiles.isEmpty()) return persistentListOf()

    return themeFiles
      .mapNotNull { fileName ->
        try {
          val jsonString =
            assetManager.open("themes/$fileName").bufferedReader().use { it.readText() }
          parser.parse(jsonString)
        } catch (_: Exception) {
          null
        }
      }
      .toImmutableList()
  }
}
