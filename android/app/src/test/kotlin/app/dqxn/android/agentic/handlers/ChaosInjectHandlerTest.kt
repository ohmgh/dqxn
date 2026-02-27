package app.dqxn.android.agentic.handlers

import app.dqxn.android.agentic.chaos.ChaosProviderInterceptor
import com.google.common.truth.Truth.assertThat
import dev.agentic.android.chaos.Fault
import dev.agentic.android.runtime.CommandParams
import dev.agentic.android.runtime.CommandResult
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("fast")
class ChaosInjectHandlerTest {

  private val interceptor = ChaosProviderInterceptor()
  private val handler = ChaosInjectHandler(interceptor)

  @Test
  fun `execute injects kill fault`() = runTest {
    val result =
      handler.execute(
        CommandParams(
          raw = mapOf("providerId" to "test-provider", "fault" to "kill"),
          traceId = "t1",
        ),
        "cmd-1",
      )

    assertThat(result).isInstanceOf(CommandResult.Success::class.java)
    val data = (result as CommandResult.Success).data
    val json = Json.parseToJsonElement(data).jsonObject
    assertThat(json["injected"]?.jsonPrimitive?.content).isEqualTo("true")
    assertThat(json["providerId"]?.jsonPrimitive?.content).isEqualTo("test-provider")
    assertThat(json["fault"]?.jsonPrimitive?.content).isEqualTo("kill")

    assertThat(interceptor.activeFaults()["test-provider"]).isEqualTo(Fault.Kill)
  }

  @Test
  fun `execute injects delay fault with delayMs`() = runTest {
    val result =
      handler.execute(
        CommandParams(
          raw = mapOf("providerId" to "test-provider", "fault" to "delay", "delayMs" to "500"),
          traceId = "t1",
        ),
        "cmd-1",
      )

    assertThat(result).isInstanceOf(CommandResult.Success::class.java)

    val fault = interceptor.activeFaults()["test-provider"]
    assertThat(fault).isInstanceOf(Fault.Delay::class.java)
    assertThat((fault as Fault.Delay).delayMs).isEqualTo(500L)
  }

  @Test
  fun `execute injects flap fault with onMs and offMs`() = runTest {
    val result =
      handler.execute(
        CommandParams(
          raw =
            mapOf(
              "providerId" to "test-provider",
              "fault" to "flap",
              "onMs" to "1000",
              "offMs" to "3000",
            ),
          traceId = "t1",
        ),
        "cmd-1",
      )

    assertThat(result).isInstanceOf(CommandResult.Success::class.java)

    val fault = interceptor.activeFaults()["test-provider"]
    assertThat(fault).isInstanceOf(Fault.Flap::class.java)
    val flap = fault as Fault.Flap
    assertThat(flap.onMillis).isEqualTo(1000L)
    assertThat(flap.offMillis).isEqualTo(3000L)
  }

  @Test
  fun `execute with missing providerId returns error`() = runTest {
    val result =
      handler.execute(
        CommandParams(
          raw = mapOf("fault" to "kill"),
          traceId = "t1",
        ),
        "cmd-1",
      )

    assertThat(result).isInstanceOf(CommandResult.Error::class.java)
    val error = result as CommandResult.Error
    assertThat(error.code).isEqualTo("MISSING_PARAM")
    assertThat(error.message).contains("providerId")
  }

  @Test
  fun `execute with missing fault returns error`() = runTest {
    val result =
      handler.execute(
        CommandParams(
          raw = mapOf("providerId" to "test-provider"),
          traceId = "t1",
        ),
        "cmd-1",
      )

    assertThat(result).isInstanceOf(CommandResult.Error::class.java)
    val error = result as CommandResult.Error
    assertThat(error.code).isEqualTo("MISSING_PARAM")
  }

  @Test
  fun `execute with unknown fault type returns error`() = runTest {
    val result =
      handler.execute(
        CommandParams(
          raw = mapOf("providerId" to "test-provider", "fault" to "explode"),
          traceId = "t1",
        ),
        "cmd-1",
      )

    assertThat(result).isInstanceOf(CommandResult.Error::class.java)
    val error = result as CommandResult.Error
    assertThat(error.code).isEqualTo("UNKNOWN_FAULT")
    assertThat(error.message).contains("explode")
  }

  @Test
  fun `execute injects stall fault`() = runTest {
    handler.execute(
      CommandParams(
        raw = mapOf("providerId" to "test-provider", "fault" to "stall"),
        traceId = "t1",
      ),
      "cmd-1",
    )

    assertThat(interceptor.activeFaults()["test-provider"]).isEqualTo(Fault.Stall)
  }

  @Test
  fun `execute injects error fault`() = runTest {
    handler.execute(
      CommandParams(
        raw = mapOf("providerId" to "test-provider", "fault" to "error"),
        traceId = "t1",
      ),
      "cmd-1",
    )

    val fault = interceptor.activeFaults()["test-provider"]
    assertThat(fault).isInstanceOf(Fault.Error::class.java)
  }

  @Test
  fun `handler name and category are correct`() {
    assertThat(handler.name).isEqualTo("chaos-inject")
    assertThat(handler.category).isEqualTo("chaos")
  }

  @Test
  fun `handler has inject-fault alias`() {
    assertThat(handler.aliases).contains("inject-fault")
  }
}
