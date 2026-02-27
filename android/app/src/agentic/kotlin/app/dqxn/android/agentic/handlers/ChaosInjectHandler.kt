package app.dqxn.android.agentic.handlers

import dev.agentic.android.chaos.Fault
import dev.agentic.android.chaos.FaultInjector
import dev.agentic.android.runtime.AgenticCommand
import dev.agentic.android.runtime.CommandHandler
import dev.agentic.android.runtime.CommandParams
import dev.agentic.android.runtime.CommandResult
import dev.agentic.android.runtime.getString
import javax.inject.Inject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Injects a single fault into a specific provider via [FaultInjector], independent of any
 * [ChaosEngine] session. Useful for targeted manual fault injection.
 *
 * Params: `providerId` (required), `fault` (required: "kill", "stall", "delay", "error", "flap"),
 * `delayMs` (optional, for delay fault), `onMs`/`offMs` (optional, for flap fault).
 */
@AgenticCommand(
  name = "chaos-inject",
  description = "Inject a single fault into a specific data provider",
  category = "chaos",
)
internal class ChaosInjectHandler
@Inject
constructor(
  private val injector: FaultInjector,
) : CommandHandler {

  override val name: String = "chaos-inject"
  override val description: String = "Inject a single fault into a specific data provider"
  override val category: String = "chaos"
  override val aliases: List<String> = listOf("inject-fault")

  override suspend fun execute(params: CommandParams, commandId: String): CommandResult {
    val providerId =
      params.getString("providerId")
        ?: return CommandResult.Error(
          message = "Missing required param: providerId",
          code = "MISSING_PARAM",
        )

    val faultName =
      params.getString("fault")
        ?: return CommandResult.Error(
          message = "Missing required param: fault (kill, stall, delay, error, flap)",
          code = "MISSING_PARAM",
        )

    val fault =
      when (faultName.lowercase()) {
        "kill" -> Fault.Kill
        "stall" -> Fault.Stall
        "delay" -> {
          val delayMs = params.getString("delayMs")?.toLongOrNull() ?: 1000L
          Fault.Delay(delayMs)
        }
        "error" -> Fault.Error(RuntimeException("Chaos injected error"))
        "flap" -> {
          val onMs = params.getString("onMs")?.toLongOrNull() ?: 2000L
          val offMs = params.getString("offMs")?.toLongOrNull() ?: 2000L
          Fault.Flap(onMillis = onMs, offMillis = offMs)
        }
        else ->
          return CommandResult.Error(
            message = "Unknown fault type: $faultName. Valid: kill, stall, delay, error, flap",
            code = "UNKNOWN_FAULT",
          )
      }

    injector.inject(providerId, fault)

    val activeFaults = injector.activeFaults()
    val json = buildJsonObject {
      put("injected", true)
      put("providerId", providerId)
      put("fault", faultName.lowercase())
      put("activeFaultCount", activeFaults.size)
    }
    return CommandResult.Success(json.toString())
  }

  override fun paramsSchema(): Map<String, String> =
    mapOf(
      "providerId" to "Target provider source ID (required)",
      "fault" to "Fault type: kill, stall, delay, error, flap (required)",
      "delayMs" to "Delay duration in ms (for 'delay' fault, default: 1000)",
      "onMs" to "On window duration in ms (for 'flap' fault, default: 2000)",
      "offMs" to "Off window duration in ms (for 'flap' fault, default: 2000)",
    )
}
