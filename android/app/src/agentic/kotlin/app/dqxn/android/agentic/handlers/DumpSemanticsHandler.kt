package app.dqxn.android.agentic.handlers

import app.dqxn.android.core.agentic.AgenticCommand
import app.dqxn.android.core.agentic.CommandHandler
import app.dqxn.android.core.agentic.CommandParams
import app.dqxn.android.core.agentic.CommandResult
import app.dqxn.android.core.agentic.SemanticsOwnerHolder
import javax.inject.Inject
import kotlinx.serialization.json.Json

/** Dumps the full Compose semantics tree for UI inspection. */
@AgenticCommand(
  name = "dump-semantics",
  description = "Returns full Compose semantics tree snapshot as JSON",
  category = "ui",
)
internal class DumpSemanticsHandler
@Inject
constructor(
  private val semanticsHolder: SemanticsOwnerHolder,
) : CommandHandler {

  override val name: String = "dump-semantics"
  override val description: String = "Returns full Compose semantics tree snapshot as JSON"
  override val category: String = "ui"
  override val aliases: List<String> = listOf("semantics")

  override suspend fun execute(params: CommandParams, commandId: String): CommandResult {
    val snapshot = semanticsHolder.snapshot()
      ?: return CommandResult.Success(
        """{"nodes":[],"message":"No semantics owner registered"}"""
      )

    val json = Json { prettyPrint = false }
    return CommandResult.Success(json.encodeToString(snapshot))
  }

  override fun paramsSchema(): Map<String, String> = emptyMap()
}
