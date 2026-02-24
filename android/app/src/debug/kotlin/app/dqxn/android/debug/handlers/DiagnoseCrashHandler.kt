package app.dqxn.android.debug.handlers

import app.dqxn.android.core.agentic.AgenticCommand
import app.dqxn.android.core.agentic.CommandHandler
import app.dqxn.android.core.agentic.CommandParams
import app.dqxn.android.core.agentic.CommandResult
import app.dqxn.android.sdk.observability.crash.CrashEvidenceWriter
import javax.inject.Inject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Returns the last crash evidence from SharedPreferences for post-mortem analysis. */
@AgenticCommand(
  name = "diagnose-crash",
  description = "Returns last crash evidence including exception, stack trace, and widget typeId",
  category = "diagnostics",
)
class DiagnoseCrashHandler
@Inject
constructor(
  private val crashEvidenceWriter: CrashEvidenceWriter,
) : CommandHandler {

  override val name: String = "diagnose-crash"
  override val description: String =
    "Returns last crash evidence including exception, stack trace, and widget typeId"
  override val category: String = "diagnostics"
  override val aliases: List<String> = listOf("crash")

  override suspend fun execute(params: CommandParams, commandId: String): CommandResult {
    val evidence = crashEvidenceWriter.readLastCrash()
      ?: return CommandResult.Success("""{"hasCrash":false}""")

    val json = buildJsonObject {
      put("hasCrash", true)
      evidence.typeId?.let { put("typeId", it) }
      put("exception", evidence.exception)
      put("stackTop5", evidence.stackTop5)
      put("timestamp", evidence.timestamp)
    }
    return CommandResult.Success(json.toString())
  }

  override fun paramsSchema(): Map<String, String> = emptyMap()
}
