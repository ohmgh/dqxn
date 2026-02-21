# State Management

> Decomposed state architecture, coordinator patterns, data binding, and lifecycle management.

## Decomposed State Architecture

State is split into independent flows by update frequency and concern. This prevents recomposition storms where a 60Hz sensor update forces every widget to recompose.

```kotlin
// LayoutCoordinator — structural state (changes on user edit)
val layoutState: StateFlow<LayoutState>

data class LayoutState(
    val widgets: ImmutableList<DashboardWidgetInstance>,
    val isLoading: Boolean,
)

// EditModeCoordinator — UI mode (changes on user interaction)
val editState: StateFlow<EditState>

data class EditState(
    val isEditMode: Boolean,       // NOT saved to SavedStateHandle — see Process Death
    val focusedWidgetId: String?,
    val showStatusBar: Boolean,
)

// ThemeCoordinator — theme (changes on theme switch or auto-switch trigger)
val themeState: StateFlow<ThemeState>

data class ThemeState(
    val currentTheme: DashboardThemeDefinition,
    val darkTheme: DashboardThemeDefinition,
    val lightTheme: DashboardThemeDefinition,
    val autoSwitchMode: AutoSwitchMode,
    val previewTheme: DashboardThemeDefinition?,
) {
    val displayTheme: DashboardThemeDefinition
        get() = previewTheme ?: currentTheme
}

// WidgetBindingCoordinator — per-widget data (changes at sensor frequency)
// Each widget subscribes to its OWN flow — no shared map in a single state object
fun widgetData(widgetId: String): StateFlow<WidgetData>
fun widgetStatus(widgetId: String): StateFlow<WidgetStatusCache>
```

A single `DashboardState` containing all widget data means 60+ `.copy()` allocations per second and universal recomposition. With decomposed flows, each widget composable collects only `widgetData(myId)`. The speedometer doesn't recompose when the clock ticks.

## Split Event Channels

High-frequency positional updates (drag, resize) and discrete commands (add, remove, set theme) have fundamentally different delivery semantics:

```kotlin
// Discrete commands — serialized, ordered, transactional
private val commandChannel = Channel<DashboardCommand>(capacity = 64)

sealed interface DashboardCommand {
    val traceId: String? // propagated from sender's coroutine context for trace correlation
    data class AddWidget(val widget: DashboardWidgetInstance, override val traceId: String? = null) : DashboardCommand
    data class RemoveWidget(val widgetId: String, override val traceId: String? = null) : DashboardCommand
    data class SetTheme(val themeId: String, override val traceId: String? = null) : DashboardCommand
    data object EnterEditMode : DashboardCommand { override val traceId: String? = null }
    data object ExitEditMode : DashboardCommand { override val traceId: String? = null }
    data class FocusWidget(val widgetId: String?, override val traceId: String? = null) : DashboardCommand
    data class PreviewTheme(val theme: DashboardThemeDefinition?, override val traceId: String? = null) : DashboardCommand
    // ... all non-continuous operations
}

// Continuous gestures — conflated, latest-value-wins
private val _dragState = MutableStateFlow<DragUpdate?>(null)
val dragState: StateFlow<DragUpdate?> = _dragState.asStateFlow()

data class DragUpdate(
    val widgetId: String,
    val currentPosition: IntOffset,
    val isDragging: Boolean,
)

// Resize uses the same pattern
private val _resizeState = MutableStateFlow<ResizeUpdate?>(null)
val resizeState: StateFlow<ResizeUpdate?> = _resizeState.asStateFlow()
```

Discrete commands go through `commandChannel` for ordered processing. Drag/resize update a `MutableStateFlow` directly on the main thread — latest value wins, no queuing.

## Command Processing & Error Handling

All command processing happens on `Dispatchers.Main` via sequential `commandChannel` consumption. This guarantees thread-safe state mutation without locks:

```kotlin
for (command in commandChannel) {
    val ctx = command.traceId?.let { TraceContext(it, generateSpanId(), null) }
    try {
        withContext(ctx ?: EmptyCoroutineContext) {
            val start = SystemClock.elapsedRealtimeNanos()
            routeCommand(command)
            val elapsed = SystemClock.elapsedRealtimeNanos() - start
            if (elapsed > 1_000_000_000L) { // > 1s
                logger.warn(LogTag.LAYOUT, "command" to command::class.simpleName, "elapsedMs" to elapsed / 1_000_000) {
                    "Slow command handler"
                }
            }
            if (BuildConfig.DEBUG) {
                StrictMode.noteSlowCall("DashboardCommand: ${command::class.simpleName}")
            }
        }
    } catch (e: CancellationException) {
        throw e // don't swallow cancellation
    } catch (e: Exception) {
        logger.error(LogTag.LAYOUT, "command" to command::class.simpleName) { "Command handler failed" }
        errorReporter.reportNonFatal(e, ErrorContext.Coordinator(command))
    }
}
```

A coordinator throwing an unhandled exception must NOT kill the command processing loop. All disk I/O (DataStore reads/writes, Proto serialization) MUST run on `Dispatchers.IO`.

One-shot effects via `Channel<DashboardEffect>` (navigation triggers, toasts, haptics).

## Per-Widget Data Binding

Each widget gets a coroutine binding that:
1. Calls `WidgetDataBinder.bind(widget)` -> `StateFlow<WidgetData>`
2. Collects with `WhileSubscribed` — timeout is provider-specific (1s clock, 5s default, 30s GPS)
3. Exposes data via `widgetData(widgetId)` — individual `StateFlow` per widget

Bindings are managed as a `Map<String, Job>` in `WidgetBindingCoordinator` — cancelled on widget removal, created on add, rebound on data source change.

### SupervisorJob for Binding Isolation

All binding jobs are children of a `SupervisorJob` parented to the ViewModel scope:

```kotlin
class WidgetBindingCoordinator @Inject constructor(
    private val binder: WidgetDataBinder,
    @ViewModelCoroutineScope private val scope: CoroutineScope,
) {
    private val bindingSupervisor = SupervisorJob(scope.coroutineContext.job)
    private val bindingScope = scope + bindingSupervisor

    private val bindings = mutableMapOf<String, Job>()
    private val errorCounts = ConcurrentHashMap<String, AtomicInteger>()

    fun bind(widget: DashboardWidgetInstance) {
        bindings[widget.id]?.cancel()
        bindings[widget.id] = bindingScope.launch(
            CoroutineExceptionHandler { _, e ->
                logger.error(LogTag.SENSOR, "widgetTypeId" to widget.typeId, "widgetId" to widget.id) {
                    "Provider failed"
                }
                val attempts = errorCounts.getOrPut(widget.id) { AtomicInteger(0) }.incrementAndGet()
                if (attempts <= 3) {
                    bindingScope.launch {
                        delay(1000L * (1 shl (attempts - 1))) // 1s, 2s, 4s exponential backoff
                        bind(widget) // retry
                    }
                } else {
                    updateStatus(widget.id, WidgetStatusCache.ProviderError(e, retriesExhausted = true))
                }
                errorReporter.reportNonFatal(e, widgetContext(widget))
            }
        ) {
            binder.bind(widget).collect { data ->
                errorCounts[widget.id]?.set(0) // reset on successful emission
                emitWidgetData(widget.id, data)
            }
        }
    }
}
```

Automatic retry with exponential backoff (1s, 2s, 4s) attempts recovery up to 3 times before marking the widget as permanently errored.

### Thermal-Aware Data Throttling

Under thermal pressure, `WidgetBindingCoordinator` throttles data emission rate to match the target frame rate:

```kotlin
fun bind(widget: DashboardWidgetInstance, renderConfig: StateFlow<RenderConfig>): StateFlow<WidgetData> {
    val providerFlows = widget.compatibleSnapshots.mapNotNull { snapshotType ->
        resolveProvider(snapshotType)?.let { provider ->
            provider.provideState()
                .throttle { 1000L / renderConfig.value.targetFps }
                .map { snapshot -> snapshotType to snapshot }
        }
    }

    return combine(providerFlows) { results ->
        WidgetData(results.toMap().toImmutableMap(), SystemClock.elapsedRealtimeNanos())
    }.stateIn(bindingScope, SharingStarted.WhileSubscribed(timeout), WidgetData.Empty)
}
```

Compose draws when state changes. Throttling data emissions is the correct lever for reducing frame work on API 31-33 where system-level frame rate control is unavailable.

### Stuck Provider Watchdog

Each binding enforces a `firstEmissionTimeout` (default 5s). If a provider does not emit within this window after binding, `WidgetStatusCache` transitions to `DataTimeout`:

```kotlin
bindingScope.launch {
    val firstEmission = withTimeoutOrNull(provider.firstEmissionTimeout) {
        binder.bind(widget).first()
    }
    if (firstEmission == null) {
        updateStatus(widget.id, WidgetStatusCache.DataTimeout("Waiting for data..."))
        delay(provider.retryDelay)
        bind(widget) // recursive rebind
    }
}
```

## Typed DataSnapshot

Widget data uses typed sealed subtypes per data type instead of `Map<String, Any?>`. Each sealed subtype aligns 1:1 with a provider boundary:

```kotlin
@Immutable
sealed interface DataSnapshot {
    val timestamp: Long
}

@Immutable
data class SpeedSnapshot(
    val speed: Float,
    override val timestamp: Long,
) : DataSnapshot

@Immutable
data class AccelerationSnapshot(
    val acceleration: Float,
    override val timestamp: Long,
) : DataSnapshot

@Immutable
data class SpeedLimitSnapshot(
    val speedLimit: Float,
    val source: SpeedLimitSource, // MAP, SIGN_RECOGNITION, NETWORK
    override val timestamp: Long,
) : DataSnapshot

@Immutable
data class TimeSnapshot(
    val epochMillis: Long,
    val zoneId: String,
    override val timestamp: Long,
) : DataSnapshot

@Immutable
data class OrientationSnapshot(
    val bearing: Float,
    val pitch: Float,
    val roll: Float,
    override val timestamp: Long,
) : DataSnapshot

@Immutable
data class BatterySnapshot(
    val level: Int,
    val isCharging: Boolean,
    val temperature: Float?,
    override val timestamp: Long,
) : DataSnapshot

// Additional subtypes: TripSnapshot, MediaSnapshot, WeatherSnapshot,
// SolarSnapshot, AmbientLightSnapshot, AltitudeSnapshot
```

**Why 1:1 provider-to-snapshot alignment**: Bundling speed + acceleration + speed limit into a single composite type forces a single provider to own data from three independent sources with different availability, frequency, and failure modes. When the accelerometer is unavailable, a composite provider must fabricate a zero — wrong (zero means "not accelerating", not "unknown"). With 1:1 alignment, each snapshot is independently available. A speedometer widget binds to all three separately and renders gracefully with whatever data is available.

### Multi-Slot WidgetData

Widgets that consume multiple data types receive them via `KClass`-keyed multi-slot delivery:

```kotlin
@Immutable
data class WidgetData(
    private val snapshots: ImmutableMap<KClass<out DataSnapshot>, DataSnapshot>,
    val timestamp: Long,
) {
    inline fun <reified T : DataSnapshot> snapshot(): T? = snapshots[T::class] as? T
    fun hasData(): Boolean = snapshots.isNotEmpty()

    companion object {
        val Empty = WidgetData(persistentMapOf(), 0L)
        val Unavailable = WidgetData(persistentMapOf(), -1L)
    }
}
```

`KClass` keys provide compiler enforcement — `data.snapshot<SpeedSnapshot>()` cannot reference a nonexistent type. The single `as? T` cast is safe because the map is internally consistent.

### Batching

The binder uses `combine()` to merge multiple provider flows into a single `StateFlow<WidgetData>` per widget:

```kotlin
fun bind(widget: WidgetInstance): StateFlow<WidgetData> {
    val providerFlows = widget.compatibleSnapshots.mapNotNull { snapshotType ->
        resolveProvider(snapshotType)?.let { provider ->
            provider.provideState().map { snapshot -> snapshotType to snapshot }
        }
    }

    if (providerFlows.isEmpty()) return MutableStateFlow(WidgetData.Unavailable)

    return combine(providerFlows) { results ->
        WidgetData(
            snapshots = results.toMap().toImmutableMap(),
            timestamp = SystemClock.elapsedRealtimeNanos(),
        )
    }.stateIn(bindingScope, SharingStarted.WhileSubscribed(timeout), WidgetData.Empty)
}
```

**Application-level allocation target: <4KB per frame** (DataSnapshot objects, event routing, state updates — excluding Compose framework overhead). **Total allocation budget: <64KB per frame** including Compose's snapshot system, slot table, and recomposition scope tracking.

## Backpressure Strategy

Backpressure handling is defined per provider type as a contract requirement:

| Provider Type | Strategy | Rationale |
|---|---|---|
| Display-only (speed, compass, time, battery) | `StateFlow` (conflated) | Latest value is always correct; intermediate values have no meaning |
| Accumulation (trip distance, trip duration) | Provider-internal accumulation on `Dispatchers.Default` at full fidelity; emit aggregated `TripSnapshot` at 1Hz | Never rely on UI collection rate for data accuracy — a dropped speed sample would cause distance drift |
| Event-based (media session changes) | `SharedFlow(replay=1, extraBufferCapacity=1, DROP_OLDEST)` | Rare updates, replay for late subscribers |
| Network (weather) | `StateFlow` with manual refresh intervals (15min) | Rate-limited API, cache-first |

**Critical rule for accumulation providers**: The trip accumulator integrates distance from every GPS sample on a background dispatcher. The UI-facing `StateFlow` is a conflated projection. If the UI is slow to collect, only display updates are dropped — never source data.

## Widget Status

`WidgetStatusCache` computes overlay state per-widget with priority ordering:
EntitlementRevoked > ProviderMissing > SetupRequired > ConnectionError > DataTimeout > Disconnected > DataStale > Ready

## Process Death Recovery

- `SavedStateHandle` in coordinators for transient UI state: `focusedWidgetId`, overlay scroll positions
- **`isEditMode` is NOT saved** — on process death, the app always restores into view mode. The 500ms layout save debounce means recent widget moves are likely persisted. Restoring into edit mode risks presenting stale edit state.
- Theme preview state is intentionally lost (reverts to committed theme — correct behavior)
- Widget data bindings are re-established automatically from persisted layout on `ViewModel` recreation

## Data Staleness

Each data type declares a staleness threshold on `DataSchema`:

| Data Type | Staleness Threshold |
|---|---|
| SPEED | 3s |
| ACCELERATION | 3s |
| ORIENTATION | 5s |
| TIME | 2s |
| BATTERY | 30s |
| AMBIENT_LIGHT | 10s |
| SOLAR | 5min |
| WEATHER | 30min |
| MEDIA_SESSION | 10s |
| TRIP | 5s |
| ALTITUDE | 5s |
| SPEED_LIMIT | 5s |

When a widget's last data snapshot exceeds its staleness threshold, `WidgetStatusCache` transitions to `DataStale` with a subtle visual indicator (dimmed values, stale icon). The widget continues showing the last-known value.

## DI Scoping

| Component | Scope | Rationale |
|---|---|---|
| `WidgetRegistry` | `@Singleton` | App-wide, immutable after initialization |
| `DataProviderRegistryImpl` | `@Singleton` | App-wide, indexes all injected providers |
| Proto DataStore instances | `@Singleton` | DataStore enforces single-instance per file |
| Preferences DataStore instances | `@Singleton` | DataStore enforces single-instance per file |
| `ConnectionStateMachine` | `@Singleton` | Survives configuration changes |
| `ThemeAutoSwitchEngine` | `@Singleton` | Eager sharing via `SharingStarted.Eagerly`, ready at cold start |
| `EntitlementManager` | `@Singleton` | Shared across all gated components |
| `ThermalManager` | `@Singleton` | System-level, survives config changes |
| `DrivingModeDetector` | `@Singleton` | Continuous GPS monitoring |
| `DqxnLogger` / sinks | `@Singleton` | Available before ViewModel creation |
| `CrashReporter` / `CrashMetadataWriter` | `@Singleton` | Bound to Firebase impl via `:core:firebase` Hilt module |
| `ErrorReporter` | `@Singleton` | Wraps `CrashReporter`, deduplication decorator |
| `MetricsCollector` | `@Singleton` | Pre-allocated counters, app-wide |
| `PerformanceTracer` | `@Singleton` | Bound to Firebase Perf impl via `:core:firebase` Hilt module |
| `AnrWatchdog` | `@Singleton` | Dedicated thread, started at app init |
| Coordinators (Layout, Theme, etc.) | `@ViewModelScoped` | Tied to dashboard ViewModel lifecycle |
| Per-widget data bindings | ViewModel-scoped Jobs | Created/cancelled by WidgetBindingCoordinator |

All DI-scoped components that interact with WindowInsets receive insets via constructor injection of a `WindowInsetsProvider` interface, not by reading from the Activity directly. This keeps components testable.

## Background Lifecycle

### BLE Connection Management

- **Foreground service** (`FOREGROUND_SERVICE_CONNECTED_DEVICE`) starts when a paired device is expected and the app is active
- Service maintains BLE connection and `ConnectionStateMachine` state
- When app backgrounds: service continues running (required for CDM auto-wake)
- `REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE` allows CDM to wake the app when a paired device appears
- Android 14+ `ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE` is declared

### Connection State Machine

```
Idle -> Searching -> Connecting -> Connected -> Disconnected -> Searching (retry)
                                            -> Error -> Searching (retry, with backoff)
                                            -> Error -> Idle (max retries exhausted)
```

- Searching timeout: 30s -> Error
- Connecting timeout: 10s -> Error
- Retry: exponential backoff (1s, 2s, 4s), max 3 attempts before giving up
- Retry counter is modeled as state machine state, not an external variable
- Process death recovery: on app restart, if paired devices exist, transition from Idle -> Searching

### Screen Timeout & Wake Locks

- `FLAG_KEEP_SCREEN_ON` on the Activity window (not a wake lock — automatically released when Activity stops)
- No `PARTIAL_WAKE_LOCK` or `SCREEN_BRIGHT_WAKE_LOCK` — these trigger Android Vitals warnings
- Sensor delivery uses hardware FIFO batching to reduce SoC wakeups
