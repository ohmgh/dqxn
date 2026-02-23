package app.dqxn.android.sdk.observability.trace

import java.util.UUID
import kotlin.coroutines.CoroutineContext

/**
 * Coroutine context element carrying trace and span IDs for distributed tracing.
 * Attach to a coroutine scope to automatically correlate log entries with traces.
 */
public data class TraceContext(
  val traceId: String,
  val spanId: String,
) : CoroutineContext.Element {

  override val key: CoroutineContext.Key<TraceContext> get() = Key

  public companion object Key : CoroutineContext.Key<TraceContext> {

    /** Creates a new trace with optional agentic trace ID override. */
    public fun newTrace(agenticTraceId: String? = null): TraceContext {
      return TraceContext(
        traceId = agenticTraceId ?: UUID.randomUUID().toString(),
        spanId = UUID.randomUUID().toString(),
      )
    }
  }

  /** Creates a child span under the same trace. */
  public fun childSpan(): TraceContext {
    return copy(spanId = UUID.randomUUID().toString())
  }
}
