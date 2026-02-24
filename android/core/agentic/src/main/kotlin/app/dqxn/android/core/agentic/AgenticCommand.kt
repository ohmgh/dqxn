package app.dqxn.android.core.agentic

/**
 * Marks a [CommandHandler] implementation as an agentic command discoverable by the KSP processor
 * in `:codegen:agentic`. The processor generates a Hilt `@Binds @IntoSet` module that registers
 * annotated handlers into the `Set<CommandHandler>` consumed by [AgenticCommandRouter].
 *
 * This annotation has `SOURCE` retention -- it is consumed at compile time by KSP only and does not
 * appear in the compiled bytecode.
 *
 * @param name Unique command name used for routing (e.g., "dump-health", "ping").
 * @param description Human-readable description for `list-commands` output.
 * @param category Optional grouping category (e.g., "diagnostics", "layout").
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class AgenticCommand(
  val name: String,
  val description: String,
  val category: String = "",
)
