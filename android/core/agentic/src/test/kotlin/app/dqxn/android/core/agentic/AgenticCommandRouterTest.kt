package app.dqxn.android.core.agentic

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("fast")
class AgenticCommandRouterTest {

  @Test
  fun `single handler registered - routes by name and returns success`() = runTest {
    val handler = FakeCommandHandler(name = "ping", result = CommandResult.Success("""{"pong":true}"""))
    val router = AgenticCommandRouter(setOf(handler))

    val result = router.route("ping", params())
    val json = parseJson(result)

    assertThat(json["status"]?.jsonPrimitive?.content).isEqualTo("ok")
  }

  @Test
  fun `unknown command name returns error JSON`() = runTest {
    val router = AgenticCommandRouter(emptySet())

    val result = router.route("nonexistent", params())
    val json = parseJson(result)

    assertThat(json["status"]?.jsonPrimitive?.content).isEqualTo("error")
    assertThat(json["message"]?.jsonPrimitive?.content).contains("Unknown command: nonexistent")
    assertThat(json["code"]?.jsonPrimitive?.content).isEqualTo("UNKNOWN_COMMAND")
  }

  @Test
  fun `handler throws exception - wraps in error JSON`() = runTest {
    val handler =
      FakeCommandHandler(
        name = "explode",
        throwable = IllegalStateException("something broke"),
      )
    val router = AgenticCommandRouter(setOf(handler))

    val result = router.route("explode", params())
    val json = parseJson(result)

    assertThat(json["status"]?.jsonPrimitive?.content).isEqualTo("error")
    assertThat(json["message"]?.jsonPrimitive?.content).isEqualTo("something broke")
    assertThat(json["code"]?.jsonPrimitive?.content).isEqualTo("HANDLER_ERROR")
  }

  @Test
  fun `empty handler set - all commands return unknown error`() = runTest {
    val router = AgenticCommandRouter(emptySet())

    val result = router.route("anything", params())
    val json = parseJson(result)

    assertThat(json["status"]?.jsonPrimitive?.content).isEqualTo("error")
    assertThat(json["message"]?.jsonPrimitive?.content).contains("Unknown command")
  }

  @Test
  fun `multiple handlers with distinct names - correct dispatch`() = runTest {
    val ping = FakeCommandHandler(name = "ping", result = CommandResult.Success(""""pong""""))
    val health =
      FakeCommandHandler(name = "dump-health", result = CommandResult.Success(""""healthy""""))
    val router = AgenticCommandRouter(setOf(ping, health))

    val pingResult = router.route("ping", params())
    assertThat(parseJson(pingResult)["status"]?.jsonPrimitive?.content).isEqualTo("ok")

    val healthResult = router.route("dump-health", params())
    assertThat(parseJson(healthResult)["status"]?.jsonPrimitive?.content).isEqualTo("ok")
  }

  @Test
  fun `trace ID propagated to handler`() = runTest {
    var receivedCommandId: String? = null
    val handler =
      FakeCommandHandler(
        name = "trace-test",
        result = CommandResult.Success(""""ok""""),
        onExecute = { _, commandId -> receivedCommandId = commandId },
      )
    val router = AgenticCommandRouter(setOf(handler))
    val traceId = "agentic-123456"

    router.route("trace-test", CommandParams(traceId = traceId))

    assertThat(receivedCommandId).isEqualTo(traceId)
  }

  @Test
  fun `alias routes to correct handler`() = runTest {
    val handler =
      FakeCommandHandler(
        name = "dump-health",
        aliases = listOf("health", "dh"),
        result = CommandResult.Success(""""healthy""""),
      )
    val router = AgenticCommandRouter(setOf(handler))

    val aliasResult = router.route("health", params())
    assertThat(parseJson(aliasResult)["status"]?.jsonPrimitive?.content).isEqualTo("ok")

    val shortAliasResult = router.route("dh", params())
    assertThat(parseJson(shortAliasResult)["status"]?.jsonPrimitive?.content).isEqualTo("ok")
  }

  @Test
  fun `success result with valid JSON data is unwrapped`() = runTest {
    val handler =
      FakeCommandHandler(
        name = "json-test",
        result = CommandResult.Success("""{"key":"value"}"""),
      )
    val router = AgenticCommandRouter(setOf(handler))

    val result = router.route("json-test", params())
    val json = parseJson(result)

    assertThat(json["status"]?.jsonPrimitive?.content).isEqualTo("ok")
    // data should be parsed JSON object, not a quoted string
    assertThat(result).contains(""""key":"value"""")
  }

  private fun params(traceId: String = "test-trace"): CommandParams =
    CommandParams(traceId = traceId)

  private fun parseJson(jsonString: String): JsonObject =
    Json.decodeFromString<JsonObject>(jsonString)
}

/**
 * Fake [CommandHandler] for testing [AgenticCommandRouter] dispatch and error handling. Supports
 * configurable result or exception throwing, and an optional callback for argument inspection.
 */
private class FakeCommandHandler(
  override val name: String,
  override val description: String = "Fake handler for testing",
  override val category: String = "test",
  override val aliases: List<String> = emptyList(),
  private val result: CommandResult = CommandResult.Success(""""ok""""),
  private val throwable: Throwable? = null,
  private val onExecute: ((CommandParams, String) -> Unit)? = null,
) : CommandHandler {

  override suspend fun execute(params: CommandParams, commandId: String): CommandResult {
    onExecute?.invoke(params, commandId)
    throwable?.let { throw it }
    return result
  }

  override fun paramsSchema(): Map<String, String> = emptyMap()
}
