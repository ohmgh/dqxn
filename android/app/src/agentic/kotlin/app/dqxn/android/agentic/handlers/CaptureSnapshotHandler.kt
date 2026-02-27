package app.dqxn.android.agentic.handlers

import app.dqxn.android.sdk.observability.diagnostic.AnomalyTrigger
import app.dqxn.android.sdk.observability.diagnostic.DiagnosticSnapshotCapture
import dev.agentic.android.runtime.AgenticCommand
import dev.agentic.android.runtime.CommandHandler
import dev.agentic.android.runtime.CommandParams
import dev.agentic.android.runtime.CommandResult
import dev.agentic.android.runtime.getString
import javax.inject.Inject

/** Fires a diagnostic snapshot capture with a custom reason from parameters. */
@AgenticCommand(
  name = "capture-snapshot",
  description = "Captures a diagnostic snapshot with custom reason, returns file path",
  category = "testing",
)
internal class CaptureSnapshotHandler
@Inject
constructor(
  private val diagnosticCapture: DiagnosticSnapshotCapture,
) : CommandHandler {

  override val name: String = "capture-snapshot"
  override val description: String =
    "Captures a diagnostic snapshot with custom reason, returns file path"
  override val category: String = "testing"
  override val aliases: List<String> = listOf("snapshot")

  override suspend fun execute(params: CommandParams, commandId: String): CommandResult {
    val reason = params.getString("reason") ?: "agentic-capture"

    val snapshot =
      diagnosticCapture.capture(
        trigger = AnomalyTrigger.JankSpike(consecutiveFrames = 0),
        agenticTraceId = "$commandId:$reason",
      )

    return if (snapshot != null) {
      CommandResult.Success(
        """{"captured":true,"snapshotId":"${snapshot.id}","reason":"$reason"}"""
      )
    } else {
      CommandResult.Success(
        """{"captured":false,"reason":"concurrent capture or storage pressure"}"""
      )
    }
  }

  override fun paramsSchema(): Map<String, String> =
    mapOf(
      "reason" to
        "Custom reason string for the snapshot capture (optional, defaults to 'agentic-capture')",
    )
}
