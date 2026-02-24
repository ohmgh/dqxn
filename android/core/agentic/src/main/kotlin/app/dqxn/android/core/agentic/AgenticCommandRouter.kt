package app.dqxn.android.core.agentic

import javax.inject.Inject

/**
 * Routes named agentic commands to their registered [CommandHandler] implementations.
 *
 * Handlers are provided via Hilt multibinding (`Set<CommandHandler>`) and indexed by
 * [CommandHandler.name] on first access. The router also indexes [CommandHandler.aliases] so
 * commands can be invoked by alternative names.
 *
 * This class is internal to the shell -- packs never interact with it directly.
 */
public class AgenticCommandRouter
@Inject
constructor(
  private val handlers: Set<@JvmSuppressWildcards CommandHandler>,
) {

  private val handlerMap: Map<String, CommandHandler> by lazy {
    buildMap {
      for (handler in handlers) {
        put(handler.name, handler)
        for (alias in handler.aliases) {
          put(alias, handler)
        }
      }
    }
  }

  /**
   * Routes [method] to the appropriate handler, executes it with [params], and returns the result
   * as a JSON string following the agentic response protocol.
   *
   * @param method The command name or alias to invoke.
   * @param params Parsed parameters including trace ID for per-invocation tracing.
   * @return JSON string: `{"status":"ok","data":...}` on success,
   *   `{"status":"error","message":"...","code":"..."}` on failure.
   */
  suspend fun route(method: String, params: CommandParams): String {
    val handler =
      handlerMap[method]
        ?: return CommandResult.Error(
            message = "Unknown command: $method",
            code = "UNKNOWN_COMMAND",
          )
          .toJson()

    return try {
      handler.execute(params, params.traceId).toJson()
    } catch (e: Exception) {
      CommandResult.Error(
          message = e.message ?: "Handler threw ${e::class.simpleName}",
          code = "HANDLER_ERROR",
        )
        .toJson()
    }
  }
}
