package app.dqxn.android.agentic.handlers

import dev.agentic.android.runtime.AgenticCommand
import dev.agentic.android.runtime.CommandHandler
import dev.agentic.android.runtime.CommandParams
import dev.agentic.android.runtime.CommandResult
import dev.agentic.android.runtime.getString
import dev.agentic.android.chaos.ChaosEngine
import app.dqxn.android.sdk.contracts.provider.DataProvider
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Starts a deterministic chaos testing session via ADB. Resolves all registered provider IDs
 * and delegates to [ChaosEngine.start].
 *
 * Params: `seed` (optional Long, default: current time), `profile` (optional, default: "combined").
 */
@AgenticCommand(
  name = "chaos-start",
  description = "Start a deterministic chaos testing session",
  category = "chaos",
)
internal class ChaosStartHandler
@Inject
constructor(
  private val engine: ChaosEngine,
  private val providers: Set<@JvmSuppressWildcards DataProvider<*>>,
) : CommandHandler {

  override val name: String = "chaos-start"
  override val description: String = "Start a deterministic chaos testing session"
  override val category: String = "chaos"
  override val aliases: List<String> = emptyList()

  override suspend fun execute(params: CommandParams, commandId: String): CommandResult {
    val seed = params.getString("seed")?.toLongOrNull() ?: System.currentTimeMillis()
    val profile = params.getString("profile") ?: "combined"
    val providerIds = providers.map { it.sourceId }

    return try {
      val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
      val session = engine.start(seed, profile, providerIds, scope)
      val json = buildJsonObject {
        put("sessionId", session.sessionId)
        put("seed", seed)
        put("profile", profile)
        put("providerCount", providerIds.size)
      }
      CommandResult.Success(json.toString())
    } catch (e: IllegalStateException) {
      CommandResult.Error(message = e.message ?: "Failed to start chaos session", code = "ALREADY_ACTIVE")
    } catch (e: IllegalArgumentException) {
      CommandResult.Error(message = e.message ?: "Invalid profile", code = "INVALID_PROFILE")
    }
  }

  override fun paramsSchema(): Map<String, String> = mapOf(
    "seed" to "Random seed for deterministic reproduction (Long, default: current time)",
    "profile" to "Chaos profile name: provider-stress, provider-flap, widget-storm, process-death, combined (default: combined)",
  )
}
