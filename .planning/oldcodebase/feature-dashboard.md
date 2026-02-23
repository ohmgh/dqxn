# Old Codebase: feature/dashboard

**Source:** `/Users/ohm/Workspace/dqxn.old/android/feature/dashboard/`
**Analysis Date:** 2026-02-23

---

## 1. Architecture

### Module Structure

The dashboard feature is a single Gradle module using `dqxn.android.feature` convention plugin with `kotlinx.serialization` and `roborazzi`.

**Build dependencies** (`build.gradle.kts`):
- `:data:persistence` — DataStore, user prefs
- `:core:plugin-api` — `WidgetRenderer`, `DataProvider`, `WidgetData`, `WidgetStyle`, `SettingDefinition`, etc.
- `:core:widget-primitives` — `DashboardWidgetInstance`, `DashboardThemeDefinition`, `GRID_UNIT_SIZE`, CompositionLocals
- `:core:agentic` — Capture session registry for debug tooling
- `:feature:packs:free` — Free pack (direct dependency, violates IoC)
- `:feature:packs:sg-erp2` — SG-ERP2 pack (direct dependency, violates IoC)
- `kotlinx-serialization-json`, `datastore-preferences`, `accompanist-permissions`

### Package Layout

```
app.dqxn.android.feature.dashboard/
├── DashboardScreen.kt          # Main composable entry point
├── DashboardState.kt           # State class + Event sealed interface + Effect sealed interface
├── DashboardViewModel.kt       # God-object ViewModel (~1040 lines)
├── DashboardAnimations.kt      # DashboardMotion animation specs
├── DashboardThemeExtensions.kt # Spacing, typography, card size extensions
├── capture/
│   └── LocalCaptureSessionRegistry.kt  # CompositionLocal for agentic capture
├── context/
│   ├── ContextProvider.kt      # Interface + RealContextProvider (timezone, locale, region)
│   └── FakeContextProvider.kt  # Test fake
├── data/
│   ├── LayoutModels.kt         # SavedWidget, DashboardCanvas, Viewport
│   ├── LayoutRepository.kt     # Interface for canvas persistence
│   ├── LayoutDataStore.kt      # Preferences DataStore implementation
│   └── LayoutMigrator.kt       # Schema version migration (v0->v1->v2)
├── di/
│   ├── DashboardModule.kt      # Core DI: registries, entitlement, binder, context
│   ├── DashboardDataModule.kt  # Binds LayoutDataStore -> LayoutRepository
│   └── DashboardThemeModule.kt # Binds ThemeRepositoryImpl -> ThemeRepository
├── grid/
│   ├── DashboardGrid.kt        # DashboardGridContainer composable (~1037 lines)
│   ├── DashboardGridLayout.kt  # DashboardCustomLayout (custom Layout composable)
│   ├── GridPlacementEngine.kt  # Optimal new-widget position finder
│   ├── WidgetGestureHandler.kt # Per-widget gesture state machine (drag, resize, tap)
│   └── BlankSpaceGestureHandler.kt  # Background tap/long-press handler
├── navigation/
│   ├── DashboardNavigation.kt  # DashboardRoute composable, DASHBOARD_ROUTE constant
│   └── SharedTransitionLocals.kt  # LocalSharedTransitionScope, LocalAnimatedVisibilityScope
├── preset/
│   ├── PresetModels.kt         # PresetManifest, PresetWidget, PresetWidgetStyle
│   ├── PresetLoader.kt         # Region-based preset loading from assets
│   └── PresetImportSource.kt   # Interface for ADB-based debug import
├── registry/
│   ├── WidgetRegistry.kt       # Interface: getRenderer, getAvailableWidgets, etc.
│   ├── WidgetRegistryImpl.kt   # Hilt multibinding-based impl
│   ├── EntitlementAwareRegistry.kt  # Decorator filtering by entitlement
│   ├── DataProviderRegistry.kt      # Interface: getProvider, getProviders
│   ├── DataProviderRegistryImpl.kt  # Hilt multibinding-based impl
│   ├── EntitlementAwareDataProviderRegistry.kt  # Decorator
│   ├── ActionProviderRegistry.kt      # Interface: getProvider, getProviders
│   ├── ActionProviderRegistryImpl.kt  # Hilt multibinding-based impl
│   ├── EntitlementAwareActionProviderRegistry.kt  # Decorator
│   ├── PackRegistry.kt         # Interface: pack discovery and activation
│   ├── PackRegistryImpl.kt     # Hilt multibinding-based impl
│   ├── EntitlementManager.kt   # Interface: hasEntitlement, purchaseProduct
│   ├── StubEntitlementManager.kt  # Dev stub (grants all, simulate-free toggle)
│   ├── WidgetDataBinder.kt     # Interface + RealWidgetDataBinder (combines provider flows)
│   ├── WidgetContentDispatcher.kt  # Composable: renders widget + overlay states
│   └── SettingsSheetDispatcher.kt  # Composable: widget settings pager UI
├── setup/
│   ├── SetupEvaluator.kt       # Evaluates SetupDefinition against runtime state
│   └── [UI files: SetupSheetContent, DeviceScanCard, etc.]
├── theme/
│   ├── ThemeRepository.kt      # Interface: dual light/dark theme, custom CRUD
│   ├── ThemeRepositoryImpl.kt  # Implementation
│   ├── ThemeAutoSwitchEngine.kt  # SYSTEM/SOLAR/ILLUMINANCE auto-switch
│   ├── CustomThemeRepository.kt
│   └── [UI files: ThemeSelector, ThemeStudio, etc.]
└── ui/
    ├── DashboardButtonBar.kt   # Bottom bar: Settings, Edit/Add Widget FABs
    ├── DashboardHaptics.kt     # Haptic feedback wrapper (rememberDashboardHaptics)
    ├── WidgetPickerContent.kt  # Widget picker overlay
    ├── MainSettingsContent.kt  # Main settings overlay
    ├── PackBrowserContent.kt   # Pack browser overlay
    ├── common/
    │   ├── OverlayScaffold.kt       # Base scaffold for overlays
    │   ├── WidgetSettingsPager.kt    # 3-tab pager (Info, Settings, Data Source)
    │   ├── DataProviderSettingsContent.kt
    │   ├── StyleSettingsContent.kt
    │   └── WidgetInfoContent.kt
    ├── settings/
    │   ├── SettingRowDispatcher.kt   # Dispatches setting type -> row composable
    │   ├── FeatureSettingsContent.kt
    │   └── rows/                     # Per-type setting row composables
    └── widgets/common/
        └── UnknownWidgetPlaceholder.kt
```

### ViewModel (God-Object Pattern)

`DashboardViewModel` (~1040 lines) is the **single ViewModel** managing:
- Widget list state (add, remove, move, resize, style, settings, data sources)
- Edit mode toggle
- Theme management (current, preview, light/dark, auto-switch mode)
- Widget data binding lifecycle (per-widget coroutine jobs)
- Widget status computation (entitlements, setup requirements, connection state)
- Layout persistence (debounced save)
- Preset import
- Status bar visibility
- Entitlement simulation callbacks

**Constructor injection (17 parameters):**
```kotlin
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val layoutRepository: LayoutRepository,
    private val themeRepository: ThemeRepository,
    private val themeAutoSwitchEngine: ThemeAutoSwitchEngine,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val widgetDataBinder: WidgetDataBinder,
    private val contextProvider: ContextProvider,
    private val presetLoader: PresetLoader,
    private val presetImportSource: PresetImportSource,
    val widgetRegistry: WidgetRegistry,
    val packRegistry: PackRegistry,
    val dataProviderRegistry: DataProviderRegistry,
    val baseDataProviderRegistry: DataProviderRegistryImpl,
    val entitlementManager: EntitlementManager,
    private val gridPlacementEngine: GridPlacementEngine,
    private val dataProviders: Set<@JvmSuppressWildcards DataProvider>,
    private val pairedDeviceStore: PairedDeviceStore,
    @ApplicationContext private val context: Context
)
```

Multiple registries are exposed as `val` (public) so `DashboardScreen` and `WidgetContentDispatcher` can access them directly.

---

## 2. Grid/Canvas

### Grid Unit System

Grid uses a fixed unit size from `:core:widget-primitives`:
```kotlin
val GRID_UNIT_SIZE = 16.dp  // Each grid unit is 16dp
```

Grid dimensions are calculated dynamically from container constraints:
```kotlin
val unitPx = with(density) { GRID_UNIT_SIZE.roundToPx() }
val maxGridColumns = constraints.maxWidth / unitPx
val maxGridRows = constraints.maxHeight / unitPx
```

### Canvas Model ("Infinity Canvas")

The canvas is unbounded conceptually. A `Viewport` determines which widgets are visible:

```kotlin
data class Viewport(val cols: Int, val rows: Int) {
    fun isVisible(gridX: Int, gridY: Int, widthUnits: Int, heightUnits: Int): Boolean {
        return gridX < cols && gridY < rows &&
               gridX + widthUnits > 0 && gridY + heightUnits > 0
    }
}
```

Viewport is anchored to (0,0) — top-left. No scrolling/panning is implemented. Widgets beyond the viewport boundary are simply not rendered.

**Single canvas per app** — no profile system in old codebase. `DashboardCanvas` is a flat list of `SavedWidget`.

### Custom Layout (`DashboardCustomLayout`)

Uses Compose `Layout` composable with custom `MeasurePolicy`:

```kotlin
@Composable
fun DashboardCustomLayout(
    widgets: List<DashboardWidgetInstance>,
    modifier: Modifier,
    maxGridColumns: Int,
    maxGridRows: Int,
    previewSizes: Map<String, WidgetPreviewSize>,
    content: @Composable () -> Unit
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val placeables = measurables.map { measurable ->
            val widgetId = measurable.layoutId as? String
            val widget = widgetId?.let { widgetsById[it] }
            if (widget != null) {
                val widthPx = effectiveWidths[widget.id]!! * unitSizePx
                val heightPx = effectiveHeights[widget.id]!! * unitSizePx
                Pair(widget, measurable.measure(Constraints.fixed(widthPx, heightPx)))
            } else {
                Pair(null, measurable.measure(Constraints.fixed(0, 0)))
            }
        }
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEach { (widget, placeable) ->
                if (widget != null) {
                    placeable.placeRelative(
                        x = widget.gridX * unitSizePx,
                        y = widget.gridY * unitSizePx
                    )
                }
            }
        }
    }
}
```

Key implementation details:
- Each child must have `Modifier.layoutId(widget.id)` for matching
- Supports `previewSizes` map to override widget dimensions during resize (live preview)
- Layout fills constraint dimensions exactly (`constraints.maxWidth`, `constraints.maxHeight`)
- Widgets placed at exact `gridX * unitSizePx`, `gridY * unitSizePx` positions

### Widget Positioning

Widgets are positioned using integer grid coordinates (`gridX`, `gridY`). Position is stored in `DashboardWidgetInstance`:

```kotlin
data class DashboardWidgetInstance(
    val id: String,
    val typeId: String,
    val sizeSpec: WidgetSizeSpec,
    val style: WidgetStyle,
    val gridX: Int,
    val gridY: Int,
    val zIndex: Int = 0,
    val settings: Map<String, Any?> = emptyMap(),
    val selectedDataSourceIds: List<String> = emptyList()
)
```

Widgets can overlap freely — z-ordering via `zIndex` (higher = on top). `BringWidgetToFront` assigns `maxZIndex + 1`.

### GridPlacementEngine

`@Singleton` class that finds optimal position for new widgets:

```kotlin
fun findOptimalPosition(
    existingWidgets: List<DashboardWidgetInstance>,
    widgetWidth: Int, widgetHeight: Int,
    viewport: Viewport
): Pair<Int, Int>
```

- Scans grid at step=2 units
- Scores each position by overlap area with existing widgets
- Distance penalty favors center (upper-middle area: `maxRows / 3`)
- Returns position with lowest adjusted score

---

## 3. Widget Rendering

### Rendering Pipeline

1. `DashboardScreen` collects `state` via `collectAsStateWithLifecycle()`
2. Provides `LocalDashboardTheme` via `CompositionLocalProvider`
3. `DashboardGridContainer` filters widgets to viewport, iterates `visibleWidgets`
4. Each widget wrapped in `Box` with `layoutId`, `zIndex`, `offset`, `graphicsLayer`
5. Widget content rendered via `widgetContent` lambda → `WidgetContentDispatcher`
6. Overlay states rendered on top of widget content when degraded

### WidgetContentDispatcher

Dispatches rendering to the registered `WidgetRenderer`:

```kotlin
@Composable
fun WidgetContentDispatcher(
    widget: DashboardWidgetInstance,
    state: DashboardState,
    registry: WidgetRegistry,
    dataProviderRegistry: DataProviderRegistry,
    entitlementManager: EntitlementManager,
    baseDataProviderRegistry: DataProviderRegistry,
    onOpenWidgetInfo: ((widgetId: String, issues: List<WidgetIssue>) -> Unit)?
) {
    val renderer = registry.getRenderer(widget.typeId)
    val widgetData = state.widgetData[widget.id] ?: WidgetData.EMPTY

    renderer.Render(
        widgetData = widgetData,
        isEditMode = state.isEditMode,
        style = widget.style,
        settings = settings,
        modifier = Modifier.fillMaxSize()
    )

    // Then render overlays based on WidgetRenderState
}
```

**Overlay states** (layered on top of widget content):
- `EntitlementRevoked` — premium feature locked
- `ProviderMissing` — pack uninstalled
- `SetupRequired` — permissions/hardware not configured
- `ConnectionError` — provider error
- `Disconnected` — hardware disconnected
- `Ready` — no overlay

**Tap handling** in content dispatcher:
- Edit mode: no click handler (handled by gesture system)
- Degraded state: click opens widget info page
- Normal + `renderer.supportsTap`: delegates to `renderer.onTap()` with fallback
- Otherwise: no click handler

### Widget Preview During Resize

`LocalWidgetPreviewUnits` CompositionLocal provides override dimensions during resize:
```kotlin
CompositionLocalProvider(
    LocalWidgetPreviewUnits provides if (previewWidthUnits != null && previewHeightUnits != null) {
        WidgetPreviewUnits(previewWidthUnits, previewHeightUnits)
    } else null,
    LocalWidgetScale provides WidgetScale()
) {
    widgetContent(widget)
}
```

---

## 4. Edit Mode

### Entering Edit Mode

Three entry points:
1. **Button bar "Edit" button** → `DashboardEvent.EnterEditMode` (idempotent)
2. **Long press on widget** → `onLongPressWidget` callback → `DashboardEvent.EnterEditMode` + focus widget
3. **Long press on blank space** → `BlankSpaceGestureHandler` emits `LongPress` → enter edit mode without focus

### Edit Mode State

Boolean flag in `DashboardState`:
```kotlin
data class DashboardState(
    val isEditMode: Boolean = false,
    // ...
)
```

Local grid-level state managed within `DashboardGridContainer`:
- `focusedWidgetId: String?` — currently selected widget
- `forceShowEditModeUI: Boolean` — immediate UI during drag-entry (before prop arrives)
- `draggingWidgetId: String?` — widget being dragged
- `lastSnapGridX/Y: Int` — last snapped position for haptic feedback

### Exiting Edit Mode

- **Tap blank space** in edit mode → clear focus, exit
- **Tap focused widget** → `haptics.performEditModeExit()`, clear focus, toggle edit mode
- **Tap unfocused widget** → focus that widget (stay in edit mode)
- **Button bar tap** → `ToggleEditMode` event

### Focus System

Widget focus is tracked locally in `DashboardGridContainer` (not in ViewModel state):
- Focus saved/restored when sheets open/close (`savedFocusedWidgetId`)
- Focus cleared when edit mode exits
- Focused widget shows:
  - Corner bracket handles (Canvas-drawn curved arcs)
  - Delete + Settings action buttons above/below widget
  - Pulsing bracket thickness animation
  - Resize corner handles (32dp touch targets)

### Visual Effects in Edit Mode

```kotlin
// Wiggle animation (all widgets)
val infiniteTransition = rememberInfiniteTransition()
val wiggleRotation by infiniteTransition.animateFloat(
    initialValue = -0.5f, targetValue = 0.5f,
    animationSpec = infiniteRepeatable(tween(150), RepeatMode.Reverse)
)

// Lift scale during drag
val liftScale by animateFloatAsState(
    targetValue = if (isDragging) 1.03f else 1.0f,
    animationSpec = spring(DampingRatioMediumBouncy, StiffnessMedium)
)
```

---

## 5. Drag & Drop / Resize

### WidgetGestureHandler

`@Stable` class per widget. Manages all gesture state and emits events via `SharedFlow`:

```kotlin
@Stable
class WidgetGestureHandler(val widgetId: String, private val view: View) {
    val events: SharedFlow<WidgetGestureEvent>

    // Observable state for visual rendering
    var isDragging: Boolean          // mutableStateOf
    var isResizing: Boolean          // mutableStateOf
    var dragOffsetX: Float           // mutableFloatStateOf
    var dragOffsetY: Float           // mutableFloatStateOf
    var resizeOffsetX: Float         // mutableFloatStateOf
    var resizeOffsetY: Float         // mutableFloatStateOf
    var activeHandle: HandlePosition?  // mutableStateOf

    // Computed: snapped to grid positions
    val snappedDragOffsetX: Float    // gridX delta * unitPx
    val snappedDragOffsetY: Float
    val snappedResizeOffsetX: Float  // rounded to unit boundaries
    val snappedResizeOffsetY: Float
    val dropzoneGridPosition: IntOffset?
}
```

**Gesture event flow:**

```
WidgetGestureEvent sealed class:
├── Tap(widgetId)
├── LongPress(widgetId)
├── DragStart(widgetId, wasInEditMode)
├── DragUpdate(widgetId, offsetX, offsetY)
├── DragEnd(widgetId, newGridX, newGridY)
├── DragCancel(widgetId)
├── ResizeStart(widgetId, handle)
├── ResizeUpdate(widgetId, handle, deltaWidth, deltaHeight)
├── ResizeEnd(widgetId, handle, newWidth, newHeight, deltaWidth, deltaHeight)
└── ResizeCancel(widgetId)
```

### Drag Implementation

Uses `awaitEachGesture` + `awaitFirstDown` + manual pointer tracking:

1. `awaitFirstDown` (edit mode: `PointerEventPass.Initial` to intercept before widget clicks; otherwise `Main`)
2. Track time for long-press detection (`LONG_PRESS_TIMEOUT_MS = 400ms`)
3. Track total drag distance (`DRAG_THRESHOLD_PX = 8f`)
4. If movement exceeds threshold before timeout → cancel (user scrolling)
5. If timeout elapses → emit `LongPress` + haptic
6. After long-press, further movement starts drag → emit `DragStart`
7. During drag: `updateDragOffset()` applies bounds clamping
8. On release: emit `DragEnd` with `dropzoneGridPosition`

**Drag offset** uses snapped grid positions for visual rendering:
```kotlin
val snappedDragOffsetX: Float get() {
    val dropPos = dropzoneGridPosition ?: return 0f
    return (dropPos.x - gridX) * unitPx  // Snap to grid
}
```

Widget position is applied via `Modifier.offset { IntOffset(snappedDragOffsetX, snappedDragOffsetY) }`.

### Resize Implementation

Four corner handles (`HandlePosition.TopLeft/TopRight/BottomRight/BottomLeft`), each 32dp touch targets:

```kotlin
fun resizeHandleModifier(handle: HandlePosition): Modifier = Modifier.pointerInput(widgetId, handle) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        down.consume()
        // Haptic on touch
        // Track drag → update resizeOffsetX/Y
        // On release → calculate new size with aspect ratio preservation
    }
}
```

**Resize position compensation:** For TL/TR/BL handles, the widget origin shifts:
```kotlin
val resizeCompensationX = when (activeHandle) {
    HandlePosition.TopLeft, HandlePosition.BottomLeft ->
        -((previewWidthUnits - widget.sizeSpec.widthUnits) * unitPx).toFloat()
    else -> 0f
}
```

**Aspect ratio preservation:** When `sizeSpec.preserveAspectRatio` is true, resize maintains original width/height ratio. Dominant drag direction drives calculation, with proportional scaling when bounds are hit.

**Min size:** `MIN_WIDGET_UNITS = 2` (2 grid units = 32dp)

### BlankSpaceGestureHandler

Separate handler for dashboard background gestures:
- Uses `requireUnconsumed = true` so widgets consume their events first
- Emits `Tap` (clear focus / toggle bar) or `LongPress` (enter edit mode)
- Same timeout/threshold constants as widget handler

---

## 6. State Management

### DashboardState (God-Object)

Single data class holding ALL state:

```kotlin
data class DashboardState(
    val widgets: List<DashboardWidgetInstance> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isEditMode: Boolean = false,
    val currentTheme: DashboardThemeDefinition = DefaultTheme,
    val availableThemes: List<DashboardThemeDefinition> = emptyList(),
    val darkTheme: DashboardThemeDefinition? = null,
    val lightTheme: DashboardThemeDefinition? = null,
    val autoSwitchMode: AutoSwitchMode = AutoSwitchMode.SYSTEM,
    val currentLuxLevel: Float = 0f,
    val illuminanceThreshold: Int = 50,
    val previewTheme: DashboardThemeDefinition? = null,
    val currentPackId: String = "free",
    val currentTime: java.time.LocalTime = java.time.LocalTime.now(),
    val currentDate: java.time.LocalDate = java.time.LocalDate.now(),
    val showStatusBar: Boolean = false,
    val widgetData: Map<String, WidgetData> = emptyMap(),
    val widgetStatus: Map<String, WidgetStatusCache> = emptyMap()
) {
    val displayTheme: DashboardThemeDefinition
        get() = previewTheme ?: currentTheme
}
```

**Problems for migration:**
- Single `MutableStateFlow<DashboardState>` — any change to ANY field triggers recomposition
- `widgetData: Map<String, WidgetData>` grows with every widget — clock tick updates entire map
- `widgetStatus: Map<String, WidgetStatusCache>` recomputed for all widgets on any connection change
- Theme, status bar, lux level all mixed into same state object

### DashboardEvent (Sealed Interface)

Comprehensive sealed interface (~30 events):
```
Refresh, Disconnect, OpenSettings, OpenAppSettings, DismissError, EnableDemoMode,
ToggleEditMode, EnterEditMode, MoveWidget, ResizeWidget,
SetTheme, SetDarkTheme, SetLightTheme, SetAutoSwitchMode, SetIlluminanceThreshold,
ApplyPack, ConfirmResetDashboard, ResetLayout,
AddWidget, ConfirmRemoveWidget, BringWidgetToFront,
UpdateWidgetStyle, UpdateWidgetDataSources, UpdateWidgetSetting,
ToggleStatusBar, NavigateToWidgetSetup,
DeleteCustomTheme, SaveCustomTheme, SetPreviewTheme, ClearPreviewTheme
```

### DashboardEffect (One-Shot Events)

```
NavigateToSettings, NavigateToAppSettings, NavigateToWidgetSetup(widgetId),
ShowToast(message), ShowWidgetSettings(widgetId)
```

Uses `Channel<DashboardEffect>(Channel.BUFFERED)` → `receiveAsFlow()`.

### Save Mechanism

Debounced save with 500ms delay:
```kotlin
private fun scheduleSave() {
    saveJob?.cancel()
    saveJob = viewModelScope.launch {
        delay(500)
        saveCanvas()
    }
}
```

Called after: move, resize, style update, data source change, setting update, widget add/remove, bring-to-front.

Immediate save (no debounce) when exiting edit mode.

---

## 7. Data Binding

### WidgetDataBinder

Combines per-widget data provider flows into a single `WidgetData`:

```kotlin
@Singleton
class RealWidgetDataBinder @Inject constructor(
    private val providerRegistry: DataProviderRegistry,
    @ApplicationScope private val scope: CoroutineScope
) : WidgetDataBinder {
    override fun bindWidget(widget: DashboardWidgetInstance): StateFlow<WidgetData> {
        val providerFlows = widget.selectedDataSourceIds.mapNotNull { sourceId ->
            providerRegistry.getProvider(sourceId)?.let { provider ->
                provider.provideState().map { snapshot -> provider.dataType to snapshot }
            }
        }
        return if (providerFlows.isEmpty()) {
            MutableStateFlow(WidgetData.EMPTY)
        } else {
            combine(providerFlows) { results -> WidgetData(results.toMap()) }
                .stateIn(scope, SharingStarted.WhileSubscribed(5000), WidgetData.EMPTY)
        }
    }
}
```

### Binding Lifecycle (ViewModel)

`observeWidgetData()` observes `state.widgets` changes:
```kotlin
state.map { s -> s.widgets }.distinctUntilChanged().collect { widgets ->
    // Cancel removed widget bindings
    // Start or rebind widget bindings when:
    //   - New widget added (id not in widgetBindings)
    //   - Data sources changed (previousSourceIds != currentSourceIds)
    widgetBindings[widget.id] = launch {
        widgetDataBinder.bindWidget(widget).collect { data ->
            _state.update { currentState ->
                currentState.copy(widgetData = currentState.widgetData + (widget.id to data))
            }
        }
    }
}
```

**Problem:** Every widget data update creates a new `Map` copy via `currentState.widgetData + (widget.id to data)`, which replaces the entire `DashboardState`. A clock ticking every second causes the entire state to recompose.

### Registry Architecture

Three-layer pattern:
1. **Core registry** (`WidgetRegistryImpl`, `DataProviderRegistryImpl`, `ActionProviderRegistryImpl`) — Hilt `@IntoSet` multibinding, simple map lookup
2. **Entitlement decorator** (`EntitlementAwareRegistry`, `EntitlementAwareDataProviderRegistry`, `EntitlementAwareActionProviderRegistry`) — filters by `hasEntitlement()`
3. **Interface binding** in DI module — consumers get entitlement-filtered registries

WidgetRegistry uses **priority-based resolution** when multiple renderers exist for same typeId:
```kotlin
override fun getRenderer(typeId: String): WidgetRenderer? {
    return renderersByType[typeId]?.maxByOrNull { it.priority }
}
```

### Widget Status Computation

Heavy `suspend fun computeWidgetStatus()` per widget checking:
1. Renderer entitlement
2. Provider entitlements (each selected data source)
3. Provider availability (missing from registry)
4. Setup requirements (permissions, system services, device scan) via `SetupEvaluator`
5. Connection state (per-provider `connectionState` flow)
6. Aggregation into `WidgetRenderState` overlay + `WidgetIssue` list

Cached in `widgetStatus: Map<String, WidgetStatusCache>` on `DashboardState`.

---

## 8. Performance Techniques

### graphicsLayer

Applied on each widget box:
```kotlin
.graphicsLayer {
    scaleX = liftScale * focusScale
    scaleY = liftScale * focusScale
    rotationZ = currentRotation  // wiggle
    alpha = settingsAlpha
    transformOrigin = TransformOrigin.Center
    clip = false  // Allow edit controls to overflow
}
```

Separate inner layer with `CompositingStrategy.Offscreen` prevents transparency flash:
```kotlin
Box(modifier = Modifier.matchParentSize().graphicsLayer {
    compositingStrategy = CompositingStrategy.Offscreen
}) { widgetContent(widget) }
```

### derivedStateOf

Used for edit mode visibility:
```kotlin
val showEditModeUI by remember { derivedStateOf {
    (currentIsEditModeState.value || forceShowEditModeUI) && currentEditingId == null
} }
```

### rememberUpdatedState

Extensively used for callbacks to avoid stale closures:
```kotlin
val currentOnMoveWidget by rememberUpdatedState(onMoveWidget)
val currentOnResizeWidget by rememberUpdatedState(onResizeWidget)
val currentMaxGridColumns by rememberUpdatedState(maxGridColumns)
// ... (12+ instances)
```

### key() for Widget Stability

Each widget is wrapped in `key(widget.id)` within the `forEach`:
```kotlin
visibleWidgets.forEach { widget ->
    key(widget.id) {
        // Widget composable tree
    }
}
```

### Viewport Filtering

Widgets outside viewport are not rendered at all:
```kotlin
val visibleWidgets = widgets.filter { widget ->
    viewport.isVisible(widget.gridX, widget.gridY, widget.sizeSpec.widthUnits, widget.sizeSpec.heightUnits)
}
```

### What's Missing

- No `collectAsState()` (Layer 0) — all uses are `collectAsStateWithLifecycle()` (Layer 1)
- No `drawWithCache` for reusable draw objects
- No per-widget `StateFlow` — all widget data flows through single `DashboardState`
- No `LocalWidgetData` CompositionLocal pattern — data passed through `WidgetData` parameter
- No frame pacing / `setFrameRate()` API
- No immutable collections — `List<DashboardWidgetInstance>` is standard mutable Kotlin list
- No `SupervisorJob` per widget binding — uses plain `launch` (one crash could cancel siblings)
- No throttled emission on high-frequency data

---

## 9. Key Classes & Relationships

### Data Models

| Class | File | Purpose |
|---|---|---|
| `DashboardWidgetInstance` | `:core:widget-primitives` | Runtime widget with id, typeId, position, size, style, settings, data sources |
| `WidgetSizeSpec` | `:core:widget-primitives` | widthUnits, heightUnits, preserveAspectRatio |
| `WidgetStyle` | `:core:plugin-api` | backgroundStyle, opacity, showBorder, hasGlowEffect, cornerRadiusPercent, rimSizePercent |
| `SavedWidget` | `data/LayoutModels.kt` | Serializable persistence model (mirrors DashboardWidgetInstance) |
| `DashboardCanvas` | `data/LayoutModels.kt` | Serializable canvas: `List<SavedWidget>` + version |
| `Viewport` | `data/LayoutModels.kt` | cols/rows viewport for visibility filtering |
| `WidgetPreviewSize` | `grid/DashboardGridLayout.kt` | Override dimensions during resize |
| `DashboardThemeDefinition` | `:core:widget-primitives` | Theme colors, brushes, accent |
| `WidgetData` | `:core:plugin-api` | `Map<String, DataSnapshot>` keyed by data type |
| `DataSnapshot` | `:core:plugin-api` | Generic key-value snapshot from provider |

### Core Interfaces

| Interface | File | Implementations |
|---|---|---|
| `WidgetRegistry` | `registry/WidgetRegistry.kt` | `WidgetRegistryImpl` → `EntitlementAwareRegistry` |
| `DataProviderRegistry` | `registry/DataProviderRegistry.kt` | `DataProviderRegistryImpl` → `EntitlementAwareDataProviderRegistry` |
| `ActionProviderRegistry` | `registry/ActionProviderRegistry.kt` | `ActionProviderRegistryImpl` → `EntitlementAwareActionProviderRegistry` |
| `PackRegistry` | `registry/PackRegistry.kt` | `PackRegistryImpl` |
| `EntitlementManager` | `registry/EntitlementManager.kt` | `StubEntitlementManager` |
| `WidgetDataBinder` | `registry/WidgetDataBinder.kt` | `RealWidgetDataBinder` |
| `LayoutRepository` | `data/LayoutRepository.kt` | `LayoutDataStore` |
| `ThemeRepository` | `theme/ThemeRepository.kt` | `ThemeRepositoryImpl` |
| `ContextProvider` | `context/ContextProvider.kt` | `RealContextProvider`, `FakeContextProvider` |
| `PresetImportSource` | `preset/PresetImportSource.kt` | `NoOpPresetImportSource` |

### Gesture Handlers

| Class | File | Purpose |
|---|---|---|
| `WidgetGestureHandler` | `grid/WidgetGestureHandler.kt` | Per-widget tap/long-press/drag/resize state machine |
| `BlankSpaceGestureHandler` | `grid/BlankSpaceGestureHandler.kt` | Background tap/long-press for edit mode |
| `DashboardHaptics` | `ui/DashboardHaptics.kt` | Haptic feedback wrapper (edit enter/exit, snap, boundary, resize) |

### Composable Entry Points

| Composable | File | Purpose |
|---|---|---|
| `DashboardRoute` | `navigation/DashboardNavigation.kt` | Nav graph entry |
| `DashboardScreen` | `DashboardScreen.kt` | Main screen: theme, status bar, grid, button bar |
| `DashboardGridContainer` | `grid/DashboardGrid.kt` | Grid container with edit mode, gestures, overlays |
| `DashboardCustomLayout` | `grid/DashboardGridLayout.kt` | Custom `Layout` for grid positioning |
| `WidgetContentDispatcher` | `registry/WidgetContentDispatcher.kt` | Render widget via registry + overlays |
| `SettingsSheetDispatcher` | `registry/SettingsSheetDispatcher.kt` | Widget settings pager |
| `DashboardButtonBar` | `ui/DashboardButtonBar.kt` | Auto-hiding bottom bar with FABs |

---

## 10. Theme System

### Dual-Theme Model

Users select separate light and dark themes. `ThemeAutoSwitchEngine` determines which is active based on mode:

```
AutoSwitchMode:
├── LIGHT     → always light theme
├── DARK      → always dark theme
├── SYSTEM    → follows Android system dark mode
├── SOLAR_AUTO → sunrise/sunset from SolarDataProvider
└── ILLUMINANCE_AUTO → ambient light sensor threshold
```

Active theme flows through: `ThemeAutoSwitchEngine.activeTheme` → `DashboardState.currentTheme` → `LocalDashboardTheme` CompositionLocal.

### Theme Preview

`previewTheme` field in `DashboardState` overrides active theme when non-null:
```kotlin
val displayTheme: DashboardThemeDefinition get() = previewTheme ?: currentTheme
```

Set via `SetPreviewTheme` event, cleared via `ClearPreviewTheme`.

---

## 11. Persistence

### LayoutDataStore

Preferences DataStore at `"dashboard_layouts"`. Single key `"canvas"` stores JSON-serialized `DashboardCanvas`:

```kotlin
private val canvasKey = stringPreferencesKey("canvas")
override val canvas: Flow<DashboardCanvas> = context.layoutDataStore.data.map { prefs ->
    val jsonString = prefs[canvasKey]
    jsonString?.let { json.decodeFromString<DashboardCanvas>(it) } ?: DashboardCanvas()
}
```

Thread-safe mutations via `Mutex`:
```kotlin
private val canvasMutex = Mutex()
override suspend fun addWidgetToCanvas(widget: SavedWidget) {
    canvasMutex.withLock {
        val current = canvas.first()
        saveCanvas(current.copy(widgets = current.widgets + widget))
    }
}
```

### Schema Migration

`LayoutMigrator` handles v0 → v1 → v2:
- v0→v1: Widget type namespace migration (`"Speedometer"` → `"core:speedometer"`)
- v1→v2: Consolidate typed settings fields into single `settings` map

Migration is forward-only, preserves unknown fields.

### Preset System

Region-based preset loading from `assets/presets/`:
- `PresetLoader` detects region via `ContextProvider`
- Maps region to preset file (e.g., SG/MY → `preset_demo_sg.json`)
- Falls back to `preset_demo_default.json`
- Generates new UUIDs for widget IDs on import
- `PresetImportSource` interface for ADB-based debug import

---

## 12. Test Infrastructure

### DashboardTestHarness

DSL-style test harness in `src/test/.../harness/DashboardTestHarness.kt`:
- `FakeLayoutRepository` — in-memory canvas storage
- `FakeWidgetDataBinder` — returns `WidgetData.EMPTY`
- `SpyActionProviderRegistry` — records action calls
- MockK mocks for: ThemeRepository, UserPreferencesRepository, WidgetRegistry, PackRegistry, DataProviderRegistry, EntitlementManager, ThemeAutoSwitchEngine, ContextProvider, PresetLoader, PairedDeviceStore, Context
- `createViewModel()` factory method

### Test Files

| Test | Purpose |
|---|---|
| `DashboardViewModelTest.kt` | Basic ViewModel: init state, add/remove widget |
| `LayoutDataStoreTest.kt` | DataStore persistence |
| `LayoutMigratorTest.kt` | Schema migration |
| `GridPlacementEngineTest.kt` | Optimal position finding |
| `WidgetDataBinderTest.kt` | Data binding lifecycle |
| `WidgetRegistryTest.kt` | Registry lookup |
| `SetupEvaluatorTest.kt` | Setup requirement evaluation |
| `ThemeAutoSwitchEngineTest.kt` | Theme switching modes |
| `AutoSwitchModeGatingTest.kt` | Theme mode gating |
| `PureRendererFlowTest.kt` | Renderer flow integration |
| `WidgetRenderingTest.kt` | Visual regression (roborazzi) |

Uses JUnit4 (not JUnit5), MockK, Truth, kotlinx-coroutines-test with `StandardTestDispatcher`.

---

## 13. Migration Notes

### What Maps to New `:feature:dashboard`

| Old | New | Notes |
|---|---|---|
| `DashboardViewModel` (god-object) | Per-coordinator `StateFlow` slices | Split into `GridCoordinator`, `EditCoordinator`, `ThemeCoordinator`, `BindingCoordinator`, etc. |
| `DashboardState` (god-object) | Per-slice state classes | `GridState`, `EditState`, `ThemeState` each as separate `StateFlow` |
| `DashboardCustomLayout` | Custom `Layout` + `MeasurePolicy` | Same approach, but with per-profile canvas support |
| `DashboardGridContainer` (~1037 lines) | Decomposed into grid + edit + gesture layers | Separate grid rendering from edit mode overlay logic |
| `WidgetGestureHandler` | Similar approach but with `DashboardCommand` channel | Discrete commands via sealed `DashboardCommand` → `Channel` |
| `Modifier.offset { snappedDragOffset }` | `graphicsLayer` offset animation | New arch mandates `graphicsLayer` for drag offset, not `Modifier.offset` |
| Single `DashboardCanvas` | Per-profile `DashboardCanvas` | Each profile owns independent canvas |
| `Viewport` (viewport only) | Unbounded canvas + viewport rendering window | Same concept but with configuration boundaries + no-straddle snap |
| `widgetData: Map<String, WidgetData>` in state | Per-widget `StateFlow<WidgetData>` via `LocalWidgetData` | `widgetData(widgetId)` individual flows — clock tick does NOT recompose speedometer |
| `List<DashboardWidgetInstance>` | `ImmutableList<DashboardWidgetInstance>` | All UI state uses kotlinx-collections-immutable |
| `GRID_UNIT_SIZE = 16.dp` | Same grid unit system | Grid unit size unchanged |
| Registries in `feature/dashboard/registry/` | Moved to `:sdk:contracts` (interfaces) and `:feature:dashboard` (impls) | Interfaces are SDK surface, implementations stay in shell |
| `graphicsLayer { compositingStrategy = Offscreen }` | Preserve — prevents transparency flash | |
| `key(widget.id)` | Preserve — stability key | |
| `derivedStateOf` for edit mode | Expand — use for all high-frequency state reads | New arch: `LocalWidgetData.current` + `derivedStateOf` defers reads to draw phase |
| No `SupervisorJob` for bindings | `SupervisorJob` parent for all binding jobs | One crash must NOT cancel siblings |
| `viewModelScope.launch { collect }` | `WidgetCoroutineScope` via CompositionLocal | Per-widget scope with `CoroutineExceptionHandler` → `widgetStatus` |
| 500ms debounced save | Preserve — debounced 500ms on `Dispatchers.IO` | |
| `collectAsStateWithLifecycle()` everywhere | Layer 0: `collectAsState()`, Layer 1: `collectAsStateWithLifecycle()` | Dashboard is Layer 0 (always present) |
| No frame pacing | `Window.setFrameRate()` API 34+, emission throttling API 31-33 | |
| Standard Kotlin lists | `ImmutableList`/`ImmutableMap` everywhere | `@Immutable`/`@Stable` on all UI types |

### Critical Architectural Differences

1. **No god-object state.** New arch decomposes state into coordinator-owned slices. Each coordinator (`GridCoordinator`, `EditCoordinator`, `ThemeCoordinator`, `WidgetBindingCoordinator`) owns its own `StateFlow`.

2. **Per-widget data isolation.** Old: `widgetData: Map<String, WidgetData>` in single state — clock update recompose everything. New: `widgetData(widgetId)` returns per-widget `StateFlow`, `LocalWidgetData` CompositionLocal + `derivedStateOf` defers reads to draw phase.

3. **Per-profile canvas.** Old: single `DashboardCanvas`. New: per-profile independent `DashboardCanvas`, profile switching via horizontal swipe + bottom bar icons.

4. **Configuration boundaries.** Old: viewport just filters visibility. New: configuration boundaries (fold x orientation) shown in edit mode, no-straddle snap hard constraint.

5. **Drag animation.** Old: `Modifier.offset { IntOffset(...) }`. New: `graphicsLayer` offset animation (RenderNode isolation).

6. **Immutable collections.** Old: standard `List`/`Map`. New: `ImmutableList`/`ImmutableMap` from kotlinx-collections-immutable everywhere in UI state.

7. **Widget error isolation.** Old: no crash boundary. New: `SupervisorJob` parent, `WidgetCoroutineScope`, `CoroutineExceptionHandler` → fallback UI, >3 crashes in 60s → safe mode.

8. **Bottom bar.** Old: Settings + Edit/Add buttons. New: settings + profile icons (2+) + add-widget (edit mode), auto-hides, floats over canvas.

### Reusable As-Is

- `GridPlacementEngine` — optimal position algorithm (pure logic, no UI)
- `LayoutMigrator` — schema migration pattern (extend for new schema)
- `SetupEvaluator` — runtime requirement evaluation (stateless utility)
- `DashboardHaptics` — haptic feedback wrapper
- `DashboardMotion` — animation spec constants
- `DashboardThemeExtensions` — spacing/typography system
- `WidgetGestureHandler` event model — sealed class hierarchy is well-designed
- `BlankSpaceGestureHandler` — clean separation of background gestures
- `RealWidgetDataBinder` combine pattern — but needs per-widget `StateFlow` instead of collecting into god-state
- `PresetLoader`/`PresetModels` — preset import system
- `ContextProvider` — environment detection

### Must Refactor

- `DashboardViewModel` → decompose into coordinators
- `DashboardState` → decompose into per-coordinator state slices
- `DashboardGridContainer` → extract edit mode, gesture handling, and widget rendering into separate composables
- Registries → interfaces to `:sdk:contracts`, implementations stay in `:feature:dashboard`
- `LayoutDataStore` → migrate to Proto DataStore
- Widget data binding → per-widget `StateFlow` via CompositionLocal, not `Map` in state
- All `List`/`Map` in state → `ImmutableList`/`ImmutableMap`

### Must Remove

- Direct pack imports (`:feature:packs:free`, `:feature:packs:sg-erp2`) — packs depend on SDK only
- `accompanist-permissions` — providers handle via SetupDefinition schema
- `roborazzi` visual regression — new arch uses semantics-based UI verification
- `currentTime`/`currentDate` in `DashboardState` — should come from data provider
- `currentPackId` in `DashboardState` — replaced by profile system
- `MAX_WIDGETS_PHONE`/`MAX_WIDGETS_TABLET` constants (already commented out)
