# Observability

> Structured logging, distributed tracing, metrics, health monitoring, crash/error reporting, analytics, and Firebase integration.

## Overview (`:sdk:observability`)

A non-Compose, Firebase-free module providing structured logging, distributed tracing, metrics collection, health monitoring, and crash/error reporting **interfaces**. No Firebase dependency — all Firebase-specific implementations live in `:core:firebase`. Both `:sdk:observability` and `:sdk:analytics` live under the `sdk/` directory.

This module defines:
- `DqxnLogger` + `LogSink` — structured logging with sink pipeline
- `DqxnTracer` + `TraceContext` — coroutine-context-propagated tracing
- `MetricsCollector` — pre-allocated atomic counters
- `CrashReporter` — interface for crash/non-fatal reporting **and custom metadata keys**
- `ErrorReporter` — interface for structured non-fatal error reporting with context
- `WidgetHealthMonitor`, `ThermalTrendAnalyzer`, `AnrWatchdog` — health monitoring
- `DiagnosticSnapshotCapture` — auto-captures correlated state on anomalies (widget crash, ANR, jank, thermal escalation, binding stall, DataStore corruption)

## Crash & Error Reporting Interfaces

```kotlin
interface CrashReporter {
    fun recordNonFatal(e: Throwable, keys: ImmutableMap<String, String> = persistentMapOf())
    fun log(message: String)
    fun setUserId(id: String)
    fun setKey(key: String, value: String)
    fun setKey(key: String, value: Int)
    fun setKey(key: String, value: Float)
    fun setKey(key: String, value: Boolean)
}

interface ErrorReporter {
    fun reportNonFatal(e: Throwable, context: ErrorContext)
    fun reportWidgetCrash(typeId: String, widgetId: String, throwable: Throwable, context: WidgetErrorContext)
}

// PerformanceTracer deferred — Firebase Performance used directly in :core:firebase for v1.
// Extract interface when a second module needs trace instrumentation.
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

// Inline extensions — zero-allocation when logging is disabled (no vararg array, no lambda invocation)

// Zero-allocation: no-fields overload — disabled calls are truly free (inline + branch only)
inline fun DqxnLogger.debug(tag: LogTag, message: () -> String) {
    if (isEnabled(LogLevel.DEBUG, tag)) {
        log(LogLevel.DEBUG, tag, message())
    }
}

// With-fields overload — fields lambda deferred past isEnabled check
inline fun DqxnLogger.debug(tag: LogTag, crossinline fields: () -> ImmutableMap<String, Any>, message: () -> String) {
    if (isEnabled(LogLevel.DEBUG, tag)) {
        log(LogLevel.DEBUG, tag, message(), fields = fields())
    }
}

// Usage:
// logger.debug(LogTags.BINDING) { "Provider bound" }
// logger.debug(LogTags.BINDING, { persistentMapOf("widgetId" to id, "latencyMs" to ms) }) { "Provider bound" }

inline fun DqxnLogger.warn(tag: LogTag, message: () -> String) { ... }
inline fun DqxnLogger.warn(tag: LogTag, crossinline fields: () -> ImmutableMap<String, Any>, message: () -> String) { ... }
inline fun DqxnLogger.error(tag: LogTag, throwable: Throwable? = null, message: () -> String) { ... }
inline fun DqxnLogger.error(tag: LogTag, throwable: Throwable? = null, crossinline fields: () -> ImmutableMap<String, Any>, message: () -> String) { ... }
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
    val INTERACTION = LogTag("INTERACTION")
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
                val spanInfo = activeSpanMap.remove(ctx.spanId)
                val elapsed = SystemClock.elapsedRealtimeNanos() - (spanInfo?.startNanos ?: 0)
                logger.debug(tag, { persistentMapOf("span" to name, "elapsedMs" to elapsed / 1_000_000) }) { name }
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
    private val widgetDrawTimes = ConcurrentHashMap<String, LongArrayRingBuffer>().also { map ->
        widgetRegistry.all().forEach { map[it.typeId] = LongArrayRingBuffer(64) }
    }

    // Pre-populated for known types; getOrPut at recording sites handles late registration.
    fun recordFrame(durationMs: Long) { /* atomic bucket increment */ }
    fun recordRecomposition(typeId: String) {
        recompositionCounts.getOrPut(typeId) { AtomicLong(0) }.incrementAndGet()
    }
    fun recordProviderLatency(providerId: String, latencyMs: Long) {
        providerLatency.getOrPut(providerId) { LongArrayRingBuffer(64) }.add(latencyMs)
    }
    fun recordWidgetDrawTime(typeId: String, nanos: Long) {
        widgetDrawTimes.getOrPut(typeId) { LongArrayRingBuffer(64) }.add(nanos)
    }
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

Records recent thermal headroom samples (last 60s at 5s intervals) for diagnostic visibility:

```kotlin
class ThermalTrendAnalyzer @Inject constructor(
    private val thermalManager: ThermalManager,
) {
    private val headroomSamples = RingBuffer<Pair<Long, Float>>(12)

    fun recordSample(timestamp: Long, headroom: Float) {
        headroomSamples.add(timestamp to headroom)
    }

    fun recentSamples(): List<Pair<Long, Float>> = headroomSamples.toList()
}
```

Exposes headroom history for `diagnose-thermal`. Predictive FPS adaptation (linear regression on headroom trend) deferred to post-launch — reactive thermal response via `ThermalManager.registerThermalStatusListener()` tier transitions is the v1 approach. Add prediction when real-world thermal data shows reactive adaptation causes observable jank spikes during transitions.

### ErrorReporter

Non-fatal reporting for: widget render crashes, provider failures, DataStore corruption, binding timeouts. Forwards to `CrashReporter`.

### DeduplicatingErrorReporter

Prevents report flooding — 60-second deduplication window per unique `(sourceId, exceptionType)` pair:

```kotlin
class DeduplicatingErrorReporter(
    private val delegate: ErrorReporter,
    private val defaultWindowMs: Long = 60_000,
) : ErrorReporter {
    private data class ErrorRecord(val lastReportedMs: Long, val suppressedCount: Int)
    private val recentErrors = ConcurrentHashMap<String, ErrorRecord>()

    override fun reportNonFatal(e: Throwable, context: ErrorContext) {
        dedupAndReport("${context.sourceId}:${e::class.simpleName}", e, context) {
            delegate.reportNonFatal(e, it)
        }
    }

    override fun reportWidgetCrash(typeId: String, widgetId: String, throwable: Throwable, context: WidgetErrorContext) {
        dedupAndReport("$typeId:${throwable::class.simpleName}", throwable, context) {
            delegate.reportWidgetCrash(typeId, widgetId, throwable, it as WidgetErrorContext)
        }
    }

    private fun <C : ErrorContext> dedupAndReport(key: String, e: Throwable, context: C, report: (C) -> Unit) {
        val now = SystemClock.elapsedRealtime()
        var shouldReport = false
        var enrichedContext = context
        recentErrors.compute(key) { _, existing ->
            if (existing == null || now - existing.lastReportedMs > defaultWindowMs) {
                shouldReport = true
                if (existing != null && existing.suppressedCount > 0) {
                    @Suppress("UNCHECKED_CAST")
                    enrichedContext = context.withField("suppressedCount", existing.suppressedCount) as C
                }
                ErrorRecord(now, 0)
            } else {
                existing.copy(suppressedCount = existing.suppressedCount + 1)
            }
        }
        if (shouldReport) report(enrichedContext)
    }
}
```

### AnrWatchdog

Dedicated background thread pinging main thread every 2s with 2.5s timeout. Requires 2 consecutive missed pings (~5s block) before capturing — single misses from GC pauses don't trigger false positives. Skips capture when debugger is attached.

```kotlin
class AnrWatchdog @Inject constructor(
    private val ringBufferSink: RingBufferSink,
    private val logger: DqxnLogger,
) {
    fun start() {
        thread(name = "AnrWatchdog", isDaemon = true) {
            var consecutiveMisses = 0
            while (true) {
                val responded = CountDownLatch(1)
                mainHandler.post { responded.countDown() }
                if (!responded.await(2500, TimeUnit.MILLISECONDS)) {
                    if (Debug.isDebuggerConnected()) {
                        consecutiveMisses = 0
                        Thread.sleep(2_000)
                        continue // skip under debugger
                    }
                    consecutiveMisses++
                    if (consecutiveMisses >= 2) {
                        val allStacks = Thread.getAllStackTraces()
                        val mainStack = Looper.getMainLooper().thread.stackTrace
                        val fdCount = File("/proc/self/fd/").listFiles()?.size ?: -1

                        logger.error(LogTags.ANR, { persistentMapOf("fdCount" to fdCount) }) {
                            "ANR detected: main thread blocked for ${consecutiveMisses * 2500}ms\n" +
                            mainStack.joinToString("\n") { "  at $it" }
                        }
                        writeAnrFile(mainStack, fdCount, allStacks)
                        consecutiveMisses = 0
                    }
                } else {
                    consecutiveMisses = 0
                }
                Thread.sleep(2_000)
            }
        }
    }

    private fun writeAnrFile(mainStack: Array<StackTraceElement>, fdCount: Int, allStacks: Map<Thread, Array<StackTraceElement>>) {
        try {
            val file = File(diagnosticsDir, "anr_latest.json")
            FileOutputStream(file).bufferedWriter().use { writer ->
                // Direct file write on watchdog thread — no main thread, no Dispatchers.IO
                writer.write(buildAnrJson(mainStack, fdCount, allStacks))
            }
        } catch (_: Exception) { /* observability must never crash the app */ }
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
            if (count == 5 || count == 20 || count == 100) {
                val snapshot = metricsCollector.snapshot()
                diagnosticCapture.capture(
                    AnomalyTrigger.JankSpike(
                        consecutiveJankFrames = count,
                        p99Ms = snapshot.frameP99Ms,
                        worstWidgetTypeId = snapshot.topRecomposingWidget(),
                    )
                )
                logger.warn(LogTags.DIAGNOSTIC, { persistentMapOf("consecutiveJank" to count) }) {
                    "Jank spike detected: $count consecutive frames >16ms"
                }
            }
        } else {
            consecutiveJankFrames.set(0)
        }
    }
}
```

Sits between `FrameMetrics` callbacks and `MetricsCollector`. Fires at exponential thresholds (5, 20, 100 consecutive frames) to capture both jank onset and sustained jank progression. Single threshold at 5 would miss the evolving state during sustained thermal-induced jank.

### OOM Detection

Deferred to post-launch. Firebase Crashlytics tracks crash-free sessions; LeakCanary catches memory leaks during development. Will implement `session_active` flag detection when production data establishes a crash-free baseline to compare against.

### Crash Evidence Persistence

`DiagnosticSnapshotCapture` writes files asynchronously on `Dispatchers.IO`. If a crash kills the process (which is exactly when safe mode triggers), the async write may not complete. Three rapid crashes mean three potentially lost snapshots.

**Sync crash record** in `UncaughtExceptionHandler` writes a minimal crash record to `SharedPreferences` **synchronously** before the process dies:

```kotlin
class CrashEvidenceWriter @Inject constructor(
    private val prefs: SharedPreferences, // crash_evidence prefs
) : Thread.UncaughtExceptionHandler {
    private val delegate = Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(t: Thread, e: Throwable) {
        try {
            // Sync write — must complete before process death
            prefs.edit().apply {
                putString("last_crash_type_id", extractWidgetTypeId(e))
                putString("last_crash_exception", "${e::class.simpleName}: ${e.message}")
                putString("last_crash_stack_top5", e.stackTrace.take(5).joinToString("\n"))
                putLong("last_crash_timestamp", System.currentTimeMillis())
            }.commit() // commit(), not apply() — synchronous
        } catch (_: Exception) { /* must not interfere with crash handling */ }
        delegate?.uncaughtException(t, e)
    }
}
```

This follows the same pattern as `AnrWatchdog` writing to SharedPreferences. The `diagnose-crash` agentic command reads this when no snapshot file exists for the widget — the agent always has something to investigate after safe mode activation.

### Network Observability

Weather API calls:
- OkHttp `HttpLoggingInterceptor` (debug builds) routed to `DqxnLogger`
- Custom interceptor recording request latency to `MetricsCollector`
- Retry with exponential backoff (max 3 attempts, 1s/2s/4s)
- `Cache` with 30min `max-age` matching weather refresh interval

```kotlin
val weatherClient = OkHttpClient.Builder()
    .cache(Cache(cacheDir / "weather", 5 * 1024 * 1024))
    .addInterceptor(MetricsInterceptor(metricsCollector, "weather"))
    .addInterceptor(RetryInterceptor(maxRetries = 3))
    .apply {
        if (BuildConfig.DEBUG) {
            addInterceptor(HttpLoggingInterceptor { msg ->
                logger.debug(LogTags.PROVIDER, { persistentMapOf("source" to "weather-http") }) { msg }
            }.setLevel(HttpLoggingInterceptor.Level.BASIC))
        }
    }
    .build()
```

### CrashContextProvider

Sets crash report custom keys on every significant state transition:

```kotlin
class CrashContextProvider @Inject constructor(
    private val crashReporter: CrashReporter,
    private val thermalManager: ThermalManager,
    private val metricsCollector: MetricsCollector,
) {
    fun install() {
        crashReporter.setKey("session_id", sessionId)
        crashReporter.setUserId(sessionId)
        scope.launch {
            thermalManager.thermalLevel.collect { level ->
                crashReporter.setKey("thermal_level", level.name)
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
| Widget draw time record | < 25ns (`System.nanoTime()` + ring buffer write) |
| Health check cycle (every 10s) | < 1ms |
| Crash context update | < 5ms (state transitions only) |
| Sync crash evidence write | < 15ms (SharedPreferences.commit(), only on crash path) |

## Anomaly Auto-Capture

When anomalies are detected, the observability system captures a `DiagnosticSnapshot` — correlated state from all subsystems at the moment of the anomaly. This eliminates the agent scavenger hunt of calling 5+ dump commands and manually correlating by timestamp.

### DiagnosticSnapshot

```kotlin
@Immutable
data class DiagnosticSnapshot(
    val timestamp: Long,
    val trigger: AnomalyTrigger,
    val ringBufferTail: ImmutableList<LogEntry>,
    val metricsSnapshot: MetricsSnapshot,
    val thermalState: ThermalState,
    val widgetHealth: ImmutableMap<String, WidgetHealthStatus>,
    val activeTraces: ImmutableList<SpanSummary>,
    val agenticTraceId: String?,
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

    data class BindingStalled(
        val widgetId: String,
        val typeId: String,
        val bindingDurationMs: Long,
        val firstEmissionTimeoutMs: Long,
    ) : AnomalyTrigger

    data class DataStoreCorruption(
        val fileName: String,
        val fallbackApplied: Boolean,
    ) : AnomalyTrigger
}
```

### Trigger Conditions

| Trigger | Condition | Auto-action |
|---|---|---|
| Widget crash | `WidgetSlot` error boundary triggered | Capture full snapshot, persist to file |
| ANR | `AnrWatchdog` timeout fired | Capture, persist to SharedPreferences + `anr_latest.json` (survives process death) |
| Thermal escalation | `ThermalManager` → DEGRADED or CRITICAL | Capture, persist to file (thermal rotation pool) |
| Jank spike | `JankDetector`: ≥5 consecutive frames >16ms | Capture, include worst-recomposing widget |
| Provider timeout | `firstEmissionTimeout` exceeded | Partial capture (affected widget only) |
| Escalated staleness | Widget data freshness exceeds 3x staleness threshold while provider reports CONNECTED | Capture, include binding lifecycle events |
| Binding stall | Binding active >2x `firstEmissionTimeout` and `widgetData` still `Empty` | Capture, include `combine()` upstream status |
| DataStore corruption | `ReplaceFileCorruptionHandler` invoked | Capture, crash rotation pool (data loss event) |

### DiagnosticSnapshotCapture

```kotlin
class DiagnosticSnapshotCapture @Inject constructor(
    private val ringBufferSink: RingBufferSink,
    private val metricsCollector: MetricsCollector,
    private val thermalManager: ThermalManager,
    private val widgetHealthMonitor: WidgetHealthMonitor,
    private val tracer: DqxnTracer,
    private val logger: DqxnLogger,
) {
    private val capturing = AtomicBoolean(false)

    fun capture(trigger: AnomalyTrigger, agenticTraceId: String? = null): DiagnosticSnapshot? {
        if (!capturing.compareAndSet(false, true)) {
            logger.warn(LogTags.DIAGNOSTIC, { persistentMapOf("droppedTrigger" to (trigger::class.simpleName ?: "unknown")) }) {
                "Diagnostic capture already in progress, dropping ${trigger::class.simpleName}"
            }
            return null
        }
        return try {
            DiagnosticSnapshot(
                timestamp = SystemClock.elapsedRealtimeNanos(),
                trigger = trigger,
                ringBufferTail = ringBufferSink.tail(50).toImmutableList(),
                metricsSnapshot = metricsCollector.snapshot(),
                thermalState = thermalManager.currentState(),
                widgetHealth = widgetHealthMonitor.allStatuses(),
                activeTraces = tracer.activeSpans().toImmutableList(),
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
| Crash | `snap_crash_{timestamp}.json` | 20 | `WidgetCrash`, `AnrDetected`, `DataStoreCorruption` |
| Thermal | `snap_thermal_{timestamp}.json` | 10 | `ThermalEscalation` |
| Performance | `snap_perf_{timestamp}.json` | 10 | `JankSpike`, `ProviderTimeout`, `EscalatedStaleness`, `BindingStalled` |

Separate pools prevent frequent thermal oscillation (common in vehicles) from evicting crash snapshots. Agent pulls via `adb pull`. The `diagnose-crash` agentic command returns the most recent snapshot for a given widget (see [build-system.md](build-system.md#compound-diagnostic-commands)).

**Storage pressure handling**: Before writing a diagnostic file, check `StatFs(diagnosticsDir).availableBytes`. If available space is below 10MB, skip the file write and log a warning via `DqxnLogger` at `WARN` level with `LogTags.DIAGNOSTIC`. `list-diagnostics` verifies file existence before including entries in results — stale metadata from failed writes is filtered out.

Release builds: only the `AnomalyTrigger` type and timestamp are forwarded to `CrashReporter` as custom keys. `agenticTraceId` is always `null` in release — the agentic framework is debug-only. No full diagnostic dump in production.

### Interaction Event Logging

UI interaction events (tap, move, resize, navigation) are logged as structured `LogEntry` items into the standard `RingBufferSink` pipeline. No separate capture session or ring buffer required — the existing `RingBufferSink` (512 entries) captures these alongside binding events, state transitions, and other breadcrumbs. They appear automatically in `DiagnosticSnapshot.ringBufferTail` and `JsonFileLogSink`.

```kotlin
// Logged by DashboardGrid / EditModeCoordinator / OverlayNavHost
logger.info(LogTags.INTERACTION, { persistentMapOf("widgetId" to widgetId, "action" to "TAP") }) { "Widget tapped" }
logger.info(LogTags.INTERACTION, { persistentMapOf("widgetId" to widgetId, "from" to from, "to" to to) }) { "Widget moved" }
logger.info(LogTags.INTERACTION, { persistentMapOf("widgetId" to widgetId, "action" to "RESIZE") }) { "Widget resized" }
logger.info(LogTags.NAVIGATION, { persistentMapOf("route" to route) }) { "Overlay navigated" }
```

No `capture-start`/`capture-stop` commands needed — interaction events are always captured as part of the standard log stream. `diagnose-widget` log tail filtering by `widgetId` naturally surfaces recent interactions for that widget.

### Binding Lifecycle Events

`WidgetBindingCoordinator` logs all binding state transitions at `INFO` level using `LogTags.BINDING`. These entries are never sampled — always captured by `RingBufferSink`, ensuring `diagnose-widget` log tail contains the binding history that led to the current state.

**Required fields on all binding log entries**: `widgetId` and `traceId` in `LogEntry.fields`, making them filterable by the `diagnose-widget` command's log tail query. The `traceId` field enables correlation back to the originating agentic command (if any) or user action.

The specific events and their field schemas are defined by the `WidgetBindingCoordinator` implementation. At minimum, the following transitions must be logged: binding started, binding cancelled/failed, provider fallback activation, and first data emission received.

These events are the primary diagnostic data source for binding-related issues. Without them, `diagnose-widget` can report current state but not the *sequence of transitions* that led to it — which is where most binding bugs manifest.

### Agentic Trace Correlation

When an agentic command triggers a `DashboardCommand`, the `AgenticContentProvider` attaches a trace ID:

```kotlin
// In AgenticContentProvider.call()
val traceId = "agentic-${SystemClock.elapsedRealtimeNanos()}"
// traceId is passed to handler via TypedParams, then attached to DashboardCommand
val params = parseParams(arg, traceId)
val result = router.route(method, params)
// Handler attaches traceId to DashboardCommand:
// commandChannel.send(DashboardCommand.AddWidget(typeId = typeId, traceId = params.traceId))
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
    @Binds @Singleton abstract fun errorReporter(impl: FirebaseErrorReporter): ErrorReporter
    @Binds @Singleton abstract fun analytics(impl: FirebaseAnalyticsTracker): AnalyticsTracker
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
    override fun setKey(key: String, value: String) { crashlytics.setCustomKey(key, value) }
    override fun setKey(key: String, value: Int) { crashlytics.setCustomKey(key, value) }
    override fun setKey(key: String, value: Float) { crashlytics.setCustomKey(key, value) }
    override fun setKey(key: String, value: Boolean) { crashlytics.setCustomKey(key, value) }
}
```

### Firebase Performance Monitoring

Firebase Performance used directly in `:core:firebase` for v1 — no abstraction layer. Traces are instrumented at the call site within `:core:firebase`:

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

When a second module needs performance tracing, extract `PerformanceTracer` / `PerfTrace` / `HttpMetric` interfaces into `:sdk:observability` and move implementations behind `@Binds`.
