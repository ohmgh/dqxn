package app.dqxn.android.agentic.handlers

import app.dqxn.android.core.agentic.AgenticCommand
import app.dqxn.android.core.agentic.CommandHandler
import app.dqxn.android.core.agentic.CommandParams
import app.dqxn.android.core.agentic.CommandResult
import app.dqxn.android.sdk.contracts.theme.ThemeProvider
import javax.inject.Inject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Lists all registered theme providers with their theme IDs. Empty in Phase 6. */
@AgenticCommand(
  name = "list-themes",
  description = "Returns JSON array of registered theme IDs grouped by pack",
  category = "registry",
)
internal class ListThemesHandler
@Inject
constructor(
  private val themes: Set<@JvmSuppressWildcards ThemeProvider>,
) : CommandHandler {

  override val name: String = "list-themes"
  override val description: String = "Returns JSON array of registered theme IDs grouped by pack"
  override val category: String = "registry"
  override val aliases: List<String> = listOf("themes")

  override suspend fun execute(params: CommandParams, commandId: String): CommandResult {
    val json = buildJsonObject {
      put("providerCount", themes.size)
      put(
        "themes",
        buildJsonArray {
          for (provider in themes) {
            val themeList = provider.getThemes()
            for (theme in themeList) {
              add(
                buildJsonObject {
                  put("themeId", theme.themeId)
                  put("displayName", theme.displayName)
                  put("packId", provider.packId)
                },
              )
            }
          }
        },
      )
    }
    return CommandResult.Success(json.toString())
  }

  override fun paramsSchema(): Map<String, String> = emptyMap()
}
