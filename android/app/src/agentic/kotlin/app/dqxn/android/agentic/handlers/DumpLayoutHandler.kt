package app.dqxn.android.agentic.handlers

import app.dqxn.android.core.agentic.AgenticCommand
import app.dqxn.android.core.agentic.CommandHandler
import app.dqxn.android.core.agentic.CommandParams
import app.dqxn.android.core.agentic.CommandResult
import javax.inject.Inject

/**
 * Dumps the current dashboard layout state. Phase 6 returns a placeholder; real LayoutRepository
 * injection is added in Phase 7 when `:data` repositories are wired.
 */
@AgenticCommand(
  name = "dump-layout",
  description = "Returns dashboard layout state with profiles and widget positions",
  category = "diagnostics",
)
internal class DumpLayoutHandler @Inject constructor() : CommandHandler {

  override val name: String = "dump-layout"
  override val description: String =
    "Returns dashboard layout state with profiles and widget positions"
  override val category: String = "diagnostics"
  override val aliases: List<String> = listOf("layout")

  override suspend fun execute(params: CommandParams, commandId: String): CommandResult =
    CommandResult.Success("""{"profiles":[],"activeProfile":null}""")

  override fun paramsSchema(): Map<String, String> = emptyMap()
}
