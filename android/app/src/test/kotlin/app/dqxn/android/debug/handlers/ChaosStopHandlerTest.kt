package app.dqxn.android.debug.handlers

import app.dqxn.android.core.agentic.CommandParams
import app.dqxn.android.core.agentic.CommandResult
import app.dqxn.android.core.agentic.chaos.ChaosEngine
import app.dqxn.android.core.agentic.chaos.ChaosProviderInterceptor
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("fast")
class ChaosStopHandlerTest {

  private val interceptor = ChaosProviderInterceptor()
  private val engine = ChaosEngine(interceptor)
  private val handler = ChaosStopHandler(engine)

  @Test
  fun `execute stops session and returns summary`() = runTest {
    engine.start(42L, "provider-stress", listOf("provider-a"), backgroundScope)

    val result = handler.execute(
      CommandParams(traceId = "test-trace"),
      "test-cmd",
    )

    assertThat(result).isInstanceOf(CommandResult.Success::class.java)
    val data = (result as CommandResult.Success).data
    val json = Json.parseToJsonElement(data).jsonObject
    assertThat(json["sessionId"]?.jsonPrimitive?.content).contains("chaos-42-provider-stress")
    assertThat(json["seed"]?.jsonPrimitive?.content).isEqualTo("42")
    assertThat(json["profile"]?.jsonPrimitive?.content).isEqualTo("provider-stress")
    assertThat(json.containsKey("faultCount")).isTrue()
    assertThat(json.containsKey("injectedFaults")).isTrue()
  }

  @Test
  fun `execute with no active session returns error`() = runTest {
    val result = handler.execute(
      CommandParams(traceId = "test-trace"),
      "test-cmd",
    )

    assertThat(result).isInstanceOf(CommandResult.Error::class.java)
    val error = result as CommandResult.Error
    assertThat(error.code).isEqualTo("NO_SESSION")
    assertThat(error.message).contains("No active chaos session")
  }

  @Test
  fun `handler name and category are correct`() {
    assertThat(handler.name).isEqualTo("chaos-stop")
    assertThat(handler.category).isEqualTo("chaos")
  }

  @Test
  fun `handler has chaos-end alias`() {
    assertThat(handler.aliases).contains("chaos-end")
  }
}
