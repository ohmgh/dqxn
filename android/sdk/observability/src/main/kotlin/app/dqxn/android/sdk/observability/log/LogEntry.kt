package app.dqxn.android.sdk.observability.log

import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf

/**
 * Immutable log entry dispatched to [LogSink] instances.
 *
 * [timestamp] uses [android.os.SystemClock.elapsedRealtimeNanos] for monotonic timing.
 * [fields] carries structured key-value context without string formatting.
 */
public data class LogEntry(
  val timestamp: Long,
  val level: LogLevel,
  val tag: LogTag,
  val message: String,
  val throwable: Throwable? = null,
  val traceId: String? = null,
  val spanId: String? = null,
  val fields: ImmutableMap<String, Any> = persistentMapOf(),
  val sessionId: String,
)
