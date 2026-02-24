package app.dqxn.android.debug.handlers

import app.dqxn.android.core.agentic.AgenticCommand
import app.dqxn.android.core.agentic.CommandHandler
import app.dqxn.android.core.agentic.CommandParams
import app.dqxn.android.core.agentic.CommandResult
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Lists all registered agentic commands with metadata for discovery. */
@AgenticCommand(
  name = "list-commands",
  description = "Lists all registered agentic commands with name, description, category, and params",
  category = "system",
)
internal class ListCommandsHandler
@Inject
constructor(
  private val handlersProvider: Provider<Set<@JvmSuppressWildcards CommandHandler>>,
) : CommandHandler {

  override val name: String = "list-commands"
  override val description: String =
    "Lists all registered agentic commands with name, description, category, and params"
  override val category: String = "system"
  override val aliases: List<String> = listOf("help", "commands")

  override suspend fun execute(params: CommandParams, commandId: String): CommandResult {
    val json = buildJsonArray {
      for (handler in handlersProvider.get().sortedBy { it.name }) {
        add(
          buildJsonObject {
            put("name", handler.name)
            put("description", handler.description)
            put("category", handler.category)
            put(
              "params",
              buildJsonObject {
                handler.paramsSchema().forEach { (key, desc) -> put(key, desc) }
              },
            )
          }
        )
      }
    }
    return CommandResult.Success(json.toString())
  }

  override fun paramsSchema(): Map<String, String> = emptyMap()
}
