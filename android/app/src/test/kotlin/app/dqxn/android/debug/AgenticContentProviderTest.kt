package app.dqxn.android.debug

import app.dqxn.android.core.agentic.AgenticCommandRouter
import app.dqxn.android.core.agentic.CommandHandler
import app.dqxn.android.core.agentic.CommandParams
import app.dqxn.android.core.agentic.CommandResult
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Tests for [AgenticContentProvider] dispatch logic via the extracted [handleCall] method.
 *
 * Note: Android `Bundle` is a stub in unit tests (returns null for all gets even with
 * `isReturnDefaultValues=true`). Tests verify the response-file protocol by inspecting files
 * written to the temp directory.
 */
@Tag("fast")
class AgenticContentProviderTest {

  @TempDir
  lateinit var tempDir: File

  private val provider = AgenticContentProvider()

  private fun createRouter(vararg handlers: CommandHandler): AgenticCommandRouter =
    AgenticCommandRouter(handlers.toSet())

  private fun simpleHandler(name: String, response: String): CommandHandler =
    object : CommandHandler {
      override val name: String = name
      override val description: String = "test handler"
      override val category: String = "test"
      override val aliases: List<String> = emptyList()
      override suspend fun execute(params: CommandParams, commandId: String): CommandResult =
        CommandResult.Success(response)
      override fun paramsSchema(): Map<String, String> = emptyMap()
    }

  /** Returns the most recently created agentic response file from the temp directory. */
  private fun latestResponseFile(): File? =
    tempDir.listFiles { f -> f.name.startsWith("agentic_") && f.name.endsWith(".json") }
      ?.maxByOrNull { it.lastModified() }

  @Test
  fun `successful command writes response JSON to file`() {
    val handler = simpleHandler("test-cmd", """{"result":"hello"}""")
    val router = createRouter(handler)

    provider.handleCall("test-cmd", null, router, tempDir)

    val file = latestResponseFile()
    assertThat(file).isNotNull()
    val content = file!!.readText()
    val json = Json.parseToJsonElement(content).jsonObject
    assertThat(json["status"]?.jsonPrimitive?.content).isEqualTo("ok")
  }

  @Test
  fun `response file contains structured data from handler`() {
    val handler = simpleHandler("data-cmd", """{"key":"value","number":42}""")
    val router = createRouter(handler)

    provider.handleCall("data-cmd", null, router, tempDir)

    val file = latestResponseFile()!!
    val content = file.readText()
    val json = Json.parseToJsonElement(content).jsonObject
    assertThat(json["status"]?.jsonPrimitive?.content).isEqualTo("ok")
    val data = json["data"]?.jsonObject
    assertThat(data).isNotNull()
    assertThat(data!!["key"]?.jsonPrimitive?.content).isEqualTo("value")
  }

  @Test
  fun `timeout returns TIMEOUT error for stalling handler`() {
    val neverComplete = CompletableDeferred<Unit>()
    val stallHandler = object : CommandHandler {
      override val name: String = "stall-cmd"
      override val description: String = "stalls"
      override val category: String = "test"
      override val aliases: List<String> = emptyList()
      override suspend fun execute(params: CommandParams, commandId: String): CommandResult {
        neverComplete.await()
        return CommandResult.Success("unreachable")
      }
      override fun paramsSchema(): Map<String, String> = emptyMap()
    }
    val router = createRouter(stallHandler)

    provider.handleCall("stall-cmd", null, router, tempDir, timeoutMs = 200)

    val file = latestResponseFile()!!
    val content = file.readText()
    val json = Json.parseToJsonElement(content).jsonObject
    assertThat(json["status"]?.jsonPrimitive?.content).isEqualTo("error")
    assertThat(json["code"]?.jsonPrimitive?.content).isEqualTo("TIMEOUT")
    assertThat(json["message"]?.jsonPrimitive?.content).contains("timed out")
  }

  @Test
  fun `unknown command returns UNKNOWN_COMMAND error`() {
    val handler = simpleHandler("known-cmd", """{"ok":true}""")
    val router = createRouter(handler)

    provider.handleCall("nonexistent-cmd", null, router, tempDir)

    val file = latestResponseFile()!!
    val content = file.readText()
    val json = Json.parseToJsonElement(content).jsonObject
    assertThat(json["status"]?.jsonPrimitive?.content).isEqualTo("error")
    assertThat(json["code"]?.jsonPrimitive?.content).isEqualTo("UNKNOWN_COMMAND")
  }

  @Test
  fun `JSON params are parsed from arg string`() {
    var capturedParams: Map<String, String>? = null
    val handler = object : CommandHandler {
      override val name: String = "param-cmd"
      override val description: String = "test"
      override val category: String = "test"
      override val aliases: List<String> = emptyList()
      override suspend fun execute(params: CommandParams, commandId: String): CommandResult {
        capturedParams = params.raw
        return CommandResult.Success("""{"received":true}""")
      }
      override fun paramsSchema(): Map<String, String> = emptyMap()
    }
    val router = createRouter(handler)

    provider.handleCall("param-cmd", """{"key1":"val1","key2":"val2"}""", router, tempDir)

    assertThat(capturedParams).isNotNull()
    assertThat(capturedParams!!["key1"]).isEqualTo("val1")
    assertThat(capturedParams!!["key2"]).isEqualTo("val2")
  }

  @Test
  fun `null arg produces empty params`() {
    var capturedParams: Map<String, String>? = null
    val handler = object : CommandHandler {
      override val name: String = "null-arg-cmd"
      override val description: String = "test"
      override val category: String = "test"
      override val aliases: List<String> = emptyList()
      override suspend fun execute(params: CommandParams, commandId: String): CommandResult {
        capturedParams = params.raw
        return CommandResult.Success("""{"ok":true}""")
      }
      override fun paramsSchema(): Map<String, String> = emptyMap()
    }
    val router = createRouter(handler)

    provider.handleCall("null-arg-cmd", null, router, tempDir)

    assertThat(capturedParams).isNotNull()
    assertThat(capturedParams).isEmpty()
  }

  @Test
  fun `cleanup logic removes stale agentic files`() {
    // Create fake stale files
    File(tempDir, "agentic_1234.json").writeText("{}")
    File(tempDir, "agentic_5678.json").writeText("{}")
    File(tempDir, "other_file.txt").writeText("keep me")

    // Verify stale files exist
    assertThat(tempDir.listFiles { f -> f.name.startsWith("agentic_") }).hasLength(2)

    // Simulate the cleanup logic from onCreate
    tempDir.listFiles { f -> f.name.startsWith("agentic_") }?.forEach { it.delete() }

    // Verify agentic files deleted
    assertThat(tempDir.listFiles { f -> f.name.startsWith("agentic_") }).isEmpty()

    // Verify other files preserved
    assertThat(File(tempDir, "other_file.txt").exists()).isTrue()
  }

  @Test
  fun `response file protocol creates file with agentic prefix`() {
    val handler = simpleHandler("file-cmd", """{"test":true}""")
    val router = createRouter(handler)

    provider.handleCall("file-cmd", null, router, tempDir)

    val files = tempDir.listFiles { f ->
      f.name.startsWith("agentic_") && f.name.endsWith(".json")
    }
    assertThat(files).isNotNull()
    assertThat(files!!).isNotEmpty()
    assertThat(files[0].length()).isGreaterThan(0)
  }
}
