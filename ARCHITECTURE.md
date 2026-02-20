# DQXN Architecture

> Target technical architecture for the DQXN Android dashboard platform.

## 1. Overview

DQXN is a single-activity Android dashboard platform that renders real-time telemetry on a fully configurable widget grid. A phone or tablet mounted in a vehicle displays speed, time, compass, ambient light, solar position, and data from feature packs — all through a modular, pack-based architecture.

Packs (widgets, themes, data providers) are fully decoupled from the dashboard shell. Packs know nothing about the shell; the shell discovers packs at runtime via Hilt multibinding. This enables regional feature sets, premium gating, and first-party modular extensibility without touching the core. All packs are compiled modules — there is no runtime plugin loading.

**Identity**: "Life is a dash. Make it beautiful." — The Dashing Dachshund

## 2. Tech Stack

| Category | Choice |
|---|---|
| Language | Kotlin 2.3+ (no Java) |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM + MVI-style sealed events, single-Activity |
| DI | Hilt + KSP |
| Navigation | Navigation Compose (type-safe, kotlinx.serialization) |
| Async | Coroutines, StateFlow/SharedFlow |
| Persistence | Proto DataStore (structured data), Preferences DataStore (simple settings) |
| Serialization | kotlinx.serialization-json, Protocol Buffers (persistence) |
| Code Gen | KSP + KotlinPoet |
| Collections | kotlinx-collections-immutable (for Compose stability) |
| Android SDK | compileSdk 36, minSdk 31, targetSdk 36 |
| Build | AGP 9.0.1, Gradle 9.3.1, JDK 25 |
| Perf | Baseline Profiles, Macrobenchmarks, Compose compiler metrics |
| Firebase | Crashlytics, Analytics, Performance Monitoring — all behind interfaces in `:core:observability` / `:core:analytics`, implementations in `:core:firebase` |
| Debug | LeakCanary, StrictMode (debug builds) |

## 3. Module Structure

```
android/
├── build-logic/convention/       # Gradle convention plugins (composite build)
├── lint-rules/                   # Custom lint checks (module boundaries, KAPT detection, etc.)
├── app/                          # Single-activity entry, DI assembly, nav host
│   ├── src/main/
│   ├── src/debug/                # Agentic framework, debug overlays, LeakCanary, StrictMode
│   └── src/release/
├── core/
│   ├── common/                   # AppResult, AppError, coroutine dispatchers, stability config
│   ├── plugin-api/               # Plugin contracts (WidgetRenderer, DataProvider, etc.)
│   ├── plugin-processor/         # KSP: @DashboardWidget → generated pack manifests
│   ├── widget-primitives/        # WidgetContainer, DashboardThemeDefinition, ThemeProvider
│   ├── design-system/            # Theme tokens, typography, spacing, shared overlay composables
│   ├── thermal/                  # ThermalManager, RenderConfig, adaptive frame rate
│   ├── observability/            # Logging, tracing, metrics, health monitoring, ANR watchdog
│   ├── analytics/                # AnalyticsTracker interface, sealed AnalyticsEvent hierarchy
│   ├── firebase/                 # Firebase implementations (Crashlytics, Analytics, Perf) — sole Firebase dependency point
│   ├── agentic/                  # ADB broadcast debug automation
│   └── agentic-processor/        # KSP: route listing generation
├── data/
│   ├── persistence/              # Proto DataStore (layouts, devices), Preferences DataStore (settings)
│   └── proto/                    # .proto schema definitions
├── feature/
│   ├── dashboard/                # Dashboard shell — coordinators, grid, theme engine, presets
│   ├── driving/                  # Driving mode detection, safety gating (v1 scope)
│   └── packs/
│       ├── free/                 # "Essentials" — core widgets, providers, themes
│       ├── plus/                 # "Plus" — trip computer, media, G-force, altimeter, weather
│       ├── themes/               # Premium themes (JSON-driven)
│       └── demo/                 # Hardware simulation for debug/demo
├── baselineprofile/              # Baseline Profile generation
└── benchmark/                    # Macrobenchmark tests
```

Regional packs (e.g., Singapore ERP integration) plug in as additional `:feature:packs:*` modules without any changes to the shell or core.

Convention plugins enforce shared defaults across all modules: compileSdk 36, minSdk 31, JVM target matching AGP/Gradle requirements. **Compose compiler is only applied to modules with UI** (not `:data:*`, not `:core:common`, not `:core:plugin-processor`, not `:core:observability`, not `:core:analytics`, not `:core:firebase`).

### Module Dependency Rules

Packs depend on `:core:plugin-api`, never on `:feature:dashboard`. The shell imports nothing from packs at compile time. Discovery is pure runtime via Hilt `Set<T>` multibinding.

```
:app
  → :feature:dashboard (shell)
  → :feature:driving (safety gating)
  → :feature:packs:* (widget/provider/theme implementations)
  → :core:plugin-api (contracts)
  → :core:common (shared types)
  → :core:widget-primitives (shared UI)
  → :core:design-system (theme tokens, shared composables)
  → :core:thermal (thermal management)
  → :core:observability (logging, tracing, metrics)
  → :core:analytics (analytics abstraction)
  → :core:firebase (Firebase implementations — sole Firebase import point)
  → :data:persistence (DataStore)

:feature:packs:*
  → :core:plugin-api (contracts only)
  → :core:common
  → :core:widget-primitives
  → :core:observability

:feature:dashboard
  → :core:plugin-api
  → :core:common
  → :core:widget-primitives
  → :core:design-system
  → :core:thermal
  → :core:observability
  → :core:analytics
  → :data:persistence

:feature:driving
  → :core:plugin-api
  → :core:common
  → :core:observability

:core:firebase
  → :core:observability (CrashReporter, CrashMetadataWriter, ErrorReporter interfaces)
  → :core:analytics (AnalyticsTracker interface)
  → :core:common

:core:observability
  → :core:common

:core:design-system
  → :core:common
  → :core:widget-primitives

:core:analytics
  → :core:common

Every module → :core:observability
```

No module other than `:core:firebase` and `:app` depends on Firebase SDKs. Feature modules, packs, and all core modules interact exclusively through the interfaces in `:core:observability` and `:core:analytics`.

This strict boundary means adding or removing a pack never requires changes to the shell.

## 4. Architecture Layers

```
┌─────────────────────────────────────────────────────────┐
│  PRESENTATION                                           │
│  DashboardScreen / DashboardGrid / OverlayNavHost       │
│  Jetpack Compose — stateless renderers                  │
│  Layer 0: collectAsState() — Layer 1: collectAsState-   │
│  WithLifecycle()                                        │
├─────────────────────────────────────────────────────────┤
│  COORDINATION                                           │
│  Focused coordinators (NOT a monolithic ViewModel)      │
│  LayoutCoordinator, ThemeCoordinator,                   │
│  EditModeCoordinator, WidgetBindingCoordinator          │
│  Each owns its own state slice                          │
├─────────────────────────────────────────────────────────┤
│  DOMAIN                                                 │
│  ThemeAutoSwitchEngine, GridPlacementEngine              │
│  SetupEvaluator, EntitlementManager                     │
│  DrivingModeDetector, ThermalManager                    │
├─────────────────────────────────────────────────────────┤
│  PLUGIN / PACK                                          │
│  DataProvider (Flow<DataSnapshot>)                       │
│  WidgetRenderer (@Composable Render)                    │
│  ThemeProvider (List<DashboardThemeDefinition>)          │
├─────────────────────────────────────────────────────────┤
│  DATA                                                   │
│  LayoutDataStore (Proto), UserPreferencesRepository      │
│  PairedDeviceStore (Proto), ConnectionEventStore         │
│  ProviderSettingsStore (Preferences, namespaced keys)   │
├─────────────────────────────────────────────────────────┤
│  OBSERVABILITY                                          │
│  DqxnLogger, DqxnTracer, MetricsCollector               │
│  WidgetHealthMonitor, ThermalTrendAnalyzer, AnrWatchdog │
│  ErrorReporter, CrashContextProvider                    │
└─────────────────────────────────────────────────────────┘
```

### State Collection Policy

- **Dashboard Layer 0 (always present)**: Use `collectAsState()` (no lifecycle awareness) for widget data flows. The dashboard is always active when visible — lifecycle pausing causes a jank spike when all 12+ widgets resume simultaneously as flows restart and emit initial values.
- **Overlay Layer 1**: Use `collectAsStateWithLifecycle()` for overlay-specific state (settings, pickers). These composables come and go with navigation — lifecycle-aware collection correctly stops work when overlays are dismissed.
- **Manual pause/resume**: Suspend widget data collection explicitly when CPU-heavy overlays are open (via `WidgetBindingCoordinator.pauseAll()`/`resumeAll()`), not via lifecycle. This gives the shell precise control over when data collection pauses, avoiding the thundering-herd resume problem.

### Design Principles

- **Decomposed state** — Widget data, theme state, layout state, and UI mode are separate `StateFlow`s. No god-object state class. Per-widget data flows are independent — a clock tick does not cause the speedometer to recompose.
- **IoC data binding** — widgets never choose their data source; the system binds providers to widgets by data type compatibility
- **Declarative schemas** — settings UI and setup wizards are driven by schema definitions, not custom composables per widget
- **Entitlement gating via `Gated` interface** — applied uniformly to renderers, providers, themes, individual settings, and auto-switch modes
- **Widget error isolation** — each widget renders inside a catch boundary; one widget's failure shows a fallback, not an app crash
- **ConnectionStateMachine** — validated state transitions with explicit transition rules, retry counts, and timeouts
- **Thermal adaptation** — rendering quality degrades gracefully under thermal pressure before the OS forces throttling

## 5. State Management

### Decomposed State Architecture

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

### Why Not a Single StateFlow

A single `DashboardState` containing all widget data means:
- Clock ticking at 1Hz + speed at 10Hz + compass at 50Hz = 60+ `.copy()` allocations per second
- Every emission triggers recomposition of the entire composable tree
- Every widget re-renders even when only one widget's data changed

With decomposed flows, each widget composable collects only `widgetData(myId)`. The speedometer doesn't recompose when the clock ticks.

### Split Event Channels

High-frequency positional updates (drag, resize) and discrete commands (add, remove, set theme) have fundamentally different delivery semantics. Mixing them in a single channel means 60Hz drag events get serialized behind slow commands (theme change triggering DataStore write, etc.):

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

Discrete commands go through the `commandChannel` for ordered processing. Drag/resize update a `MutableStateFlow` directly on the main thread — latest value wins, no queuing.

### Command Processing & Error Handling

All command processing happens on `Dispatchers.Main` via the `commandChannel` processed sequentially. This guarantees thread-safe state mutation without locks. The processing loop is protected against coordinator failures:

```kotlin
// In DashboardViewModel command loop
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

A coordinator throwing an unhandled exception must NOT kill the command processing loop. Errors are reported and the loop continues processing the next command.

All disk I/O (DataStore reads/writes, Proto serialization) MUST run on `Dispatchers.IO`. Proto DataStore serialization must never run on `Dispatchers.Main` — use `withContext(Dispatchers.IO)` in repository implementations.

One-shot effects via `Channel<DashboardEffect>` (navigation triggers, toasts, haptics).

### Per-Widget Data Binding

Each widget gets a coroutine binding that:
1. Calls `WidgetDataBinder.bind(widget)` → `StateFlow<WidgetData>`
2. Collects with `WhileSubscribed` — timeout is provider-specific (1s clock, 5s default, 30s GPS)
3. Exposes data via `widgetData(widgetId)` — individual `StateFlow` per widget

Bindings are managed as a `Map<String, Job>` in `WidgetBindingCoordinator` — cancelled on widget removal, created on add, rebound on data source change.

#### SupervisorJob for Binding Isolation

All binding jobs are children of a `SupervisorJob` parented to the ViewModel scope:

```kotlin
class WidgetBindingCoordinator @Inject constructor(
    private val binder: WidgetDataBinder,
    @ViewModelCoroutineScope private val scope: CoroutineScope,
) {
    // SupervisorJob ensures one provider crash doesn't cancel siblings
    // via CancellationException propagation
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

A failed provider reports the error via `widgetStatus` but does not cancel sibling bindings. Without `SupervisorJob`, a `CancellationException` from one child propagates up and cancels all siblings sharing the same parent `Job`. Automatic retry with exponential backoff (1s, 2s, 4s) attempts recovery up to 3 times before marking the widget as permanently errored.

#### Thermal-Aware Data Throttling

Under thermal pressure, `WidgetBindingCoordinator` throttles data emission rate to match the target frame rate. This controls the data rate feeding Compose — by changing state less often, we reduce frame work without fighting the framework:

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

#### Stuck Provider Watchdog

Each binding enforces a `firstEmissionTimeout` (default 5s). If a provider does not emit within this window after binding, `WidgetStatusCache` transitions to `DataTimeout`:

```kotlin
bindingScope.launch {
    val firstEmission = withTimeoutOrNull(provider.firstEmissionTimeout) {
        binder.bind(widget).first()
    }
    if (firstEmission == null) {
        updateStatus(widget.id, WidgetStatusCache.DataTimeout("Waiting for data..."))
        // Retry: rebind after backoff
        delay(provider.retryDelay)
        bind(widget) // recursive rebind
    }
}
```

This prevents widgets from sitting in an ambiguous "loading" state indefinitely when a provider stalls during initialization (e.g., waiting for GPS fix, BLE handshake timeout).

### Typed DataSnapshot

Widget data uses typed sealed subtypes per data type instead of `Map<String, Any?>`. This eliminates boxing of primitives and provides compile-time safety. Each sealed subtype aligns 1:1 with a provider boundary — a provider emits exactly one snapshot type:

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

**Why 1:1 provider-to-snapshot alignment**: Bundling speed + acceleration + speed limit into a single composite type forces a single provider to own data from three independent sources (GPS, accelerometer, map data) with different availability, frequency, and failure modes. When the accelerometer is unavailable, a composite provider must fabricate a zero for `acceleration` — wrong (zero means "not accelerating", not "unknown"). With 1:1 alignment, each snapshot is independently available. A speedometer widget binds to `SpeedSnapshot`, `AccelerationSnapshot`, and `SpeedLimitSnapshot` separately — it renders immediately with just speed, acceleration appears when the sensor is ready, speed limit appears when map data is available. This preserves graceful degradation without boxing overhead.

#### Multi-Slot WidgetData

Widgets that consume multiple data types receive them via `KClass`-keyed multi-slot delivery:

```kotlin
@Immutable
data class WidgetData(
    private val snapshots: ImmutableMap<KClass<out DataSnapshot>, DataSnapshot>,
    val timestamp: Long,
) {
    /** Type-safe snapshot access. Returns null if the data type is not yet available. */
    inline fun <reified T : DataSnapshot> snapshot(): T? = snapshots[T::class] as? T

    /** Check if any data is available. */
    fun hasData(): Boolean = snapshots.isNotEmpty()

    companion object {
        val Empty = WidgetData(persistentMapOf(), 0L)
        val Unavailable = WidgetData(persistentMapOf(), -1L)
    }
}
```

**Why `KClass` keys instead of string keys**: String-keyed maps (`Map<String, DataSnapshot>` with `"SPEED"`, `"ACCELERATION"`) allow typos, have no compiler enforcement, and require separate `DataTypeDescriptor` registration for validation. `KClass` keys make the type system do the work — `data.snapshot<SpeedSnapshot>()` cannot reference a nonexistent type, and the binder validates at bind time that a provider's output type matches a widget's declared input types. The single `as? T` cast is safe because the map is internally consistent: a `KClass<SpeedSnapshot>` key always maps to a `SpeedSnapshot` value.

**Why not a single-slot `WidgetData`**: A speedometer consuming speed + acceleration + speed limit would require either (a) a composite provider that merges three independent data sources, pushing binding logic into the provider layer and violating IoC, or (b) a single sealed type per widget rather than per data type, creating an explosion of widget-specific snapshot types. Multi-slot preserves independent provider availability while keeping the sealed hierarchy aligned with data sources.

#### Batching

The binder uses `combine()` to merge multiple provider flows into a single `StateFlow<WidgetData>` per widget. If GPS speed and accelerometer emit within the same frame, the widget gets one recomposition with both values updated — not two:

```kotlin
// In WidgetDataBinder
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

Each slot is independently nullable via `snapshot<T>()` — a widget renders gracefully with partial data. `combine()` emits only after all flows have emitted at least once; before that, `WidgetData.Empty` is used and `WidgetStatusCache` shows a loading indicator.

**Application-level allocation target: <4KB per frame** (DataSnapshot objects, event routing, state updates — excluding Compose framework overhead). **Total allocation budget: <64KB per frame** including Compose's snapshot system, slot table, and recomposition scope tracking. Validated via allocation tracking in macrobenchmarks using `Debug.startAllocCounting()`. A realistic Compose screen with 12 widgets incurs 20-60KB/frame of framework overhead depending on skip rate.

No `Any` casting, no boxing. Each `Float`/`Int` is stored as a primitive field in its snapshot data class. The only `as?` cast is the type-safe `KClass`-keyed lookup in `WidgetData.snapshot<T>()`.

### Backpressure Strategy

Backpressure handling is defined per provider type as a contract requirement:

| Provider Type | Strategy | Rationale |
|---|---|---|
| Display-only (speed, compass, time, battery) | `StateFlow` (conflated) | Latest value is always correct; intermediate values have no meaning |
| Accumulation (trip distance, trip duration) | Provider-internal accumulation on `Dispatchers.Default` at full fidelity; emit aggregated `TripSnapshot` at 1Hz | Never rely on UI collection rate for data accuracy — a dropped speed sample would cause distance drift |
| Event-based (media session changes) | `SharedFlow(replay=1, extraBufferCapacity=1, DROP_OLDEST)` | Rare updates, replay for late subscribers |
| Network (weather) | `StateFlow` with manual refresh intervals (15min) | Rate-limited API, cache-first |

**Critical rule for accumulation providers**: The trip accumulator integrates distance from every GPS sample on a background dispatcher. The UI-facing `StateFlow` is a conflated projection of the accumulated state. If the UI is slow to collect, only display updates are dropped — never source data.

### Widget Status

`WidgetStatusCache` computes overlay state per-widget with priority ordering:
EntitlementRevoked > ProviderMissing > SetupRequired > ConnectionError > DataTimeout > Disconnected > DataStale > Ready

### Process Death Recovery

- `SavedStateHandle` in coordinators for transient UI state: `focusedWidgetId`, overlay scroll positions
- **`isEditMode` is NOT saved to `SavedStateHandle`** — on process death, the app always restores into view mode. The 500ms layout save debounce means recent widget moves are likely persisted. Restoring into edit mode risks presenting stale edit state.
- Layout debounce (500ms) means a widget mid-drag may not be saved — this is acceptable; edit-mode cancel already restores pre-edit state
- Theme preview state is intentionally lost on process death (reverts to committed theme — correct behavior)
- Widget data bindings are re-established automatically from persisted layout on `ViewModel` recreation

### Data Staleness

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

When a widget's last data snapshot exceeds its staleness threshold, `WidgetStatusCache` transitions to `DataStale` with a subtle visual indicator (dimmed values, stale icon). The widget continues showing the last-known value — it does not blank out.

## 6. Compose Performance

### Recomposition Isolation

Each widget is an isolated recomposition scope. The grid does NOT use a single Canvas for all widgets:

```kotlin
@Composable
fun DashboardGrid(widgets: ImmutableList<WidgetInstance>) {
    widgets.forEach { widget ->
        key(widget.id) {
            // Isolated recomposition scope — updates to this widget
            // do NOT trigger recomposition of sibling widgets
            WidgetSlot(widget)
        }
    }
}
```

### State Read Deferral

High-frequency state (needle angle, compass bearing) is read in the draw phase, not the composition phase. Per-widget data is provided via `CompositionLocal` so renderers can defer reads:

```kotlin
// In WidgetSlot (the container that wraps each widget)
val widgetData by widgetDataFlow.collectAsState()

CompositionLocalProvider(
    LocalWidgetData provides widgetData,
) {
    renderer.Render(isEditMode, style, settings, modifier)
}
```

Widgets access data via `LocalWidgetData.current` and defer high-frequency reads to the draw phase:

```kotlin
@Composable
override fun Render(isEditMode: Boolean, style: WidgetStyle, settings: ImmutableMap<String, Any>, modifier: Modifier) {
    val data = LocalWidgetData.current
    val speed = remember { derivedStateOf { data.snapshot<SpeedSnapshot>()?.speed ?: 0f } }
    val accel = remember { derivedStateOf { data.snapshot<AccelerationSnapshot>()?.acceleration } }
    val limit = remember { derivedStateOf { data.snapshot<SpeedLimitSnapshot>()?.speedLimit } }
    // speed/accel/limit read in draw phase, not composition phase
    // Each slot is independently nullable — widget renders with whatever data is available
    SpeedometerCanvas(speed, accel, limit)
}
```

The draw-phase deferral pattern:

```kotlin
@Composable
fun SpeedometerCanvas(speedState: State<Float>) {
    // Pass State<Float>, not Float — defers the read
    Canvas(modifier = Modifier.fillMaxSize().drawWithCache {
        // Expensive geometry computed ONCE per size change
        val dialPath = Path().apply { buildDial(this, size) }
        val tickPositions = computeTickPositions(size)

        onDrawBehind {
            // speedState.value read HERE — draw phase only
            val angle = speedState.value.toNeedleAngle()
            drawPath(dialPath, dialBrush)
            drawTicks(tickPositions)
            drawNeedle(angle)
        }
    })
}
```

### `graphicsLayer` Isolation

Every widget wrapped in `graphicsLayer` creates an isolated RenderNode. When widget A updates, widget B's RenderNode is GPU-cached and not re-executed:

```kotlin
Modifier.graphicsLayer {
    // Each widget is its own hardware layer
    // Transform animations (wiggle, focus scale, drag) operate
    // at the RenderNode level — no recomposition
    rotationZ = wiggleAngle
    scaleX = focusScale
    scaleY = focusScale
}
```

### Draw Object Caching

All `Path`, `Paint`, `Brush`, and `Shader` objects are cached via `remember` or `drawWithCache` — never allocated per frame:

```kotlin
// Cached speedometer dial geometry — recomputed only on size change
Modifier.drawWithCache {
    val outerArcPath = Path().apply {
        addArc(
            oval = Rect(center = size.center, radius = size.minDimension / 2 - strokeWidth),
            startAngleDegrees = 135f,
            sweepAngleDegrees = 270f,
        )
    }
    val tickMarks = buildTickMarkPaths(size, tickCount = 12)
    val arcBrush = Brush.sweepGradient(listOf(accentColor, highlightColor))

    onDrawBehind {
        drawPath(outerArcPath, arcBrush, style = Stroke(strokeWidth))
        tickMarks.forEach { tick -> drawPath(tick, tickBrush) }
    }
}
```

### Stability Annotations

All domain types emitted to the UI layer are annotated `@Immutable` or `@Stable`. Collections use `kotlinx-collections-immutable` (`ImmutableList`, `ImmutableMap`). `WidgetData` is defined in the Typed DataSnapshot section (Section 5).

A Compose stability configuration file covers cross-module types:

```
// compose_compiler_config.txt
app.dqxn.core.plugin.api.DataSnapshot
app.dqxn.core.plugin.api.WidgetData
app.dqxn.core.plugin.api.SpeedSnapshot
app.dqxn.core.plugin.api.AccelerationSnapshot
app.dqxn.core.plugin.api.SpeedLimitSnapshot
app.dqxn.core.plugin.api.TimeSnapshot
app.dqxn.core.plugin.api.OrientationSnapshot
app.dqxn.core.plugin.api.BatterySnapshot
app.dqxn.core.widget.DashboardThemeDefinition
app.dqxn.data.persistence.SavedWidget
```

Compose compiler metrics (`-Pcompose.compiler.metrics=true`) are audited regularly to catch regressions in skippability.

### Derived State

`derivedStateOf` prevents unnecessary recomposition when computing values from state:

```kotlin
// ThemeState.displayTheme — computed from previewTheme and currentTheme
val displayTheme by remember {
    derivedStateOf { themeState.value.previewTheme ?: themeState.value.currentTheme }
}
// Only recomposes consumers when the RESULT changes, not when themeState changes
```

Use `derivedStateOf` for:
- Filtered widget lists (viewport culling result)
- Computed theme properties (e.g., `displayTheme` from preview + current)
- Widget status aggregation (e.g., "N widgets in error state")
- Any value derived from multiple state sources where the computed result changes less frequently than its inputs

### Glow Effect Strategy

Since minSdk is 31, `RenderEffect.createBlurEffect()` (GPU shader, no offscreen allocation) is always available. There is no API-level fallback needed.

Strategy:
- **Default**: `RenderEffect.createBlurEffect()` — GPU shader, zero offscreen buffer allocation. With 12 widgets, no additional bitmap memory.
- **Thermal degradation**: Glow effects are the first thing disabled when `thermalLevel >= DEGRADED`. The `RadialGradient` approximation is the thermal-degraded visual path — a simpler gradient that avoids blur shader cost entirely while retaining a hint of the glow aesthetic. This is a performance tier, not an API-level fallback.
- **Widget previews**: glow always disabled (barely visible at preview scale)

### Frame Pacing

The architecture must not assume 60Hz. Many target devices (Pixel 7a, Galaxy A54) have 90Hz displays. Some automotive head units run at 120Hz. Default to the display's native refresh rate at NORMAL thermal level. Track actual display refresh rate via `Display.getRefreshRate()` and adapt frame budgets accordingly.

Under thermal pressure, the target FPS drops below native. Frame pacing uses a two-pronged approach:

**Primary (API 34+):** Use `Window.setFrameRate()` to request a lower display refresh rate from the system. This reduces GPU work at the hardware level:

```kotlin
class FramePacer @Inject constructor() {
    fun applyFrameRate(window: Window, renderConfig: RenderConfig) {
        if (Build.VERSION.SDK_INT >= 34) {
            window.setFrameRate(
                renderConfig.targetFps,
                Window.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE,
            )
        }
    }
}
```

**Fallback (API 31-33):** Throttle data emission rate in `WidgetBindingCoordinator` (see Section 5, Thermal-Aware Data Throttling). Under thermal pressure, debounce `StateFlow` emissions to match the target frame rate. This controls the data rate feeding Compose, not the render rate itself. Compose draws when state changes — by changing state less often, we reduce frame work without fighting the framework.

The Choreographer-based approach (manual `postFrameCallback` with frame skip counter) is explicitly NOT used. It fights Compose's own rendering pipeline — setting `frameCount` triggers a second recomposition pass on top of Compose's normal frame, and skipping the callback doesn't prevent Compose from drawing pending `StateFlow` invalidations.

### Widget Reordering During Drag

During drag operations in edit mode, widgets are reordered in the grid. Use `Modifier.offset` with animation — during drag, only the visual position changes via `graphicsLayer` offset while the composition stays in the same slot:

```kotlin
@Composable
fun DashboardGrid(widgets: ImmutableList<WidgetInstance>) {
    widgets.forEach { widget ->
        key(widget.id) {
            val animatedOffset = animateOffsetAsState(
                targetValue = widget.position.toOffset(),
                animationSpec = spring(dampingRatio = 0.8f),
            )
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        translationX = animatedOffset.value.x
                        translationY = animatedOffset.value.y
                    }
            ) {
                WidgetSlot(widget)
            }
        }
    }
}
```

`movableContentOf` is designed for moving content between different parent composables (e.g., phone/tablet adaptive layouts), not for reordering within the same parent where only position changes. Using it with a `remember(widgets)` key invalidates the entire map on every reorder, defeating the purpose.

### Grid Layout

For a viewport-sized dashboard where most widgets are visible, use a `Layout` composable with a custom `MeasurePolicy` for absolute positioning:

```kotlin
@Composable
fun DashboardGrid(
    widgets: ImmutableList<WidgetInstance>,
    gridUnit: Dp = 16.dp,
    content: @Composable () -> Unit,
) {
    Layout(content = content) { measurables, constraints ->
        val placeables = measurables.mapIndexed { i, measurable ->
            val w = widgets[i]
            measurable.measure(
                Constraints.fixed(
                    (w.widthUnits * gridUnit.roundToPx()),
                    (w.heightUnits * gridUnit.roundToPx()),
                )
            )
        }
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEachIndexed { i, placeable ->
                placeable.placeRelative(
                    x = widgets[i].gridX * gridUnit.roundToPx(),
                    y = widgets[i].gridY * gridUnit.roundToPx(),
                    zIndex = widgets[i].zIndex.toFloat(),
                )
            }
        }
    }
}
```

`LazyLayout` adds `SubcomposeLayout` overhead (separate `Composer` per slot, one-frame-behind for new compositions) without benefit when most widgets are visible. Reserve `LazyLayout` only if the canvas becomes scrollable (larger than viewport with significant off-screen widget count).

### Display Refresh Rate Awareness

The architecture must not assume 60Hz. Many target devices (Pixel 7a, Galaxy A54) have 90Hz displays. Some automotive head units run at 120Hz.

- Default to the display's native refresh rate at NORMAL thermal level
- Under thermal pressure, use `Window.setFrameRate()` to request a lower rate
- Track actual display refresh rate via `Display.getRefreshRate()` and adapt frame budgets accordingly
- Frame duration budgets: 16.6ms at 60Hz, 11.1ms at 90Hz, 8.3ms at 120Hz — CI gates must account for the target device's refresh rate

### Pixel Shift for OLED Burn-in

OLED burn-in pixel shift (1-2px every 5 minutes) is applied as a single `graphicsLayer` translation on the outermost `DashboardLayer` composable, NOT per-widget. Per-widget shift invalidates every widget's RenderNode. A single grid-level shift invalidates only the grid container:

```kotlin
@Composable
fun DashboardLayer(pixelShift: State<IntOffset>) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationX = pixelShift.value.x.toFloat()
                translationY = pixelShift.value.y.toFloat()
            }
    ) {
        DashboardGrid(...)
    }
}
```

## 7. Widget Error Isolation

### Composition Phase Limitation

**Known limitation**: Jetpack Compose has no equivalent of React's `ErrorBoundary` for the composition phase. If `renderer.Render()` throws during composition (e.g., NPE in a `remember` block), the exception propagates to the parent composition and crashes the app. The error boundary covers:
- **Draw-phase errors**: Via a wrapped `DrawScope` that delegates all calls inside try/catch
- **Effect errors**: Via `LocalWidgetScope` with `SupervisorJob` + `CoroutineExceptionHandler`
- **NOT composition-phase errors**: These are mitigated by contract tests verifying `Render()` with null/empty/malformed data, and by crash recovery (safe mode after 3 crashes in 60s)

### WidgetSlot Error Boundary

```kotlin
@Composable
fun WidgetSlot(widget: WidgetInstance, renderer: WidgetRenderer) {
    var renderError by remember { mutableStateOf<Throwable?>(null) }
    val crashCount = remember { mutableIntStateOf(0) }

    // Supervised scope for widget-internal effects
    val widgetScope = rememberCoroutineScope().let { parentScope ->
        remember(parentScope) {
            CoroutineScope(
                parentScope.coroutineContext +
                SupervisorJob(parentScope.coroutineContext.job) +
                CoroutineExceptionHandler { _, e ->
                    crashCount.intValue++
                    renderError = e
                    errorReporter.reportWidgetCrash(widget, e)
                }
            )
        }
    }

    if (renderError != null || crashCount.intValue >= 3) {
        WidgetErrorFallback(
            widget = widget,
            error = renderError,
            onRetry = {
                renderError = null
                // Re-invoke renderer on next composition
            },
        )
    } else {
        CompositionLocalProvider(
            LocalWidgetScope provides widgetScope,
            LocalWidgetErrorHandler provides { e ->
                crashCount.intValue++
                renderError = e
                errorReporter.reportWidgetCrash(widget, e)
            },
        ) {
            renderer.Render(...)
        }
    }
}
```

`WidgetErrorFallback` displays "Tap to retry" that resets `renderError` to null. After 3 accumulated crashes, the fallback persists without retry option — the widget is considered broken for this session.

Per-widget data binding jobs use `CoroutineExceptionHandler` — a failed provider reports the error via `widgetStatus` but does not cancel sibling bindings.

### LaunchedEffect Error Boundary

Standard composition error boundaries do NOT catch exceptions thrown inside `LaunchedEffect`, `SideEffect`, or `DisposableEffect`. These escape to the parent coroutine scope and can crash the app. Every widget must use a supervised `WidgetCoroutineScope` for internal effects:

```kotlin
val LocalWidgetScope = staticCompositionLocalOf<CoroutineScope> {
    error("No WidgetCoroutineScope provided")
}
```

**Mandatory pattern**: All widget `LaunchedEffect` calls must use `LocalWidgetScope.current` instead of the default composition scope. This is enforced by lint rule and documented in the plugin API contract. Widgets that launch coroutines via the default scope bypass error isolation.

## 8. Plugin System

The core architectural pattern. Packs are decoupled feature modules that register widgets, data providers, and themes via contracts.

### Contracts (`:core:plugin-api`)

**`WidgetRenderer`** — extends `WidgetSpec` + `Gated`:
- `typeId: String` (e.g., `"core:speedometer"`)
- `compatibleSnapshots: Set<KClass<out DataSnapshot>>` — snapshot types this widget can consume (e.g., `setOf(SpeedSnapshot::class, AccelerationSnapshot::class)`)
- `settingsSchema: List<SettingDefinition<*>>` — declarative settings UI
- `getDefaults(context: WidgetContext): WidgetDefaults` — context-aware sizing
- `@Composable Render(isEditMode, style, settings, modifier)` — widget data accessed via `LocalWidgetData.current`
- `supportsTap`, `onTap()`, `priority`
- `accessibilityDescription(data: WidgetData): String` — semantic description for TalkBack (e.g., "Speed: 65 km/h")

**`DataProvider`** — extends `DataProviderSpec`:
- `snapshotType: KClass<out DataSnapshot>` — the sealed subtype this provider emits (e.g., `SpeedSnapshot::class`)
- `provideState(): Flow<DataSnapshot>` — reactive data stream (actual emission type matches `snapshotType`)
- `schema: DataSchema` — describes output shape, staleness thresholds, and display metadata
- `setupSchema: List<SetupPageDefinition>` — declarative setup wizard
- `subscriberTimeout: Duration` — how long to keep alive after last subscriber (default 5s)
- `firstEmissionTimeout: Duration` — max time to wait for first emission (default 5s)
- `isAvailable`, `connectionState: Flow<Boolean>`, `connectionErrorDescription: Flow<String?>`

**`ActionableProvider`** — extends `DataProvider` for widgets that support bidirectional interaction:
- `onAction(action: WidgetAction)` — receives actions from bound widgets

**`ThemeProvider`**:
- `themes: List<DashboardThemeDefinition>`

**`Gated`** interface — implemented by renderers, providers, themes, and individual settings:
- `requiredAnyEntitlement: Set<String>?` — OR logic (any one entitlement grants access)

### Widget-to-Widget Communication

Widgets never communicate directly. Interaction flows through the binding system:

```kotlin
// In :core:plugin-api
sealed interface WidgetAction {
    data class Tap(val widgetId: String) : WidgetAction
    data class MediaControl(val command: MediaCommand) : WidgetAction
    data class TripReset(val tripId: String) : WidgetAction
    // Extensible — packs add their own action types
}

interface ActionableProvider : DataProvider {
    fun onAction(action: WidgetAction)
}
```

`WidgetDataBinder` routes actions from widgets to their bound `ActionableProvider`. Widgets call `onAction()` via the binder — they never hold a reference to the provider directly. This maintains full decoupling: a media widget doesn't know whether its controls are routed to a local `MediaSession` provider or a BLE-connected device provider.

### Data Type Extensibility

Data types are **string identifiers**, not a closed enum. This allows packs to define new data types without modifying `:core:plugin-api`:

```kotlin
// Data types are string constants, not an enum
object DataTypes {
    const val SPEED = "SPEED"
    const val TIME = "TIME"
    const val ORIENTATION = "ORIENTATION"
    // ... core types
}

// Packs register DataTypeDescriptor alongside providers
data class DataTypeDescriptor(
    val typeId: String,           // e.g., "sg-erp:toll-rate"
    val displayName: String,      // "ERP Toll Rate"
    val unit: String?,            // "SGD"
    val formatting: FormatSpec?,  // decimal places, prefix/suffix
)
```

Matching between widgets and providers is by `KClass` equality on snapshot types. A widget declaring `compatibleSnapshots = setOf(SpeedSnapshot::class)` binds to any provider whose `snapshotType == SpeedSnapshot::class`, regardless of which pack the provider comes from. This replaces string-based matching — `KClass` keys are compiler-enforced and cannot contain typos.

### Snapshot Type Validation

Runtime validation in `WidgetRegistry` catches unresolvable snapshot type declarations at startup:

```kotlin
// At registration time
fun validateBindings(providers: Set<DataProvider>, widgets: Set<WidgetRenderer>) {
    val providedTypes = providers.map { it.snapshotType }.toSet()
    widgets.forEach { widget ->
        widget.compatibleSnapshots.forEach { type ->
            if (type !in providedTypes) {
                logger.warn(LogTag.BINDING, "widgetTypeId" to widget.typeId, "missingSnapshot" to type.simpleName) {
                    "Widget declares snapshot type '${type.simpleName}' but no provider emits it"
                }
            }
        }
    }
}
```

A CI fitness test validates this exhaustively: after full DI graph assembly, every `compatibleSnapshots` entry must match at least one registered provider's `snapshotType`. Unmatched types fail the build.

### Setup Schema Types

`SetupDefinition` sealed interface supporting:
- `RuntimePermission` — runtime permission request with satisfaction check
- `SystemServiceToggle` — Bluetooth/Location on/off toggle
- `SystemService` — service availability check
- `DeviceScan` — CDM device discovery
- `Instruction` — guidance text with icon
- `Info` — read-only information
- `Setting` — wraps a `SettingDefinition<*>` for setup-time configuration

### Settings Schema Types

`SettingDefinition<T>` with key, label, description, default value, visibility predicate, group, and entitlement gating. Rendered automatically:

| Definition Type | Rendered As |
|---|---|
| Boolean | Toggle switch |
| Int/Float with presets | FlowRow button group |
| Enum (2-10 options) | FlowRow button group |
| Enum (11-25 options) | Dropdown menu |
| Timezone | Hub route -> searchable timezone list |
| DateFormat | Hub route -> format list with live preview |
| AppPicker | Hub route -> installed app grid |
| Info | Read-only text row |
| Instruction | Text with icon |

No sliders — they interfere with HorizontalPager gestures.

### Plugin API Versioning

```kotlin
@PluginApiVersion(major = 1, minor = 0)
interface WidgetRenderer { ... }
```

`@PluginApiVersion` serves as a documentation annotation marking the current contract version. All packs are first-party compiled modules that ship in the same APK — there will never be a V1 pack running against a V2 shell in production. Adapter patterns and backward-compatibility windows add complexity for a future (third-party plugins) that is explicitly Out of Scope.

If third-party packs are ever introduced, re-introduce versioning with adapter wrapping at that time.

### KSP Code Generation

`@DashboardWidget` and `@DashboardDataProvider` annotations -> KSP processor generates `PackManifest` classes with `generatedWidgetRefs` / `generatedProviderRefs`. These wire into Hilt multibinding sets automatically.

The processor:
- Validates `typeId` uniqueness within a module (cross-module uniqueness enforced at runtime by the registry)
- Reports clear errors when annotations are misused (not buried in KSP stack traces)
- Runs as a single processor alongside the agentic-processor to minimize KSP passes

### Runtime Discovery

`WidgetRegistry` and `DataProviderRegistryImpl` index the injected `Set<WidgetRenderer>` and `Set<DataProvider>` for lookup by type ID or data type. No dynamic loading — all compiled in, gated by entitlements at runtime.

Duplicate `typeId` detection: if two packs register the same `typeId`, the registry logs an error and uses the higher-priority registration. This is a development error, not a user-facing concern.

### Provider Fallback

When a widget's assigned provider becomes unavailable (disconnected, error, uninstalled), `WidgetDataBinder` automatically falls back to the next available provider for the same snapshot type:

**Priority order**: user-selected > hardware (BLE device) > device sensor > network

```kotlin
class WidgetDataBinder {
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

    private fun resolveProvider(snapshotType: KClass<out DataSnapshot>): DataProvider? {
        // Try user-selected, then fall back through priority chain
        return userSelectedProviders[snapshotType]?.takeIf { it.isAvailable }
            ?: findByPriority(snapshotType, ProviderPriority.HARDWARE)
            ?: findByPriority(snapshotType, ProviderPriority.DEVICE_SENSOR)
            ?: findByPriority(snapshotType, ProviderPriority.NETWORK)
    }
}
```

Fallback is per-slot: if the hardware speed provider disconnects but the accelerometer is fine, only the speed slot falls back — acceleration continues from its original provider. The fallback indicator is transient (5s) and non-blocking.

## 9. Dashboard-as-Shell Pattern

The dashboard is **not a navigation destination**. It lives as a persistent layer below the NavHost:

```
Box {
    Layer 0: DashboardLayer (always present, live canvas)
    Layer 1: OverlayNavHost (settings, pickers, dialogs)
}
```

Benefits:
- Dashboard state never destroyed on navigation
- Overlays stack visually on top
- Suspendable routes (Settings, PackBrowser, WidgetPicker) pause dashboard state collection to reduce CPU — identified via **type-safe route matching**, not string comparison

## 10. Navigation

Type-safe routes via `sealed interface Route` with `@Serializable`:

| Route | Purpose |
|---|---|
| `Empty` | Dashboard only, no overlay |
| `Settings` | Main settings sheet |
| `ThemeSelector(isDark)` | Theme picker grid |
| `ThemeModeSelector` | Auto-switch mode selector |
| `WidgetPicker` | Widget picker with previews |
| `PackBrowser` | Installed packs listing |
| `WidgetSettings(widgetId, ...)` | Per-widget settings (3-page pager) |
| `ThemeEditor(baseThemeId, ...)` | Theme Studio |
| `TimezoneSelector(...)` | Searchable timezone picker |
| `DateFormatSelector(...)` | Date format picker with preview |
| `AppSelector(...)` | App picker for Shortcuts widget |
| `ProviderSetup(providerId)` | Schema-driven provider setup wizard |
| `Diagnostics(...)` | Device connection diagnostics |

Confirmation dialogs (delete widget, reset dashboard) use composable state (`var showConfirm by remember`), not navigation destinations — avoids back-stack complications.

Permission requests are handled as side-effects within the calling screen, not navigation destinations.

### Animation Profiles

- **Sheets** (button bars): spring slide from bottom
- **Hubs** (fullscreen overlays): bouncy spring scale 0.85->1.0 + fade
- **Previews** (settings sheets): balanced spring slide up
- **Dialogs**: fade scrim + scale card
- **PackBrowser**: direction-sensitive (horizontal slide from Settings, crossfade from WidgetInfo)
- **Shared element transitions**: Pack card <-> Widget info

Deep links at `https://dqxn.app/open/*`.

### Deep Link Security

All deep links are secured via Digital Asset Links:
- `/.well-known/assetlinks.json` hosted at `dqxn.app` with app signing certificate fingerprint
- `autoVerify="true"` on all `<intent-filter>` elements in the manifest
- All deep link parameters are validated at the `NavHost` level before routing — no raw parameter passthrough to coordinators
- Unknown or malformed parameters result in navigation to the dashboard (safe fallback), not a crash

### Predictive Back Gesture

Android 14+ predictive back is fully supported:
- `android:enableOnBackInvokedCallback="true"` in manifest
- Overlay sheets use `PredictiveBackHandler` for animated dismiss previews
- Confirmation dialogs in edit mode register `OnBackPressedCallback` to intercept back and prompt for save/discard
- Dashboard Layer 0 does not consume back — back from the root overlay exits the app

## 11. Grid System

- **Grid unit**: 16dp
- **Viewport**: dynamically computed from screen dp dimensions
- **Orientation-locked**: viewport dimensions are stable (no reflow on rotation)
- **Rendering**: filters to viewport-intersecting widgets only (zero cost for off-screen)
- **Placement**: `GridPlacementEngine` scans at 2-unit steps, minimizes overlap area, prefers center positioning
- **Interactions**: drag-to-move, 4-corner resize handles (76dp minimum touch targets, quadrant-based detection), focus animation (translate to center + scale to 38% viewport height)
- **Edit mode**: wiggle animation (+/-0.5 degree rotation at 150ms via `graphicsLayer`), animated corner brackets, delete/settings buttons with spring animations
- **Overlap**: tolerated but not encouraged. Tap targets resolve to the highest z-index widget. Glow effects render independently per z-layer.
- **Pixel shift**: OLED burn-in mitigation applied at the grid level (see Section 6, Pixel Shift for OLED Burn-in)

## 12. Widget Container

`WidgetContainer` (`:core:widget-primitives`) — shared wrapper applied to all widgets:

1. **Error boundary** (catches render failures, shows fallback — see Section 7 for scope and limitations)
2. **`graphicsLayer` isolation** (own RenderNode, hardware-accelerated transforms)
3. **Glow padding** (4/8/12dp responsive by widget size) — `RenderEffect.createBlurEffect` (GPU shader, always available on minSdk 31). Under thermal pressure, falls back to `RadialGradient` approximation (no blur shader cost).
4. **Rim padding** (0-15% of min dimension, user-controlled via `rimSizePercent`)
5. **Layer stack**: error boundary -> graphicsLayer -> outer clip -> glow draw -> warning background -> SOLID/TRANSPARENT fill -> border overlay -> content area

Per-widget style properties persisted in `SavedWidget`:
- `backgroundStyle` (SOLID/TRANSPARENT), `opacity` (0-1), `showBorder`, `hasGlowEffect`
- `cornerRadiusPercent` (0-100), `rimSizePercent` (0-100)

### Widget Accessibility

Every widget root composable includes semantic content descriptions:

```kotlin
// In WidgetContainer
Modifier.semantics {
    contentDescription = renderer.accessibilityDescription(currentData)
    // e.g., "Speed: 65 km/h", "Time: 2:45 PM", "Battery: 82%, charging"
}
```

`accessibilityDescription(data: WidgetData): String` is defined on `WidgetRenderer` as a required contract method. For read-only widgets (speedometer, clock, compass), this provides a spoken summary of the current value. For interactive widgets (media controls, shortcuts), additional `Role` and action semantics are added.

## 13. Theme System

### Dual-Theme Model

Users maintain separate `lightTheme` and `darkTheme` selections. `ThemeAutoSwitchEngine` determines which is active.

### 5 Auto-Switch Modes

| Mode | Behavior | Premium |
|---|---|---|
| LIGHT | Always light | No |
| DARK | Always dark | No |
| SYSTEM | Follows OS setting | No |
| SOLAR_AUTO | GPS sunrise/sunset calculation | Yes (themes pack) |
| ILLUMINANCE_AUTO | Ambient light sensor vs configurable lux threshold | Yes (themes pack) |

### Theme Sources

- **Free**: 2 themes (Slate, Minimalist)
- **Themes pack**: 22 premium JSON-driven themes
- **Custom**: User-created via Theme Studio, max 12, stored in Proto DataStore

**Selector ordering**: Free themes are always listed first in theme selectors, followed by custom themes, then premium themes (gated with preview). This ensures users always see usable options at the top regardless of entitlement state.

### Theme Definition

`DashboardThemeDefinition` (annotated `@Immutable`): `primaryTextColor`, `secondaryTextColor`, `accentColor`, `highlightColor`, `widgetBorderColor`, `backgroundBrush` (gradient), `widgetBackgroundBrush` (gradient), `isDark`, plus gradient specs for serialization. Default fallback: "Cyberpunk".

Spacing tokens (`SpaceXXS` through `SpaceXL`, `ScreenEdgePadding`, `ItemGap`, `SectionGap`, `CardSize`, etc.) and typography scale (`title`, `itemTitle`, `label`, `description`, `caption`, `buttonLabel`) are defined in `:core:design-system` as theme extension properties, keeping pack rebuilds isolated from settings UI changes.

### Theme JSON Format

```json
{
  "id": "tron",
  "name": "The Grid",
  "isDark": true,
  "colors": { "primary", "secondary", "accent", "highlight", "widgetBorder" },
  "gradients": {
    "background": { "type": "radial|vertical|horizontal|linear|sweep", "stops": [...] },
    "widgetBackground": { "type": "...", "stops": [...] }
  },
  "requiredAnyEntitlement": ["themes"]
}
```

### Theme Studio

Create/edit custom themes with: name, 5 color fields, 2 gradient fields (type + 2-5 color stops each). Auto-saves on every change when dirty. Undo restores to entry state. Max 12 custom themes.

### Entitlement Gating

- All themes available for **preview** regardless of entitlement — users can see what they're buying
- Gating at **persistence** layer — can't save a premium theme without the entitlement
- Reactive revocation — losing an entitlement auto-reverts affected selections to free defaults

## 14. Persistence

### Proto DataStore Architecture

Structured data uses Proto DataStore for type-safe, binary-efficient persistence:

```protobuf
message DashboardCanvas {
  int32 schema_version = 1;
  repeated SavedWidget widgets = 2;
}

message SavedWidget {
  string id = 1;
  string type = 2;
  int32 grid_x = 3;
  int32 grid_y = 4;
  int32 width_units = 5;
  int32 height_units = 6;
  string background_style = 7;
  float opacity = 8;
  bool show_border = 9;
  bool has_glow_effect = 10;
  int32 corner_radius_percent = 11;
  int32 rim_size_percent = 12;
  optional string variant = 13;
  map<string, string> settings = 14;  // JSON-encoded values
  repeated string selected_data_source_ids = 15;
  int32 z_index = 16;
}
```

Benefits over JSON-in-Preferences:
- Type-safe schema with generated code
- Binary format (faster serialization, smaller on disk)
- Schema evolution via protobuf field addition (non-breaking)
- Still atomic writes (DataStore guarantee)

### DataStore Corruption Handling

All DataStore instances MUST have explicit corruption handlers. Without them, Proto deserialization failure is an unrecoverable crash:

```kotlin
val layoutDataStore = DataStoreFactory.create(
    serializer = DashboardCanvasSerializer,
    corruptionHandler = ReplaceFileCorruptionHandler { exception ->
        logger.error(LogTag.DATASTORE, "corruption" to true, "file" to "dashboard_layouts") {
            "Layout DataStore corrupted, resetting"
        }
        errorReporter.reportDataStoreCorruption("dashboard_layouts", exception)
        DashboardCanvas.getDefaultInstance() // safe fallback
    },
)
```

This pattern applies to ALL DataStore instances (`dashboard_layouts`, `paired_devices`, `custom_themes`). Each corruption handler resets to its respective default instance and reports the corruption as a non-fatal error with the file name and exception for diagnosis.

### Store Organization

| Store | Type | Contents |
|---|---|---|
| `dashboard_layouts` | Proto | `DashboardCanvas` (widget list + schema version) |
| `paired_devices` | Proto | `Map<definitionId, List<PairedDeviceMetadata>>` |
| `custom_themes` | Proto | User-created theme definitions |
| `user_preferences` | Preferences | Device config, onboarding, themes, status bar, demo mode |
| `provider_settings` | Preferences | Per-provider settings with pack-namespaced keys (`{packId}:{providerId}:{key}`) |
| `connection_events` | Preferences | Rolling list of 50 connection events (JSON) |

### Layout Persistence

Layout saves are debounced at 500ms with atomic writes (write to temp file, rename on success). Corruption detection: if Proto deserialization fails, the `ReplaceFileCorruptionHandler` resets to default and reports via `ErrorReporter`.

### Schema Migration

Versioned via `schema_version` field. Migration transformers are registered per version step (N -> N+1). Unknown fields are preserved (protobuf forward compatibility).

### Preset System

JSON preset files define default widget layouts. `PresetLoader` selects region-appropriate presets via `RegionDetector` (timezone-derived country code). Presets generate fresh UUIDs for all widgets on load.

## 15. Thermal Management

`:core:thermal` provides proactive thermal adaptation:

```kotlin
class ThermalManager(private val powerManager: PowerManager) {
    val thermalLevel: StateFlow<ThermalLevel>  // NORMAL, WARM, DEGRADED, CRITICAL

    init {
        val headroom = powerManager.getThermalHeadroom(10) // 10s for reactive decisions
        if (headroom < 0) {
            // Device doesn't support thermal headroom — fall back to discrete listener
            powerManager.addThermalStatusListener(executor) { status ->
                thermalLevel.value = when (status) {
                    THERMAL_STATUS_NONE, THERMAL_STATUS_LIGHT -> ThermalLevel.NORMAL
                    THERMAL_STATUS_MODERATE -> ThermalLevel.WARM
                    THERMAL_STATUS_SEVERE -> ThermalLevel.DEGRADED
                    else -> ThermalLevel.CRITICAL
                }
            }
        } else {
            // headroom > 0.95 → CRITICAL
            // headroom > 0.85 → DEGRADED
            // headroom > 0.70 → WARM
            // else → NORMAL
        }
    }
}

data class RenderConfig(
    val targetFps: Float,      // 60 → 45 → 30 → 24
    val glowEnabled: Boolean,  // disabled at DEGRADED
)
```

`RenderConfig` is consumed by `DashboardGrid` and `WidgetContainer` to adapt rendering quality before the OS forces thermal throttling.

### Thermal Headroom Usage

- **`getThermalHeadroom(10)`**: Used for reactive frame pacing decisions — 10s lookahead is responsive enough to trigger FPS reduction before hitting a tier boundary
- **`getThermalHeadroom(30)`**: Used only by `ThermalTrendAnalyzer` for predictive degradation analysis where a longer trend window produces more stable predictions

### Supplementary Thermal Signals

Monitor battery temperature via `BatteryManager.EXTRA_TEMPERATURE` as a supplementary signal. Car-mounted phones in sunlight can hit battery thermal limits while the SoC is fine — `getThermalHeadroom()` may report NORMAL while the battery is dangerously hot.

### GPU vs CPU Bottleneck Differentiation

Under thermal pressure, differentiate the bottleneck source to apply the correct mitigation:
- **GPU bottleneck** (long frame time + low CPU time): Reduce shaders/layers — disable glow, reduce `graphicsLayer` usage, simplify gradients
- **CPU bottleneck** (long frame time + high CPU time): Reduce emission rate — throttle provider data, increase debounce intervals

Frame duration analysis uses `FrameMetrics` (API 31+) to separate CPU and GPU work phases.

### Glow Under Thermal Pressure

Since minSdk 31, `RenderEffect.createBlurEffect()` is always available at the API level. The glow rendering tiers are purely thermal:

| Thermal Level | Glow Behavior |
|---|---|
| NORMAL | Full `RenderEffect.createBlurEffect()` — GPU shader |
| WARM | Full `RenderEffect.createBlurEffect()` — GPU shader |
| DEGRADED | `RadialGradient` approximation — no blur shader cost, retains visual hint |
| CRITICAL | Glow fully disabled |

## 16. Driving Mode

`:feature:driving` provides motion detection and safety gating:

```kotlin
class DrivingModeDetector {
    val isDriving: StateFlow<Boolean>
    // Speed > 0 for 3s → driving
    // Speed = 0 for 5s → parked
}
```

When `isDriving == true`:
- Edit mode is disabled (button hidden, long-press suppressed)
- Widget picker, settings, Theme Studio are inaccessible
- Interactive widgets (Shortcuts tap, Media Controller controls) remain functional
- All touch targets enforce 76dp minimum

## 17. Entitlement System

| ID | Scope |
|---|---|
| `free` | Core pack — all users (speedometer, clock, date, compass, battery, speed limit, shortcuts) |
| `plus` | Plus pack — trip computer, media controller, G-force, altimeter, weather |
| `themes` | Themes pack — premium themes + Theme Studio + Solar/Illuminance auto-switch |

Regional packs define their own entitlement IDs as needed.

`EntitlementManager` interface: `hasEntitlement(id)`, `getActiveEntitlements()`, `entitlementChanges: Flow<Set<String>>`, `purchaseProduct()`, `restorePurchases()`.

Debug builds provide `StubEntitlementManager` with a "Simulate Free User" toggle for testing entitlement gating.

## 18. DI Scoping

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

## 19. Background Lifecycle

### BLE Connection Management

- **Foreground service** (`FOREGROUND_SERVICE_CONNECTED_DEVICE`) starts when a paired device is expected and the app is active
- Service maintains BLE connection and `ConnectionStateMachine` state
- When app backgrounds: service continues running (required for CDM auto-wake)
- `REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE` allows CDM to wake the app when a paired device appears
- Android 14+ `ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE` is declared

### Connection State Machine

```
Idle → Searching → Connecting → Connected → Disconnected → Searching (retry)
                                         → Error → Searching (retry, with backoff)
                                         → Error → Idle (max retries exhausted)
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

## 20. Alerts & Notifications

### Connection Notification

- Channel: `device_connection`, low importance, silent, no badge
- Shows on device connect ("Connected to {deviceName}"), dismissed on disconnect
- CDM handles background wakeups — no foreground service needed for notification alone

### Alert Sound Manager

Configurable per-alert: SILENT / VIBRATE / SOUND mode. Custom sound URIs supported. TTS readout via Android `TextToSpeech` (500ms delay to avoid overlap with ringtone). Vibration patterns per alert type.

## 21. Startup Optimization

### Initialization Order

1. **Hilt application** — DI graph construction
2. **Proto DataStore** — layout and preferences loaded (binary, fast)
3. **Eager singletons** — `ThemeAutoSwitchEngine`, `ThermalManager`, `AnrWatchdog`, `CrashContextProvider` triggered eagerly from `Application.onCreate()` after Hilt setup via `@Inject constructor` + `@Singleton` scope
4. **WidgetRegistry / DataProviderRegistry** — index injected sets
5. **First composition** — dashboard renders with layout data
6. **Widget bindings** — per-widget data flows established

**Why not App Startup for Hilt-managed components**: App Startup initializers run during `ContentProvider.onCreate()`, which executes BEFORE `Application.onCreate()` and therefore before Hilt sets up the DI graph. Components like `ThemeAutoSwitchEngine` depend on Hilt-injected dependencies (DataStore, entitlements, sensor managers) and cannot be created in a ContentProvider context.

The solution: Use `@Inject constructor` with `@Singleton` scope and trigger initialization eagerly from `Application.onCreate()` after `super.onCreate()` completes Hilt setup:

```kotlin
@HiltAndroidApp
class DqxnApplication : Application() {
    // Hilt injects these — eagerly fetching them in onCreate() forces initialization
    @Inject lateinit var themeAutoSwitchEngine: ThemeAutoSwitchEngine
    @Inject lateinit var thermalManager: ThermalManager
    @Inject lateinit var anrWatchdog: AnrWatchdog
    @Inject lateinit var crashContextProvider: CrashContextProvider

    override fun onCreate() {
        super.onCreate() // Hilt DI graph is ready after this
        // Fields above are now injected and initialized
        anrWatchdog.start()
        crashContextProvider.install()
    }
}
```

App Startup is retained ONLY for non-DI components that genuinely need ContentProvider-phase initialization: WorkManager configuration and Firebase Crashlytics early init (mapping provider must initialize before Hilt to capture DI graph construction crashes).

### Baseline Profiles

`:baselineprofile` module generates profiles for:
- Dashboard cold start -> first frame with widgets
- Widget picker open -> render previews
- Edit mode toggle -> wiggle animation start

Expected improvement: 20-40% reduction in cold-start jank (per Android team benchmarks with Reddit, etc.).

### Shader Prewarming

On first launch (and after GPU driver updates), every `RenderEffect`, `Brush.sweepGradient`, and custom `Path` render triggers shader compilation — 50-200ms per unique shader. With 12 widget types, first-frame jank can reach 600ms+.

Mitigation:
1. **Pre-warm shaders during the splash screen** by rendering a 1x1dp version of each widget type's draw operations in an offscreen `Canvas`. This triggers shader compilation before the dashboard is visible.
2. **Defer glow enable** — `RenderEffect.createBlurEffect()` is not applied until 2-3 frames after first meaningful paint. The blur shader is the most expensive to compile, and deferring it prevents a visible hitch on the first dashboard frame.
3. **Track first-frame-after-install duration** separately from steady-state metrics in macrobenchmarks. This metric captures shader compilation overhead that disappears on subsequent launches (shaders are cached by the GPU driver).

### Crash Recovery / Safe Mode

Track crash counts in `SharedPreferences` (NOT DataStore — `SharedPreferences` is readable synchronously before DataStore initializes):

```kotlin
class CrashRecovery(private val context: Context) {
    private val prefs = context.getSharedPreferences("crash_recovery", MODE_PRIVATE)

    fun recordCrash() {
        val now = SystemClock.elapsedRealtime()
        val crashes = getRecentCrashes(windowMs = 60_000L)
        crashes.add(now)
        prefs.edit().putString("crashes", crashes.joinToString(",")).apply()
    }

    fun shouldEnterSafeMode(): Boolean {
        return getRecentCrashes(windowMs = 60_000L).size > 3
    }

    fun clearAfterStableUptime() {
        // Called 10s after successful dashboard render
        prefs.edit().remove("crashes").apply()
    }
}
```

Safe mode behavior:
- Minimal layout: clock widget only
- Banner at top: "DQXN recovered from repeated crashes. Reset layout or report issue?"
- Two actions: "Reset Layout" (load default preset) and "Report" (open crash log share)
- Exit safe mode after user action or next clean launch

## 22. Edge-to-Edge & Window Management

### Edge-to-Edge Rendering

Required by targetSdk 36. `enableEdgeToEdge()` in `onCreate()`:

```kotlin
class DashboardActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // ...
    }
}
```

**Layer 0 (Dashboard)**: Draws behind all system bars. No inset padding — the dashboard canvas uses the full display area. Status bar and navigation bar overlap the dashboard content.

**Layer 1 (Overlays)**: Respects `systemBars` and `ime` insets via `Modifier.windowInsetsPadding()`. Settings sheets, widget picker, and dialogs do not render behind system chrome.

### Status Bar Toggle

User-configurable status bar visibility:

```kotlin
val controller = WindowCompat.getInsetsController(window, window.decorView)
if (showStatusBar) {
    controller.show(WindowInsetsCompat.Type.statusBars())
} else {
    controller.hide(WindowInsetsCompat.Type.statusBars())
    controller.systemBarsBehavior =
        WindowInsetsControllerCompat.BEHAVIOR_TRANSIENT_BARS_BY_SWIPE
}
```

Transient bars appear on swipe from edge and auto-hide after a few seconds.

### Multi-Window

`resizeableActivity="false"` in manifest. The dashboard is a fullscreen-only experience — split-screen and freeform modes are not supported. Despite this flag, some OEMs and launcher interactions can still cause window size changes. `DashboardGrid` re-runs viewport culling on any window size change via `BoxWithConstraints` or `onGloballyPositioned`.

## 23. Observability (`:core:observability`)

A non-Compose, Firebase-free module providing structured logging, distributed tracing, metrics collection, health monitoring, and crash/error reporting **interfaces**. No Compose compiler applied. No Firebase dependency — all Firebase-specific implementations live in `:core:firebase`.

This module defines:
- `DqxnLogger` + `LogSink` — structured logging with sink pipeline
- `DqxnTracer` + `TraceContext` — coroutine-context-propagated tracing
- `MetricsCollector` — pre-allocated atomic counters
- `CrashReporter` — interface for crash/non-fatal reporting (Firebase Crashlytics impl in `:core:firebase`)
- `CrashMetadataWriter` — interface for custom key setting on crash reports
- `ErrorReporter` — interface for structured non-fatal error reporting with context
- `PerformanceTracer` — interface for network/custom trace recording (Firebase Perf impl in `:core:firebase`)
- `WidgetHealthMonitor`, `ThermalTrendAnalyzer`, `AnrWatchdog` — health monitoring

### Crash & Error Reporting Interfaces

```kotlin
// In :core:observability — no Firebase dependency

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

Feature modules call these interfaces. `:core:firebase` provides the Firebase-backed implementations. Debug builds can swap in logging/no-op implementations via Hilt module override.

### Self-Protection

Every `LogSink.write()` implementation is wrapped in try/catch. The observability system must never crash the app:

```kotlin
class SafeLogSink(private val delegate: LogSink) : LogSink {
    override fun write(entry: LogEntry) {
        try {
            delegate.write(entry)
        } catch (_: Exception) {
            // Swallow — observability must not crash the app
        }
    }
}
```

The `AnrWatchdog` thread uses `Thread.setDefaultUncaughtExceptionHandler` to log the exception and restart the watchdog loop rather than crashing.

### DqxnLogger

```kotlin
interface DqxnLogger {
    fun log(level: LogLevel, tag: LogTag, message: String, throwable: Throwable? = null, fields: ImmutableMap<String, Any> = persistentMapOf())
}

// Zero-allocation inline extensions with structured fields — disabled calls are free
inline fun DqxnLogger.debug(tag: LogTag, vararg fields: Pair<String, Any>, message: () -> String) {
    if (isEnabled(LogLevel.DEBUG, tag)) {
        log(LogLevel.DEBUG, tag, message(), fields = fields.toMap().toImmutableMap())
    }
}

inline fun DqxnLogger.warn(tag: LogTag, vararg fields: Pair<String, Any>, message: () -> String) {
    if (isEnabled(LogLevel.WARN, tag)) {
        log(LogLevel.WARN, tag, message(), fields = fields.toMap().toImmutableMap())
    }
}

inline fun DqxnLogger.error(tag: LogTag, vararg fields: Pair<String, Any>, message: () -> String, throwable: Throwable? = null) {
    if (isEnabled(LogLevel.ERROR, tag)) {
        log(LogLevel.ERROR, tag, message(), throwable, fields = fields.toMap().toImmutableMap())
    }
}

// Usage:
logger.debug(LogTag.BINDING, "providerId" to "core:gps-speed", "widgetId" to "def456", "elapsedMs" to 12) {
    "Provider bound"
}
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

The `JsonFileLogSink` writes `fields` as top-level JSON keys alongside standard fields. The `RingBufferSink` stores the full `LogEntry` for agentic dump.

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
// SamplingLogSink — per-tag rate limiting (see Log Sampling below)
```

`RingBufferSink` uses a lock-free `AtomicReferenceArray` with atomic index increment. No allocations on the write path beyond the `LogEntry` itself.

### Log Sampling

High-frequency tags (SENSOR, WIDGET_RENDER) can produce thousands of entries per second. Per-tag rate limiting prevents log flooding while preserving visibility:

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

Explicit criteria for what enters the crash report breadcrumb trail (via `CrashReporter.log()`):

- **Always**: Event dispatch, state transitions (thermal, driving, connection FSM), widget add/remove, theme change, provider bind/unbind, overlay navigation
- **Never**: Per-frame metrics, sensor data emissions, draw-phase logs
- **Conditional**: Errors/warnings always; debug-level only when diagnostics mode is active

### TraceContext

```kotlin
class TraceContext(
    val traceId: String,
    val spanId: String,
    val parentSpanId: String?,
) : AbstractCoroutineContextElement(TraceContext) {
    companion object Key : CoroutineContext.Key<TraceContext>
}
```

Cross-coordinator correlation: when a command flows from `DashboardViewModel` through `LayoutCoordinator` to `WidgetBindingCoordinator` to `DataStore`, all log entries share the same `traceId`. Visible in JSON log output and agentic trace dumps.

### Trace Context Propagation

`Channel.send()` does not carry the sender's coroutine context. Trace context is propagated explicitly via `DashboardCommand.traceId`:

```kotlin
sealed interface DashboardCommand {
    val traceId: String? // propagated from sender context
}
```

When processing a command, the trace context is restored:

```kotlin
for (command in commandChannel) {
    val ctx = command.traceId?.let { TraceContext(it, generateSpanId(), null) }
    withContext(ctx ?: EmptyCoroutineContext) {
        routeCommand(command)
    }
}
```

### DqxnTracer

`DqxnTracer.withSpan` is a suspend function that reads and propagates `TraceContext` from `coroutineContext`:

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
            try {
                block()
            } finally {
                val elapsed = SystemClock.elapsedRealtimeNanos() - start
                logger.debug(tag, "span" to name, "elapsedMs" to elapsed / 1_000_000) { name }
            }
        }
    }
}
```

### MetricsCollector

Pre-allocated counters with atomic operations. All known keys are pre-allocated at init from the widget/provider registry — no lazy allocation on hot paths:

```kotlin
class MetricsCollector @Inject constructor(
    widgetRegistry: WidgetRegistry,
    providerRegistry: DataProviderRegistry,
) {
    // Frame timing histogram (buckets: <8ms, <12ms, <16ms, <24ms, <33ms, >33ms)
    // AtomicLongArray for thread safety (data race fix vs plain LongArray)
    private val frameHistogram = AtomicLongArray(6)
    private val totalFrameCount = AtomicLong(0) // for P99 percentile computation

    // Recomposition counter per widget typeId — pre-allocated from registry
    private val recompositionCounts = ConcurrentHashMap<String, AtomicLong>().also { map ->
        widgetRegistry.all().forEach { map[it.typeId] = AtomicLong(0) }
    }

    // Provider latency (last N samples per provider)
    // LongArray-backed ring buffer — RingBuffer<Long> boxes every value
    private val providerLatency = ConcurrentHashMap<String, LongArrayRingBuffer>().also { map ->
        providerRegistry.all().forEach { map[it.providerId] = LongArrayRingBuffer(64) }
    }

    // Memory watermark — periodic reads on health check
    private val memoryWatermarkBytes = AtomicLong(0)

    fun recordFrame(durationMs: Long) { /* atomic bucket increment + totalFrameCount++ */ }
    fun recordRecomposition(typeId: String) { /* atomic increment */ }
    fun recordProviderLatency(providerId: String, latencyMs: Long) { /* ring buffer write */ }
    fun recordMemoryWatermark() {
        val used = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
        memoryWatermarkBytes.updateAndGet { maxOf(it, used) }
    }

    fun snapshot(): MetricsSnapshot { /* read-only copy for dump */ }
}
```

### WidgetHealthMonitor

Periodic liveness checks (every 10s):
- Stale data detection: widget's last data timestamp exceeds staleness threshold
- Stalled render detection: widget's last draw timestamp exceeds 2x target frame interval
- Reports health status to `CrashContextProvider` and agentic dump

### ThermalTrendAnalyzer

Linear regression on recent thermal headroom samples (last 60s at 5s intervals). Predicts time-to-threshold for the next thermal tier. Enables preemptive degradation — start reducing FPS before hitting the threshold, smoothing the visual transition.

```kotlin
class ThermalTrendAnalyzer @Inject constructor(
    private val thermalManager: ThermalManager,
) {
    // Last 12 samples at 5s intervals = 60s window
    private val headroomSamples = RingBuffer<Pair<Long, Float>>(12)

    fun predictTimeToThreshold(targetHeadroom: Float): Duration? {
        if (headroomSamples.size < 3) return null
        // Simple linear regression on (timestamp, headroom) pairs
        val slope = linearRegressionSlope(headroomSamples)
        if (slope <= 0) return null // cooling or stable
        val current = headroomSamples.last().second
        val remaining = targetHeadroom - current
        return (remaining / slope).seconds
    }
}
```

This enables the `FramePacer` to preemptively reduce FPS 10-15s before hitting a thermal tier boundary, smoothing the visual transition instead of an abrupt quality drop. Uses `getThermalHeadroom(30)` for trend analysis (longer window for stable predictions).

### ErrorReporter

`ErrorReporter` is an interface in `:core:observability`. Non-fatal reporting for:
- Widget render crashes (with typeId, last data snapshot, widget settings, stack trace)
- Provider failures (with providerId, connection state, last successful emission)
- DataStore corruption (with file path, schema version, byte count)
- Binding timeouts (with widgetId, providerId, elapsed time)

Forwards to `CrashReporter` — the Firebase-backed implementation in `:core:firebase` calls `FirebaseCrashlytics.recordException()` with structured custom keys.

### DeduplicatingErrorReporter

Decorator wrapping any `ErrorReporter`. Prevents report flooding when a provider fails repeatedly or a widget crashes on every frame:

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

Deduplication window: 60 seconds per unique `(sourceId, exceptionType)` pair.

### AnrWatchdog

Dedicated background thread that pings the main thread every 2s with a 2.5s timeout (gives warning 2.5s before Android's 5s ANR threshold):

```kotlin
class AnrWatchdog @Inject constructor(
    private val ringBufferSink: RingBufferSink,
    private val logger: DqxnLogger,
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    fun start() {
        thread(name = "AnrWatchdog", isDaemon = true) {
            while (true) {
                val responded = CountDownLatch(1)
                mainHandler.post { responded.countDown() }
                if (!responded.await(2500, TimeUnit.MILLISECONDS)) {
                    // Main thread stalled — capture diagnostics
                    val allStacks = Thread.getAllStackTraces() // all threads, not just main — to identify lock holders
                    val mainStack = Looper.getMainLooper().thread.stackTrace
                    val ringBuffer = ringBufferSink.snapshot()
                    val fdCount = File("/proc/self/fd/").listFiles()?.size ?: -1

                    logger.error(LogTag.STARTUP, "fdCount" to fdCount) {
                        "ANR detected: main thread blocked\n" +
                        mainStack.joinToString("\n") { "  at $it" }
                    }

                    // Write diagnostic snapshot to SharedPreferences (survives process death)
                    diagnosticPrefs.edit()
                        .putString("last_anr_stack", mainStack.joinToString("\n"))
                        .putLong("last_anr_time", System.currentTimeMillis())
                        .apply()

                    // Report non-fatal with full context
                    // Immediately post next ping — no Thread.sleep after detection
                }
                Thread.sleep(2_000) // 2s ping interval
            }
        }
    }
}
```

### OOM Detection

OOM kills don't trigger crash reporters. Detection uses a `session_active` flag:

- Set `session_active = true` in `SharedPreferences` in `onStart()`, `false` in `onStop()`
- On cold start, if `session_active == true` AND the crash reporter has no crash for that session → likely OOM or force-stop
- Register `ComponentCallbacks2.onTrimMemory(TRIM_MEMORY_RUNNING_CRITICAL)` and report as non-fatal with memory stats (`Runtime.totalMemory()`, `Runtime.freeMemory()`, native heap via `Debug.getNativeHeapAllocatedSize()`)
- Track memory watermark in `MetricsCollector` via periodic `Runtime.totalMemory() - freeMemory()` reads on health check cycle

### Network Observability

Weather API calls need observability:
- OkHttp `HttpLoggingInterceptor` (debug builds) routed to `DqxnLogger` with `LogTag.PROVIDER`
- `PerformanceTracerInterceptor` records HTTP metrics via `PerformanceTracer.newHttpMetric()` — the Firebase Perf implementation reports to Firebase console; debug builds log locally
- Custom interceptor recording request latency to `MetricsCollector`
- Retry with exponential backoff (max 3 attempts, 1s/2s/4s)
- `Cache` with 30min `max-age` matching the weather refresh interval

```kotlin
val weatherClient = OkHttpClient.Builder()
    .cache(Cache(cacheDir / "weather", 5 * 1024 * 1024)) // 5MB
    .addInterceptor(PerformanceTracerInterceptor(performanceTracer)) // HTTP metrics via PerformanceTracer interface
    .addInterceptor(MetricsInterceptor(metricsCollector, "weather")) // local MetricsCollector
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

Sets crash report custom keys on every significant state transition via the `CrashMetadataWriter` interface (no direct Firebase dependency):

```kotlin
class CrashContextProvider @Inject constructor(
    private val crashMetadata: CrashMetadataWriter,
    private val crashReporter: CrashReporter,
    private val thermalManager: ThermalManager,
    private val metricsCollector: MetricsCollector,
) {
    fun install() {
        // Set session ID for cross-telemetry correlation
        crashMetadata.setKey("session_id", sessionId)
        crashReporter.setUserId(sessionId)

        // Observe and update crash context
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

### Observability Performance Budget

| Operation | Budget |
|---|---|
| Disabled log call | < 5ns (inline + branch) |
| Enabled log call | < 500ns |
| Ring buffer write | < 50ns (atomic) |
| Metrics counter increment | < 20ns |
| Health check cycle (every 10s) | < 1ms |
| Crash context update | < 5ms (state transitions only) |

## 24. Analytics (`:core:analytics`)

Analytics abstraction layer decoupled from implementation:

```kotlin
// In :core:analytics
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
    // ... sealed hierarchy for all tracked events
}
```

All feature modules depend on the `:core:analytics` interface. The Firebase Analytics implementation lives in `:core:firebase`, wired via Hilt `@Binds`. Debug builds use a logging implementation that routes events to `DqxnLogger` with `LogTag.ANALYTICS`.

On init, `AnalyticsTracker.setUserProperty("session_id", sessionId)` is called with the same `sessionId` used in `LogEntry` and `CrashContextProvider` for cross-telemetry correlation.

Privacy: no PII in events. PDPA-compliant. User opt-out toggle in settings kills the tracker at the interface level.

## 25. Firebase Integration (`:core:firebase`)

The sole Firebase dependency point. This module implements all observability and analytics interfaces defined in `:core:observability` and `:core:analytics`. No other module imports Firebase SDKs.

### Dependencies

```
:core:firebase
  → :core:observability (CrashReporter, CrashMetadataWriter, ErrorReporter, PerformanceTracer interfaces)
  → :core:analytics (AnalyticsTracker interface)
  → :core:common
  → firebase-crashlytics
  → firebase-analytics
  → firebase-perf
```

### Hilt Wiring

```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class FirebaseModule {
    @Binds @Singleton
    abstract fun crashReporter(impl: FirebaseCrashReporter): CrashReporter

    @Binds @Singleton
    abstract fun crashMetadata(impl: FirebaseCrashMetadataWriter): CrashMetadataWriter

    @Binds @Singleton
    abstract fun errorReporter(impl: FirebaseErrorReporter): ErrorReporter

    @Binds @Singleton
    abstract fun analytics(impl: FirebaseAnalyticsTracker): AnalyticsTracker

    @Binds @Singleton
    abstract fun performanceTracer(impl: FirebasePerformanceTracer): PerformanceTracer
}
```

Debug builds override with a separate Hilt module in `:app:src/debug/`. The `FirebaseModule` lives in `:core:firebase` (release classpath only) while `DebugObservabilityModule` lives in `:app:src/debug/` — Hilt sees exactly one binding per interface per build variant because `:core:firebase` is excluded from debug dependencies via Gradle `releaseImplementation`:

```kotlin
// In :app:src/debug/
@Module
@InstallIn(SingletonComponent::class)
abstract class DebugObservabilityModule {
    @Binds @Singleton
    abstract fun crashReporter(impl: LoggingCrashReporter): CrashReporter

    @Binds @Singleton
    abstract fun crashMetadata(impl: LoggingCrashMetadataWriter): CrashMetadataWriter

    @Binds @Singleton
    abstract fun errorReporter(impl: LoggingErrorReporter): ErrorReporter

    @Binds @Singleton
    abstract fun analytics(impl: LoggingAnalyticsTracker): AnalyticsTracker

    @Binds @Singleton
    abstract fun performanceTracer(impl: NoOpPerformanceTracer): PerformanceTracer
}
```

### Firebase Crashlytics

`FirebaseCrashReporter` wraps `FirebaseCrashlytics`:

```kotlin
class FirebaseCrashReporter @Inject constructor() : CrashReporter {
    private val crashlytics = FirebaseCrashlytics.getInstance()

    override fun recordNonFatal(e: Throwable, keys: ImmutableMap<String, String>) {
        keys.forEach { (k, v) -> crashlytics.setCustomKey(k, v) }
        crashlytics.recordException(e)
    }

    override fun log(message: String) {
        crashlytics.log(message)
    }

    override fun setUserId(id: String) {
        crashlytics.setUserId(id)
    }
}
```

`FirebaseErrorReporter` wraps `CrashReporter` with structured `ErrorContext` serialization into custom keys and deduplication via `DeduplicatingErrorReporter` decorator.

### Firebase Performance Monitoring

`FirebasePerformanceTracer` implements the `PerformanceTracer` interface:

```kotlin
class FirebasePerformanceTracer @Inject constructor() : PerformanceTracer {
    override fun newTrace(name: String): PerfTrace = FirebasePerfTrace(Firebase.performance.newTrace(name))
    override fun newHttpMetric(url: String, method: String): HttpMetric =
        FirebaseHttpMetric(Firebase.performance.newHttpMetric(url, method))
}
```

**What it covers:**

| Trace | What it measures |
|---|---|
| `cold_start` | App launch → first dashboard frame with widget data |
| `warm_start` | Activity recreate → first dashboard frame |
| `theme_switch` | Theme change command → all widgets re-rendered in new theme |
| `widget_bind` | Widget add → first data emission received (per-widget, attribute: typeId) |
| `layout_save` | Layout mutation → DataStore write complete |
| `preset_load` | Preset selection → layout fully restored with bindings |
| `overlay_open` | Overlay nav event → overlay first frame |
| `weather_fetch` | HTTP metric for weather API (via `PerformanceTracerInterceptor`) |

Custom traces use attributes for segmentation:

```kotlin
performanceTracer.newTrace("widget_bind").apply {
    putAttribute("type_id", widget.typeId)
    putAttribute("pack_id", widget.packId)
    putAttribute("thermal_level", thermalLevel.name)
    start()
}
```

**What it does NOT cover:** Per-frame timing (too high frequency, would overwhelm Firebase Perf — that's `MetricsCollector`'s job). Provider emission latency at sensor frequency (same reason — `MetricsCollector` handles this locally). Firebase Perf is for coarse-grained operation timing visible in the Firebase console, not hot-path instrumentation.

**Sampling:** Firebase Perf automatically samples traces on the server side. No client-side sampling needed — the trace creation overhead is negligible (~1μs per `newTrace()` call) and traces are only created for discrete operations, not per-frame.

### Package Structure

```
app.dqxn.core.firebase                — Hilt module, FirebaseCrashReporter, FirebaseCrashMetadataWriter
app.dqxn.core.firebase.analytics      — FirebaseAnalyticsTracker
app.dqxn.core.firebase.perf           — FirebasePerformanceTracer, PerformanceTracerInterceptor
```

## 26. Memory Leak Prevention

Provider `provideState()` flows MUST use `callbackFlow` with `awaitClose` for all sensor and BLE callbacks:

```kotlin
// Correct: sensor callback with proper cleanup
fun provideState(): Flow<OrientationSnapshot> = callbackFlow {
    val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            trySend(OrientationSnapshot(
                bearing = event.values[0],
                pitch = event.values[1],
                roll = event.values[2],
                timestamp = event.timestamp,
            ))
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    sensorManager.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_UI)

    awaitClose {
        sensorManager.unregisterListener(listener) // MUST unregister
    }
}
```

**Leak assertions in `WidgetBindingCoordinator`**: When a binding job is cancelled (widget removed), assert that `cancelAndJoin()` completes within a timeout (500ms). In debug builds, a timeout indicates a leaked coroutine that isn't responding to cancellation.

**Debug-build periodic heap analysis**: LeakCanary watches all `WidgetRenderer` instances, `DataProvider` instances, and foreground service binders. Periodic heap dumps (every 5min in debug) check for retained widget-related objects.

## 27. Persistence Security

### R8 / ProGuard Rules

Each module owns a `consumer-proguard-rules.pro` file that travels with the module:

```proguard
# :data:proto — keep proto-generated classes
-keep class app.dqxn.data.proto.** { *; }

# :core:plugin-api — keep @Serializable classes
-keepclassmembers class app.dqxn.** {
    @kotlinx.serialization.Serializable <methods>;
}

# :core:plugin-processor — keep KSP-generated PackManifest
-keep class **PackManifest { *; }
-keep class **_GeneratedWidgetRef { *; }
-keep class **_GeneratedProviderRef { *; }
```

Release-build smoke test in CI: assemble release APK, install on managed device, verify dashboard loads with at least one widget rendering data. Catches R8 over-stripping.

## 28. Thermal Management (continued)

(Covered fully in Section 15.)

## 29. DI Scoping (continued)

### Edge-to-Edge & Insets

All DI-scoped components that interact with WindowInsets receive the insets via constructor injection of a `WindowInsetsProvider` interface, not by reading from the Activity directly. This keeps components testable.

## 30. Background Lifecycle (continued)

(Covered fully in Section 19.)

## 31. Testing Strategy

### Test Infrastructure

`DashboardTestHarness` provides a full DSL for coordinator-level testing:

```kotlin
@Test
fun `adding widget triggers binding and persists layout`() = dashboardTest {
    // Arrange
    val speedometer = testWidget(typeId = "core:speedometer")

    // Act
    dispatch(DashboardCommand.AddWidget(speedometer))

    // Assert
    assertThat(layoutState().widgets).hasSize(1)
    assertThat(bindingJobs()).containsKey(speedometer.id)
    turbine(layoutStore.flow) {
        assertThat(awaitItem().widgets).hasSize(1)
    }
}
```

`testFixtures` source sets per module share fakes and builders:
- `testWidget(typeId, size, position, ...)` — widget instance builder
- `testTheme(name, isDark, colors, ...)` — theme definition builder
- `testSnapshot<T>()` — typed snapshot builder (e.g., `testSnapshot<SpeedSnapshot>(speed = 65f)`)
- `FakeLayoutRepository`, `FakeWidgetDataBinder`, `SpyActionProviderRegistry`, `TestDataProvider`

All tests use `StandardTestDispatcher` — no `Thread.sleep`, no real-time delays. Deterministic, fast, agent-friendly.

### Test Tags

Tests are tagged for tiered execution:
- `@Tag("fast")` — pure logic, <100ms per test
- `@Tag("compose")` — requires `ComposeTestRule`
- `@Tag("visual")` — Roborazzi screenshot tests
- `@Tag("integration")` — full DI graph
- `@Tag("benchmark")` — device required

### Test Layers

**Unit Tests** (JUnit5 + MockK + Truth):
- Core types, plugin API contracts, persistence stores, coordinators
- Target: all result/error types, all state machine transitions, all coordinator logic
- `assertWithMessage()` everywhere for clear failure diagnostics

**Flow Tests** (Turbine + StandardTestDispatcher):
- Widget data binding lifecycle (subscribe, emit, unsubscribe, timeout)
- Theme auto-switch transitions
- Entitlement change propagation
- Coordinator state emissions in response to events

**Visual Regression Tests** (Roborazzi + Robolectric):
- Baselines stored in `src/test/resources/screenshots/{testClass}/{testName}.png`
- Use Roborazzi `compare` mode in CI, `record` mode for baseline updates
- Screenshot matrix:
  - Every widget at default size with Cyberpunk + Minimalist themes (34 screenshots)
  - Every theme with speedometer widget (24 screenshots)
  - Error/stale/edit states with reference theme (~50 screenshots)
  - Total: ~108 manageable screenshots

**Interaction Tests** (compose.ui.test + Robolectric):
- Drag-to-move widget gesture sequences
- Resize handle detection and 4-corner behavior
- Long-press to enter edit mode (when parked)
- Widget focus/unfocus tap sequences
- HorizontalPager swipe in settings

**Performance Tests** (Macrobenchmarks on managed device):
- Dashboard cold start to first frame
- Edit mode toggle to wiggle animation start
- Widget picker open to full render
- 12-widget steady-state frame duration

**Contract Tests** (abstract test classes in `:core:plugin-api` testFixtures):
- Every pack extends `WidgetRendererContractTest` and `DataProviderContractTest`
- Validates typeId format, data type declarations, settings schema completeness
- Ensures `Render()` doesn't throw with null/empty data
- Ensures `provideState()` emits within `firstEmissionTimeout`

```kotlin
// In :core:plugin-api testFixtures
abstract class WidgetRendererContractTest {
    abstract fun createRenderer(): WidgetRenderer

    @Test
    fun `typeId follows packId-colon-name format`() {
        val renderer = createRenderer()
        assertThat(renderer.typeId).matches("[a-z]+:[a-z][a-z0-9-]+")
    }

    @Test
    fun `render does not throw with empty data`() {
        // ... compose test rule with empty WidgetData
    }

    @Test
    fun `accessibility description is non-empty`() {
        val desc = createRenderer().accessibilityDescription(WidgetData.Empty)
        assertThat(desc).isNotEmpty()
    }
}
```

```kotlin
// In :core:plugin-api testFixtures
abstract class DataProviderContractTest {
    abstract fun createProvider(): DataProvider

    @Test
    fun `emits within firstEmissionTimeout`() = runTest {
        val provider = createProvider()
        val first = withTimeoutOrNull(provider.firstEmissionTimeout) {
            provider.provideState().first()
        }
        assertWithMessage("Provider must emit within ${provider.firstEmissionTimeout}")
            .that(first).isNotNull()
    }

    @Test
    fun `respects cancellation without leaking`() = runTest {
        val provider = createProvider()
        val job = launch { provider.provideState().collect {} }
        job.cancelAndJoin()
        // Assert no lingering coroutines
    }

    @Test
    fun `snapshotType is a valid DataSnapshot subtype`() {
        val provider = createProvider()
        assertThat(provider.snapshotType).isAssignableTo(DataSnapshot::class)
    }
}
```

**State Machine Tests** (exhaustive + jqwik property-based):
- `ConnectionStateMachine`: exhaustive transition coverage (every state x every event)
- Property-based: random event sequences never reach an illegal state
- Property-based: `ConnectionStateMachine` always terminates (no infinite retry loops)

```kotlin
@Property
fun `connection FSM never reaches illegal state`(
    @ForAll("connectionEvents") events: List<ConnectionEvent>,
) {
    val fsm = ConnectionStateMachine()
    events.forEach { event ->
        val result = fsm.transition(event)
        assertThat(result).isNotInstanceOf(IllegalStateTransition::class.java)
    }
}
```

**Integration Tests** (Hilt testing):
- Full DI graph construction (no missing bindings)
- DataStore read/write roundtrip with real Proto serialization
- Registry population from actual pack modules
- Entitlement flow propagation through real EntitlementManager

**Accessibility Tests**:
- Semantics assertions: every widget root has `contentDescription`
- Contrast verification: theme colors meet WCAG 2.1 AA for critical text
- Touch target size assertions: all interactive elements >= 76dp

**Schema UI Tests** (parameterized):
- Every `SettingDefinition` subtype renders correctly
- Boolean -> toggle, Enum -> button group/dropdown (count-based)
- Visibility predicates hide/show settings correctly
- Entitlement gating disables locked settings

**Chaos Tests**:
- Random provider failures injected during steady-state rendering
- Thermal level spike to CRITICAL during edit mode
- Entitlement revocation during theme preview
- Process death simulation mid-edit
- Rapid widget add/remove cycles
- `ChaosEngine` accepts a `seed: Long` parameter. Failed chaos tests include the seed in the failure message. Re-running with the same seed produces the same failure sequence for deterministic reproduction.

### Mutation Testing

Pitest on critical modules:
- `:feature:dashboard` coordinators
- `:core:thermal`
- `:feature:driving`
- Kill rate target: > 80%

### Fuzz Testing

Jazzer on parsing boundaries:
- JSON theme parsing (malformed gradients, missing fields, extra fields)
- JSON preset parsing
- Proto DataStore deserialization with corrupted bytes

### KSP Processor Tests

Compilation tests for `@DashboardWidget` and `@DashboardDataProvider`:
- Valid annotations produce correct `PackManifest`
- Duplicate `typeId` within module -> compilation error
- Missing required fields -> clear error message (not KSP stack trace)

### CI Gates

| Gate | Threshold |
|---|---|
| P50 frame duration | < 8ms |
| P95 frame duration | < 12ms |
| P99 frame duration | < 16ms |
| Jank rate (frames > 16ms) | < 2% |
| Cold startup time | < 1.5s |
| Compose stability | max 0 unstable classes |
| Non-skippable composables | max 5 |
| Mutation kill rate (critical modules) | > 80% |
| Unit test coverage (coordinators) | > 90% line |
| Release smoke test | Dashboard renders with data |
| P50 trend detection | Alert when P50 increases >20% from 7-day rolling average |

### Agentic Validation Pipeline

Tiered validation for fast feedback loops during development:

```
Tier 1 — Compile Check (~8s):
  ./gradlew :affected:module:compileDebugKotlin --console=plain

Tier 2 — Fast Unit Tests (~12s):
  ./gradlew :affected:module:testDebugUnitTest --console=plain -PincludeTags=fast

Tier 3 — Full Module Tests (~30s):
  ./gradlew :affected:module:testDebugUnitTest --console=plain

Tier 4 — Dependent Module Tests (~60s):
  ./gradlew :dep1:test :dep2:test --console=plain

Tier 5 — Visual Regression (if UI changed, ~45s):
  ./gradlew :affected:module:verifyRoborazzi --console=plain

Tier 6 — Full Suite (before commit):
  ./gradlew assembleDebug test lintDebug --console=plain
```

Agent stops at the first failing tier and fixes before proceeding.

### Agentic Debug Runbook

Structured patterns for diagnosing and fixing common failures:

**Compile Errors**:
- Parse build output for `e:` prefixed lines (Kotlin compiler errors)
- Extract file path and line number from `e: file:///path/File.kt:42:15`
- Read the offending file, fix the error, re-run Tier 1

**Test Failures**:
- Parse output for `FAILED` test names: `> Task :module:testDebugUnitTest FAILED`
- Extract test class and method from JUnit XML or console output
- Read the test file and the source file under test
- Fix the source (not the test) unless the test expectation is wrong
- Re-run Tier 2 or 3

**Visual Regressions**:
- Roborazzi outputs diff images to `build/outputs/roborazzi/`
- Compare `_actual.png` vs `_expected.png` vs `_diff.png`
- If intentional: re-run with `./gradlew :module:recordRoborazzi` to update baselines
- If unintentional: fix the rendering code, re-run Tier 5

**Verification After Fix**:
- Always re-run the failing tier before proceeding to the next
- On success, continue from the next tier (don't restart from Tier 1)
- On new failure in a higher tier, fix and re-run that tier

### Test Principles

- **Deterministic**: `StandardTestDispatcher` everywhere. No `Thread.sleep`, no `runBlocking` with real delays, no flaky timing.
- **Clear failures**: `assertWithMessage("widget should be bound after AddWidget event")` — every assertion explains the expectation.
- **Fast**: < 10s per module for unit tests. Visual tests batched but parallelized.
- **Self-contained**: No test depends on device state, network, or file system outside the test sandbox.

## 32. Build System

### AGP 9.0 — Key Build Changes

AGP 9.0.1 introduces breaking changes from the 8.x line:
- **Built-in Kotlin support** — AGP manages Kotlin compilation directly; the `org.jetbrains.kotlin.android` plugin is replaced by AGP's built-in Kotlin integration
- **New DSL interfaces** — old `BaseExtension` types are gone; convention plugins must use the new DSL exclusively
- **New R8 options** — `-processkotlinnullchecks` for controlling Kotlin null-check processing

### Convention Plugins

Convention plugins (`:build-logic/convention`) enforce shared defaults using AGP 9.0's new DSL. Compose compiler is only applied to modules with UI:

```kotlin
// Modules WITH Compose: :app, :feature:*, :core:widget-primitives, :core:design-system
id("dqxn.android.compose")

// Modules WITHOUT Compose: :core:common, :core:plugin-api, :core:observability, :core:analytics, :core:firebase, :data:*, :core:thermal
// No Compose compiler overhead
```

### KSP Over KAPT

All annotation processing uses KSP (Hilt, plugin-processor, agentic-processor). No KAPT modules — this enables Gradle configuration cache and significantly reduces incremental build time.

### Custom Lint Rules (`:lint-rules`)

| Rule | Severity | Description |
|---|---|---|
| `ModuleBoundaryViolation` | Error | Pack modules importing `:feature:dashboard` |
| `KaptDetection` | Error | Any module applying `kapt` plugin |
| `ComposeInNonUiModule` | Error | Compose imports in `:core:common`, `:core:plugin-api`, `:data:*`, `:core:thermal`, `:core:observability`, `:core:analytics`, `:core:firebase` |
| `MutableCollectionInImmutable` | Warning | `MutableList`/`MutableMap` inside `@Immutable`-annotated types |
| `WidgetScopeBypass` | Error | `LaunchedEffect` without `LocalWidgetScope` in widget renderers |
| `MainThreadDiskIo` | Warning | DataStore/SharedPreferences access without `Dispatchers.IO` |

### Pre-commit Hooks

```bash
# .githooks/pre-commit
# Dependency validation
./gradlew :lint-rules:test --console=plain --warning-mode=summary
# Verify no pack → dashboard imports
grep -r "feature.dashboard" feature/packs/ && exit 1
# Verify no kapt usage
grep -r "kapt" --include="*.kts" */build.gradle.kts && exit 1
```

### Architectural Fitness Functions (CI)

```kotlin
// ModuleBoundaryTest — runs as unit test in :app
@Test
fun `no pack module depends on dashboard`() {
    // Parses Gradle module dependencies, asserts no :feature:packs:* → :feature:dashboard edge
}

// AllWidgetsConformanceTest — runs in :app with full DI graph
@Test
fun `all registered widgets pass contract tests`() {
    widgetRegistry.all().forEach { renderer ->
        assertThat(renderer.typeId).matches("[a-z]+:[a-z][a-z0-9-]+")
        assertThat(renderer.compatibleSnapshots).isNotEmpty()
        assertThat(renderer.accessibilityDescription(WidgetData.Empty)).isNotEmpty()
    }
}

// SnapshotTypeValidationTest — runs in :app with full DI graph
@Test
fun `all widget snapshot types have at least one provider`() {
    val providedTypes = providerRegistry.all().map { it.snapshotType }.toSet()
    widgetRegistry.all().forEach { widget ->
        widget.compatibleSnapshots.forEach { type ->
            assertWithMessage("Widget ${widget.typeId} declares '${type.simpleName}' but no provider emits it")
                .that(providedTypes).contains(type)
        }
    }
}
```

### Gradle Scaffold Tasks

```bash
# Generate a new widget skeleton in a pack
./gradlew :feature:packs:free:scaffoldWidget --name=altimeter --snapshots=AltitudeSnapshot

# Generate a new provider skeleton
./gradlew :feature:packs:plus:scaffoldProvider --name=weather --snapshot=WeatherSnapshot

# Generate a new theme JSON
./gradlew :feature:packs:themes:scaffoldTheme --name="Ocean Breeze" --isDark=false
```

### Build Configuration

```properties
org.gradle.caching=true
org.gradle.configuration-cache=true
org.gradle.parallel=true
ksp.incremental=true
```

Agent builds use `--console=plain --warning-mode=summary` for machine-parseable output.

### Build Time Budget

- Incremental (single file change): < 15s
- Clean build: < 120s
- Measured on reference dev machine, tracked in CI

## 33. Agentic Framework (Debug Only)

ADB broadcast-based automation for testing and development:

```
adb shell am broadcast \
  -a app.dqxn.android.AGENTIC.{command} \
  -n app.dqxn.android.debug/.debug.AgenticReceiver \
  --es params '{"widgetType":"core:speedometer"}'
```

### Commands

| Command | Description |
|---|---|
| `ping` | Health check, returns app state summary |
| `navigate` | Navigate to a Route |
| `back` | Trigger back navigation |
| `query-state` | Return current dashboard state as JSON |
| `capture-start/stop` | Start/stop event capture session |
| `widget-add/remove/move/resize/focus/tap/settings-set` | Widget manipulation |
| `dashboard-reset` | Reset to default preset |
| `theme-set-mode/apply-preset` | Theme operations |
| `preset-load/save/list/remove` | Preset management |
| `list-commands` | List all available commands with parameter schemas |
| `dump-state` | Full dashboard state as structured JSON |
| `dump-metrics` | MetricsCollector snapshot (frame histogram, recomposition counts, provider latency) |
| `dump-log-buffer` | RingBufferSink contents (last 512 entries) as JSON-lines |
| `dump-traces` | Active TraceContext spans and recent completed spans |
| `dump-health` | WidgetHealthMonitor status per widget (stale data, stalled renders) |
| `dump-connections` | ConnectionStateMachine state, paired devices, retry counts |
| `simulate-thermal` | Force thermal level to specified tier (for testing degradation) |

### Large Payload Handling

For large payloads (`dump-state`, `dump-log-buffer`, `dump-metrics`), write to a temp file and return the file path via broadcast result data:

```kotlin
val file = File(context.cacheDir, "dump_${System.currentTimeMillis()}.json")
file.writeText(jsonPayload)
setResultData(file.absolutePath)
// Agent reads via: adb pull /data/data/app.dqxn.android.debug/cache/dump_xxx.json
```

This avoids the broadcast result extras size limit (~1MB) and handles arbitrarily large state dumps.

### Structured State Dumps

All dump commands return JSON. Small payloads use broadcast result extras directly; large payloads use the temp file pattern above:

```json
{
  "command": "dump-state",
  "timestamp": 1708444800000,
  "data": {
    "layout": {
      "widgetCount": 8,
      "widgets": [
        {"id": "abc", "typeId": "core:speedometer", "position": [0, 0], "size": [12, 12]}
      ]
    },
    "theme": {
      "current": "cyberpunk",
      "mode": "DARK",
      "preview": null
    },
    "thermal": {
      "level": "NORMAL",
      "headroom": 0.45,
      "targetFps": 60
    },
    "driving": false,
    "editMode": false
  }
}
```

### Debug Overlay System

Located in `:app:src/debug/`. Activated via agentic command or debug settings toggle. Each overlay is independently toggleable:

| Overlay | Content |
|---|---|
| Frame Stats | Real-time FPS, frame time histogram, jank count |
| Recomposition Visualizer | Color flash on recomposing widgets (green = skip, yellow = recompose, red = full re-layout) |
| Provider Flow DAG | Visual graph of provider -> binder -> widget data flow with live throughput |
| State Machine Viewer | ConnectionStateMachine current state + transition history |
| Thermal Trending | Live graph of thermal headroom with tier boundaries and predicted transition |
| Widget Health | Per-widget badge showing data freshness, error state, binding status |
| Trace Viewer | Active spans and recent completed spans with timing |

### Machine-Readable Log Format

`JsonFileLogSink` writes JSON-lines to `${filesDir}/debug/dqxn.jsonl` (debug builds only). Structured fields from `LogEntry` appear as top-level JSON keys:

```json
{"ts":1708444800123,"level":"DEBUG","tag":"BINDING","trace":"abc123","span":"bind-speedometer","session":"sess-001","msg":"Provider bound","providerId":"core:gps-speed","widgetId":"def456","elapsedMs":12}
{"ts":1708444800456,"level":"WARN","tag":"THERMAL","trace":"xyz789","span":null,"session":"sess-001","msg":"Headroom trending up: 0.72 → 0.78, predicted DEGRADED in 45s"}
```

### Crash Report Enrichment

Widget crashes include structured context via `ErrorReporter` (routed to Crashlytics in release builds):

```kotlin
errorReporter.reportWidgetCrash(
    typeId = "core:speedometer",
    widgetId = "abc-123",
    lastSnapshot = SpeedSnapshot(speed = 65f, ...),
    settings = mapOf("showArcs" to "true", "speedUnit" to "KMH"),
    stackTrace = throwable,
    thermalLevel = ThermalLevel.NORMAL,
    drivingState = false,
)
```

`CaptureSessionRegistry` records TAP, WIDGET_MOVE, WIDGET_RESIZE, NAVIGATION events for replay and analysis.

The receiver is restricted to debug builds only. Demo providers are gated to debug builds to prevent unintended discovery in release.

## 34. Security Requirements

| Requirement | Approach |
|---|---|
| No hardcoded secrets | SDK keys via `local.properties` / secrets gradle plugin — never in source |
| Agentic receiver | Debug builds only, never in release manifest |
| Demo providers | Gated to debug builds, not just UI-hidden |
| BT scan permission | `neverForLocation="true"` — scan data not used for location |
| Deep links | Digital Asset Links verification, parameter validation at NavHost |
| R8 rules | Per-module `consumer-proguard-rules.pro`, release smoke test in CI |
| No NDK | No first-party native code. Compose/Skia/HWUI native paths are standard framework code — `RenderEffect.createBlurEffect()` is the only non-trivial GPU path. If unexplained silent process deaths appear post-launch via `session_active` flag detection, add `firebase-crashlytics-ndk` then. |

## 35. Permissions

| Permission | Purpose |
|---|---|
| `BLUETOOTH_CONNECT` | Device connection (API 31+) |
| `BLUETOOTH_SCAN` (neverForLocation) | Device scanning (API 31+) |
| `INTERNET`, `ACCESS_NETWORK_STATE` | Billing/entitlement validation, weather data |
| `ACCESS_FINE_LOCATION` | GPS speed, altitude, trip accumulation |
| `ACCESS_COARSE_LOCATION` | Solar GPS (passive priority — zero additional battery) |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE` | Device connection service |
| `POST_NOTIFICATIONS` | Connection status notification |
| `VIBRATE` | Alert vibration, haptic feedback |
| `REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE` | CDM auto-wake |

**Required hardware**: `android.software.companion_device_setup` (CDM support mandatory).

The app is fully functional offline — all persistence is local, all sensor data is device-native, hardware data travels over Bluetooth. Internet is required only for entitlement purchase/restore via Play Billing and optional weather data (with graceful offline fallback).
