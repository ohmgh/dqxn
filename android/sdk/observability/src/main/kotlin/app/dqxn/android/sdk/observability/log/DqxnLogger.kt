package app.dqxn.android.sdk.observability.log

import app.dqxn.android.sdk.observability.trace.TraceContext
import kotlin.coroutines.coroutineContext
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf

/**
 * Zero-allocation logger interface. The inline extension functions check [isEnabled] before
 * evaluating the message lambda, so disabled log paths produce no allocations (no lambda capture,
 * no string concatenation, no ImmutableMap creation).
 */
public interface DqxnLogger {

  /** Fast-path level check. No map lookup, no allocation. */
  public fun isEnabled(level: LogLevel, tag: LogTag): Boolean

  /** Core log dispatch. Callers should prefer the inline extensions below. */
  public fun log(
    level: LogLevel,
    tag: LogTag,
    message: String,
    throwable: Throwable? = null,
    traceId: String? = null,
    spanId: String? = null,
    fields: ImmutableMap<String, Any> = persistentMapOf(),
  )
}

/** No-op logger that always returns false from [isEnabled]. */
public object NoOpLogger : DqxnLogger {
  override fun isEnabled(level: LogLevel, tag: LogTag): Boolean = false

  override fun log(
    level: LogLevel,
    tag: LogTag,
    message: String,
    throwable: Throwable?,
    traceId: String?,
    spanId: String?,
    fields: ImmutableMap<String, Any>,
  ) {
    // Intentionally empty.
  }
}

// ---------------------------------------------------------------------------
// Inline extension functions -- zero-allocation when disabled
// ---------------------------------------------------------------------------

public inline fun DqxnLogger.verbose(tag: LogTag, message: () -> String) {
  if (isEnabled(LogLevel.VERBOSE, tag)) log(LogLevel.VERBOSE, tag, message())
}

public inline fun DqxnLogger.debug(tag: LogTag, message: () -> String) {
  if (isEnabled(LogLevel.DEBUG, tag)) log(LogLevel.DEBUG, tag, message())
}

public inline fun DqxnLogger.info(tag: LogTag, message: () -> String) {
  if (isEnabled(LogLevel.INFO, tag)) log(LogLevel.INFO, tag, message())
}

public inline fun DqxnLogger.warn(tag: LogTag, message: () -> String) {
  if (isEnabled(LogLevel.WARN, tag)) log(LogLevel.WARN, tag, message())
}

public inline fun DqxnLogger.error(tag: LogTag, message: () -> String) {
  if (isEnabled(LogLevel.ERROR, tag)) log(LogLevel.ERROR, tag, message())
}

public inline fun DqxnLogger.error(tag: LogTag, throwable: Throwable, message: () -> String) {
  if (isEnabled(LogLevel.ERROR, tag)) log(LogLevel.ERROR, tag, message(), throwable)
}

// Overloads with fields

public inline fun DqxnLogger.debug(
  tag: LogTag,
  fields: () -> ImmutableMap<String, Any>,
  message: () -> String,
) {
  if (isEnabled(LogLevel.DEBUG, tag)) log(LogLevel.DEBUG, tag, message(), fields = fields())
}

public inline fun DqxnLogger.info(
  tag: LogTag,
  fields: () -> ImmutableMap<String, Any>,
  message: () -> String,
) {
  if (isEnabled(LogLevel.INFO, tag)) log(LogLevel.INFO, tag, message(), fields = fields())
}

public inline fun DqxnLogger.warn(
  tag: LogTag,
  fields: () -> ImmutableMap<String, Any>,
  message: () -> String,
) {
  if (isEnabled(LogLevel.WARN, tag)) log(LogLevel.WARN, tag, message(), fields = fields())
}

public inline fun DqxnLogger.error(
  tag: LogTag,
  fields: () -> ImmutableMap<String, Any>,
  message: () -> String,
) {
  if (isEnabled(LogLevel.ERROR, tag)) log(LogLevel.ERROR, tag, message(), fields = fields())
}

// ---------------------------------------------------------------------------
// Suspend inline extensions -- reads TraceContext from coroutine context
// ---------------------------------------------------------------------------

public suspend inline fun DqxnLogger.debugTraced(tag: LogTag, message: () -> String) {
  if (isEnabled(LogLevel.DEBUG, tag)) {
    val trace = coroutineContext[TraceContext]
    log(LogLevel.DEBUG, tag, message(), traceId = trace?.traceId, spanId = trace?.spanId)
  }
}

public suspend inline fun DqxnLogger.infoTraced(tag: LogTag, message: () -> String) {
  if (isEnabled(LogLevel.INFO, tag)) {
    val trace = coroutineContext[TraceContext]
    log(LogLevel.INFO, tag, message(), traceId = trace?.traceId, spanId = trace?.spanId)
  }
}

public suspend inline fun DqxnLogger.warnTraced(tag: LogTag, message: () -> String) {
  if (isEnabled(LogLevel.WARN, tag)) {
    val trace = coroutineContext[TraceContext]
    log(LogLevel.WARN, tag, message(), traceId = trace?.traceId, spanId = trace?.spanId)
  }
}

public suspend inline fun DqxnLogger.errorTraced(
  tag: LogTag,
  throwable: Throwable,
  message: () -> String,
) {
  if (isEnabled(LogLevel.ERROR, tag)) {
    val trace = coroutineContext[TraceContext]
    log(
      LogLevel.ERROR,
      tag,
      message(),
      throwable,
      traceId = trace?.traceId,
      spanId = trace?.spanId,
    )
  }
}
