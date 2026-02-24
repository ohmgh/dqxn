# Phase 7: Dashboard Shell - Research

**Researched:** 2026-02-24
**Domain:** Coordinator-based state decomposition, Compose grid layout, gesture handling, per-widget data binding, notification system, profile management
**Confidence:** HIGH

## Summary

Phase 7 is the structural core of the entire application. It decomposes the old 1040-line god-ViewModel into six coordinators (Layout, EditMode, Theme, WidgetBinding, Notification, Profile), each owning an independent `StateFlow` slice. This eliminates the recomposition storm where a 60Hz sensor tick forces every widget to re-render.

The implementation builds on substantial completed infrastructure: SDK contracts (Phase 2) define `WidgetRenderer`, `DataProvider`, `WidgetData`, `WidgetStatusCache`, `DataProviderInterceptor`, and all notification types. SDK UI (Phase 3) provides `LocalWidgetData`, `LocalWidgetScope`, `WidgetContainer`, `GridConstants`, and `LocalDashboardTheme`. Core infrastructure (Phase 5) delivers `ThermalMonitor`/`RenderConfig`, `ThemeAutoSwitchEngine`, `DashboardMotion` (all shared animation specs), `LayoutRepository` (with full profile CRUD), `PresetLoader`, and all DataStore persistence. Phase 6 (not yet implemented) will provide `CrashRecovery`, `AlertSoundManager`, `SemanticsOwnerHolder`, the agentic `ContentProvider` transport, and the deployable `:app` shell.

**Primary recommendation:** Implement coordinators incrementally in dependency order (LayoutCoordinator first, then EditMode/Theme/Profile in parallel, WidgetBinding after Layout, Notification last). The grid `Layout` composable, gesture handlers, and `DashboardTestHarness` DSL are the highest-risk artifacts -- they require precise animation specs from the replication advisory and careful pointer event handling that cannot be unit-tested without `compose.ui.test`.

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| F1.2 | Toggleable status bar overlay | EditModeCoordinator manages `showStatusBar` state; `WindowInsetsControllerCompat` toggle in DashboardScreen |
| F1.3 | 16dp grid unit system with dynamic viewport calculation | `GridConstants.GRID_UNIT_SIZE` (existing), `DashboardCustomLayout` with custom `MeasurePolicy` |
| F1.4 | Widget rendering with viewport culling | Layout composable skips off-viewport widgets; `Viewport.isVisible()` from old codebase |
| F1.5 | Edit mode via Edit button or long-press | `EditModeCoordinator.enterEditMode()`/`exitEditMode()`, `BlankSpaceGestureHandler` |
| F1.6 | Drag-to-move widgets in edit mode | `WidgetGestureHandler` with `awaitEachGesture` + `awaitPointerEvent`, `graphicsLayer` offset |
| F1.7 | 4-corner resize handles (48dp touch targets) | Resize mechanics from replication advisory section 6; `HANDLE_SIZE = 32.dp` touch targets |
| F1.8 | Widget focus state (overlay toolbar) | `EditModeCoordinator.focusedWidgetId`, focus animation per replication advisory section 1 |
| F1.9 | Auto-hide bottom bar | `DashboardButtonBar` composable with 3s inactivity timer, 76dp touch targets |
| F1.10 | Z-index stacking | `zIndex` field on `DashboardWidgetInstance`, `placeRelative(zIndex)` in Layout |
| F1.11 | Edit mode visual feedback (wiggle, brackets) | `infiniteRepeatable(tween(150ms))` wiggle, bracket pulse from replication advisory section 4 |
| F1.12 | No widget count limit | Unbounded canvas, no artificial cap in coordinators |
| F1.13 | Dashboard-as-shell pattern | Layer 0 (`DashboardLayer`) always present, `OverlayNavHost` at Layer 1 |
| F1.14 | Pause state collection for CPU-heavy overlays | `WidgetBindingCoordinator.pauseAll()`/`resumeAll()` triggered by overlay route matching |
| F1.15 | Orientation lock | `UserPreferencesRepository` orientation setting, Activity `requestedOrientation` |
| F1.16 | `FLAG_KEEP_SCREEN_ON` | Activity window flag controlled by `UserPreferencesRepository` setting |
| F1.17 | Haptic feedback | `DashboardHaptics` with 8 semantic methods, API 30+ branching |
| F1.20 | Grid snapping (2-unit) | Snap calculation: `roundToInt(pixelPos / unitPx)` midpoint snap per replication advisory section 6 |
| F1.21 | Widget add/remove animations | `fadeIn + scaleIn` spring via `graphicsLayer` on add; `fadeOut + scaleOut` on delete |
| F1.26 | Configuration boundaries in edit mode | `WindowInfoTracker`/`WindowMetrics` → boundary line overlay with labels |
| F1.27 | No-straddle snap | Additional snap constraint when widget bounding box crosses configuration boundary |
| F1.28 | Configuration-aware default placement | `GridPlacementEngine` places core widgets in intersection region |
| F1.29 | Profile switching (swipe + icon tap) | `HorizontalPager` + `ProfileCoordinator.switchProfile()`, bottom bar profile dots |
| F1.30 | Per-profile dashboards | `ProfileCoordinator` manages independent `DashboardCanvas` per profile via `LayoutRepository` |
| F2.3 | `WidgetContainer` (glow, rim, border) | Existing skeleton in `:sdk:ui`; Phase 7 adds `RenderEffect.createBlurEffect()` glow |
| F2.4 | `WidgetDataBinder` (IoC binding) | `WidgetBindingCoordinator` with `SupervisorJob`, `merge() + scan()` per-widget flows |
| F2.5 | `WidgetStatusCache` | Existing type in `:sdk:contracts`; `WidgetBindingCoordinator` computes overlay state |
| F2.6 | `GridPlacementEngine` | Port from old codebase with no-straddle snap addition |
| F2.10 | Context-aware defaults (`WidgetContext`) | Existing `WidgetContext` in `:sdk:contracts`; `LayoutCoordinator` provides context |
| F2.11 | `SettingsAwareSizer` | Existing interface in `:sdk:contracts`; wired by `LayoutCoordinator` on widget add |
| F2.12 | `@DashboardWidget` KSP wiring | Completed in Phase 4; manifests wired into Hilt `Set<WidgetRenderer>` |
| F2.13 | `UnknownWidgetPlaceholder` | Port to `:sdk:ui`; rendered when `WidgetRegistry.findByTypeId()` returns null |
| F2.14 | Widget error boundary | `WidgetSlot` with `SupervisorJob` + `CoroutineExceptionHandler` + fallback UI |
| F2.16 | Aspect ratio constraint | `WidgetSpec.aspectRatio` enforced in resize handle calculation |
| F2.18 | Focus interaction model | `EditModeCoordinator` gates focus/unfocus, interactive actions disabled in edit mode |
| F2.19 | Widget accessibility | `Modifier.semantics { contentDescription = renderer.accessibilityDescription(data) }` |
| F2.20 | `WidgetSpec.description` | Already defined in `WidgetSpec` interface (Phase 2) |
| F3.7 | `DataProviderRegistry` with entitlement-filtered views | Interface exists; implementation wraps Hilt `Set<DataProvider<*>>` |
| F3.9 | `WhileSubscribed` sharing | Per-provider `subscriberTimeout` in `stateIn(WhileSubscribed(timeout))` |
| F3.10 | Provider fallback | `resolveProvider()` priority chain: user-selected > hardware > sensor > network |
| F3.11 | Data staleness | `DataSchema.stalenessThresholdMs` checked in `WidgetBindingCoordinator` watchdog |
| F3.14 | Provider setup failure UX | `WidgetStatusCache` → `SetupRequired`/`ConnectionError` overlay states |
| F3.15 | Progressive error disclosure | `WidgetRenderState` variants map to user-facing strings in Android resources |
| F9.1 | Silent connection status notification | `NotificationCoordinator` observes `ConnectionStateMachine`, emits Banner |
| F9.2 | Per-alert mode selection | `AlertProfile.mode` (SILENT/VIBRATE/SOUND); `AlertSoundManager` respects |
| F9.3 | TTS readout | `AlertProfile.ttsMessage`; `AlertSoundManager.ttsReadout()` |
| F9.4 | Custom alert sound URIs | `AlertProfile.soundUri` |
| F10.4 | 76dp touch targets | Bottom bar items, edit mode controls -- enforced in composable sizing |
| F10.7 | Adaptive rendering (thermal frame rate) | `ThermalMonitor.renderConfig` → `FramePacer.applyFrameRate()` + throttled data emission |
| F10.9 | Quick theme toggle | Theme mode cycle button on bottom bar, routes to `ThemeCoordinator` |
| NF1 | 60fps with 12 widgets | `graphicsLayer` isolation, `derivedStateOf` deferral, `drawWithCache`, `LocalWidgetData` |
| NF2 | Cold start < 1.5s | Eager singletons, Proto DataStore binary parse, no blocking I/O on main thread |
| NF3 | Cold start to live data < 3s | `WidgetBindingCoordinator.bind()` starts within 100ms of widget add |
| NF4 | Widget data binding < 100ms | Coordinator bind is immediate; `stateIn` starts collection |
| NF5 | Provider shutdown within `WhileSubscribed` timeout | `SharingStarted.WhileSubscribed(subscriberTimeout)` |
| NF6 | Layout save debounce 500ms | `LayoutRepositoryImpl` already debounces on `Dispatchers.IO` |
| NF7 | Viewport culling | `Viewport.isVisible()` check in `DashboardCustomLayout` |
| NF8 | Heap < 200MB with 12 widgets | `<4KB alloc/frame` target, `WidgetData.withSlot` reuses PersistentMap |
| NF15 | Device reconnection within 5s | `ConnectionStateMachine` + CDM; `WidgetBindingCoordinator` rebinds on reconnect |
| NF16 | Exponential backoff (3 attempts) | `WidgetBindingCoordinator` retry: 1s, 2s, 4s |
| NF17 | Graceful degradation | `WidgetStatusCache` placeholder states for unavailable providers |
| NF18 | Entitlement revocation handled reactively | `EntitlementManager.entitlementChanges` → `WidgetBindingCoordinator` re-evaluates |
| NF19 | Widget error isolation | `SupervisorJob` + per-widget `CoroutineExceptionHandler` |
| NF38 | Safe mode (>3 crashes in 60s) | `CrashRecovery` (Phase 6) → `NotificationCoordinator` CRITICAL banner |
| NF39 | Reduced motion | `animator_duration_scale == 0` → disable wiggle, replace spring with instant |
| NF41 | Low storage warning banner | `NotificationCoordinator` observes `StorageMonitor`, emits HIGH banner |
| NF42 | Layout save failure notification | `LayoutRepository` catches `IOException`, surfaces via `NotificationCoordinator` toast |
| NF45 | Config-aware presets | `GridPlacementEngine` + `PresetLoader` place core widgets in intersection region |
| NF46 | Foldable viewport | `WindowInfoTracker` → viewport recalculation, no-straddle constraint |
| NF-L1 | Lifecycle pause/resume | `WidgetBindingCoordinator.pauseAll()`/`resumeAll()` on Activity lifecycle |
</phase_requirements>

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Jetpack Compose | BOM (AGP 9 managed) | UI framework for grid, overlays, containers | Project standard -- Compose compiler via `dqxn.android.compose` |
| Hilt | KSP-based | DI for coordinators, registries, binder | Project standard -- `dqxn.android.hilt` convention plugin |
| Navigation Compose | Latest stable | `OverlayNavHost` Layer 1 routing | Project standard -- type-safe routes via `@Serializable` |
| Kotlin Coroutines | 1.10+ | `StateFlow`, `Channel`, `SupervisorJob`, `WhileSubscribed` | Project standard -- coordinator state management |
| kotlinx-collections-immutable | 0.3.8+ | `ImmutableList`, `ImmutableMap`, `PersistentMap` for Compose stability | Project standard -- `@Immutable` + immutable collections everywhere |
| Proto DataStore | Latest stable | Layout persistence, profile storage | Project standard -- `:data` module provides repositories |
| Turbine | 1.2+ | Flow testing in coordinator tests | Project standard -- `StandardTestDispatcher` + `turbine {}` |
| Truth | 1.5+ | Assertions in all tests | Project standard -- `assertWithMessage()` for clear diagnostics |
| MockK | 1.13+ | Mocking in coordinator unit tests | Project standard |
| JUnit5 | 5.14+ | Test framework | Project standard -- `@Tag("fast")`, `@Nested`, `TestWatcher` |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Compose UI Test | AGP-managed | Grid layout tests, gesture simulation | Drag/resize interaction tests with Robolectric |
| WindowInfoTracker (Jetpack Window) | 1.3+ | Foldable display configuration detection | Configuration boundary calculation for no-straddle snap |
| compose.foundation.pager | AGP-managed | `HorizontalPager` for profile switching | Profile page transitions |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Custom `Layout` + `MeasurePolicy` | `LazyLayout` | `LazyLayout` adds `SubcomposeLayout` overhead without benefit -- canvas is not scrollable, viewport is fixed |
| `graphicsLayer` offset for drag | `Modifier.offset` (layout pass) | `offset` triggers re-layout of all widgets on every drag frame; `graphicsLayer` is GPU-only |
| `movableContentOf` for reordering | `graphicsLayer` translation | `movableContentOf` is for cross-parent moves; widget reordering is within the same parent |
| `Channel` for commands | `SharedFlow` | `Channel` guarantees ordered serial processing; `SharedFlow` can lose events with `DROP_OLDEST` |
| `MutableStateFlow` for drag | `Channel` for drag | Drag is continuous latest-value-wins; `Channel` queuing would cause input lag |

## Architecture Patterns

### Recommended Project Structure

```
feature/dashboard/src/main/kotlin/app/dqxn/android/feature/dashboard/
├── DashboardViewModel.kt         # Thin coordinator host, command routing
├── DashboardScreen.kt            # Root composable, layer assembly
├── command/
│   └── DashboardCommand.kt       # Sealed interface for all discrete commands
├── coordinator/
│   ├── LayoutCoordinator.kt      # Canvas positioning, viewport culling, grid placement
│   ├── EditModeCoordinator.kt    # Edit mode toggle, drag/resize gestures
│   ├── ThemeCoordinator.kt       # Active theme, auto-switch, preview/revert
│   ├── WidgetBindingCoordinator.kt # Provider binding, SupervisorJob isolation, status
│   ├── NotificationCoordinator.kt  # Banner derivation, toast queue, priority ordering
│   └── ProfileCoordinator.kt     # Profile create/clone/switch/delete
├── binding/
│   ├── WidgetDataBinder.kt       # IoC provider-to-widget binding with interceptors
│   └── WidgetSlot.kt             # Error boundary composable with LocalWidgetData
├── grid/
│   ├── DashboardGrid.kt          # DashboardCustomLayout + widget rendering
│   ├── GridPlacementEngine.kt    # Optimal position finder with no-straddle
│   ├── WidgetGestureHandler.kt   # Per-widget gesture state machine
│   └── BlankSpaceGestureHandler.kt # Background tap/long-press
├── gesture/
│   └── DashboardHaptics.kt       # Haptic feedback (6 semantic methods)
├── layer/
│   ├── DashboardLayer.kt         # Layer 0, semantics registration
│   ├── OverlayNavHost.kt         # Layer 1 (empty route table, scaffolded)
│   ├── NotificationBannerHost.kt # Layer 0.5 non-critical banners + toasts
│   └── CriticalBannerHost.kt     # Layer 1.5 CRITICAL banners
├── profile/
│   └── ProfilePageTransition.kt  # HorizontalPager profile switching
├── ui/
│   ├── DashboardButtonBar.kt     # Bottom bar (settings, profiles, add-widget)
│   ├── ConfirmationDialog.kt     # Reusable modal with scrim + animation
│   ├── OverlayScaffold.kt        # Base scaffold for overlay content
│   ├── WidgetErrorFallback.kt    # Error state UI for crashed widgets
│   └── UnknownWidgetPlaceholder.kt # Deregistered widget fallback (or in :sdk:ui)
├── safety/
│   └── SafeModeManager.kt        # Safe mode state coordination
├── di/
│   └── DashboardModule.kt        # Hilt module: coordinators, registries, binder
└── test/
    └── DashboardTestHarness.kt   # DSL for coordinator-level testing (testFixtures)
```

### Pattern 1: Coordinator-Owned State Slices

**What:** Each coordinator manages its own `StateFlow` independently. The ViewModel is a thin composition root that routes `DashboardCommand` to the correct coordinator.

**When to use:** All state management in the dashboard feature.

```kotlin
// Each coordinator owns its own state -- no shared mutable state object
class LayoutCoordinator @Inject constructor(
    private val layoutRepository: LayoutRepository,
    private val presetLoader: PresetLoader,
    private val gridPlacementEngine: GridPlacementEngine,
) {
    private val _layoutState = MutableStateFlow(LayoutState(widgets = persistentListOf(), isLoading = true))
    val layoutState: StateFlow<LayoutState> = _layoutState.asStateFlow()

    suspend fun handleAddWidget(widget: DashboardWidgetInstance) {
        val current = _layoutState.value.widgets
        val positioned = gridPlacementEngine.findOptimalPosition(widget, current)
        _layoutState.update { it.copy(widgets = current.add(positioned)) }
        layoutRepository.addWidget(positioned) // IO
    }
}

// ViewModel routes commands, holds no business logic
@HiltViewModel
class DashboardViewModel @Inject constructor(
    val layoutCoordinator: LayoutCoordinator,
    val editModeCoordinator: EditModeCoordinator,
    val themeCoordinator: ThemeCoordinator,
    val widgetBindingCoordinator: WidgetBindingCoordinator,
    val notificationCoordinator: NotificationCoordinator,
    val profileCoordinator: ProfileCoordinator,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val commandChannel = Channel<DashboardCommand>(capacity = 64)

    init {
        viewModelScope.launch {
            for (command in commandChannel) {
                try { routeCommand(command) }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { /* log, report, don't kill loop */ }
            }
        }
    }

    private suspend fun routeCommand(command: DashboardCommand) {
        when (command) {
            is DashboardCommand.AddWidget -> layoutCoordinator.handleAddWidget(command.widget)
            is DashboardCommand.EnterEditMode -> editModeCoordinator.enterEditMode()
            is DashboardCommand.SetTheme -> themeCoordinator.handleSetTheme(command.themeId)
            // ... route all commands to their coordinator
        }
    }
}
```

### Pattern 2: Per-Widget Data Binding with SupervisorJob

**What:** Each widget's data binding runs as an independent coroutine job under a shared `SupervisorJob`. One widget crashing does not cancel other widgets' bindings.

**When to use:** `WidgetBindingCoordinator.bind()`.

```kotlin
private val bindingSupervisor = SupervisorJob(scope.coroutineContext.job)
private val bindingScope = scope + bindingSupervisor

fun bind(widget: DashboardWidgetInstance) {
    bindings[widget.id]?.cancel()
    bindings[widget.id] = bindingScope.launch(
        CoroutineExceptionHandler { _, e ->
            // Report, retry with exponential backoff, or mark as errored
            handleBindingFailure(widget, e)
        }
    ) {
        binder.bind(widget, renderConfig).collect { data ->
            emitWidgetData(widget.id, data)
        }
    }
}
```

### Pattern 3: Split Event Channels (Discrete vs Continuous)

**What:** Discrete commands use `Channel` for ordered serial processing. Continuous gestures use `MutableStateFlow` for latest-value-wins conflation.

**When to use:** Always. Never queue drag events in a Channel.

```kotlin
// Discrete: ordered, serial, transactional
private val commandChannel = Channel<DashboardCommand>(capacity = 64)

// Continuous: conflated, latest-value-wins, no queuing
private val _dragState = MutableStateFlow<DragUpdate?>(null)
private val _resizeState = MutableStateFlow<ResizeUpdate?>(null)
```

### Pattern 4: Notification Re-derivation from Singletons

**What:** `NotificationCoordinator` is `@ViewModelScoped` but observes `@Singleton` state flows. On ViewModel recreation, it re-derives all condition-based banners from current singleton state. No events are stored.

**When to use:** All persistent notification conditions (safe mode, BLE adapter, storage).

```kotlin
// In NotificationCoordinator init -- 5 launch blocks observing singleton state
scope.launch {
    crashRecovery.safeModeActive.distinctUntilChanged().collect { active ->
        if (active) showBanner(id = "safe_mode", priority = CRITICAL, ...)
        else dismissBanner("safe_mode")
    }
}
```

### Pattern 5: Dashboard-as-Shell Layer Stack

**What:** Dashboard is never a navigation destination. It persists at Layer 0 beneath all overlays.

```kotlin
Box {
    DashboardLayer(...)                // Layer 0: always present
    DashboardButtonBar(...)            // Layer 0.25: auto-hiding bottom bar
    NotificationBannerHost(...)        // Layer 0.5: non-critical banners + toasts
    OverlayNavHost(...)                // Layer 1: settings, pickers, dialogs
    CriticalBannerHost(...)            // Layer 1.5: safe mode banner
}
```

### Anti-Patterns to Avoid

- **God-object state class:** The old `DashboardState` data class bundles all state. Every `copy()` triggers universal recomposition. Use decomposed `StateFlow` per coordinator.
- **`LazyLayout` for grid:** The canvas is fixed-viewport, not scrollable. `SubcomposeLayout` overhead is pure waste.
- **`movableContentOf` for drag:** Designed for cross-parent moves. Widget drag is visual offset within the same parent -- use `graphicsLayer`.
- **`Modifier.offset` for drag translation:** Layout-pass offset triggers re-measure/re-layout of all siblings. Use `graphicsLayer { translationX/Y }` for GPU-only offset.
- **`BlurMaskFilter` for glow:** Explicitly forbidden. Use `RenderEffect.createBlurEffect()` (API 31+, GPU shader).
- **`detectDragGesturesAfterLongPress`:** Does not provide the 8px cancellation threshold for scroll discrimination. Use manual `awaitEachGesture` + `awaitPointerEvent`.
- **`collectAsStateWithLifecycle()` on Layer 0:** Lifecycle pausing causes a jank spike when 12+ widgets resume simultaneously. Use `collectAsState()` for Layer 0.
- **Business logic in ViewModel:** If you're writing `when` over domain state in ViewModel, it belongs in a coordinator.
- **Global coroutine scope in coordinators:** All scopes via Hilt (`viewModelScope`) or parent `SupervisorJob`. No `GlobalScope`.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Gesture conflict resolution | Custom event bus for gesture priority | `PointerEventPass.Initial/Main` + `consume()` + `requireUnconsumed` | Compose's pointer event system already has a multi-pass design; the 6 conflict resolutions in the replication advisory all use this mechanism |
| Animation springs | Custom spring physics | `spring<Float>(dampingRatio, stiffness)` from Compose | The replication advisory documents exact spring parameters -- use them directly |
| Grid layout | Custom `Canvas` drawing with manual hit testing | `Layout` + `MeasurePolicy` + `layoutId` | Compose's layout system handles measurement, placement, and pointer routing automatically |
| Flow throttling for thermal | Custom Choreographer callbacks | `stateIn(WhileSubscribed)` + data emission throttling | Compose draws when state changes; throttle emissions, don't fight the rendering pipeline |
| Crash counting | In-memory counter | `SharedPreferences` (synchronous, survives process death) | `CrashRecovery` in Phase 6 already uses SharedPrefs; DataStore is async and may not survive the crash |
| Foldable detection | Manual `Display.getMetrics()` | `WindowInfoTracker.windowLayoutInfo()` | Jetpack Window library handles fold state, hinge angle, and configuration changes |
| Profile page transition | Custom swipe gesture detector | `HorizontalPager` from Compose Foundation | `HorizontalPager` handles velocity, snap-to-page, and overscroll natively |

**Key insight:** This phase is 80% coordination logic and 20% Compose UI. The temptation is to over-invest in custom rendering. The grid `Layout`, `graphicsLayer` isolation, `HorizontalPager`, and Compose's pointer system handle most of the hard UI work. The real complexity is in the coordinator interactions, gesture filtering, and the precise animation specs.

## Common Pitfalls

### Pitfall 1: Recomposition Storms from Shared State

**What goes wrong:** A single `StateFlow<DashboardState>` containing widget data, theme, edit mode, and layout causes every composable to recompose on any change.
**Why it happens:** `StateFlow` equality check fails when any field changes; all collectors recompose.
**How to avoid:** Decompose into independent flows per coordinator. Each widget subscribes only to `widgetData(myId)`.
**Warning signs:** Compose compiler metrics showing >50 non-skippable recompositions per frame. `MetricsCollector` frame times consistently >16ms with few widgets.

### Pitfall 2: Drag Gesture Eating Scroll/Click Events

**What goes wrong:** Edit mode drag gesture intercepts all pointer events, breaking widget tap interactions and profile swipe.
**Why it happens:** `PointerEventPass.Initial` in edit mode intercepts before child widgets; incorrect pass selection bleeds into non-edit mode.
**How to avoid:** Capture `wasInEditModeAtStart` at gesture start for consistent pass selection. Use `requireUnconsumed = true` in `BlankSpaceGestureHandler`. Never consume events in non-edit mode that would prevent widget `Clickable` from firing.
**Warning signs:** Widgets stop responding to taps; profile swipe doesn't work outside edit mode.

### Pitfall 3: Resize Handle Position Compensation Errors

**What goes wrong:** Resizing from TopLeft/TopRight/BottomLeft handles causes the widget to jump or drift because the origin shifts when size changes.
**Why it happens:** Only BottomRight resize keeps the origin fixed. Other corners require compensating position adjustment proportional to size delta.
**How to avoid:** Follow the replication advisory section 6 exactly: TopLeft `gridX -= deltaWidth, gridY -= deltaHeight`; TopRight `gridY -= deltaHeight`; BottomLeft `gridX -= deltaWidth`. Use `Modifier.offset` during resize (layout-pass, intentionally -- resize changes layout size).
**Warning signs:** Widget "jumps" when grabbing non-BottomRight handles.

### Pitfall 4: Notification Banner Flicker on State Oscillation

**What goes wrong:** BLE adapter flapping (on/off/on rapidly) causes banner dismiss/recreate animation spam.
**Why it happens:** Each state change creates a new banner with a new identity.
**How to avoid:** Condition-keyed banner IDs (`"ble_adapter_off"`, not UUID). `showBanner` with existing ID updates in-place. `distinctUntilChanged()` on observed state flows before mapping to banners.
**Warning signs:** Banner animation firing repeatedly during BLE connection settling.

### Pitfall 5: Safe Mode Cross-Widget Counting

**What goes wrong:** Safe mode triggers only when a single widget crashes 4 times, not when 4 different widgets each crash once.
**Why it happens:** Per-widget crash counter instead of global 60s rolling window.
**How to avoid:** `CrashRecovery` uses timestamp-based rolling window (60s) counting ALL widget crashes, not per-widget counts. 4 different widgets each crashing once = 4 total = safe mode triggered.
**Warning signs:** App crash-loops without entering safe mode because no single widget hit the threshold.

### Pitfall 6: Theme Preview Flash on Navigation

**What goes wrong:** Navigating to ThemeSelector shows a brief flash of the non-previewed theme before the preview applies.
**Why it happens:** Preview theme set after navigation starts; first frame renders the committed theme.
**How to avoid:** Fire `SetPreviewTheme` BEFORE navigation (caller-managed preview pattern from replication advisory section 3). `LocalDashboardTheme` is `staticCompositionLocalOf` -- change invalidates the entire tree in one frame.
**Warning signs:** Brief theme color flash when opening theme selector.

### Pitfall 7: `editingWidgetId` Derived from Wrong Source

**What goes wrong:** Widget preview mode collapses when navigating from WidgetSettings to child routes (ProviderSetup, TimezoneSelector).
**Why it happens:** Checking `navController.currentEntry` instead of scanning the entire back stack.
**How to avoid:** Scan the full `navController.currentBackStack.value` for `Route.WidgetSettings` entries, not just the current destination. This is the most important takeaway from replication advisory section 1.
**Warning signs:** Widget preview (settings peek) disappears when opening timezone picker or provider setup from widget settings.

### Pitfall 8: `animator_duration_scale` Not Checked

**What goes wrong:** Reduced motion users still see wiggle animations and spring transitions.
**Why it happens:** Forgetting to check `Settings.Global.ANIMATOR_DURATION_SCALE`.
**How to avoid:** Read the system setting once at coordinator init. When `== 0f`, disable wiggle animation, replace all `spring()` with `snap()` transitions, but keep glow effects (they're not motion).
**Warning signs:** Accessibility tester reports motion despite "Remove animations" system setting enabled.

## Code Examples

### DashboardCommand Sealed Interface

```kotlin
sealed interface DashboardCommand {
    val traceId: String?

    data class AddWidget(
        val widget: DashboardWidgetInstance,
        override val traceId: String? = null,
    ) : DashboardCommand

    data class RemoveWidget(
        val widgetId: String,
        override val traceId: String? = null,
    ) : DashboardCommand

    data class MoveWidget(
        val widgetId: String,
        val newPosition: GridPosition,
        override val traceId: String? = null,
    ) : DashboardCommand

    data class ResizeWidget(
        val widgetId: String,
        val newSize: GridSize,
        val newPosition: GridPosition?, // non-null for non-BottomRight handles
        override val traceId: String? = null,
    ) : DashboardCommand

    data class FocusWidget(
        val widgetId: String?,
        override val traceId: String? = null,
    ) : DashboardCommand

    data object EnterEditMode : DashboardCommand { override val traceId: String? = null }
    data object ExitEditMode : DashboardCommand { override val traceId: String? = null }

    data class SetTheme(
        val themeId: String,
        override val traceId: String? = null,
    ) : DashboardCommand

    data class PreviewTheme(
        val theme: DashboardThemeDefinition?,
        override val traceId: String? = null,
    ) : DashboardCommand

    data class WidgetCrash(
        val widgetId: String,
        val typeId: String,
        val throwable: Throwable,
        override val traceId: String? = null,
    ) : DashboardCommand

    data class SwitchProfile(
        val profileId: String,
        override val traceId: String? = null,
    ) : DashboardCommand

    data class CreateProfile(
        val displayName: String,
        val cloneCurrentId: String? = null,
        override val traceId: String? = null,
    ) : DashboardCommand

    data class DeleteProfile(
        val profileId: String,
        override val traceId: String? = null,
    ) : DashboardCommand

    data class ResetLayout(
        override val traceId: String? = null,
    ) : DashboardCommand

    // ... additional commands
}
```

### DashboardTestHarness DSL

```kotlin
fun dashboardTest(
    fakeLayout: Boolean = false,
    block: suspend DashboardTestScope.() -> Unit,
) = runTest {
    val harness = DashboardTestHarness(
        testScope = this,
        useFakeLayout = fakeLayout,
    )
    harness.initialize()
    DashboardTestScope(harness).block()
}

class DashboardTestScope(private val harness: DashboardTestHarness) {
    suspend fun dispatch(command: DashboardCommand) = harness.dispatch(command)
    fun layoutState(): LayoutState = harness.layoutCoordinator.layoutState.value
    fun editState(): EditState = harness.editModeCoordinator.editState.value
    fun themeState(): ThemeState = harness.themeCoordinator.themeState.value
    fun bindingJobs(): Map<String, Job> = harness.widgetBindingCoordinator.activeBindings()
    fun safeMode(): SafeModeState = harness.safeMode()
    fun widgetStatuses(): Map<String, WidgetStatusCache> = harness.widgetBindingCoordinator.allStatuses()
    fun ringBufferTail(count: Int): List<LogEntry> = harness.ringBuffer.tail(count)
}
```

### Grid Viewport Culling

```kotlin
@Composable
fun DashboardGrid(
    widgets: ImmutableList<DashboardWidgetInstance>,
    viewportCols: Int,
    viewportRows: Int,
    modifier: Modifier = Modifier,
) {
    val visibleWidgets = remember(widgets, viewportCols, viewportRows) {
        widgets.filter { widget ->
            widget.position.col < viewportCols &&
            widget.position.row < viewportRows &&
            widget.position.col + widget.size.widthUnits > 0 &&
            widget.position.row + widget.size.heightUnits > 0
        }
    }

    Layout(
        content = {
            visibleWidgets.forEach { widget ->
                key(widget.instanceId) {
                    WidgetSlot(
                        widget = widget,
                        modifier = Modifier
                            .layoutId(widget.instanceId)
                            .testTag("widget_${widget.instanceId}"),
                    )
                }
            }
        },
        modifier = modifier.testTag("dashboard_grid"),
    ) { measurables, constraints ->
        // Custom MeasurePolicy: absolute grid positioning
        // ...
    }
}
```

### Reduced Motion Check

```kotlin
class ReducedMotionHelper @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    val isReducedMotion: Boolean
        get() = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `BlurMaskFilter` for glow | `RenderEffect.createBlurEffect()` | API 31+ (minSdk) | GPU shader, no offscreen buffer allocation |
| Preferences DataStore for layouts | Proto DataStore with typed messages | Phase 5 | Type-safe layout persistence with schema migration |
| `BroadcastReceiver` for agentic | `ContentProvider.call()` | Phase 6 | Binder thread, no IPC serialization overhead |
| God-object ViewModel (1040 lines) | 6 coordinators with decomposed StateFlow | Phase 7 | Eliminates recomposition storms, enables independent testing |
| Single canvas per app | Per-profile independent canvases | Phase 5 (data) + Phase 7 (coordination) | Full profile system with clone, switch, delete |
| `detectDragGesturesAfterLongPress` | Manual `awaitEachGesture` + `awaitPointerEvent` | Phase 7 | 8px cancellation threshold for scroll discrimination |
| `Modifier.offset` for drag | `graphicsLayer { translationX/Y }` for drag | Phase 7 | GPU-only offset, no layout invalidation |

**Deprecated/outdated:**
- `BlurMaskFilter`: Explicitly banned per CLAUDE.md. CPU-side offscreen buffer allocation.
- `LazyLayout` for grid: `SubcomposeLayout` overhead without benefit; canvas is not scrollable.
- `movableContentOf` for drag: Cross-parent design, not same-parent reordering.
- `Choreographer.postFrameCallback` for frame pacing: Fights Compose's rendering pipeline.

## Open Questions

1. **`WidgetRegistry`/`DataProviderRegistry` implementation scope**
   - What we know: Interfaces exist in `:sdk:contracts`. Phase 6 provides empty `Set<WidgetRenderer>` and `Set<DataProvider<*>>` via Hilt.
   - What's unclear: Should registry implementations (`WidgetRegistryImpl`, `DataProviderRegistryImpl`) live in `:feature:dashboard` or `:app`? The old codebase put them in `feature/dashboard/registry/`. Phase 6 module plan doesn't specify.
   - Recommendation: Place in `:feature:dashboard` (they're consumed by dashboard coordinators, not by `:app` directly). Hilt `Set<T>` is injected by `:app`; registries index the sets.

2. **`StorageMonitor` implementation**
   - What we know: `NotificationCoordinator` observes `StorageMonitor.isLow` for NF41 low storage banner.
   - What's unclear: No `StorageMonitor` type exists yet. Phase 6 doesn't define it.
   - Recommendation: Create a minimal `StorageMonitor` (`@Singleton`, checks `StatFs(dataDir).availableBytes < 50MB`) in `:feature:dashboard` or `:sdk:observability`. Emits `StateFlow<Boolean>`.

3. **Configuration boundary detection for non-foldable devices**
   - What we know: `WindowInfoTracker.windowLayoutInfo()` provides fold state for foldables. Non-foldable with orientation-unlocked needs two viewport rectangles.
   - What's unclear: How to compute the "other orientation" viewport dimensions on a non-foldable without actually rotating.
   - Recommendation: Use `WindowMetrics` current + cached known-dimensions for alternate orientation. Or use `Display.supportedModes` for display resolution, then compute viewport from resolution + orientation. This is a lower-priority edge case that can be deferred if it blocks.

4. **`DashboardTestHarness` placement**
   - What we know: Architecture doc says it uses real coordinator implementations by default with fakes for isolation.
   - What's unclear: Should the harness live in `testFixtures` of `:feature:dashboard` or in a separate test utility module?
   - Recommendation: `:feature:dashboard` `testFixtures` source set. It needs direct access to coordinator implementations and the `HarnessStateOnFailure` JUnit5 `TestWatcher`.

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit5 (via `de.mannodermaus.android-junit` 2.0.1+) + Compose UI Test (Robolectric) |
| Config file | Convention plugin `dqxn.android.test` auto-configures `useJUnitPlatform()` and `@Tag` filtering |
| Quick run command | `./gradlew :feature:dashboard:testDebugUnitTest --console=plain -PincludeTags=fast` |
| Full suite command | `./gradlew :feature:dashboard:testDebugUnitTest --console=plain` |
| Estimated runtime | ~30s (full module), ~10s (fast-tagged only) |

### Phase Requirements -> Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| F1.3 | 16dp grid unit viewport calculation | unit | `:feature:dashboard:testDebugUnitTest --tests "*.GridPlacementEngineTest"` | No -- Wave 0 gap |
| F1.4 | Viewport culling skips off-screen widgets | unit | `:feature:dashboard:testDebugUnitTest --tests "*.DashboardGridTest"` | No -- Wave 0 gap |
| F1.5 | Edit mode entry/exit | unit | `:feature:dashboard:testDebugUnitTest --tests "*.EditModeCoordinatorTest"` | No -- Wave 0 gap |
| F1.6 | Drag-to-move | compose | `:feature:dashboard:testDebugUnitTest --tests "*.WidgetGestureHandlerTest"` | No -- Wave 0 gap |
| F1.7 | Resize handles | compose | `:feature:dashboard:testDebugUnitTest --tests "*.WidgetGestureHandlerTest"` | No -- Wave 0 gap |
| F1.9 | Bottom bar auto-hide | compose | `:feature:dashboard:testDebugUnitTest --tests "*.DashboardButtonBarTest"` | No -- Wave 0 gap |
| F1.11 | Wiggle animation | compose | `:feature:dashboard:testDebugUnitTest --tests "*.EditModeAnimationTest"` | No -- Wave 0 gap |
| F1.20 | Grid snapping (2-unit) | unit | `:feature:dashboard:testDebugUnitTest --tests "*.GridPlacementEngineTest"` | No -- Wave 0 gap |
| F1.27 | No-straddle snap | unit | `:feature:dashboard:testDebugUnitTest --tests "*.GridPlacementEngineTest"` | No -- Wave 0 gap |
| F1.29 | Profile switching | unit | `:feature:dashboard:testDebugUnitTest --tests "*.ProfileCoordinatorTest"` | No -- Wave 0 gap |
| F1.30 | Per-profile canvas independence | unit | `:feature:dashboard:testDebugUnitTest --tests "*.ProfileCoordinatorTest"` | No -- Wave 0 gap |
| F2.4 | WidgetDataBinder IoC binding | unit | `:feature:dashboard:testDebugUnitTest --tests "*.WidgetDataBinderTest"` | No -- Wave 0 gap |
| F2.6 | GridPlacementEngine placement | unit | `:feature:dashboard:testDebugUnitTest --tests "*.GridPlacementEngineTest"` | No -- Wave 0 gap |
| F2.14 | Widget error boundary | unit | `:feature:dashboard:testDebugUnitTest --tests "*.WidgetSlotTest"` | No -- Wave 0 gap |
| F3.10 | Provider fallback | unit | `:feature:dashboard:testDebugUnitTest --tests "*.WidgetDataBinderTest"` | No -- Wave 0 gap |
| F3.11 | Data staleness | unit | `:feature:dashboard:testDebugUnitTest --tests "*.WidgetBindingCoordinatorTest"` | No -- Wave 0 gap |
| NF19 | SupervisorJob isolation | unit | `:feature:dashboard:testDebugUnitTest --tests "*.WidgetBindingCoordinatorTest"` | No -- Wave 0 gap |
| NF38 | Safe mode (>3 crashes in 60s) | unit | `:feature:dashboard:testDebugUnitTest --tests "*.SafeModeTest"` | No -- Wave 0 gap |
| NF39 | Reduced motion | compose | `:feature:dashboard:testDebugUnitTest --tests "*.ReducedMotionTest"` | No -- Wave 0 gap |
| NF-L1 | Lifecycle pause/resume | unit | `:feature:dashboard:testDebugUnitTest --tests "*.WidgetBindingCoordinatorTest"` | No -- Wave 0 gap |
| -- | DashboardViewModel command routing | unit | `:feature:dashboard:testDebugUnitTest --tests "*.DashboardViewModelTest"` | No -- Wave 0 gap |
| -- | NotificationCoordinator banner re-derivation | unit | `:feature:dashboard:testDebugUnitTest --tests "*.NotificationCoordinatorTest"` | No -- Wave 0 gap |
| -- | DashboardTestHarness integration | unit | `:feature:dashboard:testDebugUnitTest --tests "*.DashboardTestHarnessTest"` | No -- Wave 0 gap |
| -- | Agentic mutation handlers | unit | `:feature:dashboard:testDebugUnitTest --tests "*.AgenticMutationHandlerTest"` | No -- Wave 0 gap |
| -- | dump-semantics dashboard_grid tag | on-device | `adb shell content call --method dump-semantics` | No -- Phase 7 deliverable |

### Nyquist Sampling Rate

- **Minimum sample interval:** After every committed task -> run: `./gradlew :feature:dashboard:testDebugUnitTest --console=plain -PincludeTags=fast`
- **Full suite trigger:** Before merging final task of any plan wave
- **Phase-complete gate:** Full suite green before `/gsd:verify-work` runs (`./gradlew :feature:dashboard:testDebugUnitTest :data:testDebugUnitTest :core:design:testDebugUnitTest :core:thermal:testDebugUnitTest --console=plain`)
- **Estimated feedback latency per task:** ~10-15 seconds (fast tests), ~30 seconds (full module)

### Wave 0 Gaps (must be created before implementation)

This is a greenfield feature module. Every test file is a Wave 0 gap. The planner must ensure each plan wave creates its test files alongside the production code. Key test infrastructure needed:

- [ ] `feature/dashboard/src/testFixtures/kotlin/.../DashboardTestHarness.kt` -- coordinator test DSL
- [ ] `feature/dashboard/src/testFixtures/kotlin/.../HarnessStateOnFailure.kt` -- JUnit5 TestWatcher
- [ ] `feature/dashboard/src/testFixtures/kotlin/.../FakeLayoutRepository.kt` -- fake for isolation tests
- [ ] `feature/dashboard/src/testFixtures/kotlin/.../FakeWidgetDataBinder.kt` -- fake for binding isolation
- [ ] `feature/dashboard/src/test/kotlin/.../coordinator/LayoutCoordinatorTest.kt`
- [ ] `feature/dashboard/src/test/kotlin/.../coordinator/EditModeCoordinatorTest.kt`
- [ ] `feature/dashboard/src/test/kotlin/.../coordinator/ThemeCoordinatorTest.kt`
- [ ] `feature/dashboard/src/test/kotlin/.../coordinator/WidgetBindingCoordinatorTest.kt`
- [ ] `feature/dashboard/src/test/kotlin/.../coordinator/NotificationCoordinatorTest.kt`
- [ ] `feature/dashboard/src/test/kotlin/.../coordinator/ProfileCoordinatorTest.kt`
- [ ] `feature/dashboard/src/test/kotlin/.../DashboardViewModelTest.kt`
- [ ] `feature/dashboard/src/test/kotlin/.../grid/GridPlacementEngineTest.kt`
- [ ] `feature/dashboard/src/test/kotlin/.../binding/WidgetDataBinderTest.kt`
- [ ] `feature/dashboard/src/test/kotlin/.../DashboardTestHarnessTest.kt`
- [ ] Build file additions: `:feature:dashboard` needs `implementation(project(":core:design"))`, `implementation(project(":core:thermal"))`, `implementation(project(":data"))` in `build.gradle.kts`

## Sources

### Primary (HIGH confidence)

- `/Users/ohm/Workspace/dqxn/.planning/ARCHITECTURE.md` -- Full target architecture
- `/Users/ohm/Workspace/dqxn/.planning/arch/state-management.md` -- Coordinator patterns, data binding, staleness, DI scoping
- `/Users/ohm/Workspace/dqxn/.planning/arch/compose-performance.md` -- Grid layout, graphicsLayer, thermal, glow, startup
- `/Users/ohm/Workspace/dqxn/.planning/arch/plugin-system.md` -- Widget/provider contracts, error isolation, theme system
- `/Users/ohm/Workspace/dqxn/.planning/arch/platform.md` -- Navigation, notification architecture, permissions
- `/Users/ohm/Workspace/dqxn/.planning/arch/testing.md` -- DashboardTestHarness, HarnessStateOnFailure, E2E protocol
- `/Users/ohm/Workspace/dqxn/.planning/migration/phase-07.md` -- Phase 7 detailed spec with port inventory
- `/Users/ohm/Workspace/dqxn/.planning/migration/replication-advisory.md` -- 5 of 7 sections apply (sections 1-4, 6)
- `/Users/ohm/Workspace/dqxn/.planning/REQUIREMENTS.md` -- All requirement definitions
- `/Users/ohm/Workspace/dqxn/CLAUDE.md` -- Project constraints, code style, module rules
- Existing codebase files (verified by reading): `WidgetRenderer`, `WidgetSpec`, `WidgetData`, `WidgetStatusCache`, `WidgetRenderState`, `DataProvider`, `DataProviderInterceptor`, `DataProviderRegistry`, `WidgetRegistry`, `LayoutRepository`, `DashboardWidgetInstance`, `GridPosition`, `GridSize`, `LocalWidgetData`, `LocalWidgetScope`, `WidgetContainer`, `GridConstants`, `DashboardMotion`, `ThermalMonitor`, `RenderConfig`, `InAppNotification`, `AlertProfile`, `ProviderFault`, `TestDataProvider`

### Secondary (MEDIUM confidence)

- `/Users/ohm/Workspace/dqxn/.planning/oldcodebase/feature-dashboard.md` -- Old codebase mapping (index only, source is truth)

### Tertiary (LOW confidence)

- None -- all findings sourced from project documentation and existing codebase

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH -- all libraries already in version catalog and convention plugins, no new dependencies required
- Architecture: HIGH -- architecture docs are exhaustive (state-management.md, compose-performance.md alone are >400 lines each), replication advisory documents exact animation specs
- Pitfalls: HIGH -- replication advisory sections 1, 2, 3, 4, 6 provide exact edge cases with code examples from old codebase
- Testing: HIGH -- test architecture doc defines DashboardTestHarness DSL, HarnessStateOnFailure pattern, and agentic validation pipeline

**Research date:** 2026-02-24
**Valid until:** 2026-03-24 (30 days -- architecture stable, pre-launch)
