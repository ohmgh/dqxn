# Phase 3: SDK Observability + Analytics + UI - Research

**Researched:** 2026-02-24
**Domain:** Structured logging, metrics collection, anomaly detection, analytics contracts, Compose UI primitives
**Confidence:** HIGH

## Summary

Phase 3 builds three SDK modules: `:sdk:observability` (greenfield -- zero prior implementation), `:sdk:analytics` (contract interfaces only), and `:sdk:ui` (partially ported from old `core:widget-primitives`). The observability module is the largest and most technically demanding -- it defines ~15 public types across logging, tracing, metrics, health monitoring, crash evidence, ANR detection, and anomaly auto-capture. All of these must be lock-free, zero-allocation on disabled paths, and self-protecting (observability must never crash the app).

`:sdk:analytics` is straightforward contract work: `AnalyticsTracker` interface, `AnalyticsEvent` sealed hierarchy, `PackAnalytics` scoped interface. No implementation -- that lives in `:core:firebase` (Phase 5).

`:sdk:ui` has Compose compiler enabled and contains `DashboardThemeDefinition` (6-token color model + semantic tokens), `WidgetContainer` skeleton, `InfoCardLayout` (536-line port from old codebase with weighted normalization), `LocalWidgetData`/`LocalWidgetScope` CompositionLocals, and the `EnumSetting.optionPreviews` replacement mechanism.

**Primary recommendation:** Build observability bottom-up: primitives (LogTag, LogEntry, LogSink, LongArrayRingBuffer) first, then composites (DqxnLogger, MetricsCollector, JankDetector), then high-level (DiagnosticSnapshotCapture, CrashEvidenceWriter, AnrWatchdog). Test each layer before composing. The InfoCardLayout port from old codebase requires careful study of the normalization math -- wrong weights cause text clipping in 5+ widgets.

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| F12.1 | Crash reporting integration (Firebase Crashlytics or equivalent) | `CrashReporter` and `ErrorReporter` interfaces defined in `:sdk:observability` with `ErrorContext` sealed hierarchy. Implementation deferred to `:core:firebase` (Phase 5). Interfaces must support `recordNonFatal`, `log`, `setKey`, `setUserId` |
| F13.5 | Structured state dumps: ADB-queryable JSON state dumps | `MetricsCollector.snapshot()`, `DiagnosticSnapshotCapture.capture()`, `WidgetHealthMonitor.allStatuses()` -- all produce serializable state dumps. Actual ADB wiring is Phase 6 (agentic), but the data shapes are defined here |
| F13.6 | Debug overlays: toggleable overlays for frame stats, recomposition visualization | `MetricsCollector` provides frame histogram, per-widget draw times, and recomposition counts. `JankDetector` provides consecutive jank frame count. Overlay UI rendering deferred to Phase 6/7, but data sources are Phase 3 |
| F13.7 | Machine-readable logs: JSON-lines file log sink (rotated 10MB, max 3 files) | `JsonLinesLogSink` (named `JsonFileLogSink` in arch doc) implementing `LogSink`. Rotation logic, debug-build gating, structured `LogEntry` format -- all Phase 3 |
| NF36 | Crash-free rate: target 99.5% users / 99.9% sessions | `CrashReporter` interface enables crash tracking. `CrashEvidenceWriter` provides sync crash persistence for safe mode. `ErrorReporter` with `DeduplicatingErrorReporter` prevents report flooding. `DiagnosticSnapshotCapture` with separate rotation pools (crash:20, thermal:10, perf:10) ensures crash evidence isn't evicted |
</phase_requirements>

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| kotlinx-collections-immutable | 0.3.8 | `ImmutableMap`/`ImmutableList` in `LogEntry`, `DiagnosticSnapshot`, all public API surfaces | Compose stability requirement -- mutable collections cause recomposition |
| kotlinx-coroutines-core | 1.10.2 | `AtomicLong`, `ConcurrentHashMap`, `CoroutineScope` in tracing | Already in version catalog, project standard |
| kotlinx-coroutines-android | 1.10.2 | `Dispatchers.IO` for async file writes in diagnostic persistence | Already in version catalog |
| Compose BOM | 2026.02.00 | `:sdk:ui` Compose types: `Brush`, `Color`, `TextStyle`, `CompositionLocal` | Project standard, managed via `dqxn.android.compose` convention plugin |
| Compose Material 3 | (BOM-managed) | `MaterialTheme` token access, `material-icons-extended` for icon resolution | Added by `AndroidComposeConventionPlugin` |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| JUnit 5 (Jupiter) | 5.11.4 (BOM) | Unit tests for all observability components | Added by `dqxn.android.test` convention plugin |
| MockK | 1.13.16 | Mocking `WidgetRegistry`, `DataProviderRegistry` in `MetricsCollector` tests | Added by `dqxn.android.test` |
| Truth | 1.4.4 | Assertions with `assertWithMessage()` | Added by `dqxn.android.test` |
| Turbine | 1.2.0 | Flow testing for `WidgetHealthMonitor`, `ProviderStatusProvider` | Added by `dqxn.android.test` |
| Robolectric | 4.16.1 | `SharedPreferences` in `CrashEvidenceWriter`, `Debug.isDebuggerConnected()` shadow in `AnrWatchdog` | Added by `dqxn.android.test` |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Custom `DqxnLogger` | Timber | Timber uses varargs (allocation), no structured data, no trace correlation, no tag-based sampling. DECISIONS.md explicitly rejects Timber |
| Custom `MetricsCollector` | Micrometer/Dropwizard Metrics | Massive dependency for 6 atomic counters. Custom is trivial and has no allocation overhead |
| Custom `LongArrayRingBuffer` | ArrayDeque | ArrayDeque boxes primitives. Ring buffer of `Long` must be primitive-backed for <25ns writes |
| Custom `AnrWatchdog` | ANR-Watchdog library | External library adds dependency for ~80 lines of code. Custom allows `DiagnosticSnapshotCapture` integration and `Debug.isDebuggerConnected()` guard |

## Architecture Patterns

### Recommended Module Structure

```
sdk/observability/src/main/kotlin/app/dqxn/android/sdk/observability/
  crash/
    CrashReporter.kt                    # Interface
    CrashEvidenceWriter.kt              # Sync SharedPreferences UncaughtExceptionHandler
    ErrorReporter.kt                    # Interface
    DeduplicatingErrorReporter.kt       # Decorator
    ErrorContext.kt                     # Sealed hierarchy
  log/
    DqxnLogger.kt                       # Interface + inline extensions
    LogEntry.kt                         # @Immutable data class
    LogTag.kt                           # @JvmInline value class
    LogTags.kt                          # Object with standard tags
    LogLevel.kt                         # Enum
    LogSink.kt                          # Interface
    SafeLogSink.kt                      # try/catch decorator
    RingBufferSink.kt                   # Lock-free AtomicReferenceArray
    LogcatSink.kt                       # Debug builds
    JsonLinesLogSink.kt                 # File rotation, debug builds
    RedactingSink.kt                    # GPS/BLE MAC scrubbing
    SamplingLogSink.kt                  # Per-tag rate limiting
  trace/
    TraceContext.kt                     # CoroutineContext element
    DqxnTracer.kt                      # withSpan() with span registry
  metrics/
    MetricsCollector.kt                 # AtomicLongArray + ConcurrentHashMap
    MetricsSnapshot.kt                  # Read-only copy
    LongArrayRingBuffer.kt             # Primitive ring buffer
    JankDetector.kt                     # Consecutive frame tracking
  health/
    WidgetHealthMonitor.kt             # Periodic liveness checks
    ProviderStatusProvider.kt          # Interface for diagnostics UI
    AnrWatchdog.kt                     # Daemon thread + CountDownLatch
  diagnostic/
    DiagnosticSnapshotCapture.kt       # AtomicBoolean capture guard
    DiagnosticSnapshot.kt              # @Immutable correlated state
    AnomalyTrigger.kt                  # Sealed interface hierarchy
    DiagnosticFileWriter.kt            # Rotation pools, storage pressure

sdk/analytics/src/main/kotlin/app/dqxn/android/sdk/analytics/
  AnalyticsTracker.kt                   # Interface: track(event), setUserProperty
  AnalyticsEvent.kt                     # Sealed interface hierarchy
  PackAnalytics.kt                      # Scoped per-pack interface

sdk/ui/src/main/kotlin/app/dqxn/android/sdk/ui/
  theme/
    DashboardThemeDefinition.kt         # @Immutable, 6 color tokens + semantic tokens
    ThemeProvider.kt                    # Interface (if not in :sdk:contracts)
    DefaultTheme.kt                     # Fallback Cyberpunk theme
  widget/
    WidgetContainer.kt                  # Skeleton: error boundary, graphicsLayer, background, border, rim
    LocalWidgetData.kt                  # CompositionLocal
    LocalWidgetScope.kt                 # CompositionLocal for supervised CoroutineScope
    GridConstants.kt                    # GRID_UNIT_SIZE = 16.dp
  layout/
    InfoCardLayout.kt                   # Port from old codebase, 536 lines
    InfoCardSettings.kt                 # Settings helper (parseLayoutMode, etc.)
  settings/
    EnumSettingPreviewRegistry.kt       # Extension/registry pattern for option previews
  icon/
    IconResolver.kt                     # iconName: String -> ImageVector
```

### Pattern 1: Zero-Allocation Logging (Inline + Branch Guard)

**What:** Inline extension functions on `DqxnLogger` that check `isEnabled()` before evaluating the message lambda. When logging is disabled, the entire call compiles to a single branch instruction -- no lambda allocation, no string concatenation, no `ImmutableMap` creation.

**When to use:** Every logging call site in the entire codebase.

**Critical detail:** The `isEnabled(level, tag)` method must be non-virtual and fast (<5ns). The tag-based filtering in `SamplingLogSink` keys on `LogTag.value` without registration -- packs define their own tags freely.

```kotlin
// Source: .planning/arch/observability.md
@JvmInline
value class LogTag(val value: String)

interface DqxnLogger {
    fun isEnabled(level: LogLevel, tag: LogTag): Boolean
    fun log(level: LogLevel, tag: LogTag, message: String, throwable: Throwable? = null, fields: ImmutableMap<String, Any> = persistentMapOf())
}

inline fun DqxnLogger.debug(tag: LogTag, message: () -> String) {
    if (isEnabled(LogLevel.DEBUG, tag)) {
        log(LogLevel.DEBUG, tag, message())
    }
}

inline fun DqxnLogger.debug(tag: LogTag, crossinline fields: () -> ImmutableMap<String, Any>, message: () -> String) {
    if (isEnabled(LogLevel.DEBUG, tag)) {
        log(LogLevel.DEBUG, tag, message(), fields = fields())
    }
}
```

### Pattern 2: Lock-Free Ring Buffer

**What:** `LongArrayRingBuffer` backed by a primitive `LongArray` with `AtomicInteger` write index. No synchronization, no boxing. `RingBufferSink` uses `AtomicReferenceArray` for `LogEntry` objects.

**When to use:** All high-frequency data paths: per-widget draw times, per-provider latency, frame histogram, log entries.

**Critical detail:** The `LongArrayRingBuffer` uses modular arithmetic (`index.getAndIncrement() % capacity`) for wrap-around. Reads are eventually consistent -- a concurrent read during write may see stale data for one slot, which is acceptable for metrics.

```kotlin
class LongArrayRingBuffer(private val capacity: Int) {
    private val buffer = LongArray(capacity)
    private val writeIndex = AtomicInteger(0)

    fun add(value: Long) {
        val idx = writeIndex.getAndIncrement() % capacity
        buffer[idx] = value
    }

    fun toList(): List<Long> {
        val size = minOf(writeIndex.get(), capacity)
        val start = if (writeIndex.get() > capacity) writeIndex.get() % capacity else 0
        return (0 until size).map { buffer[(start + it) % capacity] }
    }
}
```

### Pattern 3: Self-Protecting Observability

**What:** Every `LogSink.write()` call wrapped in try/catch. The observability system must never crash the app. `SafeLogSink` decorator pattern.

**When to use:** Every sink in the pipeline, every method in `DiagnosticSnapshotCapture`, `CrashEvidenceWriter`, `AnrWatchdog`.

```kotlin
class SafeLogSink(private val delegate: LogSink) : LogSink {
    override fun write(entry: LogEntry) {
        try { delegate.write(entry) } catch (_: Exception) { }
    }
}
```

### Pattern 4: Concurrent Capture Guard

**What:** `AtomicBoolean` in `DiagnosticSnapshotCapture` prevents concurrent captures. If a second anomaly fires while the first capture is in progress, it's dropped with a warning log.

**When to use:** `DiagnosticSnapshotCapture.capture()` only.

```kotlin
private val capturing = AtomicBoolean(false)

fun capture(trigger: AnomalyTrigger, agenticTraceId: String? = null): DiagnosticSnapshot? {
    if (!capturing.compareAndSet(false, true)) {
        logger.warn(LogTags.DIAGNOSTIC, ...) { "Capture already in progress, dropping..." }
        return null
    }
    return try {
        DiagnosticSnapshot(...)
    } finally {
        capturing.set(false)
    }
}
```

### Pattern 5: DashboardThemeDefinition Color Token Model

**What:** 6 named color tokens per theme (from old codebase) plus 3 new semantic tokens for error/warning/success states. The old codebase uses hardcoded colors for these states -- the new arch fixes this gap.

**When to use:** All theme rendering. `DashboardThemeDefinition` lives in `:sdk:ui` (not `:core:design`) because packs implement `ThemeProvider` returning this type, and packs can only depend on `:sdk:*`.

```kotlin
// Source: .planning/migration/replication-advisory.md section 5
@Immutable
data class DashboardThemeDefinition(
    val id: String,
    val name: String,
    // 6 core tokens (from old codebase)
    val primaryTextColor: Color,
    val secondaryTextColor: Color,
    val accentColor: Color,
    val highlightColor: Color,           // defaults to accentColor if unset
    val widgetBorderColor: Color,
    val backgroundBrush: Brush,
    val widgetBackgroundBrush: Brush,
    // 3 new semantic tokens (gap fix)
    val errorColor: Color,
    val warningColor: Color,
    val successColor: Color,
    // Metadata
    val isDark: Boolean = true,
    val defaultBackgroundStyle: BackgroundStyle = BackgroundStyle.SOLID,
    val defaultHasGlowEffect: Boolean = false,
    val requiredAnyEntitlement: Set<String>? = null,
    // Serialization support (for JSON theme loading)
    val backgroundGradientSpec: GradientSpec? = null,
    val widgetBackgroundGradientSpec: GradientSpec? = null,
) : ThemeSpec
```

### Pattern 6: InfoCardLayout Weighted Normalization

**What:** Deterministic layout algorithm that distributes available space among icon, top text, and bottom text based on `SizeOption` weights. Three modes: STACK (vertical), GRID (icon left, text right), COMPACT (icon+title inline, body below).

**When to use:** Port from old codebase (`core:widget-primitives`, ~536 lines). Used by 5+ widgets in Phase 8 (Ambient Light, Battery, Shortcuts, etc.).

**Critical detail:** `SizeOption.toMultiplier()` maps `NONE=0.0, SMALL=0.3, MEDIUM=0.5, LARGE=0.7, XL=1.0`. Normalization targets 80% of available height (20% safety buffer). Fixed spacers subtracted before normalization. Getting the weights wrong causes text clipping.

### Anti-Patterns to Avoid

- **Allocating in disabled log paths:** The entire purpose of inline extensions is zero-allocation when disabled. Never create a `persistentMapOf()` or format a string outside the `isEnabled()` guard.
- **Using `synchronized` in metrics recording:** `MetricsCollector.recordFrame()` must complete in <25ns. Use `AtomicLong.incrementAndGet()` and primitive ring buffers, never `synchronized` blocks.
- **Storing `Throwable` in long-lived collections:** `DiagnosticSnapshot.trigger` can contain `Throwable` references that hold onto large stack traces. The snapshot file writer must serialize the stack trace as a string and release the reference.
- **Using `Dispatchers.IO` in crash/ANR paths:** `CrashEvidenceWriter` uses `SharedPreferences.commit()` (synchronous). `AnrWatchdog.writeAnrFile()` uses direct `FileOutputStream` on the watchdog thread. Both must complete before process death -- coroutine dispatch may never execute.
- **Putting Compose types in `:sdk:observability` or `:sdk:analytics`:** These modules do NOT have the Compose compiler. Only `:sdk:ui` has `dqxn.android.compose`. `@Immutable` annotation is available via `compileOnly(compose.runtime)` in `:sdk:contracts` transitively, but no composable function bodies.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| JSON serialization for log entries | Custom string builder | `kotlinx.serialization-json` | Edge cases: escaping, Unicode, nested objects. Already in version catalog |
| Thread-safe counters | `synchronized` blocks | `AtomicLong`, `AtomicLongArray`, `AtomicInteger` | Lock-free is required for <25ns budget |
| Concurrent maps | Manual locking | `ConcurrentHashMap` | Java stdlib, battle-tested |
| File rotation logic | Custom rotation | Standard pattern: write to temp, rename, delete oldest | Keep it simple but don't skip atomic rename |
| Icon resolution | Manual Material icon mapping | Reflection on `Icons.Default`/`Icons.Rounded` | Material icon set has ~2000 icons. Reflection with caching is standard approach |

**Key insight:** The observability module has extreme performance constraints (<5ns disabled log, <25ns metrics record, <5ms capture). Custom solutions are appropriate here because external libraries add allocation overhead. But for non-hot-path code (file writing, JSON serialization, test utilities), use existing libraries.

## Common Pitfalls

### Pitfall 1: Inline Extension Verification

**What goes wrong:** The `inline` keyword is on the extension function, but the JVM inlines it at the call site. If the `isEnabled()` method is not fast, every call site pays the cost even when disabled.
**Why it happens:** `isEnabled()` implementation does tag filtering, which could involve map lookups.
**How to avoid:** Keep `isEnabled()` as a simple boolean flag check for the fast path (level >= minimum). Tag-based filtering only happens in sinks, not in the `isEnabled()` guard. Test with a benchmark: disabled log call must be <5ns.
**Warning signs:** Allocation profiler shows `ImmutableMap` or string objects created at log call sites.

### Pitfall 2: SharedPreferences Thread Safety in CrashEvidenceWriter

**What goes wrong:** `SharedPreferences.commit()` is documented as synchronous but can still throw if the file system is corrupted or full.
**Why it happens:** Crash paths run in extreme conditions -- process may be dying, disk may be full.
**How to avoid:** Wrap the entire `uncaughtException` body in try/catch. The observability system must never interfere with the default uncaught exception handler. Always call `delegate?.uncaughtException(t, e)` in a finally block.
**Warning signs:** Missing crash reports in Crashlytics after process-death crashes.

### Pitfall 3: AnrWatchdog False Positives Under Debugger

**What goes wrong:** ANR detection fires during debugging sessions when breakpoints pause the main thread.
**Why it happens:** `CountDownLatch.await()` times out because the main thread is paused by the debugger.
**How to avoid:** Check `Debug.isDebuggerConnected()` before incrementing the consecutive miss counter. Reset counter and sleep on debugger detection.
**Warning signs:** ANR files generated during debugging sessions.

### Pitfall 4: DiagnosticSnapshot Rotation Pool Confusion

**What goes wrong:** Thermal oscillation (common in vehicles) rapidly fills a single rotation pool, evicting crash snapshots before the agent retrieves them.
**Why it happens:** A single pool with 40 files treats all anomalies equally, but thermal events are ~10x more frequent than crashes.
**How to avoid:** Three separate pools: crash (20 files), thermal (10 files), performance (10 files). Each pool has its own file prefix and eviction policy.
**Warning signs:** `diagnose-crash` returns empty results even though a crash occurred.

### Pitfall 5: MetricsCollector Late Registration

**What goes wrong:** A widget or provider registered after `MetricsCollector` construction has no pre-allocated entry in the `ConcurrentHashMap`.
**Why it happens:** `MetricsCollector` pre-populates from `WidgetRegistry` and `DataProviderRegistry` at construction, but dynamic registration is possible.
**How to avoid:** Use `getOrPut` at recording sites -- pre-population is an optimization, not a hard requirement.
**Warning signs:** `NullPointerException` when recording metrics for a late-registered widget.

### Pitfall 6: InfoCardLayout Normalization Edge Cases

**What goes wrong:** Text clips when `SizeOption.NONE` is used for one element, or when all elements are `XL` in a small widget.
**Why it happens:** Normalization distributes available space by weight. Zero weight means zero space. All-XL means equal distribution which may still not fit.
**How to avoid:** Test every combination of `SizeOption` values across all three layout modes. The old codebase's 80% target provides a 20% safety buffer for font leading/descenders. `getTightTextStyle()` eliminates font padding. Verify boundary cases in unit tests.
**Warning signs:** Text truncation or overflow in info card widgets at certain size combinations.

### Pitfall 7: EnumSetting.optionPreviews Replacement

**What goes wrong:** The old codebase had `optionPreviews: (@Composable (E) -> Unit)?` directly on `EnumSetting`. This was stripped in Phase 2 because `:sdk:contracts` has no Compose compiler. Without a replacement, packs can't provide visual previews for enum options.
**Why it happens:** Compose compiler scope mismatch between contracts (no compiler) and UI (has compiler).
**How to avoid:** Define an `EnumPreviewRegistry` in `:sdk:ui` that packs register preview composables into via Hilt multibinding or a `Map<KClass<*>, @Composable (Any) -> Unit>` pattern. The settings UI queries this registry when rendering `EnumSetting` options.
**Warning signs:** Settings UI shows only text labels for enum options that should have visual previews (like `InfoCardLayoutMode`).

## Code Examples

### DqxnLogger Implementation Pattern

```kotlin
// Source: .planning/arch/observability.md
class DqxnLoggerImpl(
    private val sinks: List<LogSink>,
    private val minimumLevel: LogLevel = LogLevel.DEBUG,
    private val sessionId: String,
) : DqxnLogger {

    override fun isEnabled(level: LogLevel, tag: LogTag): Boolean =
        level.ordinal >= minimumLevel.ordinal

    override fun log(
        level: LogLevel,
        tag: LogTag,
        message: String,
        throwable: Throwable?,
        fields: ImmutableMap<String, Any>,
    ) {
        val entry = LogEntry(
            timestamp = SystemClock.elapsedRealtimeNanos(),
            level = level,
            tag = tag,
            message = message,
            throwable = throwable,
            traceId = currentCoroutineContext()[TraceContext]?.traceId,
            spanId = currentCoroutineContext()[TraceContext]?.spanId,
            fields = fields,
            sessionId = sessionId,
        )
        sinks.forEach { sink ->
            try { sink.write(entry) } catch (_: Exception) { }
        }
    }
}
```

**Note:** The `log()` method reads `currentCoroutineContext()` -- this is only valid when called from a coroutine. For non-coroutine call sites (like `AnrWatchdog`'s daemon thread), traceId/spanId will be null. This is acceptable behavior.

### MetricsCollector Frame Histogram

```kotlin
// Source: .planning/arch/observability.md
class MetricsCollector @Inject constructor(
    widgetRegistry: WidgetRegistry,
    providerRegistry: DataProviderRegistry,
) {
    // <8ms, <12ms, <16ms, <24ms, <33ms, >33ms
    private val frameHistogram = AtomicLongArray(6)
    private val totalFrameCount = AtomicLong(0)

    fun recordFrame(durationMs: Long) {
        totalFrameCount.incrementAndGet()
        val bucket = when {
            durationMs < 8 -> 0
            durationMs < 12 -> 1
            durationMs < 16 -> 2
            durationMs < 24 -> 3
            durationMs < 33 -> 4
            else -> 5
        }
        frameHistogram.incrementAndGet(bucket)
    }

    fun snapshot(): MetricsSnapshot = MetricsSnapshot(
        frameHistogram = (0 until 6).map { frameHistogram.get(it) },
        totalFrameCount = totalFrameCount.get(),
        // ... other metrics
    )
}
```

### CrashEvidenceWriter

```kotlin
// Source: .planning/arch/observability.md
class CrashEvidenceWriter @Inject constructor(
    private val prefs: SharedPreferences,
) : Thread.UncaughtExceptionHandler {
    private val delegate = Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(t: Thread, e: Throwable) {
        try {
            prefs.edit().apply {
                putString("last_crash_type_id", extractWidgetTypeId(e))
                putString("last_crash_exception", "${e::class.simpleName}: ${e.message}")
                putString("last_crash_stack_top5", e.stackTrace.take(5).joinToString("\n"))
                putLong("last_crash_timestamp", System.currentTimeMillis())
            }.commit() // MUST be commit(), not apply() -- process may die
        } catch (_: Exception) { /* must not interfere */ }
        delegate?.uncaughtException(t, e)
    }

    internal fun extractWidgetTypeId(e: Throwable): String? {
        // Walk exception chain looking for widget context
        var cause: Throwable? = e
        while (cause != null) {
            // Convention: widget crashes are wrapped with a message containing typeId
            val match = WIDGET_TYPE_REGEX.find(cause.message ?: "")
            if (match != null) return match.groupValues[1]
            cause = cause.cause
        }
        return null
    }
}
```

### WidgetContainer Skeleton

```kotlin
// Source: .planning/arch/compose-performance.md + old codebase core:widget-primitives
@Composable
fun WidgetContainer(
    style: WidgetStyle,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    warningColor: Color? = null,
    content: @Composable BoxWithConstraintsScope.() -> Unit,
) {
    val theme = LocalDashboardTheme.current
    val cornerRadius = style.cornerRadiusPercent / 2f // 0-100 maps to 0-50% shape

    BoxWithConstraints(
        modifier = modifier
            .graphicsLayer { alpha = style.opacity }
            .semantics { contentDescription?.let { this.contentDescription = it } }
            .clip(RoundedCornerShape(cornerRadius.percent))
            .then(
                if (style.showBorder) {
                    Modifier.border(2.dp, theme.widgetBorderColor, RoundedCornerShape(cornerRadius.percent))
                } else Modifier
            )
            .background(
                when (style.backgroundStyle) {
                    BackgroundStyle.SOLID -> theme.widgetBackgroundBrush
                    BackgroundStyle.TRANSPARENT -> Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                }
            ),
    ) {
        // Rim padding
        val rimPx = (minOf(maxWidth, maxHeight) * (style.rimSizePercent / 100f)).roundToPx()
        Box(modifier = Modifier.padding(rimPx.dp)) {
            content()
        }
    }
    // Note: Glow rendering deferred to Phase 7 (RenderEffect.createBlurEffect)
    // Error boundary deferred to Phase 7 (WidgetSlot)
}
```

### AnalyticsTracker Contract

```kotlin
// Source: .planning/arch/observability.md
interface AnalyticsTracker {
    fun isEnabled(): Boolean
    fun setEnabled(enabled: Boolean)
    fun track(event: AnalyticsEvent)
    fun setUserProperty(key: String, value: String)
}

sealed interface AnalyticsEvent {
    val name: String
    val params: ImmutableMap<String, Any> get() = persistentMapOf()

    data object AppLaunch : AnalyticsEvent { override val name = "app_launch" }
    data class WidgetAdded(val typeId: String) : AnalyticsEvent {
        override val name = "widget_added"
        override val params = persistentMapOf<String, Any>("type_id" to typeId)
    }
    data class ThemeChanged(val themeId: String, val isDark: Boolean) : AnalyticsEvent {
        override val name = "theme_changed"
        override val params = persistentMapOf<String, Any>("theme_id" to themeId, "is_dark" to isDark)
    }
    data class UpsellImpression(val trigger: String, val packId: String) : AnalyticsEvent {
        override val name = "upsell_impression"
        override val params = persistentMapOf<String, Any>("trigger_source" to trigger, "pack_id" to packId)
    }
    // ... other events per F12.2, F12.3, F12.6, F12.7
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `BlurMaskFilter` for glow | `RenderEffect.createBlurEffect()` | minSdk 31 availability | GPU shader, zero offscreen allocation. Phase 7 wiring |
| `android.util.Log` (old codebase) | Structured `DqxnLogger` with sinks | New arch (greenfield) | Zero-allocation disabled path, trace correlation, machine-readable output |
| `Map<String, Any?>` DataSnapshot | Typed `@DashboardSnapshot` subtypes | Phase 2 contracts redesign | Eliminates boxing, enables compiler-enforced type safety |
| `EnumSetting.optionPreviews` as lambda on contract | Registry/extension in `:sdk:ui` | Phase 3 contract cleanup | Removes Compose compiler dependency from `:sdk:contracts` |

**Deprecated/outdated:**
- `Timber` -- rejected per DECISIONS.md (varargs allocation, no structured data)
- `BlurMaskFilter` -- replaced by `RenderEffect` (GPU shader)
- `DQXNDispatchers` interface -- dropped, using qualifier annotations only

## Open Questions

1. **`DqxnLogger.log()` and coroutine context access**
   - What we know: The `log()` method wants to read `TraceContext` from `currentCoroutineContext()`, but `log()` is not a suspend function
   - What's unclear: How to access coroutine context from a non-suspend inline extension
   - Recommendation: Make `log()` accept optional `traceId`/`spanId` parameters. The inline extensions read from `currentCoroutineContext()` only when called from a suspend function (via `suspend inline fun` overloads). Non-suspend call sites pass null. This avoids the `runBlocking` trap. Alternatively, use `TraceContext` as a thread-local fallback for non-coroutine contexts.

2. **`MetricsCollector` constructor dependency on Phase 2 registries**
   - What we know: `MetricsCollector` pre-populates from `WidgetRegistry` and `DataProviderRegistry` at construction per the arch doc
   - What's unclear: Phase 2 may not have completed yet (STATE.md shows Phase 2 as Pending)
   - Recommendation: Phase 3 defines `MetricsCollector` with constructor parameters that accept `WidgetRegistry` and `DataProviderRegistry` interfaces. Phase 3 tests use fake/mock implementations. If Phase 2 hasn't shipped the interfaces yet, declare minimal stubs (`interface WidgetRegistry { fun all(): List<WidgetTypeInfo> }`) in `:sdk:contracts` during Phase 2, or use raw constructor parameters that accept `Set<String>` (type IDs) instead.

3. **`WidgetHealthMonitor` periodic check mechanism**
   - What we know: Checks every 10s for stale data and stalled renders
   - What's unclear: Whether to use `CoroutineScope` with `delay()` loop, `Handler.postDelayed()`, or `ScheduledExecutorService`
   - Recommendation: `CoroutineScope` with `while(true) { delay(10_000); check() }` on `Dispatchers.Default`. Consistent with the project's coroutine-first approach. The scope is supervised and injected.

4. **`EnumSettingPreviewRegistry` exact API shape**
   - What we know: Packs need to register preview composables for enum setting options. Old code had a `@Composable` lambda directly on `EnumSetting`
   - What's unclear: Whether to use Hilt multibinding (`Map<KClass<*>, @Composable (Any) -> Unit>`) or a simpler extension function pattern
   - Recommendation: Start with a `Map<KClass<out Enum<*>>, @Composable (Enum<*>) -> Unit>` injected via Hilt `@IntoMap`. Packs contribute preview composables keyed by enum class. The settings UI queries this map when rendering. If the map is empty for a given enum, fall back to text-only rendering.

## Dependency Notes

### What Phase 3 Depends On (from Phase 2)

Phase 3 imports from `:sdk:contracts` (Phase 2):
- `WidgetRegistry` interface (for `MetricsCollector` pre-population)
- `DataProviderRegistry` interface (for `MetricsCollector` pre-population)
- `DataSnapshot` interface (for `AnomalyTrigger.WidgetCrash.lastSnapshot`)
- `WidgetStyle`, `BackgroundStyle` (for `WidgetContainer`)
- `ThemeSpec` interface (for `DashboardThemeDefinition`)
- `SettingDefinition<*>` (for `EnumSetting` preview registry)
- `GradientSpec` (for theme serialization support)
- `InfoCardLayoutMode`, `SizeOption` enums (for `InfoCardLayout`)

Phase 3 imports from `:sdk:common` (Phase 2):
- `@ApplicationScope`, dispatcher qualifiers
- `AppResult`, `AppError` (for error reporting context)

**If Phase 2 is incomplete:** Phase 3 can still proceed by defining minimal interface stubs in the build files and referencing the architecture docs for type shapes. However, types defined in Phase 2 should not be duplicated -- the planner must sequence Phase 2 completion before Phase 3 begins or ensure the required interfaces exist.

### What Depends on Phase 3

- `:core:thermal` (Phase 5) depends on `:sdk:observability` for `DqxnLogger`, `MetricsCollector`
- `:core:firebase` (Phase 5) implements `CrashReporter`, `ErrorReporter`, `AnalyticsTracker`
- `:core:agentic` (Phase 6) queries `MetricsCollector`, `WidgetHealthMonitor`, `DiagnosticSnapshotCapture`
- `:feature:dashboard` (Phase 7) uses `LocalWidgetData`, `LocalWidgetScope`, `WidgetContainer`, `DashboardThemeDefinition`
- `:feature:diagnostics` (Phase 11) consumes `ProviderStatusProvider`
- `:pack:essentials` (Phase 8) uses `InfoCardLayout`, `DashboardThemeDefinition`, `LocalWidgetData`
- Every module uses `DqxnLogger` (from `:sdk:observability`)

## Sources

### Primary (HIGH confidence)
- `.planning/arch/observability.md` -- full observability architecture: interfaces, code samples, performance budgets
- `.planning/arch/compose-performance.md` -- WidgetContainer, graphicsLayer isolation, state deferral patterns
- `.planning/arch/plugin-system.md` -- widget/provider contracts, theme system, error isolation
- `.planning/arch/testing.md` -- test infrastructure, observability self-tests, framework choices
- `.planning/migration/phase-03.md` -- phase-specific implementation details, test specifications
- `.planning/migration/replication-advisory.md` section 5 -- 6-token color model, typography scale, emphasis levels, spacing system
- `.planning/oldcodebase/core-libraries.md` -- InfoCardLayout, WidgetContainer, DashboardThemeDefinition old implementations
- `.planning/DECISIONS.md` -- rejected alternatives (Timber, BlurMaskFilter, etc.)
- `.planning/REQUIREMENTS.md` -- F12.1, F13.5, F13.6, F13.7, NF36 requirement text

### Secondary (MEDIUM confidence)
- Version catalog (`libs.versions.toml`) -- verified library versions
- Convention plugin source -- verified build plugin behavior

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH -- all libraries are already in the version catalog, no new dependencies needed
- Architecture: HIGH -- exhaustive architecture docs with code samples, performance budgets, and anti-patterns
- Pitfalls: HIGH -- derived from architecture docs and DECISIONS.md rationale
- InfoCardLayout port: MEDIUM -- old codebase mapping exists but the actual 536-line source needs study during implementation. Normalization math is documented at concept level, not line-by-line

**Research date:** 2026-02-24
**Valid until:** 2026-03-24 (stable domain -- no external dependency churn expected)
