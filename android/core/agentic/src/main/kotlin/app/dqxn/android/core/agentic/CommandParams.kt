package app.dqxn.android.core.agentic

/**
 * Parsed parameters for an agentic command invocation.
 *
 * @param raw Key-value pairs extracted from the agentic transport (ADB content call arg string).
 * @param traceId Unique identifier for this invocation, used for per-command tracing.
 */
data class CommandParams(
  val raw: Map<String, String> = emptyMap(),
  val traceId: String,
)

/** Returns the value for [key], or null if not present. */
fun CommandParams.getString(key: String): String? = raw[key]

/**
 * Returns the value for [key], or throws [IllegalArgumentException] if not present.
 *
 * @throws IllegalArgumentException if [key] is not in [raw].
 */
fun CommandParams.requireString(key: String): String =
  raw[key] ?: throw IllegalArgumentException("Missing required param: $key")
