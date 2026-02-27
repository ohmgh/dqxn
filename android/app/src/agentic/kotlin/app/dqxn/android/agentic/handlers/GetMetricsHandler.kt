package app.dqxn.android.agentic.handlers

import dev.agentic.android.runtime.AgenticCommand
import dev.agentic.android.runtime.CommandHandler
import dev.agentic.android.runtime.CommandParams
import dev.agentic.android.runtime.CommandResult
import app.dqxn.android.sdk.observability.metrics.MetricsCollector
import javax.inject.Inject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Returns detailed frame timing histograms and per-widget draw times. */
@AgenticCommand(
  name = "get-metrics",
  description = "Returns frame timing histograms and per-widget draw times",
  category = "diagnostics",
)
internal class GetMetricsHandler
@Inject
constructor(
  private val metricsCollector: MetricsCollector,
) : CommandHandler {

  override val name: String = "get-metrics"
  override val description: String = "Returns frame timing histograms and per-widget draw times"
  override val category: String = "diagnostics"
  override val aliases: List<String> = listOf("metrics")

  override suspend fun execute(params: CommandParams, commandId: String): CommandResult {
    val snapshot = metricsCollector.snapshot()
    val json = buildJsonObject {
      put("totalFrameCount", snapshot.totalFrameCount)
      put(
        "frameHistogram",
        buildJsonArray {
          for (count in snapshot.frameHistogram) {
            add(kotlinx.serialization.json.JsonPrimitive(count))
          }
        },
      )
      put(
        "widgetDrawTimes",
        buildJsonObject {
          for ((typeId, times) in snapshot.widgetDrawTimes) {
            put(
              typeId,
              buildJsonObject {
                put("samples", times.size)
                if (times.isNotEmpty()) {
                  put("avgNanos", times.average().toLong())
                  put("maxNanos", times.max())
                  put("minNanos", times.min())
                }
              },
            )
          }
        },
      )
      put(
        "providerLatencies",
        buildJsonObject {
          for ((providerId, latencies) in snapshot.providerLatencies) {
            put(
              providerId,
              buildJsonObject {
                put("samples", latencies.size)
                if (latencies.isNotEmpty()) {
                  put("avgMs", latencies.average().toLong())
                  put("maxMs", latencies.max())
                }
              },
            )
          }
        },
      )
      put(
        "recompositionCounts",
        buildJsonObject {
          for ((typeId, count) in snapshot.recompositionCounts) {
            put(typeId, count)
          }
        },
      )
      put("captureTimestamp", snapshot.captureTimestamp)
    }
    return CommandResult.Success(json.toString())
  }

  override fun paramsSchema(): Map<String, String> = emptyMap()
}
