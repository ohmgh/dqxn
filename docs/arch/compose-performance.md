# Compose Performance

> Rendering strategy, grid layout, thermal adaptation, startup optimization, and memory management for 60fps with 12+ widgets.

## Recomposition Isolation

Each widget is an isolated recomposition scope. The grid does NOT use a single Canvas for all widgets:

```kotlin
@Composable
fun DashboardGrid(widgets: ImmutableList<WidgetInstance>) {
    widgets.forEach { widget ->
        key(widget.id) {
            WidgetSlot(widget)
        }
    }
}
```

## State Read Deferral

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

## `graphicsLayer` Isolation

Every widget wrapped in `graphicsLayer` creates an isolated RenderNode. When widget A updates, widget B's RenderNode is GPU-cached and not re-executed:

```kotlin
Modifier.graphicsLayer {
    rotationZ = wiggleAngle
    scaleX = focusScale
    scaleY = focusScale
}
```

## Draw Object Caching

All `Path`, `Paint`, `Brush`, and `Shader` objects are cached via `remember` or `drawWithCache` — never allocated per frame:

```kotlin
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

## Stability Annotations

All domain types emitted to the UI layer are annotated `@Immutable` or `@Stable`. Collections use `kotlinx-collections-immutable`.

A Compose stability configuration file covers cross-module types:

```
// compose_compiler_config.txt
// sdk:contracts — base interface
app.dqxn.android.sdk.contracts.DataSnapshot
app.dqxn.android.sdk.contracts.WidgetData

// Snapshot subtypes live with their producing module.
// Each pack/core module adds its own entries:
// :pack:free
app.dqxn.android.pack.free.snapshots.SpeedSnapshot
app.dqxn.android.pack.free.snapshots.AccelerationSnapshot
app.dqxn.android.pack.free.snapshots.SpeedLimitSnapshot
app.dqxn.android.pack.free.snapshots.TimeSnapshot
app.dqxn.android.pack.free.snapshots.OrientationSnapshot
app.dqxn.android.pack.free.snapshots.BatterySnapshot
// :core:driving
app.dqxn.android.core.driving.DrivingSnapshot

app.dqxn.android.core.design.DashboardThemeDefinition
app.dqxn.android.data.SavedWidget
```

Compose compiler metrics (`-Pcompose.compiler.metrics=true`) are audited regularly to catch regressions in skippability.

## Derived State

`derivedStateOf` prevents unnecessary recomposition when computing values from state:

```kotlin
val displayTheme by remember {
    derivedStateOf { themeState.value.previewTheme ?: themeState.value.currentTheme }
}
```

Use for: filtered widget lists, computed theme properties, widget status aggregation, any value derived from multiple state sources where the computed result changes less frequently than its inputs.

## Glow Effect Strategy

Since minSdk is 31, `RenderEffect.createBlurEffect()` (GPU shader, no offscreen allocation) is always available.

- **Default**: `RenderEffect.createBlurEffect()` — GPU shader, zero offscreen buffer allocation
- **Thermal degradation**: Glow disabled at DEGRADED. `RadialGradient` approximation used as the thermal-degraded visual path
- **Widget previews**: glow always disabled (barely visible at preview scale)

## Frame Pacing

The architecture must not assume 60Hz. Target devices range from 60Hz to 120Hz.

**Primary (API 34+):** `Window.setFrameRate()` to request a lower display refresh rate:

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

**Fallback (API 31-33):** Throttle data emission rate in `WidgetBindingCoordinator` (see [state-management.md](state-management.md#thermal-aware-data-throttling)). Compose draws when state changes — by changing state less often, we reduce frame work without fighting the framework.

The Choreographer-based approach (manual `postFrameCallback` with frame skip counter) is explicitly NOT used. It fights Compose's rendering pipeline.

## Widget Reordering During Drag

Use `graphicsLayer` offset animation — only the visual position changes while the composition stays in the same slot:

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

`movableContentOf` is designed for moving content between different parent composables, not for reordering within the same parent.

## Grid Layout

Viewport-sized dashboard uses `Layout` with custom `MeasurePolicy` for absolute positioning:

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

`LazyLayout` adds `SubcomposeLayout` overhead without benefit when most widgets are visible. Reserve only if the canvas becomes scrollable.

### Grid Properties

- **Grid unit**: 16dp
- **Viewport**: dynamically computed from screen dp dimensions
- **Orientation-locked**: viewport dimensions are stable
- **Rendering**: filters to viewport-intersecting widgets only
- **Placement**: `GridPlacementEngine` scans at 2-unit steps, minimizes overlap area, prefers center positioning
- **Interactions**: drag-to-move, 4-corner resize handles (76dp minimum touch targets, quadrant-based detection), focus animation (translate to center + scale to 38% viewport height)
- **Edit mode**: wiggle animation (+/-0.5 degree rotation at 150ms via `graphicsLayer`), animated corner brackets, delete/settings buttons with spring animations
- **Overlap**: tolerated but not encouraged. Tap targets resolve to highest z-index.

## Widget Container

`WidgetContainer` (`:sdk:ui`) — shared wrapper applied to all widgets:

1. **Error boundary** (catches render failures, shows fallback — see [plugin-system.md](plugin-system.md#widget-error-isolation))
2. **`graphicsLayer` isolation** (own RenderNode, hardware-accelerated transforms)
3. **Glow padding** (4/8/12dp responsive by widget size) — `RenderEffect.createBlurEffect` (GPU shader). Under thermal pressure, `RadialGradient` approximation.
4. **Rim padding** (0-15% of min dimension, user-controlled via `rimSizePercent`)
5. **Layer stack**: error boundary -> graphicsLayer -> outer clip -> glow draw -> warning background -> SOLID/TRANSPARENT fill -> border overlay -> content area

Per-widget style properties persisted in `SavedWidget`: `backgroundStyle`, `opacity`, `showBorder`, `hasGlowEffect`, `cornerRadiusPercent`, `rimSizePercent`.

### Widget Accessibility

```kotlin
Modifier.semantics {
    contentDescription = renderer.accessibilityDescription(currentData)
}
```

`accessibilityDescription(data: WidgetData)` is a required contract method on `WidgetRenderer`.

## Pixel Shift for OLED Burn-in

Applied as a single `graphicsLayer` translation on the outermost `DashboardLayer`, NOT per-widget:

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

1-2px shift every 5 minutes. Per-widget shift would invalidate every widget's RenderNode.

## Thermal Management

`:core:thermal` provides proactive thermal adaptation:

```kotlin
class ThermalManager(private val powerManager: PowerManager) {
    val thermalLevel: StateFlow<ThermalLevel>  // NORMAL, WARM, DEGRADED, CRITICAL

    init {
        val headroom = powerManager.getThermalHeadroom(10)
        if (headroom < 0) {
            powerManager.addThermalStatusListener(executor) { status ->
                thermalLevel.value = when (status) {
                    THERMAL_STATUS_NONE, THERMAL_STATUS_LIGHT -> ThermalLevel.NORMAL
                    THERMAL_STATUS_MODERATE -> ThermalLevel.WARM
                    THERMAL_STATUS_SEVERE -> ThermalLevel.DEGRADED
                    else -> ThermalLevel.CRITICAL
                }
            }
        }
    }
}

data class RenderConfig(
    val targetFps: Float,      // 60 -> 45 -> 30 -> 24
    val glowEnabled: Boolean,  // disabled at DEGRADED
)
```

### Thermal Headroom Usage

- **`getThermalHeadroom(10)`**: Reactive frame pacing decisions — 10s lookahead
- **`getThermalHeadroom(30)`**: Predictive degradation analysis in `ThermalTrendAnalyzer`

### Supplementary Thermal Signals

Battery temperature via `BatteryManager.EXTRA_TEMPERATURE` — car-mounted phones in sunlight can hit battery thermal limits while the SoC is fine.

### GPU vs CPU Bottleneck Differentiation

- **GPU bottleneck** (long frame time + low CPU time): Disable glow, reduce `graphicsLayer` usage, simplify gradients
- **CPU bottleneck** (long frame time + high CPU time): Throttle provider data, increase debounce intervals

Frame duration analysis uses `FrameMetrics` (API 31+).

### Glow Under Thermal Pressure

| Thermal Level | Glow Behavior |
|---|---|
| NORMAL | Full `RenderEffect.createBlurEffect()` |
| WARM | Full `RenderEffect.createBlurEffect()` |
| DEGRADED | `RadialGradient` approximation |
| CRITICAL | Glow fully disabled |

## Startup Optimization

### Initialization Order

1. **Hilt application** — DI graph construction
2. **Proto DataStore** — layout and preferences loaded (binary, fast)
3. **Eager singletons** — `ThemeAutoSwitchEngine`, `ThermalManager`, `AnrWatchdog`, `CrashContextProvider` triggered eagerly from `Application.onCreate()`
4. **WidgetRegistry / DataProviderRegistry** — index injected sets
5. **First composition** — dashboard renders with layout data
6. **Widget bindings** — per-widget data flows established

App Startup initializers run BEFORE `Application.onCreate()` and therefore before Hilt. Components depending on Hilt-injected dependencies use `@Inject constructor` with `@Singleton` scope, initialized eagerly from `Application.onCreate()`:

```kotlin
@HiltAndroidApp
class DqxnApplication : Application() {
    @Inject lateinit var themeAutoSwitchEngine: ThemeAutoSwitchEngine
    @Inject lateinit var thermalManager: ThermalManager
    @Inject lateinit var anrWatchdog: AnrWatchdog
    @Inject lateinit var crashContextProvider: CrashContextProvider

    override fun onCreate() {
        super.onCreate() // Hilt DI graph is ready after this
        anrWatchdog.start()
        crashContextProvider.install()
    }
}
```

App Startup retained ONLY for: WorkManager configuration and Firebase Crashlytics early init (must capture DI graph construction crashes).

### Baseline Profiles

`:baselineprofile` generates profiles for: dashboard cold start -> first frame, widget picker open -> previews, edit mode toggle -> wiggle start.

### Shader Prewarming

First launch triggers shader compilation (50-200ms per unique shader). Mitigation:
1. Pre-warm shaders during splash screen via offscreen 1x1dp Canvas
2. Defer glow enable — `RenderEffect.createBlurEffect()` not applied until 2-3 frames after first meaningful paint
3. Track first-frame-after-install duration separately from steady-state metrics

### Crash Recovery / Safe Mode

Crash counts tracked in `SharedPreferences` (NOT DataStore — readable synchronously before DataStore initializes):

```kotlin
class CrashRecovery(private val context: Context) {
    private val prefs = context.getSharedPreferences("crash_recovery", MODE_PRIVATE)

    fun recordCrash() {
        val now = SystemClock.elapsedRealtime()
        val crashes = getRecentCrashes(windowMs = 60_000L)
        crashes.add(now)
        prefs.edit().putString("crashes", crashes.joinToString(",")).apply()
    }

    fun shouldEnterSafeMode(): Boolean =
        getRecentCrashes(windowMs = 60_000L).size > 3
}
```

Safe mode: clock widget only, banner with "Reset Layout" and "Report" actions.

## Edge-to-Edge & Window Management

Required by targetSdk 36. `enableEdgeToEdge()` in `onCreate()`.

**Layer 0 (Dashboard)**: Draws behind all system bars. No inset padding.

**Layer 1 (Overlays)**: Respects `systemBars` and `ime` insets via `Modifier.windowInsetsPadding()`.

### Status Bar Toggle

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

### Multi-Window

`resizeableActivity="false"`. Despite this flag, some OEMs can still cause window size changes. `DashboardGrid` re-runs viewport culling on any window size change.

## Memory Leak Prevention

Provider `provideState()` flows MUST use `callbackFlow` with `awaitClose` for all sensor and BLE callbacks:

```kotlin
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
    awaitClose { sensorManager.unregisterListener(listener) }
}
```

**Leak assertions**: When a binding job is cancelled, `cancelAndJoin()` must complete within 500ms. Debug builds timeout indicates a leaked coroutine.

**Debug-build heap analysis**: LeakCanary watches all `WidgetRenderer`, `DataProvider`, and foreground service binders. Periodic heap dumps every 5min.
