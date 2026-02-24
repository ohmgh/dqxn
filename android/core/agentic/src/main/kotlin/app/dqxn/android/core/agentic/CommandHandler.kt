package app.dqxn.android.core.agentic

/**
 * Interface for all agentic command handlers. Implementations are annotated with
 * [@AgenticCommand][AgenticCommand] and registered via KSP-generated Hilt modules into a
 * `Set<CommandHandler>` consumed by [AgenticCommandRouter].
 *
 * The KSP processor in `:codegen:agentic` validates that every `@AgenticCommand`-annotated class
 * implements this interface. The generated Hilt module uses `@Binds @IntoSet` with this interface
 * as the binding type.
 *
 * **Signature note:** The two-parameter `execute(params, commandId)` shape is consistent with the
 * codegen stubs. The `commandId` enables per-invocation tracing and is extracted from
 * `params.traceId` by the router.
 */
interface CommandHandler {

  /** Unique command name matching [AgenticCommand.name]. Used for routing. */
  val name: String

  /** Human-readable description for discovery and help output. */
  val description: String

  /** Optional grouping category for organized command listing. */
  val category: String

  /** Alternative names that also route to this handler. */
  val aliases: List<String>

  /**
   * Executes the command with the given parameters.
   *
   * @param params Parsed command parameters from the agentic transport.
   * @param commandId Unique invocation identifier for tracing, extracted from [params]`.traceId`.
   * @return The command result indicating success or failure.
   */
  suspend fun execute(params: CommandParams, commandId: String): CommandResult

  /**
   * Returns a schema describing the parameters this command accepts.
   *
   * @return Map of parameter name to human-readable description.
   */
  fun paramsSchema(): Map<String, String>
}
