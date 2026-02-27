package app.dqxn.android.agentic.handlers

import app.dqxn.android.sdk.observability.health.WidgetHealthMonitor
import dev.agentic.android.runtime.AgenticCommand
import dev.agentic.android.runtime.CommandHandler
import dev.agentic.android.runtime.CommandParams
import dev.agentic.android.runtime.CommandResult
import javax.inject.Inject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Dumps current widget health status for all tracked widgets. */
@AgenticCommand(
  name = "dump-health",
  description = "Returns widget health snapshot with status, staleness, and crash info",
  category = "diagnostics",
)
internal class DumpHealthHandler
@Inject
constructor(
  private val healthMonitor: WidgetHealthMonitor,
) : CommandHandler {

  override val name: String = "dump-health"
  override val description: String =
    "Returns widget health snapshot with status, staleness, and crash info"
  override val category: String = "diagnostics"
  override val aliases: List<String> = listOf("health")

  override suspend fun execute(params: CommandParams, commandId: String): CommandResult {
    val statuses = healthMonitor.allStatuses()
    val json = buildJsonObject {
      put("widgetCount", statuses.size)
      put(
        "widgets",
        buildJsonArray {
          for ((widgetId, status) in statuses) {
            add(
              buildJsonObject {
                put("widgetId", widgetId)
                put("typeId", status.typeId)
                put("status", status.status.name)
                put("lastDataTimestamp", status.lastDataTimestamp)
                put("lastDrawTimestamp", status.lastDrawTimestamp)
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
