package app.dqxn.android.agentic.handlers

import app.dqxn.android.core.agentic.CommandParams
import app.dqxn.android.core.agentic.CommandResult
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("fast")
class PingHandlerTest {

  private val handler = PingHandler()

  @Test
  fun `execute returns Success with status ok`() = runTest {
    val result = handler.execute(
      CommandParams(traceId = "test-trace"),
      "test-cmd",
    )

    assertThat(result).isInstanceOf(CommandResult.Success::class.java)
    val data = (result as CommandResult.Success).data
    val json = Json.parseToJsonElement(data).jsonObject
    assertThat(json["status"]?.jsonPrimitive?.content).isEqualTo("ok")
  }

  @Test
  fun `response contains timestamp field`() = runTest {
    val result = handler.execute(
      CommandParams(traceId = "test-trace"),
      "test-cmd",
    )

    val data = (result as CommandResult.Success).data
    val json = Json.parseToJsonElement(data).jsonObject
    assertThat(json.containsKey("timestamp")).isTrue()
    val timestamp = json["timestamp"]?.jsonPrimitive?.content?.toLongOrNull()
    assertThat(timestamp).isNotNull()
    assertThat(timestamp!!).isGreaterThan(0L)
  }

  @Test
  fun `handler name is ping`() {
    assertThat(handler.name).isEqualTo("ping")
  }

  @Test
  fun `handler category is system`() {
    assertThat(handler.category).isEqualTo("system")
  }
}
