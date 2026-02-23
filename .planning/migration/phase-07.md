# Phase 7: Dashboard Shell

**What:** The hardest phase. The 1040-line god-ViewModel and god-state must be decomposed into coordinators. This is structural transformation, not porting. Full agentic debug infrastructure is available on-device — use it.

## `:feature:dashboard`

**State decomposition** — break `DashboardState` into coordinator-owned slices:

- `LayoutCoordinator` — canvas positioning, viewport culling, grid placement
- `EditModeCoordinator` — edit mode toggle, drag/resize gestures
- `ThemeCoordinator` — active theme, auto-switch engine, preview/revert
- `WidgetBindingCoordinator` — provider binding, `SupervisorJob` isolation, per-widget status tracking, error counts, safe mode trigger
- `NotificationCoordinator` — banner derivation, toast queue, priority ordering
- `ProfileCoordinator` — profile creation/clone/switch/delete, per-profile canvas ownership

**Core components:**

- `DashboardCommand` sealed interface + `Channel` routing to coordinators, with `agenticTraceId` propagation from agentic commands
- `WidgetDataBinder` — rewrite with `SupervisorJob`, `CoroutineExceptionHandler`, `widgetStatus` reporting. Applies `DataProviderInterceptor` chain (for chaos injection)
- `DashboardGrid` — migrate layout logic (custom `MeasurePolicy` ports well), swap data delivery to `LocalWidgetData`, add `graphicsLayer` isolation per widget. Wire `MetricsCollector.recordWidgetDrawTime()` in draw modifier (not `LaunchedEffect`) and `MetricsCollector.recordRecomposition()` in widget container. Apply `Modifier.testTag()` to all key elements per test tag convention (see build-system.md): `dashboard_grid`, `widget_{id}`, `bottom_bar`, `banner_{id}`, etc. — required for agentic `dump-semantics`/`query-semantics` UI verification
- `DashboardCanvas` — Layer 0 + Layer 1 overlay structure (ports with adaptation). `DashboardLayer` registers `SemanticsOwnerHolder` (debug only) — semantics commands start returning real data from this point
- `OverlayNavHost` — Layer 1 navigation
- Widget error boundary — `WidgetCoroutineScope` via CompositionLocal, crash count → safe mode

**`DashboardTestHarness`** — rebuild DSL for new coordinator architecture:

- DSL surface: `dashboardTest { dispatch(...); assertThat(...) }` with `layoutState()`, `bindingJobs()`, `safeMode()`, `editModeState()`, `widgetPickerAvailable()`, `settingsAccessible()`, `widgetStatuses()`, `ringBufferTail()`
- Default configuration uses **real coordinator implementations**, not fakes. Fakes available for isolation tests via `dashboardTest(fakeLayout = true) { ... }` but the primary integration path exercises real coordinator-to-coordinator interactions
- `FakeLayoutRepository`, `FakeWidgetDataBinder` for persistence/binding isolation
- Uses `TestDataProvider` and `ProviderFault` from `:sdk:contracts:testFixtures` for fault injection in unit tests

**`HarnessStateOnFailure`** — JUnit5 `TestWatcher`:

- Auto-dumps harness state as JSON on test failure
- Format: `layout`, `theme`, `widgetStatuses`, `bindingJobs`, `ringBufferTail(20)`
- JSON structure matches `diagnose-*` response format — agent parses both with the same logic
- No `DiagnosticSnapshotCapture` dependency (no observability graph in unit tests)

**Mutation handlers wired:** All mutation handlers listed in Phase 6 table become functional as coordinators land. Agent can now `add-widget`, `set-theme`, `inject-fault` etc. on device.

**Deferred to Phase 10 (overlay composables):** WidgetPicker, ThemeSelector, MainSettings, SetupSheet, SettingRowDispatcher (10+ row types), InlineColorPicker, GradientTypeSelector, GradientStopRow, AutoSwitchModeContent, IlluminanceThresholdControl. Phase 7 delivers the structural shell — coordinators, grid, binding, canvas. Overlays build on the shell in Phase 10 using the coordinator APIs established here. OverlayNavHost is scaffolded (empty route table) in Phase 7; routes populated in Phase 10.

**`DashboardViewModel`** — thin coordinator host, not a god-object:
- Owns `Channel<DashboardCommand>` + consumption loop routing to coordinators
- Holds `SavedStateHandle` for process-death survival (edit mode state, active profile ID)
- Constructor-injected coordinators — ViewModel is the composition root, coordinators are the logic
- No business logic in ViewModel. If you're writing `when` over domain state in ViewModel, it belongs in a coordinator

**Coordinator constructor dependencies** (validates DI wiring):
- `LayoutCoordinator(layoutRepository: LayoutRepository, presetLoader: PresetLoader, gridPlacementEngine: GridPlacementEngine)`
- `EditModeCoordinator(layoutCoordinator: LayoutCoordinator)` — needs layout state for drag/resize validation
- `ThemeCoordinator(themeAutoSwitchEngine: ThemeAutoSwitchEngine, themeProviders: Set<ThemeProvider>)`
- `WidgetBindingCoordinator(dataProviderRegistry: DataProviderRegistry, widgetRegistry: WidgetRegistry, interceptors: Set<DataProviderInterceptor>, metricsCollector: MetricsCollector, @IoDispatcher ioDispatcher: CoroutineDispatcher, @DefaultDispatcher defaultDispatcher: CoroutineDispatcher)`
- `NotificationCoordinator()` — derives banners from injected singleton state holders (safe mode, BLE adapter, storage)
- `ProfileCoordinator(layoutRepository: LayoutRepository)` — profile create/clone/switch/delete all mutate layout persistence

## Replication Advisory References

Before implementing Phase 7, consult the following sections of [replication-advisory.md](replication-advisory.md). This phase has the densest advisory coverage — 5 of 7 sections apply:

- **§1 Widget Preview Mode** — `editingWidgetId` derived from NavHost back-stack scan (NOT current entry). Per-widget animation specs: translation via `spring(StiffnessLow, NoBouncy)`, alpha `tween(300ms)` for non-focused widgets, z-index `1000f`. Critical: `focusScale` only applied when widget exceeds viewport peek area — small widgets NOT scaled up. Viewport fraction: portrait `0.38f`, landscape `0.28f`. Gesture locking: `showEditModeUI` gated on `editingWidgetId == null`. Dismissal: `navigate(Route.Empty) { popUpTo }` not `popBackStack()`. Dead code: `savedFocusedWidgetId` — don't replicate.
- **§2 Jankless Navigation** — Dashboard-as-shell (Layer 0 always present, never a NavHost destination). Three coordinated mechanisms: back-stack scan, `ExitTransition.None`/`EnterTransition.None` on WidgetSettings, dashboard suspension via `remember { mutableStateOf(viewModel.state.value) }` snapshot for opaque routes. `editingWidgetId` passed through even in suspended mode.
- **§3 Theme & Studio Preview** — `displayTheme = previewTheme ?: currentTheme` pattern in coordinator state. `LocalDashboardTheme` as `staticCompositionLocalOf` (full-tree invalidation intentional). Caller-managed preview: `SetPreviewTheme` fired BEFORE navigation to prevent flash. Phase 7 delivers the `ThemeCoordinator` state; preview lifecycle and race condition fix (dual `DisposableEffect` + `LaunchedEffect(Unit)`) implemented in Phase 11 UI.
- **§4 Source-Varying Transitions** — Widget-level animations: drag lift `1.03f` via `spring(MediumBouncy, StiffnessMedium)`, focus translation `spring(StiffnessLow, NoBouncy)`, other-widget fade `tween(300ms)`, edit wiggle `infiniteRepeatable(tween(150ms), Reverse) ±0.5°`, bracket pulse `infiniteRepeatable(tween(800ms, FastOutSlowIn), Reverse) 3→6dp`, edit controls `fadeIn+scaleIn(0.8f)/fadeOut+scaleOut(0.8f)`, button tap bounce `spring(MediumBouncy, StiffnessMedium) 1.0→0.85`, edit/add button swap `scaleIn(spring 0.65/300) + fadeIn(200ms)`. `DashboardMotion` shared transitions (`sheetEnter/Exit`, `hubEnter/Exit`, `dialogEnter/Exit`, `previewEnter/Exit`, `expandEnter/Exit`) move to `:core:design`.
- **§6 Drag/Snap/Resize/Gestures** — `GRID_UNIT_SIZE = 16.dp`. Manual `awaitEachGesture` + `awaitPointerEvent` — no `detectDragGesturesAfterLongPress`. Long-press: `400ms` with `8px` cancellation threshold for scroll discrimination. `PointerEventPass.Initial` in edit mode for interception. Snap: `roundToInt(pixelPos / unitPx)` midpoint snap, based on `dragStartGridX/Y + dragOffset` NOT live widget position. No collision detection during drag. Bounds: hard clamp. Resize: 4 corner handles `32.dp`, immediate start (no long-press), `MIN_WIDGET_UNITS = 2`, position compensation for non-BottomRight handles, `previewSizes` via `SideEffect`. Gesture filtering: 6 conflict resolutions documented. Haptic map: 8 events. Missing from old: no snap-back animation on release, no snap-back on cancel, landscape viewport mismatch — implement these improvements.

**Ported from old:** Grid layout geometry, viewport filtering, gesture handling, drag offset animation — these port with moderate adaptation. The ViewModel, state management, and data binding are rewritten from scratch against the new coordinator pattern.

**Resize preview decision:** Content-aware relayout, not visual-only scaling. Port `LocalWidgetPreviewUnits` CompositionLocal — 8 widgets in old codebase read this to switch layout modes (e.g., `InfoCardLayout` STACK/COMPACT/GRID) during resize gesture. `graphicsLayer` handles drag translation only; resize feeds target dimensions to widget via CompositionLocal so the widget re-layouts at target size. This is critical for the Speedometer (arc angle changes with aspect ratio) and InfoCard (layout mode switches at size thresholds).

**Port inventory (Phase 7 — structural shell):**

| Old artifact | Target | Notes |
|---|---|---|
| `DashboardMotion` — 3 spring profiles (`standard` 0.65/300, `hub` 0.5/300, `preview` 0.75/380) + all enter/exit transitions | `:core:design` (shared transitions) + `:feature:dashboard` (grid-local: wiggle, lift, handle pulse, focus translate) | Port; springs are tuned — do not re-guess values. Shared enter/exit specs (`sheetEnter/Exit`, `hubEnter/Exit`, `dialogEnter/Exit`, `expandEnter`) move to `:core:design` since `ConfirmationDialog` and overlays in `:feature:settings` need them. Grid-local animations (wiggle `infiniteRepeatable(tween(150))`, lift `spring(DampingRatioMediumBouncy)`, handle pulse `infiniteRepeatable(tween(800))`) stay in `:feature:dashboard` |
| `DashboardHaptics` — 6 semantic methods with API 30+ branching (`editModeEnter`, `editModeExit`, `dragStart`, `snapToGrid`, `boundaryHit`, `resizeStart`) | `:feature:dashboard` | Port; `REJECT`/`CONFIRM` constants API 30+ with fallbacks |
| `GridPlacementEngine` — auto-placement algorithm for new widgets | `:feature:dashboard` (inside or alongside `LayoutCoordinator`) | Port; has existing `GridPlacementEngineTest` — port tests: empty grid placement, first-fit algorithm, overlap detection, no valid position returns error, boundary constraints respected. Add new: no-straddle snap (widget cannot straddle configuration boundary) |
| `WidgetContainer` — glow rendering with `drawWithCache`, responsive sizing (3 tiers), border overlay, rim padding | `:sdk:ui` as `WidgetContainer` | Redesign required — old uses `BlurMaskFilter` (forbidden); new uses `RenderEffect.createBlurEffect()` API 31+. Phase 3 builds the skeleton; Phase 7 adds glow rendering with `RenderEffect` rewrite. Responsive sizing logic worth porting |
| `ConfirmationDialog` — reusable modal with scrim + animation | `:core:design` | **Delivered in Phase 7.** Port verbatim — reused by overlays in Phase 10. **Depends on `DashboardMotion.dialogEnter/dialogExit`** — co-locate shared animation specs in `:core:design` alongside this dialog |
| `OverlayScaffold` — scaffold wrapping overlay content with title bar | `:feature:dashboard` | Port + adapt. Scaffolded empty in Phase 7; overlay routes populated in Phase 10 |
| `UnknownWidgetPlaceholder` — fallback UI for deregistered/missing widget types | `:sdk:ui` | Port; required by F2.13 (Must). Must be in `:sdk:ui` so packs could theoretically reference it |
| `DashboardCustomLayout` — `Layout` composable with `layoutId`-based matching, grid-unit coordinate system | `:feature:dashboard` | Port + adapt for new coordinator-owned state |
| `BlankSpaceGestureHandler` — tap-on-blank to enter edit mode + long-press gesture detection | `:feature:dashboard` | Port; wires to `EditModeCoordinator.enterEditMode()`. Distinct from widget gesture handling |
| `WidgetGestureHandler` — tap/long-press/drag delegates per widget with hit testing | `:feature:dashboard` | Port + adapt; old implementation is tightly coupled to ViewModel — extract gesture detection, delegate actions to coordinators |
| `WidgetContentDispatcher` — routes widget tap actions (Shortcuts widget `CallActionProvider`, deep links) | `:feature:dashboard` | Port; decoupled from gesture detection — receives resolved action from `WidgetGestureHandler` |
| `LocalWidgetPreviewUnits` — CompositionLocal providing target dimensions during resize gesture | `:sdk:ui` | Port; 8 widgets read this for resize-aware relayout (see resize preview decision above) |
| `ProfilePageTransition` — horizontal swipe between profile canvases with shared-element-like dot indicator | `:feature:dashboard` | New — no direct old equivalent (old had tabs). Implement as `HorizontalPager` + `ProfileCoordinator.switchProfile()`. Bottom bar profile dots act as both indicator and tap target |

**Port inventory (deferred to Phase 10 — overlay composables):**

| Old artifact | Target | Notes |
|---|---|---|
| `InlineColorPicker` — HSL sliders + hex editor (412 lines, `WindowInsets.ime` keyboard handling) | `:feature:settings` | Port; non-trivial — do not rebuild from scratch or pull in third-party lib. Extract `colorToHsl`/`colorToHex`/`parseHexToColor` to a testable utility |
| `GradientTypeSelector` + `GradientStopRow` + `ThemeSwatchRow` (5-slot swatch selector with `parseHexColor`/`gradientSpecToBrush` utils) | `:feature:settings` | Port + adapt |
| `SetupSheetContent` + setup UI system — `SetupDefinitionRenderer`, `SetupNavigationBar`, `SetupPermissionCard`, `SetupToggleCard`, `DeviceScanCard`, `PairedDeviceCard`, `DeviceLimitCounter`, `InstructionCard` | `:feature:settings` | Port; required by F3.3/F3.4/F3.5/F3.14 (all Must). sg-erp2 pack (Phase 9) depends on this UI for BLE device pairing — Phase 9 must follow Phase 10 or pairing UI must be stubbed |
| `AutoSwitchModeContent` + `IlluminanceThresholdControl` — theme auto-switch mode selector + logarithmic Canvas-drawn lux meter (~280 lines) with drag-to-set | `:feature:settings` | Port; non-trivial custom Canvas drawing in lux meter — do not rebuild from scratch. Domain logic (`ThemeAutoSwitchEngine`) covered in Phase 5 |
| `SettingRowDispatcher` + 10 row types (`Boolean`, `Enum`, `Int`, `Float`, `String`, `Info`, `Instruction`, `AppPicker`, `DateFormat`, `Timezone`, `Sound`) + `SettingComponents.kt` building blocks | `:feature:settings` | Port; `FeatureSettingsContent` renders full settings schema with collapsible groups |

**Tests:**
- Coordinator unit tests for each of the six coordinators — each coordinator tested in isolation with fakes for its dependencies
- `DashboardViewModel` routing test: dispatch each `DashboardCommand` subtype, verify it reaches the correct coordinator
- Safety-critical tests: ≥4 crashes in 60s → safe mode trigger (cross-widget: 4 different widgets each crashing once), entitlement revocation → auto-revert
- `NotificationCoordinator` re-derivation: kill ViewModel, recreate coordinator, assert all condition-based banners (safe mode, BLE adapter off, storage pressure) re-derive from current singleton state — no lost banners after process death
- `ProfileCoordinator`: create → clone → switch → delete lifecycle, verify per-profile canvas independence (widget added to profile A not visible in profile B)
- `ProfilePageTransition`: horizontal swipe triggers `ProfileCoordinator.switchProfile()`, dot indicator count matches profile count, current dot highlighted matches active profile
- `DashboardTestHarness` DSL tests with `HarnessStateOnFailure` watcher — default path uses real coordinators, proving coordinator-to-coordinator interactions (e.g., `AddWidget` → `WidgetBindingCoordinator` creates job → `WidgetBindingCoordinator` reports ACTIVE)
- Grid layout tests (compose-ui-test + Robolectric): widget placement, overlap rejection, viewport filtering, no-straddle snap at configuration boundaries
- Drag/resize interaction tests: `graphicsLayer` offset animation for drag, `LocalWidgetPreviewUnits` content-aware relayout for resize, snap-to-grid on release
- `WidgetDataBinder`: `SupervisorJob` isolation (one provider crash doesn't cancel siblings), `CoroutineExceptionHandler` routes to `widgetStatus`
- Thermal throttle wiring: inject `FakeThermalManager`, escalate to DEGRADED, verify emission rate drops in `WidgetDataBinder`
- `ProviderFault`-based fault injection via `TestDataProvider`: `Delay`, `Error`, `Stall` → verify widget shows fallback UI, not crash
- `BlankSpaceGestureHandler`: tap-on-blank → edit mode, long-press → edit mode, tap-on-widget in non-edit → no action
- `OverlayNavHost` empty scaffold: composition test verifies empty route table renders without crash and produces a semantics node
- Agentic mutation handler unit tests: each mutation handler (`add-widget`, `remove-widget`, `move-widget`, `resize-widget`, `set-theme`, `set-data-source`, `set-setting`, `reset-layout`, `import-preset`) tested for: valid params → correct `DashboardCommand` dispatched, invalid params → error envelope with descriptive message, missing required field → error (not crash)
- On-device validation: deploy, `dump-layout` confirms grid state, `dump-health` confirms coordinator health (empty widget set), `get-metrics` confirms frame timing, `dump-semantics` confirms `dashboard_grid` test tag in semantics tree. Widget-specific semantics verification (`query-semantics {"testTagPattern":"widget_.*"}`) deferred to Phase 8 when widgets exist

**Structural delivery vs functional validation:** Requirements that span coordinators + overlays (e.g., F1.2 widget creation = coordinator binding + widget picker overlay) are structurally delivered in Phase 7 (coordinator logic) and functionally validated end-to-end in Phase 10 (when overlays exist). Phase 7 tests validate coordinator behavior via `DashboardTestHarness` and agentic commands, not through overlay UI.
