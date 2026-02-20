# DQXN Architecture

> Target technical architecture for the DQXN Android dashboard platform.

## 1. Overview

DQXN is a single-activity Android dashboard platform that renders real-time telemetry on a fully configurable widget grid. A phone or tablet mounted in a vehicle displays speed, time, compass, ambient light, solar position, and data from feature packs — all through a modular, pack-based architecture.

Packs (widgets, themes, data providers) are fully decoupled from the dashboard shell. Packs know nothing about the shell; the shell discovers packs at runtime via Hilt multibinding. This enables regional feature sets, premium gating, and first-party modular extensibility without touching the core. All packs are compiled modules — there is no runtime plugin loading.

**Identity**: "Life is a dash. Make it beautiful." — The Dashing Dachshund

## 2. Tech Stack

| Category | Choice |
|---|---|
| Language | Kotlin (no Java) |
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
| Debug | LeakCanary, StrictMode (debug builds), Firebase Crashlytics |

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

Convention plugins enforce shared defaults across all modules: compileSdk 36, minSdk 31, JVM target matching AGP/Gradle requirements. **Compose compiler is only applied to modules with UI** (not `:data:*`, not `:core:common`, not `:core:plugin-processor`, not `:core:observability`, not `:core:analytics`).

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

:core:observability
  → :core:common

:core:design-system
  → :core:common
  → :core:widget-primitives

:core:analytics
  → :core:common

Every module → :core:observability
```

This strict boundary means adding or removing a pack never requires changes to the shell.

## 4. Architecture Layers

```
┌─────────────────────────────────────────────────────────┐
│  PRESENTATION                                           │
│  DashboardScreen / DashboardGrid / OverlayNavHost       │
│  Jetpack Compose — stateless renderers                  │
│  collectAsStateWithLifecycle() everywhere                │
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

### MVI Event Flow

Sealed `DashboardEvent` variants are routed to the appropriate coordinator:

```kotlin
sealed interface DashboardEvent {
    // → LayoutCoordinator
    data class AddWidget(...) : DashboardEvent
    data class RemoveWidget(...) : DashboardEvent
    data class MoveWidget(...) : DashboardEvent
    data class ResizeWidget(...) : DashboardEvent

    // → ThemeCoordinator
    data class SetTheme(...) : DashboardEvent
    data class PreviewTheme(...) : DashboardEvent

    // → EditModeCoordinator
    data object EnterEditMode : DashboardEvent
    data object ExitEditMode : DashboardEvent
    data class FocusWidget(...) : DashboardEvent
}
```

All event processing happens on `Dispatchers.Main` via a single `Channel<DashboardEvent>` processed sequentially. This guarantees thread-safe state mutation without locks.

#### ANR Prevention

The event processing channel must not silently stall. Wrap each event handler with a timeout check:

```kotlin
// In DashboardViewModel event loop
for (event in eventChannel) {
    val start = SystemClock.elapsedRealtimeNanos()
    routeEvent(event)
    val elapsed = SystemClock.elapsedRealtimeNanos() - start
    if (elapsed > 1_000_000_000L) { // > 1s
        logger.warn(LogTag.LAYOUT, "Slow event handler: ${event::class.simpleName} took ${elapsed / 1_000_000}ms")
    }
    if (BuildConfig.DEBUG) {
        StrictMode.noteSlowCall("DashboardEvent: ${event::class.simpleName}")
    }
}
```

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

    fun bind(widget: DashboardWidgetInstance) {
        bindings[widget.id]?.cancel()
        bindings[widget.id] = bindingScope.launch(
            CoroutineExceptionHandler { _, e ->
                logger.error(LogTag.SENSOR, "Provider failed for ${widget.typeId}", e)
                updateStatus(widget.id, WidgetStatusCache.ProviderError(e))
                errorReporter.reportNonFatal(e, widgetContext(widget))
            }
        ) {
            binder.bind(widget).collect { data ->
                emitWidgetData(widget.id, data)
            }
        }
    }
}
```

A failed provider reports the error via `widgetStatus` but does not cancel sibling bindings. Without `SupervisorJob`, a `CancellationException` from one child propagates up and cancels all siblings sharing the same parent `Job`.

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

Widget data uses typed sealed subtypes per data type instead of `ImmutableMap<String, Any>`. This eliminates boxing of primitives and provides compile-time safety:

```kotlin
@Immutable
sealed interface DataSnapshot {
    val timestamp: Long
}

@Immutable
data class SpeedSnapshot(
    val speed: Float,
    val acceleration: Float,
    val speedLimit: Float?,
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
// SolarSnapshot, AmbientLightSnapshot, AltitudeSnapshot, AccelerationSnapshot
```

Target: < 4KB allocation per frame in steady state. No `Map` lookups, no `Any` casting, no boxing. Widgets receive their specific snapshot type via the binding system.

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

High-frequency state (needle angle, compass bearing) is read in the draw phase, not the composition phase:

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

All domain types emitted to the UI layer are annotated `@Immutable` or `@Stable`. Collections use `kotlinx-collections-immutable` (`ImmutableList`, `ImmutableMap`):

```kotlin
@Immutable
data class WidgetData(
    val snapshot: DataSnapshot,
    val timestamp: Long,
)
```

A Compose stability configuration file covers cross-module types:

```
// compose_compiler_config.txt
app.dqxn.core.plugin.api.DataSnapshot
app.dqxn.core.plugin.api.SpeedSnapshot
app.dqxn.core.plugin.api.TimeSnapshot
app.dqxn.core.plugin.api.OrientationSnapshot
app.dqxn.core.widget.DashboardThemeDefinition
app.dqxn.data.persistence.SavedWidget
```

Compose compiler metrics (`-Pcompose.compiler.metrics=true`) are audited regularly to catch regressions in skippability.

### Glow Effect Strategy

Since minSdk is 31, `RenderEffect.createBlurEffect()` (GPU shader, no offscreen allocation) is always available. There is no API-level fallback needed.

Strategy:
- **Default**: `RenderEffect.createBlurEffect()` — GPU shader, zero offscreen buffer allocation. With 12 widgets, no additional bitmap memory.
- **Thermal degradation**: Glow effects are the first thing disabled when `thermalLevel >= DEGRADED`. The `RadialGradient` approximation is the thermal-degraded visual path — a simpler gradient that avoids blur shader cost entirely while retaining a hint of the glow aesthetic. This is a performance tier, not an API-level fallback.
- **Widget previews**: glow always disabled (barely visible at preview scale)

### Frame Pacing

Under thermal pressure, the target FPS drops below 60. `FramePacer` uses `Choreographer.postFrameCallback` with a frame skip counter to enforce reduced frame rates:

```kotlin
class FramePacer(
    private val renderConfig: StateFlow<RenderConfig>,
) {
    private var lastRenderNanos = 0L

    fun shouldRender(frameTimeNanos: Long): Boolean {
        val targetIntervalNanos = (1_000_000_000L / renderConfig.value.targetFps).toLong()
        val delta = frameTimeNanos - lastRenderNanos
        if (delta < targetIntervalNanos) return false
        lastRenderNanos = frameTimeNanos
        return true
    }
}

// Usage in DashboardGrid
@Composable
fun DashboardGrid(
    framePacer: FramePacer,
    // ...
) {
    var frameCount by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        val choreographer = Choreographer.getInstance()
        suspendCancellableCoroutine<Nothing> { cont ->
            val callback = object : Choreographer.FrameCallback {
                override fun doFrame(frameTimeNanos: Long) {
                    if (framePacer.shouldRender(frameTimeNanos)) {
                        frameCount = frameTimeNanos // triggers recomposition
                    }
                    choreographer.postFrameCallback(this)
                }
            }
            choreographer.postFrameCallback(callback)
            cont.invokeOnCancellation { choreographer.removeFrameCallback(callback) }
        }
    }
}
```

On API 34+, investigate `SurfaceControl.Transaction.setFrameRate()` for system-level frame rate hints that reduce GPU work without manual frame skipping.

### `movableContentOf` for Widget Reordering

During drag operations in edit mode, widgets are reordered in the grid. Naive reordering causes detach/reattach of composition nodes, losing internal state and triggering full recomposition. Use `movableContentOf` to preserve widget composition across position changes:

```kotlin
@Composable
fun DashboardGrid(widgets: ImmutableList<WidgetInstance>) {
    val movableWidgets = remember(widgets) {
        widgets.associateWith { widget ->
            movableContentOf {
                WidgetSlot(widget)
            }
        }
    }

    widgets.forEach { widget ->
        key(widget.id) {
            Box(modifier = Modifier.offset { widget.position }) {
                movableWidgets[widget]?.invoke()
            }
        }
    }
}
```

For dashboards with many widgets (>12), consider a custom `LazyLayout` implementation for viewport-only composition — widgets outside the visible area are not composed at all, only measured for placement purposes.

## 7. Widget Error Isolation

A widget's `Render()` composable throwing an exception must not crash the dashboard:

```kotlin
@Composable
fun WidgetSlot(widget: WidgetInstance, renderer: WidgetRenderer) {
    var renderError by remember { mutableStateOf<Throwable?>(null) }

    if (renderError != null) {
        WidgetErrorFallback(widget, renderError!!)
    } else {
        // Canvas draw-phase errors caught via wrapped DrawScope
        CompositionLocalProvider(
            LocalWidgetErrorHandler provides { e -> renderError = e }
        ) {
            renderer.Render(...)
        }
    }
}
```

Per-widget data binding jobs use `CoroutineExceptionHandler` — a failed provider reports the error via `widgetStatus` but does not cancel sibling bindings.

### LaunchedEffect Error Boundary

Standard composition error boundaries do NOT catch exceptions thrown inside `LaunchedEffect`, `SideEffect`, or `DisposableEffect`. These escape to the parent coroutine scope and can crash the app. Every widget must use a supervised `WidgetCoroutineScope` for internal effects:

```kotlin
val LocalWidgetScope = staticCompositionLocalOf<CoroutineScope> {
    error("No WidgetCoroutineScope provided")
}

@Composable
fun WidgetSlot(widget: WidgetInstance, renderer: WidgetRenderer) {
    var renderError by remember { mutableStateOf<Throwable?>(null) }

    // Supervised scope for widget-internal effects
    val widgetScope = rememberCoroutineScope().let { parentScope ->
        remember(parentScope) {
            CoroutineScope(
                parentScope.coroutineContext +
                SupervisorJob(parentScope.coroutineContext.job) +
                CoroutineExceptionHandler { _, e ->
                    renderError = e
                    errorReporter.reportWidgetCrash(widget, e)
                }
            )
        }
    }

    if (renderError != null) {
        WidgetErrorFallback(widget, renderError!!)
    } else {
        CompositionLocalProvider(
            LocalWidgetScope provides widgetScope,
            LocalWidgetErrorHandler provides { e -> renderError = e },
        ) {
            renderer.Render(...)
        }
    }
}
```

**Mandatory pattern**: All widget `LaunchedEffect` calls must use `LocalWidgetScope.current` instead of the default composition scope. This is enforced by lint rule and documented in the plugin API contract. Widgets that launch coroutines via the default scope bypass error isolation.

## 8. Plugin System

The core architectural pattern. Packs are decoupled feature modules that register widgets, data providers, and themes via contracts.

### Contracts (`:core:plugin-api`)

**`WidgetRenderer`** — extends `WidgetSpec` + `Gated`:
- `typeId: String` (e.g., `"core:speedometer"`)
- `compatibleDataTypes: List<String>` — what data types the widget can consume
- `settingsSchema: List<SettingDefinition<*>>` — declarative settings UI
- `getDefaults(context: WidgetContext): WidgetDefaults` — context-aware sizing
- `@Composable Render(widgetData, isEditMode, style, settings, modifier)`
- `supportsTap`, `onTap()`, `priority`
- `accessibilityDescription(data: WidgetData): String` — semantic description for TalkBack (e.g., "Speed: 65 km/h")

**`DataProvider`** — extends `DataProviderSpec`:
- `provideState(): Flow<DataSnapshot>` — reactive data stream
- `schema: DataSchema` — describes output shape, data types, and staleness thresholds
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

Matching between widgets and providers is by string equality on data type IDs. A widget declaring `compatibleDataTypes = listOf("SPEED")` binds to any provider whose `DataSchema` includes `"SPEED"`, regardless of which pack the provider comes from.

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

- `PackManifest.apiVersion` declares the version the pack was compiled against
- Shell validates compatibility at registration — incompatible packs log a warning and are excluded
- Semver: additive changes (new optional methods) are minor; breaking changes (signature changes, removed methods) are major

#### WidgetRendererAdapter Pattern

When the plugin API evolves (e.g., V1 -> V2), older packs compiled against V1 still work via adapter wrapping:

```kotlin
// Registry always works with latest version internally
internal class WidgetRendererV1Adapter(
    private val v1: WidgetRendererV1,
) : WidgetRendererV2 {
    override fun accessibilityDescription(data: WidgetData): String =
        "${v1.typeId}: data available"  // sensible default for V1 widgets

    // Delegate all V1 methods
    override val typeId get() = v1.typeId
    override fun Render(...) = v1.Render(...)
    // ...
}
```

KSP generates code targeting the latest API version. Older packs are wrapped at registration time by the registry. This provides one major version of backward compatibility — packs must upgrade within one major version window.

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

When a widget's assigned provider becomes unavailable (disconnected, error, uninstalled), `WidgetDataBinder` automatically falls back to the next available provider for the same data type:

**Priority order**: user-selected > hardware (BLE device) > device sensor > network

```kotlin
class WidgetDataBinder {
    fun bind(widget: WidgetInstance): StateFlow<WidgetData> {
        // Try user-selected provider first
        val selected = widget.selectedDataSourceIds.firstNotNullOfOrNull { id ->
            providerRegistry.get(id)?.takeIf { it.isAvailable }
        }

        // Fall back through priority chain
        val provider = selected
            ?: findByPriority(widget.compatibleDataTypes, ProviderPriority.HARDWARE)
            ?: findByPriority(widget.compatibleDataTypes, ProviderPriority.DEVICE_SENSOR)
            ?: findByPriority(widget.compatibleDataTypes, ProviderPriority.NETWORK)
            ?: return flowOf(WidgetData.Unavailable)

        if (provider != selected) {
            // Show transient fallback indicator on widget
            updateStatus(widget.id, WidgetStatusCache.UsingFallbackProvider(provider.displayName))
        }

        return provider.provideState()
    }
}
```

The fallback indicator is transient (5s) and non-blocking — the widget renders data from the fallback provider immediately.

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
- **Widget limits**: 6 on phone free / 12 phone plus / 20 tablet — enforced
- **Overlap**: tolerated but not encouraged. Tap targets resolve to the highest z-index widget. Glow effects render independently per z-layer.

## 12. Widget Container

`WidgetContainer` (`:core:widget-primitives`) — shared wrapper applied to all widgets:

1. **Error boundary** (catches render failures, shows fallback)
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

Layout saves are debounced at 500ms with atomic writes (write to temp file, rename on success). Corruption detection: if Proto deserialization fails, fall back to last-known-good backup or default preset.

### Schema Migration

Versioned via `schema_version` field. Migration transformers are registered per version step (N -> N+1). Unknown fields are preserved (protobuf forward compatibility).

### Preset System

JSON preset files define default widget layouts. `PresetLoader` selects region-appropriate presets via `RegionDetector` (timezone-derived country code). Presets generate fresh UUIDs for all widgets on load.

## 15. Thermal Management

`:core:thermal` provides proactive thermal adaptation:

```kotlin
class ThermalManager(private val powerManager: PowerManager) {
    val thermalLevel: StateFlow<ThermalLevel>  // NORMAL, WARM, DEGRADED, CRITICAL

    // Based on PowerManager.getThermalHeadroom(30):
    // headroom > 0.95 → CRITICAL
    // headroom > 0.85 → DEGRADED
    // headroom > 0.70 → WARM
    // else → NORMAL
}

data class RenderConfig(
    val targetFps: Float,      // 60 → 45 → 30 → 24
    val glowEnabled: Boolean,  // disabled at DEGRADED
    val maxWidgets: Int,       // reduced at CRITICAL
)
```

`RenderConfig` is consumed by `DashboardGrid` and `WidgetContainer` to adapt rendering quality before the OS forces thermal throttling.

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
| `plus` | Plus pack — trip computer, media controller, G-force, altimeter, weather + widget limit increase |
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
| `MetricsCollector` | `@Singleton` | Pre-allocated counters, app-wide |
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

App Startup is retained ONLY for non-DI components that genuinely need ContentProvider-phase initialization: WorkManager configuration and Crashlytics early init.

### Baseline Profiles

`:baselineprofile` module generates profiles for:
- Dashboard cold start -> first frame with widgets
- Widget picker open -> render previews
- Edit mode toggle -> wiggle animation start

Expected improvement: 20-40% reduction in cold-start jank (per Android team benchmarks with Reddit, etc.).

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

A non-Compose module providing structured logging, distributed tracing, metrics collection, and health monitoring. No Compose compiler applied.

### DqxnLogger

```kotlin
interface DqxnLogger {
    fun log(level: LogLevel, tag: LogTag, message: String, throwable: Throwable? = null)
}

// Zero-allocation inline extensions — disabled calls are free
inline fun DqxnLogger.debug(tag: LogTag, message: () -> String) {
    if (isEnabled(LogLevel.DEBUG, tag)) log(LogLevel.DEBUG, tag, message())
}

inline fun DqxnLogger.warn(tag: LogTag, message: () -> String) {
    if (isEnabled(LogLevel.WARN, tag)) log(LogLevel.WARN, tag, message())
}

inline fun DqxnLogger.error(tag: LogTag, message: () -> String, throwable: Throwable? = null) {
    if (isEnabled(LogLevel.ERROR, tag)) log(LogLevel.ERROR, tag, message(), throwable)
}
```

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
// CrashlyticsBreadcrumbSink — forwards to Crashlytics custom log
// LogcatSink — standard Logcat output (debug builds)
// JsonFileLogSink — JSON-lines format, agent-parseable (debug builds)
// RedactingSink — wraps any sink, scrubs GPS coordinates, BLE MAC addresses
```

`RingBufferSink` uses a lock-free `AtomicReferenceArray` with atomic index increment. No allocations on the write path beyond the `LogEntry` itself.

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

Cross-coordinator correlation: when an event flows from `DashboardViewModel` through `LayoutCoordinator` to `WidgetBindingCoordinator` to `DataStore`, all log entries share the same `traceId`. Visible in JSON log output and agentic trace dumps.

### DqxnTracer

```kotlin
class DqxnTracer @Inject constructor(private val logger: DqxnLogger) {
    inline fun <T> withSpan(
        name: String,
        tag: LogTag,
        block: () -> T,
    ): T {
        val spanId = generateSpanId()
        val start = SystemClock.elapsedRealtimeNanos()
        return try {
            block()
        } finally {
            val elapsed = SystemClock.elapsedRealtimeNanos() - start
            logger.debug(tag) { "span=$name elapsed=${elapsed / 1_000_000}ms" }
        }
    }
}
```

### MetricsCollector

Pre-allocated counters with atomic operations:

```kotlin
class MetricsCollector @Inject constructor() {
    // Frame timing histogram (buckets: <8ms, <12ms, <16ms, <24ms, <33ms, >33ms)
    private val frameHistogram = LongArray(6)

    // Recomposition counter per widget typeId
    private val recompositionCounts = ConcurrentHashMap<String, AtomicLong>()

    // Provider latency (last N samples per provider)
    private val providerLatency = ConcurrentHashMap<String, RingBuffer<Long>>()

    fun recordFrame(durationMs: Long) { /* atomic bucket increment */ }
    fun recordRecomposition(typeId: String) { /* atomic increment */ }
    fun recordProviderLatency(providerId: String, latencyMs: Long) { /* ring buffer write */ }

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

### ErrorReporter

Non-fatal reporting for:
- Widget render crashes (with typeId, last data snapshot, widget settings, stack trace)
- Provider failures (with providerId, connection state, last successful emission)
- DataStore corruption (with file path, schema version, byte count)
- Binding timeouts (with widgetId, providerId, elapsed time)

Forwards to Crashlytics via `FirebaseCrashlytics.recordException()` with structured custom keys.

### AnrWatchdog

Dedicated background thread that pings the main thread every 3s:

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
                if (!responded.await(3, TimeUnit.SECONDS)) {
                    // Main thread stalled — capture diagnostics
                    val mainStack = Looper.getMainLooper().thread.stackTrace
                    val ringBuffer = ringBufferSink.snapshot()
                    logger.error(LogTag.STARTUP) {
                        "ANR detected: main thread blocked\n" +
                        mainStack.joinToString("\n") { "  at $it" }
                    }
                    // Report non-fatal with full context
                }
                Thread.sleep(3_000)
            }
        }
    }
}
```

### CrashContextProvider

Sets Crashlytics custom keys on every significant state transition:

```kotlin
class CrashContextProvider @Inject constructor(
    private val crashlytics: FirebaseCrashlytics,
    private val thermalManager: ThermalManager,
    private val metricsCollector: MetricsCollector,
) {
    fun install() {
        // Observe and update crash context
        scope.launch {
            thermalManager.thermalLevel.collect { level ->
                crashlytics.setCustomKey("thermal_level", level.name)
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

All feature modules depend on the `:core:analytics` interface. Implementation lives in a separate module (e.g., `:core:analytics-firebase`) wired via Hilt. Debug builds use a no-op or logging implementation.

Privacy: no PII in events. PDPA-compliant. User opt-out toggle in settings kills the tracker at the interface level.

## 25. Memory Leak Prevention

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

## 26. Persistence Security

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

## 27. Thermal Management (continued)

### ThermalTrendAnalyzer Details

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

This enables the `FramePacer` to preemptively reduce FPS 10-15s before hitting a thermal tier boundary, smoothing the visual transition instead of an abrupt quality drop.

## 28. DI Scoping (continued)

### Edge-to-Edge & Insets

All DI-scoped components that interact with WindowInsets receive the insets via constructor injection of a `WindowInsetsProvider` interface, not by reading from the Activity directly. This keeps components testable.

## 29. Background Lifecycle (continued)

(Covered fully in Section 19.)

## 30. Testing Strategy

### Test Infrastructure

`DashboardTestHarness` provides a full DSL for coordinator-level testing:

```kotlin
@Test
fun `adding widget triggers binding and persists layout`() = dashboardTest {
    // Arrange
    val speedometer = testWidget(typeId = "core:speedometer")

    // Act
    dispatch(DashboardEvent.AddWidget(speedometer))

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
- `testDataSnapshot(type, values, ...)` — typed snapshot builder
- `FakeLayoutRepository`, `FakeWidgetDataBinder`, `SpyActionProviderRegistry`, `TestDataProvider`

All tests use `StandardTestDispatcher` — no `Thread.sleep`, no real-time delays. Deterministic, fast, agent-friendly.

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
- Screenshot comparison for every widget renderer at multiple sizes
- Theme rendering (all 24 themes at representative widget)
- Edit mode visual states (wiggle, focus, brackets)
- WidgetContainer layer stack (glow, rim, border combinations)

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
        val desc = createRenderer().accessibilityDescription(testDataSnapshot())
        assertThat(desc).isNotEmpty()
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
| P99 frame duration | < 16ms |
| Cold startup time | < 1.5s |
| Compose stability | max 0 unstable classes |
| Non-skippable composables | max 5 |
| Mutation kill rate (critical modules) | > 80% |
| Unit test coverage (coordinators) | > 90% line |
| Release smoke test | Dashboard renders with data |

### Test Principles

- **Deterministic**: `StandardTestDispatcher` everywhere. No `Thread.sleep`, no `runBlocking` with real delays, no flaky timing.
- **Clear failures**: `assertWithMessage("widget should be bound after AddWidget event")` — every assertion explains the expectation.
- **Fast**: < 10s per module for unit tests. Visual tests batched but parallelized.
- **Self-contained**: No test depends on device state, network, or file system outside the test sandbox.

## 31. Build System

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

// Modules WITHOUT Compose: :core:common, :core:plugin-api, :core:observability, :core:analytics, :data:*, :core:thermal
// No Compose compiler overhead
```

### KSP Over KAPT

All annotation processing uses KSP (Hilt, plugin-processor, agentic-processor). No KAPT modules — this enables Gradle configuration cache and significantly reduces incremental build time.

### Custom Lint Rules (`:lint-rules`)

| Rule | Severity | Description |
|---|---|---|
| `ModuleBoundaryViolation` | Error | Pack modules importing `:feature:dashboard` |
| `KaptDetection` | Error | Any module applying `kapt` plugin |
| `ComposeInNonUiModule` | Error | Compose imports in `:core:common`, `:core:plugin-api`, `:data:*`, `:core:thermal`, `:core:observability`, `:core:analytics` |
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
        assertThat(renderer.compatibleDataTypes).isNotEmpty()
        assertThat(renderer.accessibilityDescription(emptyData)).isNotEmpty()
    }
}
```

### Gradle Scaffold Tasks

```bash
# Generate a new widget skeleton in a pack
./gradlew :feature:packs:free:scaffoldWidget --name=altimeter --dataTypes=ALTITUDE

# Generate a new provider skeleton
./gradlew :feature:packs:plus:scaffoldProvider --name=weather --dataTypes=WEATHER

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

## 32. Agentic Framework (Debug Only)

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

### Structured State Dumps

All dump commands return JSON via broadcast result extras:

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

`JsonFileLogSink` writes JSON-lines to `${filesDir}/debug/dqxn.jsonl` (debug builds only):

```json
{"ts":1708444800123,"level":"DEBUG","tag":"BINDING","trace":"abc123","span":"bind-speedometer","msg":"Provider bound: core:gps-speed → widget:def456","elapsed_ms":12}
{"ts":1708444800456,"level":"WARN","tag":"THERMAL","trace":"xyz789","span":null,"msg":"Headroom trending up: 0.72 → 0.78, predicted DEGRADED in 45s"}
```

### Crash Report Enrichment

Widget crashes include structured context in Crashlytics:

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

## 33. Security Requirements

| Requirement | Approach |
|---|---|
| No hardcoded secrets | SDK keys via `local.properties` / secrets gradle plugin — never in source |
| Agentic receiver | Debug builds only, never in release manifest |
| Demo providers | Gated to debug builds, not just UI-hidden |
| BT scan permission | `neverForLocation="true"` — scan data not used for location |
| Deep links | Digital Asset Links verification, parameter validation at NavHost |
| R8 rules | Per-module `consumer-proguard-rules.pro`, release smoke test in CI |

## 34. Permissions

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
