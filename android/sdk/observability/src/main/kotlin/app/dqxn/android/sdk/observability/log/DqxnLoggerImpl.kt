package app.dqxn.android.sdk.observability.log

import android.os.SystemClock
import kotlinx.collections.immutable.ImmutableMap

/**
 * Concrete [DqxnLogger] implementation dispatching to a list of [LogSink]s.
 *
 * [isEnabled] is a simple ordinal comparison -- fast path, no map lookup, no allocation. Each sink
 * dispatch is wrapped in try/catch as defense-in-depth beyond [SafeLogSink].
 */
public class DqxnLoggerImpl(
  private val sinks: List<LogSink>,
  private val minimumLevel: LogLevel = LogLevel.DEBUG,
  private val sessionId: String,
) : DqxnLogger {

  override fun isEnabled(level: LogLevel, tag: LogTag): Boolean {
    return level.ordinal >= minimumLevel.ordinal
  }

  override fun log(
    level: LogLevel,
    tag: LogTag,
    message: String,
    throwable: Throwable?,
    traceId: String?,
    spanId: String?,
    fields: ImmutableMap<String, Any>,
  ) {
    val entry =
      LogEntry(
        timestamp = SystemClock.elapsedRealtimeNanos(),
        level = level,
        tag = tag,
        message = message,
        throwable = throwable,
        traceId = traceId,
        spanId = spanId,
        fields = fields,
        sessionId = sessionId,
      )

    for (sink in sinks) {
      try {
        sink.write(entry)
      } catch (_: Exception) {
        // Defense in depth -- observability must never crash the app.
      }
    }
  }
}
