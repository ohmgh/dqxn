package app.dqxn.android.sdk.contracts.theme

/**
 * Provider of theme definitions from a pack.
 *
 * Packs implement this and register via Hilt multibinding. The shell aggregates all providers to
 * build the complete theme catalog.
 */
public interface ThemeProvider {
  public val packId: String

  public fun getThemes(): List<ThemeSpec>
}
