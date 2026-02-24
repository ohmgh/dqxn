package app.dqxn.android.debug.handlers

import app.dqxn.android.core.agentic.AgenticCommand
import app.dqxn.android.core.agentic.CommandHandler
import app.dqxn.android.core.agentic.CommandParams
import app.dqxn.android.core.agentic.CommandResult
import app.dqxn.android.core.agentic.SemanticsFilter
import app.dqxn.android.core.agentic.SemanticsOwnerHolder
import app.dqxn.android.core.agentic.getString
import javax.inject.Inject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Queries the Compose semantics tree with filter criteria. */
@AgenticCommand(
  name = "query-semantics",
  description = "Returns filtered semantics nodes matching testTag, text, contentDescription, or action",
  category = "ui",
)
class QuerySemanticsHandler
@Inject
constructor(
  private val semanticsHolder: SemanticsOwnerHolder,
) : CommandHandler {

  override val name: String = "query-semantics"
  override val description: String =
    "Returns filtered semantics nodes matching testTag, text, contentDescription, or action"
  override val category: String = "ui"
  override val aliases: List<String> = emptyList()

  override suspend fun execute(params: CommandParams, commandId: String): CommandResult {
    val filter = SemanticsFilter(
      testTag = params.getString("testTag"),
      text = params.getString("text"),
      contentDescription = params.getString("contentDescription"),
      hasAction = params.getString("hasAction"),
    )

    val nodes = semanticsHolder.query(filter)

    val json = buildJsonObject {
      put("matchCount", nodes.size)
      put(
        "nodes",
        buildJsonArray {
          for (node in nodes) {
            add(
              buildJsonObject {
                put("id", node.id)
                node.testTag?.let { put("testTag", it) }
                node.text?.let { put("text", it) }
                node.contentDescription?.let { put("contentDescription", it) }
                node.bounds?.let { bounds ->
                  put(
                    "bounds",
                    buildJsonObject {
                      put("left", bounds.left.toDouble())
                      put("top", bounds.top.toDouble())
                      put("right", bounds.right.toDouble())
                      put("bottom", bounds.bottom.toDouble())
                    },
                  )
                }
              }
            )
          }
        },
      )
    }
    return CommandResult.Success(json.toString())
  }

  override fun paramsSchema(): Map<String, String> = mapOf(
    "testTag" to "Exact test tag to match",
    "text" to "Substring to match in node text",
    "contentDescription" to "Substring to match in content description",
    "hasAction" to "Semantics action name to require (e.g. OnClick)",
  )
}
