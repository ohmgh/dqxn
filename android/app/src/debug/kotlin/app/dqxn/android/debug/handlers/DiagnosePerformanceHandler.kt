package app.dqxn.android.debug.handlers

import app.dqxn.android.core.agentic.AgenticCommand
import app.dqxn.android.core.agentic.CommandHandler
import app.dqxn.android.core.agentic.CommandParams
import app.dqxn.android.core.agentic.CommandResult
import app.dqxn.android.sdk.observability.metrics.MetricsCollector
import javax.inject.Inject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Returns a performance metrics snapshot including frame histogram and jank statistics. */
@AgenticCommand(
  name = "diagnose-performance",
  description = "Returns performance metrics snapshot with frame histogram and jank stats",
  category = "diagnostics",
)
class DiagnosePerformanceHandler
@Inject
constructor(
  private val metricsCollector: MetricsCollector,
) : CommandHandler {

  override val name: String = "diagnose-performance"
  override val description: String =
    "Returns performance metrics snapshot with frame histogram and jank stats"
  override val category: String = "diagnostics"
  override val aliases: List<String> = listOf("perf")

  override suspend fun execute(params: CommandParams, commandId: String): CommandResult {
    val snapshot = metricsCollector.snapshot()
    val json = buildJsonObject {
      put("totalFrameCount", snapshot.totalFrameCount)
      put(
        "frameHistogram",
        buildJsonObject {
          put("under8ms", snapshot.frameHistogram.getOrElse(0) { 0L })
          put("under12ms", snapshot.frameHistogram.getOrElse(1) { 0L })
          put("under16ms", snapshot.frameHistogram.getOrElse(2) { 0L })
          put("under24ms", snapshot.frameHistogram.getOrElse(3) { 0L })
          put("under33ms", snapshot.frameHistogram.getOrElse(4) { 0L })
          put("over33ms", snapshot.frameHistogram.getOrElse(5) { 0L })
        },
      )
      put(
        "jankStats",
        buildJsonObject {
          val total = snapshot.totalFrameCount
          val jankFrames =
            snapshot.frameHistogram.getOrElse(4) { 0L } +
              snapshot.frameHistogram.getOrElse(5) { 0L }
          put("jankFrames", jankFrames)
          put(
            "jankPercentage",
            if (total > 0) (jankFrames.toDouble() / total * 100) else 0.0,
          )
        },
      )
      put("captureTimestamp", snapshot.captureTimestamp)
    }
    return CommandResult.Success(json.toString())
  }

  override fun paramsSchema(): Map<String, String> = emptyMap()
}
