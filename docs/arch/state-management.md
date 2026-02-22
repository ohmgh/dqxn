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

```kotlin
// NotificationCoordinator — in-app banners and toasts (discrete events, not continuous state)
val activeBanners: StateFlow<ImmutableList<InAppNotification.Banner>>
val toasts: Channel<InAppNotification.Toast>  // single consumer, exactly-once
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

One-shot effects via `Channel<DashboardEffect>` (navigation triggers, haptics). Toasts route through `NotificationCoordinator` — not `DashboardEffect` — because they need driving-mode gating, priority ordering, and lifecycle management that the raw channel cannot provide.

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

    return providerFlows.merge()
        .scan(WidgetData.Empty) { current, (snapshotType, snapshot) ->
            current.withSlot(snapshotType, snapshot)
        }
        .stateIn(bindingScope, SharingStarted.WhileSubscribed(timeout), WidgetData.Empty)
}
```

Compose draws when state changes. Throttling data emissions is the correct lever for reducing frame work on API 31-33 where system-level frame rate control is unavailable.

### Per-Provider Emission Watchdog

With `merge() + scan()`, the overall binding emits as soon as any provider produces data. Individual stuck providers are tracked per-slot via independent timeouts:

```kotlin
widget.compatibleSnapshots.forEach { snapshotType ->
    resolveProvider(snapshotType)?.let { provider ->
        bindingScope.launch {
            val firstEmission = withTimeoutOrNull(provider.firstEmissionTimeout) {
                provider.provideState().first()
            }
            if (firstEmission == null) {
                updateSlotStatus(widget.id, snapshotType, SlotStatus.DataTimeout("Waiting for data..."))
                logger.warn(LogTag.BINDING, "widgetId" to widget.id, "snapshotType" to snapshotType.simpleName) {
                    "Provider has not emitted within ${provider.firstEmissionTimeout}"
                }
            }
        }
    }
}
```

If all providers for a widget stall, `WidgetData` remains `Empty` and `WidgetStatusCache` escalates to widget-level `DataTimeout`.

## Typed DataSnapshot

Widget data uses typed subtypes per data type instead of `Map<String, Any?>`. Each subtype aligns 1:1 with a provider boundary. `DataSnapshot` is a non-sealed interface in `:sdk:contracts` — concrete subtypes live with their producing module, validated at compile time by the `:codegen:plugin` KSP processor via `@DashboardSnapshot`.

```kotlin
// :sdk:contracts — interface only, no concrete subtypes
@Immutable
interface DataSnapshot {
    val timestamp: Long
}

// :pack:free:snapshots — cross-boundary snapshot types for the free pack
@DashboardSnapshot(dataType = "speed")
@Immutable
data class SpeedSnapshot(
    val speed: Float,
    override val timestamp: Long,
) : DataSnapshot

@DashboardSnapshot(dataType = "acceleration")
@Immutable
data class AccelerationSnapshot(
    val acceleration: Float,
    override val timestamp: Long,
) : DataSnapshot

@DashboardSnapshot(dataType = "speed-limit")
@Immutable
data class SpeedLimitSnapshot(
    val speedLimit: Float,
    val source: SpeedLimitSource, // MAP, SIGN_RECOGNITION, NETWORK
    override val timestamp: Long,
) : DataSnapshot

@DashboardSnapshot(dataType = "time")
@Immutable
data class TimeSnapshot(
    val epochMillis: Long,
    val zoneId: String,
    override val timestamp: Long,
) : DataSnapshot

@DashboardSnapshot(dataType = "orientation")
@Immutable
data class OrientationSnapshot(
    val bearing: Float,
    val pitch: Float,
    val roll: Float,
    override val timestamp: Long,
) : DataSnapshot

@DashboardSnapshot(dataType = "battery")
@Immutable
data class BatterySnapshot(
    val level: Int,
    val isCharging: Boolean,
    val temperature: Float?,
    override val timestamp: Long,
) : DataSnapshot

// Additional cross-boundary subtypes in :pack:free:snapshots: AltitudeSnapshot, AmbientLightSnapshot
// Pack-local subtypes in :pack:free: SolarSnapshot, WeatherSnapshot
// Pack-local subtypes in :pack:plus: TripSnapshot, MediaSnapshot
// OBU-specific subtypes in :pack:sg-erp2: ObuTrafficSnapshot, BalanceSnapshot, etc.
// Core subtypes in :core:driving:snapshots: DrivingSnapshot
```

The KSP processor (`@DashboardSnapshot`) enforces: no duplicate `dataType` strings, `@Immutable` annotation required, only `val` properties, implements `DataSnapshot`.

### Snapshot Sub-Modules

The `KClass`-keyed API (`snapshot<SpeedSnapshot>()`, `compatibleSnapshots = setOf(SpeedSnapshot::class)`) requires compile-time visibility of the concrete snapshot class. Pack isolation rules forbid importing other packs or `:core:*`. Snapshot sub-modules resolve this without promoting types to `:sdk:contracts`:

```
:pack:free:snapshots     — SpeedSnapshot, AccelerationSnapshot, BatterySnapshot, TimeSnapshot,
                           OrientationSnapshot, SpeedLimitSnapshot, AltitudeSnapshot, AmbientLightSnapshot
:core:driving:snapshots  — DrivingSnapshot
```

Each snapshot sub-module:
- Contains **only** `@DashboardSnapshot`-annotated data classes — no providers, no widgets, no business logic
- Pure Kotlin — no Android framework, no Compose dependencies
- Depends on `:sdk:contracts` only (for `DataSnapshot` interface + `@Immutable`)
- Uses the `dqxn.snapshot` convention plugin

```kotlin
// pack/free/snapshots/build.gradle.kts
plugins {
    id("dqxn.snapshot")  // auto-wires :sdk:contracts, no Compose, no Android
}
```

**Cross-boundary access:** Packs and features depend on snapshot sub-modules, not on producing modules:

```kotlin
// pack/plus/build.gradle.kts — plus pack consumes free pack's snapshot types
plugins {
    id("dqxn.pack")
}
dependencies {
    implementation(project(":pack:free:snapshots"))       // SpeedSnapshot, etc.
    implementation(project(":core:driving:snapshots"))    // DrivingSnapshot
    // implementation(project(":pack:free"))              // ❌ still forbidden
}
```

**Ownership rules:**
- **Snapshot sub-module** (`:pack:{id}:snapshots`, `:core:{id}:snapshots`) — types consumed across module boundaries. The producing module's team owns the sub-module.
- **Pack-local** — types consumed only within the producing pack stay in the pack module directly. Extract to a sub-module only when a second consumer appears.
- `:sdk:contracts` holds only the `DataSnapshot` interface and `@DashboardSnapshot` annotation — never concrete snapshot types.

| Location | Example Types | When |
|---|---|---|
| `:pack:free:snapshots` | `SpeedSnapshot`, `BatterySnapshot`, `TimeSnapshot` | Platform-level data consumed by multiple packs |
| `:core:driving:snapshots` | `DrivingSnapshot` | Shell safety gate + optional pack widget display |
| `:pack:free` (local) | `SolarSnapshot` | Only consumed by free pack's solar widget |
| `:pack:plus` (local) | `TripSnapshot` | Only consumed by plus pack's trip widget |
| `:pack:sg-erp2` (local) | `ObuTrafficSnapshot`, `BalanceSnapshot` | Regional pack, no cross-boundary consumers |

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

    /** Returns a new WidgetData with the given slot updated. Used by merge+scan binder. */
    fun withSlot(type: KClass<out DataSnapshot>, snapshot: DataSnapshot): WidgetData =
        WidgetData(snapshots.put(type, snapshot), SystemClock.elapsedRealtimeNanos())

    companion object {
        val Empty = WidgetData(persistentMapOf(), 0L)
        val Unavailable = WidgetData(persistentMapOf(), -1L)
    }
}
```

`KClass` keys provide compiler enforcement — `data.snapshot<SpeedSnapshot>()` cannot reference a nonexistent type. The `as? T` cast is safe by construction: `DataProvider<T>.provideState(): Flow<T>` guarantees emissions match `snapshotType: KClass<T>`, so the binder's `snapshotType to snapshot` entries are always correctly typed.

### Batching

The binder uses `merge()` + `scan()` to accumulate provider emissions into a single `StateFlow<WidgetData>` per widget. Each slot updates independently — a stuck BLE provider does not block speed and battery from rendering:

```kotlin
fun bind(widget: WidgetInstance): StateFlow<WidgetData> {
    val providerFlows = widget.compatibleSnapshots.mapNotNull { snapshotType ->
        resolveProvider(snapshotType)?.let { provider ->
            provider.provideState().map { snapshot -> snapshotType to snapshot }
        }
    }

    if (providerFlows.isEmpty()) return MutableStateFlow(WidgetData.Unavailable)

    return providerFlows.merge()
        .scan(WidgetData.Empty) { current, (snapshotType, snapshot) ->
            current.withSlot(snapshotType, snapshot)
        }
        .stateIn(bindingScope, SharingStarted.WhileSubscribed(timeout), WidgetData.Empty)
}
```

**Why `merge() + scan()`, not `combine()`**: `combine()` requires at least one emission from every upstream flow before producing any downstream value. A 3-slot widget where one provider is slow or stuck receives zero data — contradicting the multi-slot design goal of independent availability and graceful degradation. `merge() + scan()` emits on each upstream emission, accumulating slots incrementally. `StateFlow` conflation ensures the UI still sees at most one update per frame regardless of emission rate.

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
| `CrashReporter` | `@Singleton` | Bound to Firebase impl via `:core:firebase` Hilt module (includes metadata methods: `setKey`, `setUserId`) |
| `ErrorReporter` | `@Singleton` | Wraps `CrashReporter`, deduplication decorator |
| `MetricsCollector` | `@Singleton` | Pre-allocated counters, app-wide |
| Firebase Performance | `@Singleton` | Used directly in `:core:firebase` for v1 (extract `PerformanceTracer` interface when a second consumer needs it) |
| `AnrWatchdog` | `@Singleton` | Dedicated thread, started at app init |
| Coordinators (Layout, Theme, etc.) | `@ViewModelScoped` | Tied to dashboard ViewModel lifecycle |
| `NotificationCoordinator` | `@ViewModelScoped` | Re-derives persistent banners from `@Singleton` sources on recreation |
| `AlertSoundManager` | `@Singleton` | Implements `AlertEmitter` (`:sdk:contracts`), holds `SoundPool`/`AudioManager`/`Vibrator` — survives ViewModel recreation |
| `SystemNotificationBridge` | `@Singleton` | FGS notification, connection channel — survives config changes |
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
