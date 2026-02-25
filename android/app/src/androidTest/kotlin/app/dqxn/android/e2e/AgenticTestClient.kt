package app.dqxn.android.e2e

import android.content.ContentResolver
import android.net.Uri
import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Test client for the agentic diagnostic framework. Wraps [ContentResolver.call] to the
 * [AgenticContentProvider] authority, reads the response-file protocol output, and parses JSON.
 *
 * All E2E tests share this client for issuing agentic commands and asserting results.
 *
 * The response-file protocol writes JSON to a temp file in cacheDir and returns the file path
 * in the Bundle. This avoids Binder transaction size limits.
 */
public class AgenticTestClient {

  private val contentResolver: ContentResolver
    get() = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver

  private val uri: Uri = Uri.parse("content://app.dqxn.android.agentic")

  /**
   * Sends a command to the agentic framework and returns the parsed JSON response.
   *
   * @param command The agentic command name (e.g., "ping", "chaos-start", "dump-health").
   * @param params Optional parameters as key-value pairs, serialized to JSON.
   * @return Parsed [JsonObject] response.
   * @throws AssertionError if the response indicates an error status.
   */
  public fun send(command: String, params: Map<String, Any> = emptyMap()): JsonObject {
    val paramsJson = if (params.isEmpty()) {
      null
    } else {
      val jsonObject = JsonObject(
        params.mapValues { (_, value) ->
          when (value) {
            is String -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            else -> JsonPrimitive(value.toString())
          }
        }
      )
      jsonObject.toString()
    }

    val result: Bundle? = contentResolver.call(uri, command, paramsJson, null)
    checkNotNull(result) { "Null response from agentic command: $command" }

    val filePath = result.getString("filePath")
    check(!filePath.isNullOrBlank()) { "No filePath in response for command: $command" }

    val responseJson = File(filePath).readText()
    return Json.parseToJsonElement(responseJson).jsonObject
  }

  /**
   * Asserts that the agentic framework responds to "ping" with status "ok".
   *
   * Use as a readiness probe before issuing other commands.
   */
  public fun assertReady() {
    val response = send("ping")
    val status = response["status"]?.jsonPrimitive?.content
    check(status == "ok") { "App not ready. Ping status: $status" }
  }

  /**
   * Polls a command until a condition is met on the response, with timeout.
   *
   * @param command The command to poll.
   * @param jsonPath Dot-delimited path to a JSON array in the response (e.g., "data.snapshots").
   * @param condition Predicate on the extracted [JsonElement] list.
   * @param timeoutMs Maximum time to wait before failing.
   * @param pollIntervalMs Interval between poll attempts.
   * @return The matching response.
   * @throws AssertionError if the condition is not met within the timeout.
   */
  public fun awaitCondition(
    command: String,
    jsonPath: String,
    condition: (List<JsonElement>) -> Boolean,
    timeoutMs: Long = 10_000L,
    pollIntervalMs: Long = 500L,
    params: Map<String, Any> = emptyMap(),
  ): JsonObject {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
      val response = send(command, params)
      val elements = extractJsonPath(response, jsonPath)
      if (elements != null && condition(elements)) {
        return response
      }
      Thread.sleep(pollIntervalMs)
    }
    error("Condition not met for command '$command' at path '$jsonPath' within ${timeoutMs}ms")
  }

  /**
   * Asserts that a widget with the given typeId is rendered (appears in dump-health response
   * with ACTIVE status).
   */
  public fun assertWidgetRendered(typeId: String, timeoutMs: Long = 5_000L) {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
      val health = send("dump-health")
      val widgets = health["widgets"]?.jsonArray ?: continue
      val found = widgets.any { widget ->
        val widgetObj = widget.jsonObject
        widgetObj["typeId"]?.jsonPrimitive?.content == typeId &&
          widgetObj["status"]?.jsonPrimitive?.content == "ACTIVE"
      }
      if (found) return
      Thread.sleep(500)
    }
    error("Widget '$typeId' not rendered within ${timeoutMs}ms")
  }

  private fun extractJsonPath(root: JsonObject, path: String): List<JsonElement>? {
    val segments = path.split(".")
    var current: JsonElement = root
    for (segment in segments) {
      current = when (current) {
        is JsonObject -> current.jsonObject[segment] ?: return null
        else -> return null
      }
    }
    return when (current) {
      is kotlinx.serialization.json.JsonArray -> current.jsonArray.toList()
      else -> null
    }
  }
}
