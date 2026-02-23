package app.dqxn.android.sdk.observability.log

import kotlinx.collections.immutable.toImmutableMap

/**
 * Decorator that scrubs sensitive data from log entries before delegating. Redacts:
 * - GPS coordinates (latitude/longitude decimal patterns)
 * - BLE MAC addresses (XX:XX:XX:XX:XX:XX pattern)
 */
public class RedactingSink(private val delegate: LogSink) : LogSink {

  override fun write(entry: LogEntry) {
    val redactedMessage = redact(entry.message)
    val redactedFields =
      if (entry.fields.isEmpty()) {
        entry.fields
      } else {
        entry.fields.mapValues { (_, v) -> if (v is String) redact(v) else v }.toImmutableMap()
      }
    delegate.write(entry.copy(message = redactedMessage, fields = redactedFields))
  }

  private companion object {
    // Matches decimal coordinates like 1.3521, -73.9857, 40.7128
    val GPS_PATTERN: Regex = Regex("-?\\d{1,3}\\.\\d{4,}")

    // Matches BLE MAC addresses like AA:BB:CC:DD:EE:FF
    val MAC_PATTERN: Regex = Regex("[0-9A-Fa-f]{2}(:[0-9A-Fa-f]{2}){5}")

    const val GPS_REPLACEMENT = "[REDACTED_COORD]"
    const val MAC_REPLACEMENT = "[REDACTED_MAC]"

    fun redact(input: String): String {
      return input.replace(MAC_PATTERN, MAC_REPLACEMENT).replace(GPS_PATTERN, GPS_REPLACEMENT)
    }
  }
}
