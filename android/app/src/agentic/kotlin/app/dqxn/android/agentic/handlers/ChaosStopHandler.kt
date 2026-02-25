package app.dqxn.android.agentic.handlers

import app.dqxn.android.core.agentic.AgenticCommand
import app.dqxn.android.core.agentic.CommandHandler
import app.dqxn.android.core.agentic.CommandParams
import app.dqxn.android.core.agentic.CommandResult
import app.dqxn.android.core.agentic.chaos.ChaosEngine
import javax.inject.Inject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Stops the active chaos testing session and returns a summary of injected faults.
 * Returns an error if no session is active.
 */
@AgenticCommand(
  name = "chaos-stop",
  description = "Stop the active chaos testing session and return injection summary",
  category = "chaos",
)
internal class ChaosStopHandler
@Inject
constructor(
  private val engine: ChaosEngine,
) : CommandHandler {

  override val name: String = "chaos-stop"
  override val description: String = "Stop the active chaos testing session and return injection summary"
  override val category: String = "chaos"
  override val aliases: List<String> = listOf("chaos-end")

  override suspend fun execute(params: CommandParams, commandId: String): CommandResult {
    return try {
      val summary = engine.stop()
      val json = buildJsonObject {
        put("sessionId", summary.sessionId)
        put("seed", summary.seed)
        put("profile", summary.profile)
        put("durationMs", summary.durationMs)
        put("faultCount", summary.injectedFaults.size)
        put(
          "injectedFaults",
          buildJsonArray {
            for (fault in summary.injectedFaults) {
              add(
                buildJsonObject {
                  put("providerId", fault.providerId)
                  put("faultType", fault.faultType)
                  put("description", fault.description)
                  put("atMs", fault.atMs)
                }
              )
            }
          },
        )
      }
      CommandResult.Success(json.toString())
    } catch (e: IllegalStateException) {
      CommandResult.Error(message = e.message ?: "No active session", code = "NO_SESSION")
    }
  }

  override fun paramsSchema(): Map<String, String> = emptyMap()
}
