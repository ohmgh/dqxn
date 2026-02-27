package app.dqxn.android.agentic.handlers

import app.dqxn.android.sdk.contracts.widget.WidgetRenderer
import dev.agentic.android.runtime.AgenticCommand
import dev.agentic.android.runtime.CommandHandler
import dev.agentic.android.runtime.CommandParams
import dev.agentic.android.runtime.CommandResult
import javax.inject.Inject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Lists all registered widget renderers with their type IDs. Empty in Phase 6. */
@AgenticCommand(
  name = "list-widgets",
  description = "Returns JSON array of registered widget typeIds and display names",
  category = "registry",
)
internal class ListWidgetsHandler
@Inject
constructor(
  private val widgets: Set<@JvmSuppressWildcards WidgetRenderer>,
) : CommandHandler {

  override val name: String = "list-widgets"
  override val description: String =
    "Returns JSON array of registered widget typeIds and display names"
  override val category: String = "registry"
  override val aliases: List<String> = listOf("widgets")

  override suspend fun execute(params: CommandParams, commandId: String): CommandResult {
    val json = buildJsonObject {
      put("count", widgets.size)
      put(
        "widgets",
        buildJsonArray {
          for (widget in widgets) {
            add(
              buildJsonObject {
                put("typeId", widget.typeId)
                put("displayName", widget.displayName)
                put("description", widget.description)
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
