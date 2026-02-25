package app.dqxn.android.agentic.handlers

import app.dqxn.android.core.agentic.AgenticCommand
import app.dqxn.android.core.agentic.CommandHandler
import app.dqxn.android.core.agentic.CommandParams
import app.dqxn.android.core.agentic.CommandResult
import app.dqxn.android.sdk.contracts.provider.DataProvider
import javax.inject.Inject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Lists all registered data providers with their source IDs. Empty in Phase 6. */
@AgenticCommand(
  name = "list-providers",
  description = "Returns JSON array of registered data provider sourceIds and metadata",
  category = "registry",
)
internal class ListProvidersHandler
@Inject
constructor(
  private val providers: Set<@JvmSuppressWildcards DataProvider<*>>,
) : CommandHandler {

  override val name: String = "list-providers"
  override val description: String =
    "Returns JSON array of registered data provider sourceIds and metadata"
  override val category: String = "registry"
  override val aliases: List<String> = listOf("providers")

  override suspend fun execute(params: CommandParams, commandId: String): CommandResult {
    val json = buildJsonObject {
      put("count", providers.size)
      put(
        "providers",
        buildJsonArray {
          for (provider in providers) {
            add(
              buildJsonObject {
                put("sourceId", provider.sourceId)
                put("displayName", provider.displayName)
                put("dataType", provider.dataType)
                put("isAvailable", provider.isAvailable)
              }
            )
          }
        },
      )
    }
    return CommandResult.Success(json.toString())
  }

  override fun paramsSchema(): Map<String, String> = emptyMap()
}
