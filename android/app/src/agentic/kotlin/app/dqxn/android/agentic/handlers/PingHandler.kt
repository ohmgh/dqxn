package app.dqxn.android.agentic.handlers

import app.dqxn.android.core.agentic.AgenticCommand
import app.dqxn.android.core.agentic.CommandHandler
import app.dqxn.android.core.agentic.CommandParams
import app.dqxn.android.core.agentic.CommandResult
import javax.inject.Inject

/** E2E startup probe. Returns `{"status":"ok","timestamp":...}` confirming the app is alive. */
@AgenticCommand(
  name = "ping",
  description = "E2E startup probe returning status ok with timestamp",
  category = "system",
)
internal class PingHandler @Inject constructor() : CommandHandler {

  override val name: String = "ping"
  override val description: String = "E2E startup probe returning status ok with timestamp"
  override val category: String = "system"
  override val aliases: List<String> = emptyList()

  override suspend fun execute(params: CommandParams, commandId: String): CommandResult =
    CommandResult.Success("""{"status":"ok","timestamp":${System.currentTimeMillis()}}""")

  override fun paramsSchema(): Map<String, String> = emptyMap()
}
