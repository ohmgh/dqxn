package app.dqxn.android.debug.handlers

import app.dqxn.android.core.agentic.AgenticCommand
import app.dqxn.android.core.agentic.CommandHandler
import app.dqxn.android.core.agentic.CommandParams
import app.dqxn.android.core.agentic.CommandResult
import app.dqxn.android.sdk.observability.diagnostic.AnomalyTrigger
import app.dqxn.android.sdk.observability.diagnostic.DiagnosticSnapshotCapture
import javax.inject.Inject

/** Fires a diagnostic snapshot capture with a synthetic anomaly trigger for testing. */
@AgenticCommand(
  name = "trigger-anomaly",
  description = "Fires a diagnostic snapshot capture with reason 'agentic-trigger'",
  category = "testing",
)
class TriggerAnomalyHandler
@Inject
constructor(
  private val diagnosticCapture: DiagnosticSnapshotCapture,
) : CommandHandler {

  override val name: String = "trigger-anomaly"
  override val description: String =
    "Fires a diagnostic snapshot capture with reason 'agentic-trigger'"
  override val category: String = "testing"
  override val aliases: List<String> = emptyList()

  override suspend fun execute(params: CommandParams, commandId: String): CommandResult {
    val snapshot = diagnosticCapture.capture(
      trigger = AnomalyTrigger.JankSpike(consecutiveFrames = 0),
      agenticTraceId = commandId,
    )

    return if (snapshot != null) {
      CommandResult.Success(
        """{"triggered":true,"reason":"agentic-trigger","snapshotId":"${snapshot.id}"}"""
      )
    } else {
      CommandResult.Success(
        """{"triggered":false,"reason":"concurrent capture or storage pressure"}"""
      )
    }
  }

  override fun paramsSchema(): Map<String, String> = emptyMap()
}
