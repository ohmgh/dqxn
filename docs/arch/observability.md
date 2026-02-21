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

### LogTag Enum

```kotlin
enum class LogTag {
    LAYOUT, THEME, SENSOR, BLE, CONNECTION_FSM,
    DATASTORE, THERMAL, BINDING, EDIT_MODE,
    ENTITLEMENT, DRIVING, NAVIGATION, STARTUP,
    WIDGET_RENDER, PROVIDER, PRESET, ANALYTICS,
}
```

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
    suspend inline fun <T> withSpan(
        name: String,
        tag: LogTag,
        crossinline block: suspend () -> T,
    ): T {
        val parent = currentCoroutineContext()[TraceContext]
        val ctx = TraceContext(
            traceId = parent?.traceId ?: generateTraceId(),
            spanId = generateSpanId(),
            parentSpanId = parent?.spanId,
        )
        return withContext(ctx) {
            val start = SystemClock.elapsedRealtimeNanos()
            try { block() } finally {
                val elapsed = SystemClock.elapsedRealtimeNanos() - start
                logger.debug(tag, "span" to name, "elapsedMs" to elapsed / 1_000_000) { name }
            }
        }
    }
}
```

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

                    logger.error(LogTag.STARTUP, "fdCount" to fdCount) {
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
