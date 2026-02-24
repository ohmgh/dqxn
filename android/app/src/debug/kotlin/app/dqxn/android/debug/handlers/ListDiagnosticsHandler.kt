package app.dqxn.android.debug.handlers

import app.dqxn.android.core.agentic.AgenticCommand
import app.dqxn.android.core.agentic.CommandHandler
import app.dqxn.android.core.agentic.CommandParams
import app.dqxn.android.core.agentic.CommandResult
import app.dqxn.android.core.agentic.getString
import app.dqxn.android.sdk.observability.diagnostic.DiagnosticSnapshotCapture
import javax.inject.Inject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Lists diagnostic snapshot files with metadata, optionally filtered by timestamp. */
@AgenticCommand(
  name = "list-diagnostics",
  description = "Lists diagnostic snapshot files with metadata, optional since-epoch filter",
  category = "diagnostics",
)
class ListDiagnosticsHandler
@Inject
constructor(
  private val diagnosticCapture: DiagnosticSnapshotCapture,
) : CommandHandler {

  override val name: String = "list-diagnostics"
  override val description: String =
    "Lists diagnostic snapshot files with metadata, optional since-epoch filter"
  override val category: String = "diagnostics"
  override val aliases: List<String> = listOf("diagnostics")

  override suspend fun execute(params: CommandParams, commandId: String): CommandResult {
    val sinceMs = params.getString("since")?.toLongOrNull()
    val snapshots = diagnosticCapture.recentSnapshots()
      .let { all ->
        if (sinceMs != null) all.filter { it.timestamp >= sinceMs } else all
      }

    val json = buildJsonObject {
      put("count", snapshots.size)
      put(
        "snapshots",
        buildJsonArray {
          for (snapshot in snapshots) {
            add(
              buildJsonObject {
                put("id", snapshot.id)
                put("timestamp", snapshot.timestamp)
                put("triggerType", snapshot.triggerType)
                put("triggerDescription", snapshot.triggerDescription)
                snapshot.agenticTraceId?.let { put("agenticTraceId", it) }
              }
            )
          }
        },
      )
    }
    return CommandResult.Success(json.toString())
  }

  override fun paramsSchema(): Map<String, String> = mapOf(
    "since" to "Filter snapshots after this epoch millisecond timestamp (optional)",
  )
}
