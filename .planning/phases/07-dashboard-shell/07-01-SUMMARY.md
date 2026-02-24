---
phase: 07-dashboard-shell
plan: 01
subsystem: dashboard
tags: [coordinator, grid-placement, safe-mode, state-flow, viewport-culling, test-harness]

# Dependency graph
requires:
  - phase: 05-core-infrastructure
    provides: "LayoutRepository, PresetLoader, Proto DataStore, IoDispatcher"
  - phase: 06-deployable-app-agentic-framework
    provides: "CrashRecovery patterns (SharedPreferences commit), app shell"
provides:
  - "DashboardCommand sealed interface (16 variants) for all discrete operations"
  - "LayoutCoordinator with widget CRUD, viewport culling, StateFlow-based state"
  - "GridPlacementEngine with no-straddle snap and center-biased placement"
  - "ConfigurationBoundaryDetector for foldable and non-foldable boundary detection"
  - "SafeModeManager with cross-widget crash counting (4+ in 60s rolling window)"
  - "DashboardTestHarness DSL + FakeLayoutRepository + FakeSharedPreferences + TestWidgetFactory in testFixtures"
affects: [07-02, 07-03, 07-04, 07-05, 07-06, 07-07, 08-essentials-pack]

# Tech tracking
tech-stack:
  added: [androidx.window (WindowInfoTracker, FoldingFeature)]
  patterns: [coordinator-owns-state-slice, no-straddle-snap, cross-widget-crash-counting, constructor-injected-clock]

key-files:
  created:
    - android/feature/dashboard/src/main/kotlin/app/dqxn/android/feature/dashboard/command/DashboardCommand.kt
    - android/feature/dashboard/src/main/kotlin/app/dqxn/android/feature/dashboard/coordinator/LayoutCoordinator.kt
    - android/feature/dashboard/src/main/kotlin/app/dqxn/android/feature/dashboard/grid/GridPlacementEngine.kt
    - android/feature/dashboard/src/main/kotlin/app/dqxn/android/feature/dashboard/grid/ConfigurationBoundaryDetector.kt
    - android/feature/dashboard/src/main/kotlin/app/dqxn/android/feature/dashboard/safety/SafeModeManager.kt
    - android/feature/dashboard/src/testFixtures/kotlin/app/dqxn/android/feature/dashboard/test/DashboardTestHarness.kt
    - android/feature/dashboard/src/testFixtures/kotlin/app/dqxn/android/feature/dashboard/test/FakeLayoutRepository.kt
    - android/feature/dashboard/src/testFixtures/kotlin/app/dqxn/android/feature/dashboard/test/FakeSharedPreferences.kt
    - android/feature/dashboard/src/testFixtures/kotlin/app/dqxn/android/feature/dashboard/test/TestWidgetFactory.kt
    - android/feature/dashboard/src/testFixtures/kotlin/app/dqxn/android/feature/dashboard/test/HarnessStateOnFailure.kt
    - android/feature/dashboard/src/test/kotlin/app/dqxn/android/feature/dashboard/coordinator/LayoutCoordinatorTest.kt
    - android/feature/dashboard/src/test/kotlin/app/dqxn/android/feature/dashboard/grid/GridPlacementEngineTest.kt
    - android/feature/dashboard/src/test/kotlin/app/dqxn/android/feature/dashboard/grid/ConfigurationBoundaryDetectorTest.kt
    - android/feature/dashboard/src/test/kotlin/app/dqxn/android/feature/dashboard/safety/SafeModeManagerTest.kt
  modified: []

key-decisions:
  - "SafeModeManager clock as constructor parameter (not mutable var) -- init block reads clock, must be available before first use"
  - "handleResetLayout captures existing widgets before state update -- prevents repo/StateFlow race when flow collection is active"
  - "android.graphics.Rect fields set via apply{} in unit tests -- AGP returnDefaultValues=true stubs constructors"
  - "runTest(UnconfinedTestDispatcher()) for coordinator tests -- backgroundScope does not advance with StandardTestDispatcher advanceUntilIdle()"

patterns-established:
  - "Coordinator state slice: each coordinator owns MutableStateFlow, exposes read-only StateFlow"
  - "UnconfinedTestDispatcher for coordinator tests: backgroundScope + flow collection requires eager dispatch"
  - "android.graphics.Rect workaround: use apply{} to set fields after construction in AGP stub environment"
  - "FakeSharedPreferences: in-memory SharedPreferences for JUnit5 tests without Robolectric"

requirements-completed: [F1.3, F1.4, F1.10, F1.12, F1.20, F1.26, F1.27, F1.28, F2.6, F2.10, F2.11, NF6, NF7, NF38, NF45, NF46]

# Metrics
duration: 25min
completed: 2026-02-24
---

# Phase 7 Plan 01: Foundation Types + Layout Coordinator Summary

**LayoutCoordinator with widget CRUD and viewport culling, GridPlacementEngine with no-straddle snap, SafeModeManager with cross-widget crash counting, and DashboardTestHarness DSL in testFixtures**

## Performance

- **Duration:** ~25 min
- **Started:** 2026-02-24T05:06:00Z
- **Completed:** 2026-02-24T05:33:00Z
- **Tasks:** 2
- **Files created:** 14

## Accomplishments

- DashboardCommand sealed interface with 16 variants (AddWidget through CycleThemeMode), each carrying optional traceId
- LayoutCoordinator managing full widget CRUD (add/remove/move/resize/reset) with StateFlow-based layout state, viewport culling, and IO dispatcher isolation
- GridPlacementEngine with center-biased row-by-row scan, overlap avoidance, no-straddle snap enforcement at configuration boundaries, and canvas extension fallback
- ConfigurationBoundaryDetector using WindowInfoTracker for foldable FoldingFeature detection and alternate-orientation boundary computation for non-foldable devices
- SafeModeManager with 4-crash/60s rolling window, cross-widget counting via SharedPreferences commit() for process-death safety
- Test infrastructure: DashboardTestHarness DSL, FakeLayoutRepository, FakeSharedPreferences, TestWidgetFactory, HarnessStateOnFailure watcher -- all via testFixtures for reuse in subsequent plans
- 27 unit tests across 4 test classes, all passing

## Task Commits

Each task was committed atomically:

1. **Task 1: Build config + DashboardCommand + LayoutCoordinator + GridPlacementEngine + SafeModeManager** - `b31a512` (feat)
2. **Task 2: Test infrastructure + unit tests** - `322f465` (test)

## Files Created/Modified

### Production (Task 1)
- `android/feature/dashboard/src/main/kotlin/.../command/DashboardCommand.kt` - Sealed interface with 16 command variants
- `android/feature/dashboard/src/main/kotlin/.../coordinator/LayoutCoordinator.kt` - Widget CRUD, viewport culling, StateFlow state
- `android/feature/dashboard/src/main/kotlin/.../grid/GridPlacementEngine.kt` - Optimal placement with no-straddle snap
- `android/feature/dashboard/src/main/kotlin/.../grid/ConfigurationBoundaryDetector.kt` - Foldable/orientation boundary detection
- `android/feature/dashboard/src/main/kotlin/.../safety/SafeModeManager.kt` - Cross-widget crash counting

### Test Infrastructure (Task 2 - testFixtures)
- `android/feature/dashboard/src/testFixtures/.../test/DashboardTestHarness.kt` - DSL entry point for coordinator testing
- `android/feature/dashboard/src/testFixtures/.../test/FakeLayoutRepository.kt` - In-memory LayoutRepository
- `android/feature/dashboard/src/testFixtures/.../test/FakeSharedPreferences.kt` - In-memory SharedPreferences
- `android/feature/dashboard/src/testFixtures/.../test/TestWidgetFactory.kt` - Test widget construction helpers
- `android/feature/dashboard/src/testFixtures/.../test/HarnessStateOnFailure.kt` - JUnit5 TestWatcher for state dumps

### Tests (Task 2)
- `android/feature/dashboard/src/test/.../coordinator/LayoutCoordinatorTest.kt` - 9 tests: init, CRUD, reset, viewport culling
- `android/feature/dashboard/src/test/.../grid/GridPlacementEngineTest.kt` - 7 tests: placement, overlap, no-straddle, snap, canvas extension
- `android/feature/dashboard/src/test/.../grid/ConfigurationBoundaryDetectorTest.kt` - 4 tests: foldable, non-foldable, fixed, updates
- `android/feature/dashboard/src/test/.../safety/SafeModeManagerTest.kt` - 7 tests: threshold, expiry, reset, persistence

## Decisions Made

1. **SafeModeManager clock as constructor parameter** -- The plan specified a mutable var/setter for the clock, but the init block calls `checkSafeMode()` which uses the clock. In tests, crash timestamps written with a fake clock (1_000_000L) would expire immediately when a restored manager used `System.currentTimeMillis()`. Constructor parameter with default `{ System.currentTimeMillis() }` ensures clock is available before init.

2. **handleResetLayout captures existing widgets before state update** -- The original implementation read `_layoutState.value.widgets` inside the persistence `withContext` block after updating state. With an active flow collection from `initialize()`, the repo mutations caused re-emission that overwrote the preset widgets. Capturing existing widget IDs before the state update fixes the race.

3. **android.graphics.Rect field assignment via apply{}** -- AGP `returnDefaultValues=true` stubs out Android framework constructors. `Rect(0, 480, 1080, 500)` creates a Rect with all zeros. The workaround: `Rect().apply { left = 0; top = 480; right = 1080; bottom = 500 }` sets public fields directly.

4. **runTest(UnconfinedTestDispatcher()) for coordinator/detector tests** -- `backgroundScope` in `runTest` does not advance with `advanceUntilIdle()` when using `StandardTestDispatcher`. Coordinator tests that launch never-completing flow collections on `backgroundScope` require `UnconfinedTestDispatcher` so coroutines execute eagerly.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] SafeModeManager clock init ordering**
- **Found during:** Task 2 (SafeModeManagerTest)
- **Issue:** `clock` was a mutable var set after construction, but `init` block used it via `checkSafeMode()`, causing NPE and incorrect behavior in "survives process death" test
- **Fix:** Changed `clock` to constructor parameter with default `{ System.currentTimeMillis() }`
- **Files modified:** SafeModeManager.kt
- **Verification:** All 7 SafeModeManagerTest pass including process-death survival
- **Committed in:** 322f465 (Task 2 commit)

**2. [Rule 1 - Bug] LayoutCoordinator.handleResetLayout race condition**
- **Found during:** Task 2 (LayoutCoordinatorTest)
- **Issue:** `handleResetLayout` read widget IDs from `_layoutState.value` after updating it, missing the old widgets for removal from repository. With active flow collection, repo mutations overwrote the preset state.
- **Fix:** Capture `existingWidgets` before the state update
- **Files modified:** LayoutCoordinator.kt
- **Verification:** handleResetLayout test passes -- exactly 2 preset widgets after reset
- **Committed in:** 322f465 (Task 2 commit)

**3. [Rule 3 - Blocking] android.graphics.Rect stub incompatibility**
- **Found during:** Task 2 (ConfigurationBoundaryDetectorTest)
- **Issue:** `Rect(left, top, right, bottom)` constructor body is stubbed by AGP `returnDefaultValues=true`, all fields remain 0
- **Fix:** Use `Rect().apply { field = value }` to set public fields directly
- **Files modified:** ConfigurationBoundaryDetectorTest.kt
- **Verification:** Foldable boundary test passes with correct coordinates
- **Committed in:** 322f465 (Task 2 commit)

---

**Total deviations:** 3 auto-fixed (2 bugs, 1 blocking)
**Impact on plan:** All fixes necessary for correctness. No scope creep.

## Issues Encountered

- **backgroundScope + StandardTestDispatcher incompatibility**: Tests using `backgroundScope.launch` with flow collection never processed because `advanceUntilIdle()` does not advance backgroundScope's dispatcher. Resolved by using `runTest(UnconfinedTestDispatcher())` which makes all dispatching eager. This is a known limitation of kotlinx.coroutines.test -- backgroundScope is designed for concurrent work that doesn't participate in virtual time advancement.

- **Build.gradle.kts already modified by prior 07-02 execution**: The 07-02 plan (wave 1 parallel) had already committed changes to the dashboard build.gradle.kts including testFixtures configuration and several dependencies. Task 1 of this plan did not need to modify it again.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- LayoutCoordinator, GridPlacementEngine, ConfigurationBoundaryDetector, and SafeModeManager ready for consumption by Plans 03-07
- DashboardTestHarness DSL available via testFixtures for all subsequent dashboard plans
- FakeLayoutRepository and TestWidgetFactory reusable in integration tests
- DashboardCommand sealed interface ready for CommandDispatcher (Plan 03)

## Self-Check: PASSED

- All 14 created files verified on disk
- Both task commits (b31a512, 322f465) verified in git log
- All 27 unit tests pass (0 failures)
- Production code compiles successfully

---
*Phase: 07-dashboard-shell*
*Completed: 2026-02-24*
