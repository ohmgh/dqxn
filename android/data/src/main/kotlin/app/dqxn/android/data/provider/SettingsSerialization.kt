package app.dqxn.android.data.provider

import kotlinx.serialization.json.Json

/**
 * Type-prefixed serialization for provider settings values. Each value is encoded with a single-char
 * type prefix followed by a colon and the string representation:
 * - `s:` String
 * - `i:` Int
 * - `b:` Boolean
 * - `f:` Float
 * - `d:` Double
 * - `l:` Long
 * - `j:` JSON fallback (via kotlinx.serialization)
 * - `"null"` for null values
 *
 * Unprefixed strings are treated as raw String values (legacy fallback).
 */
internal object SettingsSerialization {

  private val json = Json { ignoreUnknownKeys = true }

  fun serializeValue(value: Any?): String =
    when (value) {
      null -> "null"
      is String -> "s:$value"
      is Int -> "i:$value"
      is Long -> "l:$value"
      is Float -> "f:$value"
      is Double -> "d:$value"
      is Boolean -> "b:$value"
      else -> "j:${json.encodeToString(kotlinx.serialization.serializer<String>(), value.toString())}"
    }

  fun deserializeValue(serialized: String?): Any? {
    if (serialized == null || serialized == "null") return null
    val colonIndex = serialized.indexOf(':')
    if (colonIndex != 1) return serialized // Legacy fallback: no valid prefix -> raw string
    val prefix = serialized[0]
    val data = serialized.substring(2)
    return when (prefix) {
      's' -> data
      'i' -> data.toIntOrNull()
      'l' -> data.toLongOrNull()
      'f' -> data.toFloatOrNull()
      'd' -> data.toDoubleOrNull()
      'b' -> data.toBooleanStrictOrNull()
      'j' ->
        try {
          json.decodeFromString(kotlinx.serialization.serializer<String>(), data)
        } catch (_: Exception) {
          null
        }
      else -> serialized // Legacy fallback: unrecognized prefix -> raw string
    }
  }
}
