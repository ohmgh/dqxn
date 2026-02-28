# Dashboard Gesture Regression Report

Date: 2026-02-28

Scope:
- New codebase: `/Users/ohm/Workspace/dqxn`
- Old codebase: `/Users/ohm/Workspace/dqxn.old`
- Old-code mapping: `/Users/ohm/Workspace/dqxn/.planning/oldcodebase/feature-dashboard.md`

## Summary

The old dashboard gesture system was a decentralized event-flow design:
- Per-widget stateful gesture handlers emitted tap, long-press, drag, and resize events.
- A blank-space handler emitted background tap and long-press events.
- `DashboardGrid` consumed those flows and translated them into focus, edit-mode, move, resize, and settings actions.

The new dashboard removed that event bus and moved gesture state into `EditModeCoordinator`. That redesign introduced several behavior changes and multiple likely regressions. The highest-confidence breakages are:
- widget tap actions are no longer dispatched,
- resize handles are no longer rendered as actual hit targets,
- live outer-widget resize preview is gone,
- resize bounds/origin compensation are weaker than the old implementation,
- drag snapping and drag bounds changed materially,
- focused-widget settings actions are not fully wired.

## Old Gesture Contract

### Architecture

The old dashboard used a decentralized gesture architecture:
- Each widget owned a `WidgetGestureHandler`.
- Blank canvas owned a `BlankSpaceGestureHandler`.
- `DashboardGrid` collected `SharedFlow` events and decided how they changed focus, edit mode, widget position, and widget size.

Primary sources:
- `../dqxn.old/android/feature/dashboard/src/main/java/app/dqxn/android/feature/dashboard/grid/WidgetGestureHandler.kt`
- `../dqxn.old/android/feature/dashboard/src/main/java/app/dqxn/android/feature/dashboard/grid/BlankSpaceGestureHandler.kt`
- `../dqxn.old/android/feature/dashboard/src/main/java/app/dqxn/android/feature/dashboard/grid/DashboardGrid.kt`
- `../dqxn.old/docs/gesture-architecture.md`
- `.planning/oldcodebase/feature-dashboard.md`

### Widget Taps

View-mode widget taps were not handled by the gesture handler. They were handled by `WidgetContentDispatcher`, which:
- wrapped widgets in `Modifier.clickable`,
- checked `renderer.supportsTap`,
- called `renderer.onTap(...)`,
- fell back to navigation for degraded widget states.

Key source:
- `../dqxn.old/android/feature/dashboard/src/main/java/app/dqxn/android/feature/dashboard/registry/WidgetContentDispatcher.kt`

### Drag

Old drag behavior:
- long press threshold: `400ms`
- cancellation threshold: `8px`
- widget gesture pass: `Initial` in edit mode, `Main` otherwise
- snap target: nearest grid cell
- bounds: computed from actual measured container size

Important details:
- `dropzoneGridPosition` rounded `pixel / unitPx` directly, so drag snapped to any grid cell.
- `DashboardGrid` derived `maxGridColumns` and `maxGridRows` from actual layout constraints, not fixed constants.

Key sources:
- `../dqxn.old/android/feature/dashboard/src/main/java/app/dqxn/android/feature/dashboard/grid/WidgetGestureHandler.kt`
- `../dqxn.old/android/feature/dashboard/src/main/java/app/dqxn/android/feature/dashboard/grid/DashboardGrid.kt`

### Resize

Old resize behavior:
- 4 explicit corner handles were rendered as `Box` nodes at widget corners.
- resize started immediately when touching a handle.
- live preview resized the outer widget by remeasuring it through `previewSizes`.
- top-left, top-right, and bottom-left handles applied position compensation.
- aspect ratio preservation respected both min size and available grid space.

Key sources:
- `../dqxn.old/android/feature/dashboard/src/main/java/app/dqxn/android/feature/dashboard/grid/DashboardGrid.kt`
- `../dqxn.old/android/feature/dashboard/src/main/java/app/dqxn/android/feature/dashboard/grid/DashboardGridLayout.kt`
- `../dqxn.old/android/feature/dashboard/src/main/java/app/dqxn/android/feature/dashboard/grid/WidgetGestureHandler.kt`

### Blank Space

Old blank-space behavior:
- used `requireUnconsumed = true`,
- only fired when widgets had not already claimed the gesture,
- emitted tap and long-press separately from widget handlers.

Key source:
- `../dqxn.old/android/feature/dashboard/src/main/java/app/dqxn/android/feature/dashboard/grid/BlankSpaceGestureHandler.kt`

## New Gesture Contract

### Architecture

The new dashboard moved gesture coordination into:
- `WidgetGestureHandler`
- `BlankSpaceGestureHandler`
- `EditModeCoordinator`
- `DashboardGrid`

But unlike the old code, widget gestures now mutate coordinator state directly instead of emitting a widget-local event stream consumed by the grid.

Primary sources:
- `android/feature/dashboard/src/main/kotlin/app/dqxn/android/feature/dashboard/grid/WidgetGestureHandler.kt`
- `android/feature/dashboard/src/main/kotlin/app/dqxn/android/feature/dashboard/grid/BlankSpaceGestureHandler.kt`
- `android/feature/dashboard/src/main/kotlin/app/dqxn/android/feature/dashboard/coordinator/EditModeCoordinator.kt`
- `android/feature/dashboard/src/main/kotlin/app/dqxn/android/feature/dashboard/grid/DashboardGrid.kt`

### Pager-Aware Blank Space Handling

The new blank-space handler was redesigned for `HorizontalPager`:
- it listens on `PointerEventPass.Final`,
- checks `down.isConsumed`,
- is explicitly written to coexist with profile paging.

Related sources:
- `android/feature/dashboard/src/main/kotlin/app/dqxn/android/feature/dashboard/grid/BlankSpaceGestureHandler.kt`
- `android/feature/dashboard/src/main/kotlin/app/dqxn/android/feature/dashboard/profile/ProfilePageTransition.kt`

### Long-Press Detection

The new code uses timeout-based waiting in pointer scopes. This is stronger than the old event-loop style because a stationary finger can still produce a long press even when no move events arrive.

Sources:
- `android/feature/dashboard/src/main/kotlin/app/dqxn/android/feature/dashboard/grid/WidgetGestureHandler.kt`
- `android/feature/dashboard/src/main/kotlin/app/dqxn/android/feature/dashboard/grid/BlankSpaceGestureHandler.kt`

## Concrete Differences And Likely Regressions

### 1. Widget Tap Actions Are No Longer Dispatched

Old behavior:
- `WidgetContentDispatcher` owned view-mode tap dispatch.
- It checked `supportsTap` and called `renderer.onTap(...)`.

New behavior:
- `WidgetSlot` renders the widget but never checks `supportsTap` and never calls `renderer.onTap(...)`.
- The `WidgetRenderer` contract still exposes both methods.
- Migrated widgets such as `ShortcutsRenderer` still declare `supportsTap = true` and implement `onTap(...)`, but no UI layer calls it.

Evidence:
- Old: `../dqxn.old/android/feature/dashboard/src/main/java/app/dqxn/android/feature/dashboard/registry/WidgetContentDispatcher.kt`
- New: `android/feature/dashboard/src/main/kotlin/app/dqxn/android/feature/dashboard/binding/WidgetSlot.kt`
- Contract: `android/sdk/contracts/src/main/kotlin/app/dqxn/android/sdk/contracts/widget/WidgetRenderer.kt`
- Example widget: `android/pack/essentials/src/main/kotlin/app/dqxn/android/pack/essentials/widgets/shortcuts/ShortcutsRenderer.kt`

Impact:
- View-mode taps on widgets with functional tap behavior are effectively broken.

### 2. Resize Handles Are No Longer Real UI Hit Targets

Old behavior:
- 4 actual corner handle Boxes were rendered on focused widgets.
- Each handle had its own pointer modifier.

New behavior:
- `DashboardGrid` draws bracket visuals only.
- Resize initiation depends on invisible corner hit-testing inside `WidgetGestureHandler.detectResizeHandle(...)`.
- `HANDLE_SIZE_DP` exists, but no visible handle nodes use it.

Evidence:
- Old handle nodes: `../dqxn.old/android/feature/dashboard/src/main/java/app/dqxn/android/feature/dashboard/grid/DashboardGrid.kt`
- New bracket-only rendering: `android/feature/dashboard/src/main/kotlin/app/dqxn/android/feature/dashboard/grid/DashboardGrid.kt`
- New hit-test-only resize logic: `android/feature/dashboard/src/main/kotlin/app/dqxn/android/feature/dashboard/grid/WidgetGestureHandler.kt`

Impact:
- Resize is much less discoverable.
- Touch acquisition for resize is likely worse in practice.

### 3. Live Outer-Widget Resize Preview Is Missing

Old behavior:
- resize preview updated `previewSizes`,
- `DashboardGridLayout` remeasured the outer widget using preview dimensions,
- the widget visibly resized during the gesture.

New behavior:
- `WidgetSlot` only provides `LocalWidgetPreviewUnits` to inner widget content,
- `DashboardGrid` still measures with persisted `widget.size`,
- outer bounds do not resize or reposition during preview.

Evidence:
- Old preview remeasure: `../dqxn.old/android/feature/dashboard/src/main/java/app/dqxn/android/feature/dashboard/grid/DashboardGridLayout.kt`
- New measure path: `android/feature/dashboard/src/main/kotlin/app/dqxn/android/feature/dashboard/grid/DashboardGrid.kt`
- New preview-units-only path: `android/feature/dashboard/src/main/kotlin/app/dqxn/android/feature/dashboard/binding/WidgetSlot.kt`

Impact:
- Resize gesture feedback regresses from outer-frame resize to content-only awareness.
- Non-bottom-right handles lose the old live origin-shift preview.

### 4. Resize Bounds And Position Compensation Regressed

Old behavior:
- resize size calculation clamped against available grid space,
- min size was enforced,
- aspect ratio was preserved with proportional scaling when boundaries were hit,
- non-bottom-right handles compensated position correctly.

New behavior:
- `EditModeCoordinator.updateResize(...)` enforces minimum size,
- optional aspect ratio is applied,
- but there is no clamp to viewport max size,
- no clamp to non-negative grid origin,
- no boundary enforcement during resize.

Evidence:
- Old robust size logic: `../dqxn.old/android/feature/dashboard/src/main/java/app/dqxn/android/feature/dashboard/grid/WidgetGestureHandler.kt`
- New simplified resize state update: `android/feature/dashboard/src/main/kotlin/app/dqxn/android/feature/dashboard/coordinator/EditModeCoordinator.kt`

Impact:
- Top-left, top-right, and bottom-left resize can drift negative or exceed expected bounds.
- Boundary behavior is weaker than old code and weaker than the migration research intended.

### 5. Drag Snapping Changed From Per-Cell To 2-Unit Snap

Old behavior:
- drag snapped to nearest grid cell.

New behavior:
- `GridPlacementEngine.snapToGrid(...)` forces a `SNAP_UNIT` of `2`.

Evidence:
- Old snap math: `../dqxn.old/android/feature/dashboard/src/main/java/app/dqxn/android/feature/dashboard/grid/WidgetGestureHandler.kt`
- New snap math: `android/feature/dashboard/src/main/kotlin/app/dqxn/android/feature/dashboard/grid/GridPlacementEngine.kt`

Impact:
- Dragging now lands on coarser coordinates than the old dashboard.
- This is a substantive behavior change, not just an implementation detail.

### 6. Drag Bounds Changed From Real Layout Constraints To Fixed Viewport Constants

Old behavior:
- `DashboardGrid` computed usable columns and rows from actual layout constraints.

New behavior:
- `DashboardScreen` passes fixed `DEFAULT_VIEWPORT_COLS = 20` and `DEFAULT_VIEWPORT_ROWS = 12`.
- Drag and culling use those constants.

Evidence:
- Old dynamic viewport: `../dqxn.old/android/feature/dashboard/src/main/java/app/dqxn/android/feature/dashboard/grid/DashboardGrid.kt`
- New fixed viewport: `android/feature/dashboard/src/main/kotlin/app/dqxn/android/feature/dashboard/DashboardScreen.kt`

Impact:
- Gesture bounds can diverge from actual device/container size.
- This is especially risky on larger screens, orientation changes, and foldables.

### 7. Focused-Widget Settings Action Is Not Fully Wired

New `DashboardGrid` dispatches `DashboardCommand.OpenWidgetSettings(...)`, but `DashboardViewModel` still treats that command as TODO and only logs/records it.

Evidence:
- Dispatch site: `android/feature/dashboard/src/main/kotlin/app/dqxn/android/feature/dashboard/grid/DashboardGrid.kt`
- Incomplete handling: `android/feature/dashboard/src/main/kotlin/app/dqxn/android/feature/dashboard/DashboardViewModel.kt`

Impact:
- Focus overlay toolbar settings interaction is not functionally equivalent to old behavior.

## Differences That Are Improvements, Not Regressions

- Blank-space gesture handling is pager-aware in the new code.
- Long-press detection is stronger because it no longer depends on movement events continuing to arrive.
- Edit mode, drag state, and resize state are cleaner to reason about in the coordinator-based design.

## Why Current Tests Did Not Catch This

Current dashboard unit tests passed when run on 2026-02-28:
- `WidgetGestureHandlerTest`
- `BlankSpaceGestureHandlerTest`
- `DashboardGridTest`

Command run:

```bash
cd /Users/ohm/Workspace/dqxn/android
./gradlew :feature:dashboard:testDebugUnitTest --tests 'app.dqxn.android.feature.dashboard.grid.WidgetGestureHandlerTest' --tests 'app.dqxn.android.feature.dashboard.grid.BlankSpaceGestureHandlerTest' --tests 'app.dqxn.android.feature.dashboard.grid.DashboardGridTest' --console=plain
```

Result:
- `BUILD SUCCESSFUL`

Why that is insufficient:
- these tests validate constants and helper logic,
- they do not simulate Compose pointer interactions end-to-end,
- they do not verify renderer tap dispatch,
- they do not verify actual resize handle affordances,
- they do not verify live resize preview,
- they do not verify toolbar settings navigation.

## Highest-Priority Fix Targets

1. Restore widget tap dispatch in the dashboard layer, or explicitly move it into widget renderers and add actual click handling there.
2. Reintroduce actual resize handle nodes with visible and reliable touch targets.
3. Restore live outer-widget resize preview by remeasuring layout with preview size and preview position.
4. Rework resize bounds and origin compensation to match old behavior and intended migration behavior.
5. Replace fixed viewport drag bounds with constraint-derived bounds.
6. Decide explicitly whether drag snap should be old 1-unit behavior or new 2-unit behavior.
7. Wire `DashboardCommand.OpenWidgetSettings` to real navigation.

## Supporting References

Old codebase:
- `../dqxn.old/docs/gesture-architecture.md`
- `../dqxn.old/android/feature/dashboard/src/main/java/app/dqxn/android/feature/dashboard/grid/WidgetGestureHandler.kt`
- `../dqxn.old/android/feature/dashboard/src/main/java/app/dqxn/android/feature/dashboard/grid/BlankSpaceGestureHandler.kt`
- `../dqxn.old/android/feature/dashboard/src/main/java/app/dqxn/android/feature/dashboard/grid/DashboardGrid.kt`
- `../dqxn.old/android/feature/dashboard/src/main/java/app/dqxn/android/feature/dashboard/grid/DashboardGridLayout.kt`
- `../dqxn.old/android/feature/dashboard/src/main/java/app/dqxn/android/feature/dashboard/registry/WidgetContentDispatcher.kt`
- `.planning/oldcodebase/feature-dashboard.md`

New codebase:
- `android/feature/dashboard/src/main/kotlin/app/dqxn/android/feature/dashboard/grid/WidgetGestureHandler.kt`
- `android/feature/dashboard/src/main/kotlin/app/dqxn/android/feature/dashboard/grid/BlankSpaceGestureHandler.kt`
- `android/feature/dashboard/src/main/kotlin/app/dqxn/android/feature/dashboard/grid/DashboardGrid.kt`
- `android/feature/dashboard/src/main/kotlin/app/dqxn/android/feature/dashboard/coordinator/EditModeCoordinator.kt`
- `android/feature/dashboard/src/main/kotlin/app/dqxn/android/feature/dashboard/grid/GridPlacementEngine.kt`
- `android/feature/dashboard/src/main/kotlin/app/dqxn/android/feature/dashboard/binding/WidgetSlot.kt`
- `android/feature/dashboard/src/main/kotlin/app/dqxn/android/feature/dashboard/DashboardScreen.kt`
- `android/feature/dashboard/src/main/kotlin/app/dqxn/android/feature/dashboard/DashboardViewModel.kt`
- `android/feature/dashboard/src/main/kotlin/app/dqxn/android/feature/dashboard/profile/ProfilePageTransition.kt`
