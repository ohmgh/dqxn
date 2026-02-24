package app.dqxn.android.core.agentic

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Standardized result envelope for agentic command execution.
 */
sealed interface CommandResult {

  /**
   * Successful command execution.
   *
   * @param data JSON string containing the command's output payload.
   */
  data class Success(val data: String) : CommandResult

  /**
   * Failed command execution.
   *
   * @param message Human-readable error description.
   * @param code Machine-readable error code for programmatic handling.
   */
  data class Error(val message: String, val code: String = "UNKNOWN") : CommandResult
}

/**
 * Serializes this result to a JSON string following the agentic response protocol.
 *
 * Success: `{"status":"ok","data":...}` where data is parsed as raw JSON if valid,
 * otherwise wrapped as a JSON string.
 *
 * Error: `{"status":"error","message":"...","code":"..."}`
 */
fun CommandResult.toJson(): String =
  when (this) {
    is CommandResult.Success -> {
      val dataElement =
        try {
          kotlinx.serialization.json.Json.parseToJsonElement(data)
        } catch (_: Exception) {
          JsonPrimitive(data)
        }
      val obj: JsonObject = buildJsonObject {
        put("status", "ok")
        put("data", dataElement)
      }
      obj.toString()
    }
    is CommandResult.Error -> {
      val obj: JsonObject = buildJsonObject {
        put("status", "error")
        put("message", message)
        put("code", code)
      }
      obj.toString()
    }
  }
