# Observability

> Structured logging, distributed tracing, metrics, health monitoring, crash/error reporting, analytics, and Firebase integration.

## Overview (`:sdk:observability`)

A non-Compose, Firebase-free module providing structured logging, distributed tracing, metrics collection, health monitoring, and crash/error reporting **interfaces**. No Firebase dependency — all Firebase-specific implementations live in `:core:firebase`. Both `:sdk:observability` and `:sdk:analytics` live under the `sdk/` directory.

This module defines:
- `DqxnLogger` + `LogSink` — structured logging with sink pipeline
- `DqxnTracer` + `TraceContext` — coroutine-context-propagated tracing
- `MetricsCollector` — pre-allocated atomic counters
- `CrashReporter` — interface for crash/non-fatal reporting
- `CrashMetadataWriter` — interface for custom key setting on crash reports
- `ErrorReporter` — interface for structured non-fatal error reporting with context
- `PerformanceTracer` — interface for network/custom trace recording
- `WidgetHealthMonitor`, `ThermalTrendAnalyzer`, `AnrWatchdog` — health monitoring
- `DiagnosticSnapshotCapture` — auto-captures correlated state on anomalies (widget crash, ANR, jank, thermal escalation)

## Crash & Error Reporting Interfaces

```kotlin
interface CrashReporter {
    fun recordNonFatal(e: Throwable, keys: ImmutableMap<String, String> = persistentMapOf())
    fun log(message: String)
    fun setUserId(id: String)
}

interface CrashMetadataWriter {
    fun setKey(key: String, value: String)
    fun setKey(key: String, value: Int)
    fun setKey(key: String, value: Float)
    fun setKey(key: String, value: Boolean)
}

interface ErrorReporter {
    fun reportNonFatal(e: Throwable, context: ErrorContext)
    fun reportWidgetCrash(typeId: String, widgetId: String, throwable: Throwable, context: WidgetErrorContext)
}

interface PerformanceTracer {
    fun newTrace(name: String): PerfTrace
    fun newHttpMetric(url: String, method: String): HttpMetric
}

interface PerfTrace : AutoCloseable {
    fun start()
    fun stop()
    fun putAttribute(key: String, value: String)
    fun incrementMetric(name: String, value: Long)
}

interface HttpMetric : AutoCloseable {
    fun setRequestPayloadSize(bytes: Long)
    fun setResponsePayloadSize(bytes: Long)
    fun setHttpResponseCode(code: Int)
    fun start()
    fun stop()
}
```

Feature modules call these interfaces. `:core:firebase` provides Firebase-backed implementations (`:core:firebase` depends on `:sdk:observability` and `:sdk:analytics`). Debug builds swap in logging/no-op implementations via Hilt module override.

## Self-Protection

Every `LogSink.write()` is wrapped in try/catch. The observability system must never crash the app:

```kotlin
class SafeLogSink(private val delegate: LogSink) : LogSink {
    override fun write(entry: LogEntry) {
        try { delegate.write(entry) } catch (_: Exception) { }
    }
}
```

## DqxnLogger

```kotlin
interface DqxnLogger {
    fun log(level: LogLevel, tag: LogTag, message: String, throwable: Throwable? = null, fields: ImmutableMap<String, Any> = persistentMapOf())
}

// Zero-allocation inline extensions — disabled calls are free
inline fun DqxnLogger.debug(tag: LogTag, vararg fields: Pair<String, Any>, message: () -> String) {
    if (isEnabled(LogLevel.DEBUG, tag)) {
        log(LogLevel.DEBUG, tag, message(), fields = fields.toMap().toImmutableMap())
    }
}

inline fun DqxnLogger.warn(tag: LogTag, vararg fields: Pair<String, Any>, message: () -> String) { ... }
inline fun DqxnLogger.error(tag: LogTag, vararg fields: Pair<String, Any>, message: () -> String, throwable: Throwable? = null) { ... }
```

### Structured LogEntry

```kotlin
@Immutable
data class LogEntry(
    val timestamp: Long,
    val level: LogLevel,
    val tag: LogTag,
    val message: String,
    val throwable: Throwable? = null,
    val traceId: String? = null,
    val spanId: String? = null,
    val fields: ImmutableMap<String, Any> = persistentMapOf(),
    val sessionId: String? = null,
)
```

`sessionId` is generated per cold start and appears in all `LogEntry`, `AnalyticsTracker.setUserProperty("session_id", ...)`, and `CrashContextProvider.setCustomKey("session_id", ...)` — a single join key across all telemetry systems.

### LogTag

String-based identifier — each module defines its own tags without modifying `:sdk:observability`:

```kotlin
@JvmInline
value class LogTag(val value: String)

// Core tags defined in :sdk:observability
object LogTags {
    val LAYOUT = LogTag("LAYOUT")
    val THEME = LogTag("THEME")
    val SENSOR = LogTag("SENSOR")
    val BLE = LogTag("BLE")
    val CONNECTION_FSM = LogTag("CONNECTION_FSM")
    val DATASTORE = LogTag("DATASTORE")
    val THERMAL = LogTag("THERMAL")
    val BINDING = LogTag("BINDING")
    val EDIT_MODE = LogTag("EDIT_MODE")
    val ENTITLEMENT = LogTag("ENTITLEMENT")
    val DRIVING = LogTag("DRIVING")
    val NAVIGATION = LogTag("NAVIGATION")
    val STARTUP = LogTag("STARTUP")
    val WIDGET_RENDER = LogTag("WIDGET_RENDER")
    val PROVIDER = LogTag("PROVIDER")
    val PRESET = LogTag("PRESET")
    val ANALYTICS = LogTag("ANALYTICS")
    val AGENTIC = LogTag("AGENTIC")
    val DIAGNOSTIC = LogTag("DIAGNOSTIC")
    val ANR = LogTag("ANR")
}

// Pack-defined tags — no changes to :sdk:observability needed
// in :pack:plus
object PlusLogTags {
    val TRIP_ACCUMULATOR = LogTag("TRIP_ACCUMULATOR")
    val WEATHER_API = LogTag("WEATHER_API")
}
```

`SamplingLogSink` keys on `LogTag.value` — pack-defined tags work with sampling without registration.

### LogSink Architecture

```kotlin
interface LogSink {
    fun write(entry: LogEntry)
}

// Implementations:
// RingBufferSink — 512-entry lock-free circular buffer, queryable via agentic dump
// CrashReporterBreadcrumbSink — forwards to CrashReporter.log() (interface, not Firebase)
// LogcatSink — standard Logcat output (debug builds)
// JsonFileLogSink — JSON-lines format, agent-parseable (debug builds)
// RedactingSink — wraps any sink, scrubs GPS coordinates, BLE MAC addresses
// SamplingLogSink — per-tag rate limiting
```

`RingBufferSink` uses a lock-free `AtomicReferenceArray` with atomic index increment.

### Log Sampling

```kotlin
class SamplingLogSink(
    private val delegate: LogSink,
    private val samplingRates: Map<LogTag, Int>, // e.g., SENSOR -> 100 (1 in 100)
) : LogSink {
    private val counters = ConcurrentHashMap<LogTag, AtomicLong>()
    override fun write(entry: LogEntry) {
        val rate = samplingRates[entry.tag] ?: 1
        if (rate <= 1 || counters.getOrPut(entry.tag) { AtomicLong() }.incrementAndGet() % rate == 0L) {
            delegate.write(entry)
        }
    }
}
```

### Breadcrumb Strategy

- **Always**: Event dispatch, state transitions (thermal, driving, connection FSM), widget add/remove, theme change, provider bind/unbind, overlay navigation
- **Never**: Per-frame metrics, sensor data emissions, draw-phase logs
- **Conditional**: Errors/warnings always; debug-level only when diagnostics mode is active

## TraceContext

```kotlin
class TraceContext(
    val traceId: String,
    val spanId: String,
    val parentSpanId: String?,
) : AbstractCoroutineContextElement(TraceContext) {
    companion object Key : CoroutineContext.Key<TraceContext>
}
```

`Channel.send()` does not carry the sender's coroutine context. Trace context is propagated explicitly via `DashboardCommand.traceId` (see [state-management.md](state-management.md#command-processing--error-handling)).

### DqxnTracer

```kotlin
class DqxnTracer @Inject constructor(private val logger: DqxnLogger) {
    private val activeSpanMap = ConcurrentHashMap<String, SpanInfo>()

    fun activeSpans(): List<SpanSummary> = activeSpanMap.values.map { it.toSummary() }

    suspend inline fun <T> withSpan(name: String, tag: LogTag, crossinline block: suspend () -> T): T {
        val parent = currentCoroutineContext()[TraceContext]
        val ctx = TraceContext(
            traceId = parent?.traceId ?: generateTraceId(),
            spanId = generateSpanId(),
            parentSpanId = parent?.spanId,
        )
        activeSpanMap[ctx.spanId] = SpanInfo(name, tag, ctx, SystemClock.elapsedRealtimeNanos())
        return withContext(ctx) {
            try { block() } finally {
                activeSpanMap.remove(ctx.spanId)
                val elapsed = SystemClock.elapsedRealtimeNanos() - activeSpanMap[ctx.spanId]?.startNanos
                logger.debug(tag, "span" to name, "elapsedMs" to (elapsed ?: 0) / 1_000_000) { name }
            }
        }
    }
}
```

### Span Registry

`DqxnTracer` tracks active spans for diagnostic capture via `activeSpans()`. The span is removed from the registry *before* the completion log is written — this prevents a span from appearing "active" in a `DiagnosticSnapshot` captured during its own completion logging.

## MetricsCollector

Pre-allocated counters with atomic operations:

```kotlin
class MetricsCollector @Inject constructor(
    widgetRegistry: WidgetRegistry,
    providerRegistry: DataProviderRegistry,
) {
    private val frameHistogram = AtomicLongArray(6) // <8ms, <12ms, <16ms, <24ms, <33ms, >33ms
    private val totalFrameCount = AtomicLong(0)
    private val recompositionCounts = ConcurrentHashMap<String, AtomicLong>().also { map ->
        widgetRegistry.all().forEach { map[it.typeId] = AtomicLong(0) }
    }
    private val providerLatency = ConcurrentHashMap<String, LongArrayRingBuffer>().also { map ->
        providerRegistry.all().forEach { map[it.providerId] = LongArrayRingBuffer(64) }
    }
    private val memoryWatermarkBytes = AtomicLong(0)

    fun recordFrame(durationMs: Long) { /* atomic bucket increment */ }
    fun recordRecomposition(typeId: String) { /* atomic increment */ }
    fun recordProviderLatency(providerId: String, latencyMs: Long) { /* ring buffer write */ }
    fun recordMemoryWatermark() { /* Runtime.totalMemory() - freeMemory() */ }
    fun snapshot(): MetricsSnapshot { /* read-only copy for dump */ }
}
```

## Health Monitoring

### WidgetHealthMonitor

Periodic liveness checks (every 10s):
- Stale data detection: widget's last data timestamp exceeds staleness threshold
- Stalled render detection: widget's last draw timestamp exceeds 2x target frame interval
- Reports health status to `CrashContextProvider` and agentic dump

### ThermalTrendAnalyzer

Linear regression on recent thermal headroom samples (last 60s at 5s intervals). Predicts time-to-threshold for the next thermal tier:

```kotlin
class ThermalTrendAnalyzer @Inject constructor(
    private val thermalManager: ThermalManager,
) {
    private val headroomSamples = RingBuffer<Pair<Long, Float>>(12)

    fun predictTimeToThreshold(targetHeadroom: Float): Duration? {
        if (headroomSamples.size < 3) return null
        val slope = linearRegressionSlope(headroomSamples)
        if (slope <= 0) return null
        val current = headroomSamples.last().second
        val remaining = targetHeadroom - current
        return (remaining / slope).seconds
    }
}
```

Enables `FramePacer` to preemptively reduce FPS 10-15s before hitting a thermal tier boundary. Uses `getThermalHeadroom(30)` for trend analysis.

### ErrorReporter

Non-fatal reporting for: widget render crashes, provider failures, DataStore corruption, binding timeouts. Forwards to `CrashReporter`.

### DeduplicatingErrorReporter

Prevents report flooding — 60-second deduplication window per unique `(sourceId, exceptionType)` pair:

```kotlin
class DeduplicatingErrorReporter(private val delegate: ErrorReporter) : ErrorReporter {
    private val recentErrors = ConcurrentHashMap<String, Long>()

    override fun reportNonFatal(e: Throwable, context: ErrorContext) {
        val key = "${context.sourceId}:${e::class.simpleName}"
        val now = SystemClock.elapsedRealtime()
        val last = recentErrors[key]
        if (last == null || now - last > 60_000) {
            recentErrors[key] = now
            delegate.reportNonFatal(e, context)
        }
    }
}
```

### AnrWatchdog

Dedicated background thread pinging main thread every 2s with 2.5s timeout:

```kotlin
class AnrWatchdog @Inject constructor(
    private val ringBufferSink: RingBufferSink,
    private val logger: DqxnLogger,
) {
    fun start() {
        thread(name = "AnrWatchdog", isDaemon = true) {
            while (true) {
                val responded = CountDownLatch(1)
                mainHandler.post { responded.countDown() }
                if (!responded.await(2500, TimeUnit.MILLISECONDS)) {
                    val allStacks = Thread.getAllStackTraces()
                    val mainStack = Looper.getMainLooper().thread.stackTrace
                    val fdCount = File("/proc/self/fd/").listFiles()?.size ?: -1

                    logger.error(LogTags.ANR, "fdCount" to fdCount) {
                        "ANR detected: main thread blocked\n" +
                        mainStack.joinToString("\n") { "  at $it" }
                    }
                    // Write diagnostic snapshot to SharedPreferences (survives process death)
                }
                Thread.sleep(2_000)
            }
        }
    }
}
```

### JankDetector

Monitors consecutive jank frames and fires `DiagnosticSnapshotCapture` on sustained jank:

```kotlin
class JankDetector @Inject constructor(
    private val metricsCollector: MetricsCollector,
    private val diagnosticCapture: DiagnosticSnapshotCapture,
    private val logger: DqxnLogger,
) {
    private val consecutiveJankFrames = AtomicInteger(0)

    fun onFrame(durationMs: Long) {
        metricsCollector.recordFrame(durationMs)
        if (durationMs > 16) {
            val count = consecutiveJankFrames.incrementAndGet()
            if (count == 5) {
                val snapshot = metricsCollector.snapshot()
                diagnosticCapture.capture(
                    AnomalyTrigger.JankSpike(
                        consecutiveJankFrames = count,
                        p99Ms = snapshot.frameP99Ms,
                        worstWidgetTypeId = snapshot.topRecomposingWidget(),
                    )
                )
                logger.warn(LogTags.DIAGNOSTIC, "consecutiveJank" to count) {
                    "Jank spike detected: $count consecutive frames >16ms"
                }
            }
        } else {
            consecutiveJankFrames.set(0)
        }
    }
}
```

Sits between `FrameMetrics` callbacks and `MetricsCollector`. The threshold of 5 consecutive frames ensures transient single-frame drops don't trigger captures.

### OOM Detection

OOM kills don't trigger crash reporters. Detection via `session_active` flag in `SharedPreferences`:
- Set `true` in `onStart()`, `false` in `onStop()`
- On cold start, if `session_active == true` AND no crash recorded -> likely OOM or force-stop
- `ComponentCallbacks2.onTrimMemory(TRIM_MEMORY_RUNNING_CRITICAL)` reports as non-fatal with memory stats

### Network Observability

Weather API calls:
- OkHttp `HttpLoggingInterceptor` (debug builds) routed to `DqxnLogger`
- `PerformanceTracerInterceptor` records HTTP metrics via `PerformanceTracer` interface
- Custom interceptor recording request latency to `MetricsCollector`
- Retry with exponential backoff (max 3 attempts, 1s/2s/4s)
- `Cache` with 30min `max-age` matching weather refresh interval

```kotlin
val weatherClient = OkHttpClient.Builder()
    .cache(Cache(cacheDir / "weather", 5 * 1024 * 1024))
    .addInterceptor(PerformanceTracerInterceptor(performanceTracer))
    .addInterceptor(MetricsInterceptor(metricsCollector, "weather"))
    .addInterceptor(RetryInterceptor(maxRetries = 3))
    .apply {
        if (BuildConfig.DEBUG) {
            addInterceptor(HttpLoggingInterceptor { msg ->
                logger.debug(LogTag.PROVIDER, "source" to "weather-http") { msg }
            }.setLevel(HttpLoggingInterceptor.Level.BASIC))
        }
    }
    .build()
```

### CrashContextProvider

Sets crash report custom keys on every significant state transition:

```kotlin
class CrashContextProvider @Inject constructor(
    private val crashMetadata: CrashMetadataWriter,
    private val crashReporter: CrashReporter,
    private val thermalManager: ThermalManager,
    private val metricsCollector: MetricsCollector,
) {
    fun install() {
        crashMetadata.setKey("session_id", sessionId)
        crashReporter.setUserId(sessionId)
        scope.launch {
            thermalManager.thermalLevel.collect { level ->
                crashMetadata.setKey("thermal_level", level.name)
            }
        }
        // Also tracks: widget_count, driving_state, jank_percent,
        // active_providers, edit_mode, current_theme, memory_mb
    }
}
```

### Performance Budget

| Operation | Budget |
|---|---|
| Disabled log call | < 5ns (inline + branch) |
| Enabled log call | < 500ns |
| Ring buffer write | < 50ns (atomic) |
| Metrics counter increment | < 20ns |
| Health check cycle (every 10s) | < 1ms |
| Crash context update | < 5ms (state transitions only) |

## Anomaly Auto-Capture

When anomalies are detected, the observability system captures a `DiagnosticSnapshot` — correlated state from all subsystems at the moment of the anomaly. This eliminates the agent scavenger hunt of calling 5+ dump commands and manually correlating by timestamp.

### DiagnosticSnapshot

```kotlin
@Immutable
data class DiagnosticSnapshot(
    val timestamp: Long,
    val trigger: AnomalyTrigger,
    val ringBufferTail: ImmutableList<LogEntry>,     // last 50 entries from RingBufferSink
    val metricsSnapshot: MetricsSnapshot,
    val thermalState: ThermalState,                   // level + headroom + trend prediction
    val widgetHealth: ImmutableMap<String, WidgetHealthStatus>,
    val activeTraces: ImmutableList<SpanSummary>,
    val captureEvents: ImmutableList<CaptureEvent>?,  // non-null if capture session active
    val agenticTraceId: String?,                      // non-null if triggered by agentic command
    val sessionId: String,
)

sealed interface AnomalyTrigger {
    data class WidgetCrash(
        val typeId: String,
        val widgetId: String,
        val throwable: Throwable,
        val lastSnapshot: DataSnapshot?,
        val settings: ImmutableMap<String, Any>,
    ) : AnomalyTrigger

    data class AnrDetected(
        val mainThreadStack: ImmutableList<StackTraceElement>,
        val fdCount: Int,
    ) : AnomalyTrigger

    data class ThermalEscalation(
        val from: ThermalLevel,
        val to: ThermalLevel,
        val predictedTimeToNext: Duration?,
    ) : AnomalyTrigger

    data class JankSpike(
        val consecutiveJankFrames: Int,
        val p99Ms: Long,
        val worstWidgetTypeId: String?,
    ) : AnomalyTrigger

    data class ProviderTimeout(
        val providerId: String,
        val widgetId: String,
        val timeoutMs: Long,
    ) : AnomalyTrigger

    data class EscalatedStaleness(
        val widgetId: String,
        val typeId: String,
        val actualFreshnessMs: Long,
        val stalenessThresholdMs: Long,
        val providerState: String, // "CONNECTED", "DISCONNECTED", etc.
    ) : AnomalyTrigger
}
```

### Trigger Conditions

| Trigger | Condition | Auto-action |
|---|---|---|
| Widget crash | `WidgetSlot` error boundary triggered | Capture full snapshot, persist to file |
| ANR | `AnrWatchdog` timeout fired | Capture, persist to SharedPreferences (survives process death) |
| Thermal escalation | `ThermalManager` → DEGRADED or CRITICAL | Capture, persist to file (thermal rotation pool) |
| Jank spike | `JankDetector`: ≥5 consecutive frames >16ms | Capture, include worst-recomposing widget |
| Provider timeout | `firstEmissionTimeout` exceeded | Partial capture (affected widget only) |
| Escalated staleness | Widget data freshness exceeds 3x staleness threshold while provider reports CONNECTED | Capture, include binding lifecycle events |

### DiagnosticSnapshotCapture

```kotlin
class DiagnosticSnapshotCapture @Inject constructor(
    private val ringBufferSink: RingBufferSink,
    private val metricsCollector: MetricsCollector,
    private val thermalManager: ThermalManager,
    private val widgetHealthMonitor: WidgetHealthMonitor,
    private val tracer: DqxnTracer,
    private val captureSessionRegistry: CaptureSessionRegistry,
    private val logger: DqxnLogger,
) {
    private val capturing = AtomicBoolean(false)

    fun capture(trigger: AnomalyTrigger, agenticTraceId: String? = null): DiagnosticSnapshot? {
        if (!capturing.compareAndSet(false, true)) return null // reentrance guard
        return try {
            DiagnosticSnapshot(
                timestamp = SystemClock.elapsedRealtimeNanos(),
                trigger = trigger,
                ringBufferTail = ringBufferSink.tail(50).toImmutableList(),
                metricsSnapshot = metricsCollector.snapshot(),
                thermalState = thermalManager.currentState(),
                widgetHealth = widgetHealthMonitor.allStatuses(),
                activeTraces = tracer.activeSpans().toImmutableList(),
                captureEvents = captureSessionRegistry.currentEvents(),
                agenticTraceId = agenticTraceId,
                sessionId = sessionId,
            )
        } finally {
            capturing.set(false)
        }
    }
}
```

### Persistence

Debug builds write snapshots to `${filesDir}/debug/diagnostics/` with **separate rotation pools** by trigger severity:

| Pool | Path pattern | Max files | Trigger types |
|---|---|---|---|
| Crash | `snap_crash_{timestamp}.json` | 20 | `WidgetCrash`, `AnrDetected` |
| Thermal | `snap_thermal_{timestamp}.json` | 10 | `ThermalEscalation` |
| Performance | `snap_perf_{timestamp}.json` | 10 | `JankSpike`, `ProviderTimeout`, `EscalatedStaleness` |

Separate pools prevent frequent thermal oscillation (common in vehicles) from evicting crash snapshots. Agent pulls via `adb pull`. The `diagnose-crash` agentic command returns the most recent snapshot for a given widget (see [build-system.md](build-system.md#compound-diagnostic-commands)).

Release builds: only the `AnomalyTrigger` type and timestamp are forwarded to `CrashMetadataWriter` as custom keys. `agenticTraceId` is always `null` in release — the agentic receiver is debug-only. No full diagnostic dump in production.

### CaptureSessionRegistry

Records UI interaction events during an active capture session. Used by `DiagnosticSnapshotCapture` to include recent user actions in anomaly snapshots:

```kotlin
interface CaptureSessionRegistry {
    fun startCapture()
    fun stopCapture(): ImmutableList<CaptureEvent>
    fun recordEvent(event: CaptureEvent)
    fun currentEvents(): ImmutableList<CaptureEvent>?
    val isCapturing: Boolean
}

@Singleton
class CaptureSessionRegistryImpl @Inject constructor() : CaptureSessionRegistry {
    private val events = RingBuffer<CaptureEvent>(100) // bounded, no unbounded growth
    @Volatile private var capturing = false

    override fun startCapture() { capturing = true; events.clear() }
    override fun stopCapture(): ImmutableList<CaptureEvent> {
        capturing = false
        return events.toList().toImmutableList()
    }
    override fun recordEvent(event: CaptureEvent) { if (capturing) events.add(event) }
    override fun currentEvents(): ImmutableList<CaptureEvent>? =
        if (capturing) events.toList().toImmutableList() else null
    override val isCapturing: Boolean get() = capturing
}

sealed interface CaptureEvent {
    val timestamp: Long
    data class Tap(override val timestamp: Long, val widgetId: String) : CaptureEvent
    data class WidgetMove(override val timestamp: Long, val widgetId: String, val fromPosition: GridPosition, val toPosition: GridPosition) : CaptureEvent
    data class WidgetResize(override val timestamp: Long, val widgetId: String) : CaptureEvent
    data class Navigation(override val timestamp: Long, val route: String) : CaptureEvent
}
```

Wired as `@Singleton` via Hilt. `capture-start` / `capture-stop` agentic commands delegate to this registry. The ring buffer prevents unbounded memory growth during long capture sessions.

### Anomaly Event File (Debug Only)

A debug extension of `DiagnosticSnapshotCapture` appends structured anomaly events to a dedicated file for push-based agent notification:

```
${filesDir}/debug/anomaly_events.jsonl
```

Format (one JSON object per line):
```json
{"trigger":"WidgetCrash","typeId":"core:speedometer","widgetId":"abc-123","traceId":"agentic-1708444800123","snapshotPath":"snap_crash_1708444800456.json","timestamp":1708444800456}
```

Agent tails via `adb shell tail -f` for real-time push notification of anomalies — eliminates polling overhead. Rotation: 1MB, 2 files. Deduplication: same `(trigger type, sourceId)` pair suppressed within 60s, consistent with `DeduplicatingErrorReporter`.

This extension is wired in `:app:src/debug/`, not in `:sdk:observability` — no debug concerns leak into the production module.

### Binding Lifecycle Events

`WidgetBindingCoordinator` emits guaranteed log entries at `INFO` level for all binding state transitions. These are never sampled and always captured by `RingBufferSink`, ensuring `diagnose-widget` log tail always contains the binding history that led to the current state.

**Required events:**

| Event | Tag | Required fields |
|---|---|---|
| `BIND_STARTED` | `BINDING` | `widgetId`, `providerId`, `traceId`, `snapshotType` |
| `BIND_CANCELLED` | `BINDING` | `widgetId`, `providerId`, `traceId`, `reason` |
| `BIND_TIMEOUT` | `BINDING` | `widgetId`, `providerId`, `traceId`, `timeoutMs` |
| `REBIND_SCHEDULED` | `BINDING` | `widgetId`, `providerId`, `traceId`, `attempt`, `delayMs` |
| `PROVIDER_FALLBACK` | `BINDING` | `widgetId`, `fromProviderId`, `toProviderId`, `traceId` |
| `FIRST_EMISSION` | `BINDING` | `widgetId`, `providerId`, `traceId`, `latencyMs` |

All events include `widgetId` and `traceId` in `LogEntry.fields`, making them filterable by the `diagnose-widget` command's log tail query. The `traceId` field enables correlation back to the originating agentic command (if any) or user action.

These events are the primary diagnostic data source for binding-related issues. Without them, `diagnose-widget` can report current state but not the *sequence of transitions* that led to it — which is where most binding bugs manifest.

### Agentic Trace Correlation

When an agentic command triggers a `DashboardCommand`, the `AgenticReceiver` attaches a trace ID:

```kotlin
// In AgenticReceiver
val agenticTraceId = "agentic-${SystemClock.elapsedRealtimeNanos()}"
val command = parseCommand(intent).copy(traceId = agenticTraceId)
commandChannel.send(command)
logger.debug(LogTags.AGENTIC, "traceId" to agenticTraceId, "command" to command::class.simpleName) {
    "Agentic command dispatched"
}
```

If that command triggers a downstream anomaly, `DiagnosticSnapshotCapture` receives the `agenticTraceId` from the active `TraceContext`. The agent can now correlate: "I sent `widget-add`, trace `agentic-1708444800123`, and DiagnosticSnapshot `snap_1708444800456.json` has `agenticTraceId = agentic-1708444800123`" — causal link established.

### Performance Budget (Auto-Capture)

| Operation | Budget |
|---|---|
| `DiagnosticSnapshotCapture.capture()` | < 5ms (reads pre-computed state, no allocations beyond the snapshot itself) |
| File write (debug) | < 10ms (async on `Dispatchers.IO`, never blocks anomaly handling) |
| Ring buffer tail read | < 100μs (lock-free array copy) |

Auto-capture runs on the thread that detected the anomaly (typically main for widget crashes, watchdog thread for ANR). The capture itself is non-blocking — file persistence is fire-and-forget on IO.

## Analytics (`:sdk:analytics`)

```kotlin
interface AnalyticsTracker {
    fun track(event: AnalyticsEvent)
    fun setUserProperty(key: String, value: String)
}

sealed interface AnalyticsEvent {
    data object AppLaunch : AnalyticsEvent
    data class WidgetAdded(val typeId: String) : AnalyticsEvent
    data class WidgetRemoved(val typeId: String) : AnalyticsEvent
    data class ThemeChanged(val themeId: String, val isDark: Boolean) : AnalyticsEvent
    data class PackPurchased(val packId: String) : AnalyticsEvent
    data class EditModeEntered(val widgetCount: Int) : AnalyticsEvent
    data class UpsellImpression(val trigger: String, val packId: String) : AnalyticsEvent
    data class UpsellConversion(val trigger: String, val packId: String) : AnalyticsEvent
}
```

All feature modules depend on the `:sdk:analytics` interface. Debug builds use a logging implementation routed to `DqxnLogger` with `LogTag.ANALYTICS`.

Packs fire analytics events via `PackAnalytics`, a scoped interface that automatically tags all events with the pack's `packId`:

```kotlin
interface PackAnalytics {
    fun track(event: AnalyticsEvent)
}
```

Pack modules receive `PackAnalytics` via injection — they never interact with `AnalyticsTracker` directly. The shell creates a `PackAnalytics` instance per pack, binding the `packId` so packs cannot misattribute events.

Privacy: no PII in events. PDPA-compliant. User opt-out toggle kills the tracker at the interface level.

## Firebase Integration (`:core:firebase`)

The sole Firebase dependency point. Implements all observability and analytics interfaces.

### Hilt Wiring

```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class FirebaseModule {
    @Binds @Singleton abstract fun crashReporter(impl: FirebaseCrashReporter): CrashReporter
    @Binds @Singleton abstract fun crashMetadata(impl: FirebaseCrashMetadataWriter): CrashMetadataWriter
    @Binds @Singleton abstract fun errorReporter(impl: FirebaseErrorReporter): ErrorReporter
    @Binds @Singleton abstract fun analytics(impl: FirebaseAnalyticsTracker): AnalyticsTracker
    @Binds @Singleton abstract fun performanceTracer(impl: FirebasePerformanceTracer): PerformanceTracer
}
```

Debug builds override with `DebugObservabilityModule` in `:app:src/debug/`. `:core:firebase` is excluded from debug dependencies via `releaseImplementation`.

### Firebase Crashlytics

```kotlin
class FirebaseCrashReporter @Inject constructor() : CrashReporter {
    private val crashlytics = FirebaseCrashlytics.getInstance()
    override fun recordNonFatal(e: Throwable, keys: ImmutableMap<String, String>) {
        keys.forEach { (k, v) -> crashlytics.setCustomKey(k, v) }
        crashlytics.recordException(e)
    }
    override fun log(message: String) { crashlytics.log(message) }
    override fun setUserId(id: String) { crashlytics.setUserId(id) }
}
```

### Firebase Performance Monitoring

```kotlin
class FirebasePerformanceTracer @Inject constructor() : PerformanceTracer {
    override fun newTrace(name: String): PerfTrace = FirebasePerfTrace(Firebase.performance.newTrace(name))
    override fun newHttpMetric(url: String, method: String): HttpMetric =
        FirebaseHttpMetric(Firebase.performance.newHttpMetric(url, method))
}
```

**Traces:**

| Trace | What it measures |
|---|---|
| `cold_start` | App launch -> first dashboard frame with widget data |
| `warm_start` | Activity recreate -> first dashboard frame |
| `theme_switch` | Theme change command -> all widgets re-rendered |
| `widget_bind` | Widget add -> first data emission received |
| `layout_save` | Layout mutation -> DataStore write complete |
| `preset_load` | Preset selection -> layout fully restored with bindings |
| `overlay_open` | Overlay nav event -> overlay first frame |
| `weather_fetch` | HTTP metric for weather API |

Per-frame timing and provider emission latency are NOT covered by Firebase Perf (too high frequency) — that's `MetricsCollector`'s job.
