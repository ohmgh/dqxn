package app.dqxn.android.sdk.observability.trace

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext

/**
 * Lightweight tracer that creates child [TraceContext] spans in the coroutine context.
 * Tracks active spans in a [ConcurrentHashMap] for diagnostics.
 */
public object DqxnTracer {

  private val activeSpans: ConcurrentHashMap<String, SpanInfo> = ConcurrentHashMap()

  /**
   * Runs [block] in a child span under the current trace (or a new trace if none exists).
   * The span is automatically tracked and removed on completion.
   */
  public suspend fun <T> withSpan(
    name: String,
    block: suspend CoroutineScope.() -> T,
  ): T {
    val parentTrace = kotlin.coroutines.coroutineContext[TraceContext]
    val childTrace =
      parentTrace?.childSpan() ?: TraceContext.newTrace()

    val spanInfo =
      SpanInfo(
        traceId = childTrace.traceId,
        spanId = childTrace.spanId,
        name = name,
        startTimeNanos = System.nanoTime(),
      )

    activeSpans[childTrace.spanId] = spanInfo
    try {
      return withContext(childTrace, block)
    } finally {
      activeSpans.remove(childTrace.spanId)
    }
  }

  /** Returns a snapshot of currently active spans for diagnostics. */
  public fun activeSpans(): List<SpanInfo> = activeSpans.values.toList()
}

/** Diagnostic info about an active span. */
public data class SpanInfo(
  val traceId: String,
  val spanId: String,
  val name: String,
  val startTimeNanos: Long,
)
