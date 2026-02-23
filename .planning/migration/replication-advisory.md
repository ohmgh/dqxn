# Old Codebase Replication Advisory

Hard-to-get-right logic from the old codebase that must be thoroughly mapped and replicated with full nuance during implementation. Each section identifies the exact behaviors, state machines, animation specs, and edge cases that are easy to get wrong if implemented from scratch without studying the prior art.

**When to consult:** Before implementing any phase that touches these areas. Read the relevant section, then read the actual old source files referenced. The advisory is an index of subtleties — the source is the ground truth.

---

## 1. Widget Preview Mode (Settings Peek)

**Old source:** `DashboardGrid.kt`, `WidgetGestureHandler.kt`, `OverlayNavHost.kt`, `DashboardShell.kt`

### State Machine

There is no `PreviewState` class or `isPreview` flag. "Preview mode" is an emergent behavior from `editingWidgetId: String?` derived by scanning the **entire NavHost back stack** for a `Route.WidgetSettings` entry — not just the current destination:

```
editingWidgetId = navController.currentBackStack.value
    .lastOrNull { it.destination.hasRoute<Route.WidgetSettings>() }
    ?.toRoute<Route.WidgetSettings>()?.widgetId
```

This is load-bearing: child routes of WidgetSettings (ProviderSetup, TimezoneSelector, PermissionRequest) preserve the peek because WidgetSettings is still in the back stack. If you only check `currentEntry`, navigating to any child collapses the preview.

### Per-Widget Animation Specs

| Property | Target (editing) | Target (other widgets) | Spec |
|---|---|---|---|
| Translation X | `(containerW - widgetW) / 2 - (gridX * unitPx)` | 0 | `spring(StiffnessLow, DampingRatioNoBouncy)` — critically damped, no overshoot |
| Translation Y | `viewportCenterY - originalCenterY` | 0 | Same spring |
| Scale | `min(maxW/widgetW, maxH/widgetH)` if widget exceeds viewport, else `1.0f` | 1.0 | Same spring |
| Alpha | `1.0` (snap — no fade on focused widget) | `0.0` via `tween(300ms)` | See note |
| Z-index | `1000f` | natural | Immediate |

**Critical nuance — focusScale:** Only applied when widget is too large for the viewport peek area. Small widgets are NOT scaled up for emphasis. Applying scale unconditionally causes small widgets to look awkward.

**Viewport fraction:** Portrait `0.38f`, landscape `0.28f`. Must coordinate with sheet's `previewFraction` (sheet occupies `1 - viewportFraction`). In old code, sheet always uses `0.38f` even in landscape — this is a latent mismatch bug. Fix in new arch.

### Gesture Locking

All edit mode UI (wiggle, handles, brackets, action buttons) is hidden when `editingWidgetId != null` via:
```kotlin
showEditModeUI = derivedStateOf { (isEditMode || forceShowEditMode) && editingWidgetId == null }
```

The transparent area above the sheet is a tap-to-dismiss target. The sheet itself consumes all touches.

### Dismissal Pattern

Uses `navigate(Route.Empty) { popUpTo<WidgetSettings> { inclusive = true } }` — NOT `popBackStack()`. This ensures widget return-to-position animation and sheet slide-down start in the same transition frame. Plain `popBackStack()` causes timing mismatch.

### Focus Save/Restore

Dead code in old arch — `savedFocusedWidgetId` path is permanently disabled (both triggers hardcoded to `false`). Focus simply disappears when settings open and returns via `editingWidgetId == null` gate. Don't replicate the dead code.

---

## 2. Jankless Navigation from Widget Preview

**Old source:** `DashboardShell.kt`, `OverlayNavHost.kt`

### Architecture: Dashboard-as-Shell

The dashboard is NEVER a NavHost destination. It composes unconditionally at Layer 0. `OverlayNavHost` sits at Layer 1 with `Route.Empty` as start destination. This eliminates "navigate away = lose state" entirely.

### Why Preview Survives Sub-Navigation

Three coordinated mechanisms:

1. **Back-stack scan** (not current-entry check) keeps `editingWidgetId` non-null across child routes.

2. **`ExitTransition.None` / `EnterTransition.None` on WidgetSettings:**
   ```kotlin
   composable<Route.WidgetSettings>(
       exitTransition = { ExitTransition.None },
       popEnterTransition = { EnterTransition.None }
   )
   ```
   WidgetSettings stays in composition when hub overlays (PackBrowser, ProviderSetup) push on top. No sheet dismiss/re-enter animation. The hub slides over; on pop, it reveals the still-present sheet.

3. **Dashboard suspension** on opaque routes (Settings, PackBrowser, ThemeModeSelector, ProviderSetup): dashboard freezes recomposition via `remember { mutableStateOf(viewModel.state.value) }` snapshot. Prevents wasted recomposition when dashboard isn't visible. But `editingWidgetId` is still passed through — peek animation persists even in suspended mode.

### Source-Varying Transitions on Settings

Settings' exit/popEnter transitions inspect target/initial destination:
- To preview overlays (ThemeSelector, WidgetSettings, etc.): `fadeOut(100ms)` / `previewEnter`
- To hub overlays (PackBrowser): `ExitTransition.None` / `EnterTransition.None`

The None case prevents double-animation (Settings sliding away while hub is also appearing).

### PackBrowser Source-Varying Transitions

| From | Enter | Pop Exit |
|---|---|---|
| Settings | `slideInHorizontally(spring 0.65/300) + fadeIn(200ms)` | `slideOutHorizontally(200ms) + fadeOut(200ms)` |
| WidgetSettings | `fadeIn(300ms)` — shared element drives | `fadeOut(300ms)` — shared element drives |
| Default | `fadeIn(200ms)` | `fadeOut(200ms)` |

The 300ms duration for WidgetSettings path is deliberately slower — the shared element card morph is the primary visual; the route fade should not compete.

### Shared Element: Pack Card Morph

`SharedTransitionLayout` wraps the OverlayNavHost. Two CompositionLocals propagate scope:
- Key: `"pack-card-${pack.packId}"`
- Source: `WidgetInfoContent` in WidgetSettings info tab — applies `sharedElement()` + `skipToLookaheadSize()` (prevents LazyColumn reflow)
- Destination: `PackCard` in PackBrowser — applies `sharedElement()` without `skipToLookaheadSize()`
- Guard: both sides null-check locals for graceful degradation outside NavHost

### Dismissal Stack Management

Simple destinations: `popBackStack()`. Root-level destinations (Settings, WidgetSettings): `navigate(Empty) { popUpTo<Route> { inclusive = true } }` — pops entire sub-stack in one call.

---

## 3. Theme & Studio Preview Mode

**Old source:** `DashboardState.kt`, `ThemeSelectorContent.kt`, `ThemeStudioContent.kt`, `OverlayNavHost.kt`, `ThemeAutoSwitchEngine.kt`

### The displayTheme Pattern

```kotlin
data class DashboardState(...) {
    val previewTheme: DashboardThemeDefinition? = null
    val displayTheme get() = previewTheme ?: currentTheme
}
```

`CompositionLocalProvider(LocalDashboardTheme provides state.displayTheme)` at the root. `LocalDashboardTheme` is `staticCompositionLocalOf` — any change invalidates the entire widget tree (intentional: theme changes ARE full-tree recompositions).

### Caller-Managed Preview Pattern (Critical)

`SetPreviewTheme` is ALWAYS fired by the caller **before** navigation, never by the destination on entry:
```kotlin
onNavigateToDarkThemes = {
    state.darkTheme?.let { dashboardViewModel.onEvent(SetPreviewTheme(it)) }
    navController.navigate(Route.ThemeSelector(isDark = true))
}
```

This prevents a flash of the non-previewed theme during the navigation transition. The dashboard renders the preview theme before the overlay animation starts. Same pattern on ThemeEditor dismiss — `SetPreviewTheme(savedTheme)` before `popBackStack()`.

### Race Condition Fix (Critical)

Two independent cleanup mechanisms for `previewTheme`:

1. `DisposableEffect(Unit) { onDispose { ClearPreviewTheme } }` in ThemeSelector — fires when ThemeSelector leaves composition
2. `LaunchedEffect(Unit) { ClearPreviewTheme }` in Settings — fires when Settings first enters composition

Both are required. `LaunchedEffect(Unit)` only runs once (NavHost keeps Settings in composition during child pushes — it doesn't re-enter on pop). Quick dismissal from ThemeSelector before animation completes can leave `previewTheme` set if only the Settings effect exists. The `DisposableEffect` in ThemeSelector covers this race.

### Studio Auto-Save + Live Preview

Every color slider drag in ThemeEditor auto-saves and simultaneously updates preview:
```
LaunchedEffect(all color state vars) {
    if (isDirty) {
        onAutoSave(buildCustomTheme(...))  // → SetPreviewTheme + SaveCustomTheme(json)
    }
}
```

The dashboard behind the overlay live-updates continuously. `lastSavedTheme` tracked locally (not in ViewModel) for dismiss handoff.

**Theme ID stability:** `stableThemeId = remember { existingId ?: "custom_${currentTimeMillis()}" }` — single ID generated once, all auto-saves overwrite same file.

**Undo edge case:** Undo restores editor state but NOT the file on disk. `isDirty = false` prevents next auto-save. On next open, file still has the last pre-undo version. Acceptable but worth being explicit about.

### Delete Theme While Previewing

If user deletes the currently-previewed theme, `handleDeleteTheme` reverts preview to the active dark/light default before dispatching the delete event.

### Auto-Switch Engine

Five modes: LIGHT, DARK, SYSTEM (Android uiMode), SOLAR_AUTO (solar position), ILLUMINANCE_AUTO (ambient sensor). Produces `activeTheme: StateFlow` from `combine(isDarkActive, lightTheme, darkTheme)`. Old code has a pack isolation violation (hard dep on free pack sensor providers) — fix in new arch.

### Overlay Chrome Theming

ThemeSelector: `previewedSheetTheme` on `OverlayScaffold` — selector header adopts the previewed theme's colors.
ThemeEditor: `state.displayTheme` on `OverlayScaffold` — editor chrome matches the live dashboard.

---

## 4. Source-Varying Transition Animations

**Old source:** `DashboardAnimations.kt` (`DashboardMotion` object), `OverlayNavHost.kt`

### Spring Configs

| Name | Damping | Stiffness | Character |
|---|---|---|---|
| `standardSpring` | 0.65 | 300 | Balanced — sheets, dialogs, expand |
| `hubSpring` | 0.50 | 300 | Bouncier — fullscreen hubs (premium feel) |
| `previewSpring` | 0.75 | 380 | Snappier — preview overlays |

### Named Transitions

| Name | Enter | Exit |
|---|---|---|
| `previewEnter/Exit` | `slideInVertically(previewSpring) { fullH } + fadeIn(200ms)` | `slideOutVertically(200ms) { fullH } + fadeOut(150ms)` |
| `hubEnter/Exit` | `fadeIn(hubSpring) + scaleIn(0.85f, hubSpring)` | `fadeOut(150ms) + scaleOut(0.95f, 150ms)` |
| `sheetEnter/Exit` | `slideInVertically(standardSpring) { fullH } + fadeIn(200ms)` | `slideOutVertically(200ms) { fullH } + fadeOut(150ms)` |
| `expandEnter/Exit` | `expandVertically(spring 0.65/300) + fadeIn(200ms)` | `shrinkVertically(200ms) + fadeOut(150ms)` |
| `dialogScrimEnter/Exit` | `fadeIn(200ms)` | `fadeOut(150ms)` |
| `dialogEnter/Exit` | `scaleIn(0.85f, standardSpring) + fadeIn(200ms)` | `scaleOut(0.9f, 150ms) + fadeOut(150ms)` |

**Duration asymmetry pattern:** Enter is typically 200ms, exit 150ms. This is consistent.

### Source-Varying Logic

Uses `NavBackStackEntry.destination.hasRoute<RouteClass>()` on `initialState` (for enter/popExit) or `targetState` (for exit/popEnter) within transition lambdas. Full source-varying routes:

- **Settings exit:** fadeOut(100ms) to preview overlays; ExitTransition.None to hubs
- **Settings popEnter:** previewEnter from preview overlays; EnterTransition.None from hubs
- **PackBrowser enter:** horizontal slide from Settings; slow fade from WidgetSettings; default fade otherwise
- **PackBrowser popExit:** horizontal slide to Settings; slow fade to WidgetSettings; default fade otherwise
- **WidgetSettings:** ExitTransition.None / EnterTransition.None (stays visible under hubs)
- **ThemeModeSelector/ThemeSelector/ThemeEditor popEnter:** `fadeIn(150ms)` — NOT previewEnter (avoids double-slide)

### Widget-Level Animations (Non-Route)

| Animation | Spec | Trigger |
|---|---|---|
| Drag lift scale | `spring(MediumBouncy, StiffnessMedium)` 1.0→1.03 | isDragging |
| Focus translation | `spring(StiffnessLow, NoBouncy)` | editingWidgetId match |
| Other widgets fade | `tween(300ms)` to alpha 0; `snap()` on focused | editingWidgetId set |
| Edit wiggle | `infiniteRepeatable(tween(150ms), Reverse)` ±0.5° | isEditMode && !drag/resize/settings |
| Bracket pulse | `infiniteRepeatable(tween(800ms, FastOutSlowIn), Reverse)` 3→6dp | isFocused |
| Edit controls | `fadeIn + scaleIn(0.8f)` / `fadeOut + scaleOut(0.8f)` | showEditModeUI && isFocused |
| Button tap bounce | `spring(MediumBouncy, StiffnessMedium)` 1.0→0.85 | press state |
| Button bar | `sheetEnter/sheetExit` | visibility |
| Edit/Add button swap | `scaleIn(spring 0.65/300) + fadeIn(200ms)` | AnimatedContent |

---

## 5. UI Design System Consistency

**Old source:** `DashboardThemeExtensions.kt`, `DashboardTheme.kt`, `WidgetContainer.kt`, `InfoCardLayout.kt`, `OverlayScaffold.kt`, `SettingComponents.kt`, `DashboardButtonBar.kt`

### Typography Scale

Shell UI uses `object DashboardTypography` — aliases on Material 3 defaults with minor overrides. No custom fonts.

| Role | M3 Base | Override |
|---|---|---|
| `title` | titleMedium | 17.sp |
| `sectionHeader` | labelMedium | 13.sp |
| `itemTitle` | titleMedium | none |
| `label` | labelLarge | none |
| `description` | bodyMedium | none |
| `buttonLabel` | labelMedium | 13.sp, Bold |
| `primaryButtonLabel` | labelLarge | 15.sp, Bold |
| `caption` | labelSmall | none |

Widget numerics: `FontFamily.Monospace` for fixed-width digit rendering. System sans-serif for everything else. No loaded custom typefaces.

### Color Token Model

6 named tokens per theme — no additional semantic tokens:

| Token | Role |
|---|---|
| `primaryTextColor` | Main text, primary emphasis |
| `secondaryTextColor` | Subtitles, secondary icons (typically primary at 50-70% alpha) |
| `accentColor` | Interactive emphasis, glow, FAB, selected states |
| `highlightColor` | Second vibrant accent (defaults to accent if unset) |
| `widgetBorderColor` | Widget border when enabled |
| `backgroundBrush` / `widgetBackgroundBrush` | Canvas and widget fill gradients |

**Gap to fix in new arch:** No error/warning/success semantic color tokens. Old code uses hardcoded colors for these states.

### Spacing System (4dp Grid)

| Token | Value | Semantic Aliases |
|---|---|---|
| `SpaceXXS` | 4dp | — |
| `SpaceXS` | 8dp | InGroupGap, ButtonGap, IconTextGap, LabelInputGap |
| `SpaceS` | 12dp | ItemGap |
| `SpaceM` | 16dp | ScreenEdgePadding, SectionGap, CardInternalPadding, NestedIndent |
| `SpaceL` | 24dp | — |
| `SpaceXL` | 32dp | — |
| `SpaceXXL` | 48dp | MinTouchTarget |

### Card Radius Tokens

| Category | Radius | Usage |
|---|---|---|
| SMALL | 8dp | Chips, picker cells, thumbnails, dense grids |
| MEDIUM | 12dp | Standard content cards (workhorse) |
| LARGE | 16dp | Hero cards, overlay top corners |

Widget corner radius: user-controlled percent-based system (separate from shell radii).

### Emphasis Levels

| Level | Alpha | Usage |
|---|---|---|
| High | 1.0 | Primary text |
| Medium | 0.7 | Secondary text, descriptions |
| Disabled | 0.4 | Inactive text |
| Pressed | 0.12 | Press feedback overlay |

Scrim progression: 0.15 (auto-resolving), 0.30 (needs attention), 0.60 (blocking/action required).

### Icons

Material Icons exclusively (Filled, Rounded, Default mixed without strict rule). No custom icon set. FAB: 24dp icon in 56dp circle.

### Known Inconsistencies to Fix

1. Radius fragmentation: `CardSize` enum exists but inline `8.dp` hardcoded at some callsites
2. `OverlayScaffold` uses `SpaceM` (16dp) for corner radius — conflates spacing with shape
3. `PermissionRequestPage` uses raw M3 tokens instead of `DashboardTypography`
4. `OverlayTitleBar` uses inline `alpha = 0.6f` instead of `TextEmphasis.Medium` (0.7)
5. No error/warning/success color tokens — improvised per-site

---

## 6. Dashboard Widget Drag, Snap, Resize, and Gesture Filtering

**Old source:** `WidgetGestureHandler.kt`, `DashboardGrid.kt`, `DashboardGridLayout.kt`, `BlankSpaceGestureHandler.kt`, `GridPlacementEngine.kt`

### Grid Coordinate System

- `GRID_UNIT_SIZE = 16.dp` (48px at mdpi). Positions and sizes stored as integer cell counts.
- Origin: top-left `(0, 0)`. No negative coordinates. No pan/scroll.
- Pixel position: `x = gridX * unitSizePx`, `y = gridY * unitSizePx`.
- Canvas bounds from container constraints: `maxGridColumns = maxWidth / unitPx`.

### Drag Initiation — Long Press + Threshold

All manual `awaitEachGesture` + `awaitPointerEvent` — no `detectDragGesturesAfterLongPress`.

1. `awaitFirstDown` — `PointerEventPass.Initial` in edit mode (intercepts before widget clicks), `.Main` otherwise
2. `down.consume()` immediately — prevents blank-space handler from receiving
3. Long-press detection: `LONG_PRESS_TIMEOUT_MS = 400ms`
4. Cancellation: `DRAG_THRESHOLD_PX = 8f` — movement exceeding 8px before 400ms cancels (scroll discrimination)
5. On long-press: haptic `LONG_PRESS`, set `longPressTriggered = true`
6. Drag starts only after long-press AND pointer moves
7. `wasInEditModeAtStart` captured at gesture start for consistent pass selection

### Snap Preview — No Ghost Cell

The widget itself snaps to grid cells in real time. No ghost/placeholder overlay.

```kotlin
snappedDragOffsetX = (dropzoneGridPosition.x - gridX) * unitPx
```

The offset jumps between cell boundaries as the finger crosses midpoints. `roundToInt()` on `(pixelPos / unitPx)` produces the midpoint snap.

**Drag lift:** `1.03f` scale via `spring(MediumBouncy, StiffnessMedium)`. Z-index set to `Float.MAX_VALUE`.

### Snap Calculation

```
targetCell.x = round(absolutePixelX / unitPx).coerceIn(0, maxColumns - widgetWidth)
targetCell.y = round(absolutePixelY / unitPx).coerceIn(0, maxRows - widgetHeight)
```

Based on `dragStartGridX/Y + dragOffset` (NOT live `widget.gridX/gridY`). No collision detection during drag — widgets can overlap freely.

### Bounds Handling — Hard Clamp

Raw pixel offset clamped to prevent visual exit from viewport:
```
minOffsetX = -currentPosX
maxOffsetX = containerWidth - widgetWidth - currentPosX
```

No scrolling or canvas expansion at boundary. Dragging to edge just stops.

### Resize Mechanics

**Handles:** 4 corners, `HANDLE_SIZE = 32.dp` invisible touch targets. Resize starts immediately on touch (no long-press), haptic `GESTURE_START`.

**Size calculation per handle:**
- Left handles invert X delta, top handles invert Y delta
- Max size bounded by available grid space in handle direction
- `MIN_WIDGET_UNITS = 2` (32dp minimum)
- Aspect ratio preservation: dominant dimension drives, non-dominant calculated proportionally

**Position compensation:** For non-BottomRight handles, widget origin shifts to keep the opposite corner fixed:
- TopLeft: `gridX -= deltaWidth, gridY -= deltaHeight`
- TopRight: `gridY -= deltaHeight`
- BottomLeft: `gridX -= deltaWidth`

Visual compensation applied via `Modifier.offset` during resize (layout pass, not graphicsLayer — intentional since resize changes layout size).

**Live preview:** `previewSizes: mutableStateMapOf` updated via `SideEffect`. `DashboardCustomLayout` measures resizing widget at preview dimensions. Content re-renders at each grid-unit boundary.

### Gesture Filtering

| Conflict | Resolution |
|---|---|
| Widget vs blank space | Widget `down.consume()` on `awaitFirstDown`; blank space uses `requireUnconsumed = true` |
| Drag vs scroll | 8px threshold before 400ms long-press → cancel as scroll |
| Widget click vs edit intercept | `PointerEventPass.Initial` in edit mode intercepts before widget's `Clickable` |
| Resize vs widget tap | `RESIZE_TAP_DEBOUNCE_MS = 200ms` after resize end prevents spurious taps |
| Resize vs widget drag | Resize handles are child composables — pointer hit testing naturally resolves to top composable. Widget modifier also skips long-press check when `isResizing` |
| Focus during drag | `focusedWidgetId = null` on DragStart; restores on DragEnd |
| Edit mode UI during drag | `forceShowEditModeUI = true` on long-press (before async ViewModel update) prevents 1-frame gap |

### Haptic Feedback Map

| Event | Constant |
|---|---|
| Long press | `LONG_PRESS` |
| Resize grab | `GESTURE_START` |
| Drag end / snap | `GESTURE_END` |
| Edit mode enter | `LONG_PRESS` |
| Edit mode exit | `CONFIRM` |
| Focus change | `GESTURE_END` |
| Boundary hit | `REJECT` |

### No Collision Detection During Drag

Overlap is allowed. Z-ordering via `zIndex`. `BringWidgetToFront` on touch sets `zIndex = maxZIndex + 1`. Collision avoidance only exists for initial widget placement (`GridPlacementEngine.calculateOverlapScore`).

### Missing From Old Code (Implement in New Arch)

1. **No snap-back animation on release** — offset clears to 0f synchronously. Add spring-based graphicsLayer offset for animated snap-to-final-cell.
2. **No snap-back on drag cancel** — widget teleports back. Add spring return.
3. **Landscape viewport fraction mismatch** — sheet uses 0.38f, widget targets 0.28f. Coordinate both values.
4. **`Modifier.offset` for drag** — old code uses layout-pass offset. New arch mandates `graphicsLayer` offset per CLAUDE.md.

---

## 7. Widget Setup Architecture and UI

**Old source:** `SetupDefinition.kt`, `SettingDefinition.kt`, `SetupSheetContent.kt`, `SetupDefinitionRenderer.kt`, `SettingRowDispatcher.kt`, `SettingsSheetDispatcher.kt`, `WidgetSettingsPager.kt`, `PermissionCard.kt`, `DeviceScanCard.kt`, `OverlayNavHost.kt`, `ProviderSettingsStore.kt`, `PairedDeviceStore.kt`, `SetupEvaluator.kt`

### Setup Definition Schema (7 Types in 3 Categories)

**Requirement types** (have pass/fail semantics, can block forward navigation):

| Type | Key Fields | UI |
|---|---|---|
| `RuntimePermission` | `permissions: List<String>`, `minSdk: Int` | Permission card with Grant/Open Settings |
| `SystemServiceToggle` | `settingsAction: String`, `checkEnabled: (Context) -> Boolean` | Toggle card with system settings link |
| `SystemService` | `serviceType: ServiceType` (BLUETOOTH, LOCATION, WIFI) | Service enable card |
| `DeviceScan` | `handlerId`, `deviceNamePattern`, `serviceUuids`, `maxDevices`, `onPaired`, `onCancel` | BLE scan card with CDM |

**Display types** (always satisfied):

| Type | Key Fields | UI |
|---|---|---|
| `Instruction` | `stepNumber`, `action: InstructionAction`, `verificationStrategy`, `verificationOptional`, `alternativeResolution` | Step card with action button + verification |
| `Info` | `icon`, `style: InfoStyle` (INFO/WARNING/SUCCESS/ERROR) | Styled info card |

**Input wrapper** (always satisfied, delegates to `SettingDefinition`):

| Type | Key Fields | UI |
|---|---|---|
| `Setting` | `definition: SettingDefinition<*>` | Dispatched to `SettingRowDispatcher` |

The `Setting` wrapper exists so packs can intermix input rows with requirement cards on the same setup page. It delegates `id`, `label`, `visibleWhen`, `requiredAnyEntitlement`, `hidden`, and `default` to the wrapped definition. `asSetup()` extension converts `SettingDefinition` → `SetupDefinition.Setting`.

### Setting Definition Schema (12 Types)

Every type carries: `key`, `label`, `description?`, `default: T`, `visibleWhen?`, `groupId?`, `hidden`, `requiredAnyEntitlement?`.

| Type | T | UI | Notes |
|---|---|---|---|
| `BooleanSetting` | Boolean | Switch | — |
| `IntSetting` | Int | Chips (if presets) or Slider | `presetsWhen` dynamic lambda overrides static `presets` — call `getEffectivePresets(currentSettings)` |
| `FloatSetting` | Float | Slider | `min`, `max`, `step?` |
| `StringSetting` | String | Text field | `placeholder?`, `maxLength?` |
| `EnumSetting<E>` | E | Chips (≤10), Dropdown (>10), or preview thumbnails | Serialized as `.name` string — compare with `value?.toString() == option.name` |
| `TimezoneSetting` | String? | Row → fullscreen picker | `null` = system default; `"SYSTEM"` sentinel |
| `DateFormatSetting` | DateFormatOption | Row → popup picker | Stored as `.name` string, deserialize before emitting |
| `AppPickerSetting` | String? | Row → fullscreen grid | `suggestedPackages` sorted first |
| `SoundPickerSetting` | String? | Row → system `ACTION_RINGTONE_PICKER` | Display via `RingtoneManager.getRingtone().getTitle()` |
| `UriSetting` | String? | Fallback `SettingLabel(label, description, theme)` | No dedicated row renderer — dispatched to `SettingRowDispatcher` `else` branch |
| `InstructionSetting` | Unit | Step card (display-only) | `default = Unit`, parallels `SetupDefinition.Instruction` |
| `InfoSetting` | Unit | Info card (display-only) | `default = Unit`, parallels `SetupDefinition.Info` |

### Three-Layer Conditional Visibility

All three checked in both `SetupDefinitionRenderer` and `SettingRowDispatcher`:

1. **`hidden: Boolean`** — hard skip, no animation. For feature-flagged definitions.
2. **`visibleWhen: ((Map<String, Any?>) -> Boolean)?`** — evaluated as `!= false` (null = always visible). **Double-gating:** `SetupDefinition.Setting` wrapper checks its own `visibleWhen` first, then `SettingRowDispatcher` checks the inner `SettingDefinition`'s `visibleWhen` again. This is intentional — the wrapper may add setup-context constraints beyond the raw definition's own.
3. **`requiredAnyEntitlement: List<String>?`** — "any" semantics (one match sufficient). `null` list = no gating. Empty list = no gating. Missing `entitlementManager` defaults to permissive.

Render order: `hidden` (hard skip) → `AnimatedVisibility(visible = isVisible && hasEntitlement)` with `expandEnter/expandExit`.

### Setup Flow Lifecycle

`SetupSheetContent` is fullscreen paginated (not a bottom sheet despite the name).

**Page structure:**
```
SetupSchema: List<SetupPageDefinition>
    └── SetupPageDefinition(title, description, items: List<SetupDefinition>)
```

**Settings loading:** One-shot `produceState` from `providerSettingsStore.getAllSettings(providerId)`, NOT a live Flow. Local `currentSettings: MutableMap` mutated in-memory during the flow.

**Settings persistence:** Immediate write-through on every change — no debounce (unlike layout saves which are debounced 500ms).

**Forward navigation gating:**
```kotlin
val allSatisfied = page.items.all { def ->
    def is SetupDefinition.Setting || def.id in satisfiedDefinitions
}
```

Input types (`Setting`) always count as satisfied — only requirement types block forward navigation. Done button uses the same check on the final page. **Buttons are alpha-dimmed (50%) when disabled but remain tappable** — don't disable them outright.

**Back navigation:** Two exclusive `BackHandler` instances — page > 0 goes back a page, page == 0 dismisses. Don't unify them; the exclusivity is load-bearing.

**Page transitions:** `AnimatedContent` with directional `slideInHorizontally/slideOutHorizontally` based on `targetState > initialState`.

### Re-Evaluation Trigger Pattern (Critical)

The `evaluationTrigger` counter keeps satisfied state current across async events:

```kotlin
var evaluationTrigger by remember { mutableIntStateOf(0) }

LaunchedEffect(provider, evaluationTrigger) {
    satisfiedDefinitions = SetupEvaluator.evaluateWithPersistence(...)
        .filter { it.satisfied }.map { it.definition.id }.toSet()
}
```

Three triggers increment the counter:
1. Initial composition
2. `ON_RESUME` lifecycle event (returning from system settings / permission dialog)
3. Device pairing success callback

Without the counter pattern, `LaunchedEffect` won't re-run when only external state changes (permissions granted, services enabled) — the `provider` key hasn't changed.

### Instruction Verification Lifecycle

Per-instruction state is session-only (`SnapshotStateMap`, not persisted):

1. User taps action button → `actionPerformedKeys[def.id] = true`
2. User returns to app → `ON_RESUME` fires → iterates all instructions with `actionPerformedKeys[id] == true`
3. Runs `VerificationStrategy` for each → stores result in `verificationResults[def.id]`
4. Inline `Info` card synthesized at render time (not in schema) showing Verified/Failed/Skipped
5. `verificationOptional: Boolean` — if true, failed verification does NOT block `canGoForward`

### Permission Card — Three States

| State | Detection | UI |
|---|---|---|
| All granted | `state.allPermissionsGranted` | Accent-tinted "Granted" label, no button |
| Can request | `!allGranted && (!hasRequestedPermissions \|\| shouldShowRationale)` | "Grant" button → `launchMultiplePermissionRequest()` |
| Permanently denied | `hasRequestedPermissions && !shouldShowRationale && !allGranted` | "Open Settings" → `ACTION_APPLICATION_DETAILS_SETTINGS` |

**Critical:** `hasRequestedPermissions` local state is mandatory. Without it, the pre-request state (where `shouldShowRationale = false` and `allGranted = false`) is indistinguishable from permanent denial. The guard prevents false permanent-denial detection on first check.

**minSdk gating:** If `RuntimePermission.minSdk > Build.VERSION.SDK_INT`, `SetupEvaluator` returns `null` — definition is skipped entirely. Renderer never sees it.

### BLE Device Scan — State Machine

```
PreCDM → (tap Scan) → Waiting
Waiting → (CDM returns device) → Verifying(device, attempt=1, max=3)
Verifying → (onPaired success) → Success(device)
Verifying → (onPaired failure, attempt < 3) → Verifying(attempt+1) [after 2000ms]
Verifying → (onPaired failure, attempt == 3) → Failed(device, error)
Failed → (auto-return 1500ms) → PreCDM
Waiting → (user cancels CDM) → PreCDM [silent, no error]
Verifying → (user cancels) → cancel job + onCancel() → PreCDM
```

**CDM setup:** `BluetoothLeDeviceFilter` with `deviceNamePattern` (compiled to `Pattern`) and `serviceUuids` (`ParcelUuid`). `setSingleDevice(true)`. Result callback handles two API variants: API 33+ tries `ScanResult` for `associationId`; fallback to `BluetoothDevice` for older APIs.

**Verification:** `withTimeoutOrNull(30_000L)` on `onPaired?.invoke(device.address)`. Null `onPaired` = immediate success. Retries up to 3 with 2000ms delay.

**Persistence on success:** `pairedDeviceStore.markPaired(definitionId, PairedDeviceMetadata(mac, associationId, ...))`. Then activates CDM presence observation (API 36+ via `ObservingDevicePresenceRequest`, API 31-35 via deprecated MAC-based approach).

**Already paired:** Shows `SuccessContent` directly, no state machine. Scan button disabled when `pairedDevices.size >= maxDevices`. Forget device requires `AlertDialog` confirmation with `forgettingMac: String?` guard against double-tap.

**CDM cancel handling:** "user_rejected"/"canceled" in error string → silent return to PreCDM. Any other error → `Failed` with `lastErrorMessage`.

### Picker Architecture — Four Patterns

| Picker | Navigation Event | Route Type | Result Type |
|---|---|---|---|
| Timezone | `SettingNavigation.ToTimezonePicker(key, current?)` | Fullscreen Hub | Zone ID string or `"SYSTEM"` sentinel |
| Date format | `SettingNavigation.ToDateFormatPicker(key, current)` | Popup (not fullscreen) | `DateFormatOption.name` string |
| App | `SettingNavigation.ToAppPicker(key, currentPackage?)` | Fullscreen Hub, `LazyVerticalGrid(Fixed(4))` | Package name string |
| Sound | System `ACTION_RINGTONE_PICKER` via `ActivityResultLauncher` | External activity | URI string |

**Timezone display logic:**
- `null` → show system timezone as "CityName (GMToffset)" with "System Default" subtitle
- `"SYSTEM"` → show `displayTitle` without subtitle (avoid duplicate)
- else → parse zone ID, extract city from last "/" segment, format with GMT offset

**App picker details:** `ACTION_MAIN` + `CATEGORY_LAUNCHER`, `distinctBy { packageName }`, suggested packages first (ordered by index), remaining alphabetical. Icons: 48×48 bitmap. Search via `BasicTextField` (not `TextField`). Current selection: accent 20% alpha background.

### SettingNavigation Sealed Interface

Single unified callback `onNavigate: ((SettingNavigation) -> Unit)?` with 4 events:
- `ToTimezonePicker(settingKey, currentValue?)`
- `ToDateFormatPicker(settingKey, currentValue)`
- `ToAppPicker(settingKey, currentPackage?)`
- `OnInstructionAction(settingKey, action)` — fires BOTH local action execution AND nav callback. Local handles `OpenUrl`/`LaunchApp`. Nav callback tracks `actionPerformedKeys` for verification lifecycle.

### Two-Layer Dispatcher Pattern

```
SetupDefinitionRenderer        → dispatches SetupDefinition subtypes
    └── SettingRowDispatcher   → dispatches SettingDefinition subtypes (via Setting wrapper)

SettingsSheetDispatcher        → also uses SettingRowDispatcher directly (widget settings tab)
```

Both dispatchers apply visibility/entitlement gating independently. `SettingRowDispatcher` has a fallback: `else -> SettingLabel(label, description, theme)` for unimplemented types (`UriSetting`). Do not crash on unrecognized types.

### Settings Persistence

**ProviderSettingsStore** — Preferences DataStore, key format `"$providerId:$key"`. Type-prefixed serialization:

| Type | Format | Example |
|---|---|---|
| null | `"null"` | — |
| String | `"s:value"` | `"s:America/New_York"` |
| Int | `"i:value"` | `"i:42"` |
| Long | `"l:value"` | `"l:1234567890"` |
| Float | `"f:value"` | `"f:3.14"` |
| Double | `"d:value"` | `"d:2.718"` |
| Boolean | `"b:value"` | `"b:true"` |
| Other | `"j:{json}"` | kotlinx.serialization |

Legacy fallback: no recognized prefix → treat as raw String. Must be preserved for pre-prefix data.

**PairedDeviceStore** — Preferences DataStore, key = `definitionId`, value = JSON `List<PairedDeviceMetadata>`. Decode order: try list first, fall back to single object (legacy). `markPaired` is additive upsert by MAC (case-insensitive). `removePairedDevice` removes from list; empty list removes key. `getAllPairedEntries()` used by `CdmPresenceActivator` on app startup for CDM re-registration.

### Widget Overlay States

`WidgetRenderState` determines what renders on top of a widget cell:

| State | Scrim | Trigger |
|---|---|---|
| `Ready` | None | Normal operation |
| `SetupRequired(requirementType)` | 0.60 | Setup incomplete |
| `ConnectionError(message)` | 0.30 | Provider connected but erroring |
| `Disconnected` | 0.15 | Known device not currently connected |
| `EntitlementRevoked` | 0.60 | User lost access |
| `ProviderMissing` | 0.60 | No provider bound for type |

**`requirementType`** classifies the entry point:
- `"permission"` → `Route.PermissionRequest(widgetId, sourceId)` (permission-only flow)
- `"hardware"` → `Route.ProviderSetup(sourceId)` (full setup)
- `"onboarding"` / null → `Route.ProviderSetup(sourceId)` (full setup)

Computed by evaluating source's `setupSchema` with `SetupEvaluator.evaluate()` and checking unsatisfied definition types.

### Evaluator Variants

Two evaluators with different persistence semantics:

| Evaluator | Persistence | Usage |
|---|---|---|
| `evaluateWithPersistence()` | Checks `pairedDeviceStore.wasPaired()` — device previously paired = satisfied even if disconnected | Setup flow (allows proceeding without device present) |
| `evaluate()` | Only `isDeviceConnected()` — real-time check | Widget status overlay (shows `Disconnected` when device away) |

### Edge Cases to Handle

1. **`visibleWhen` during initial load:** `produceState` initializes to `emptyMap()`. Lambdas checking `currentSettings["key"] == true` will see `null`. Ensure default-visible behavior when map is empty.
2. **Setup interrupted mid-flow:** Settings are already persisted per-change. Re-entry loads partial state. No transaction boundary — `onComplete` fires only on Done tap, but settings are committed regardless.
3. **CDM not available:** Handle `ActivityNotFoundException` and `UnsupportedOperationException` from CDM association launch. Fall back to `Failed` state.
4. **Verification timeout:** `withTimeoutOrNull(30s)` returns `null` → treat same as `Result.failure`. Counts as a failed attempt.
5. **Re-evaluation race:** `ON_RESUME` evaluationTrigger fires before system finishes reflecting permission grant. Android permission grants are synchronous from app's perspective, but if async verification is added later, use a short delay.
6. **EnumSetting serialization:** Settings map stores `Any?`. Enum values serialized as `.name` string. Compare with `value == option || value?.toString() == option.name`.
7. **InstructionAction dual execution:** Row executes action locally AND fires nav callback. Both paths are required — row handles the launch, callback handles verification tracking. Splitting to one path breaks either launch or verification.
