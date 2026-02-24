---
phase: 07-dashboard-shell
plan: 02
subsystem: dashboard
tags: [compose, theme-coordinator, haptics, reduced-motion, vibration, auto-switch]

# Dependency graph
requires:
  - phase: 05-core-infrastructure
    provides: ThemeAutoSwitchEngine, BuiltInThemes, DesignModule
  - phase: 05-core-infrastructure
    provides: UserPreferencesRepository, Preferences DataStore
  - phase: 02-sdk-contracts
    provides: AutoSwitchMode, DashboardThemeDefinition, ThemeSpec
  - phase: 03-sdk-observability
    provides: DqxnLogger, LogTags, NoOpLogger
provides:
  - ThemeCoordinator with preview/revert cycle and auto-switch integration
  - ThemeState data class with displayTheme derivation
  - DashboardHaptics with 8 semantic haptic methods
  - ReducedMotionHelper for system reduced motion detection
affects: [phase-07-dashboard-shell, phase-08-essentials-pack, phase-09-themes]

# Tech tracking
tech-stack:
  added: []
  patterns: [preview-before-navigation theme flash prevention, mockkStatic for VibrationEffect in unit tests, temporary file staging for parallel wave testing]

key-files:
  created:
    - android/feature/dashboard/src/main/kotlin/app/dqxn/android/feature/dashboard/coordinator/ThemeCoordinator.kt
    - android/feature/dashboard/src/main/kotlin/app/dqxn/android/feature/dashboard/gesture/DashboardHaptics.kt
    - android/feature/dashboard/src/main/kotlin/app/dqxn/android/feature/dashboard/gesture/ReducedMotionHelper.kt
    - android/feature/dashboard/src/test/kotlin/app/dqxn/android/feature/dashboard/coordinator/ThemeCoordinatorTest.kt
    - android/feature/dashboard/src/test/kotlin/app/dqxn/android/feature/dashboard/gesture/DashboardHapticsTest.kt
    - android/feature/dashboard/src/test/kotlin/app/dqxn/android/feature/dashboard/gesture/ReducedMotionHelperTest.kt
  modified:
    - android/feature/dashboard/build.gradle.kts

key-decisions:
  - "mockkStatic(VibrationEffect::class) for unit testing -- VibrationEffect.createPredefined returns null in Android stubs"
  - "testFixtures deps: compose-bom/runtime, junit-bom/jupiter-api, mockk, window -- required by parallel plan 07-01 testFixtures sources"
  - "Temporary file staging for parallel wave execution -- moved 07-01 uncommitted files to /tmp during test compilation"

patterns-established:
  - "Preview-before-navigation: set previewTheme BEFORE navigation to prevent theme flash (replication advisory section 3)"
  - "mockkStatic for Android framework statics: VibrationEffect.createPredefined, Settings.Global.getFloat"

requirements-completed: [F1.17, F10.9, NF39]

# Metrics
duration: 9min
completed: 2026-02-24
---

# Phase 7 Plan 2: ThemeCoordinator + DashboardHaptics + ReducedMotionHelper Summary

**ThemeCoordinator with preview/revert cycle and 5-mode auto-switch cycling, DashboardHaptics with 8 semantic vibration methods, and ReducedMotionHelper reading animator_duration_scale**

## Performance

- **Duration:** 9 min
- **Started:** 2026-02-24T05:07:14Z
- **Completed:** 2026-02-24T05:16:30Z
- **Tasks:** 2
- **Files modified:** 7

## Accomplishments
- ThemeCoordinator manages theme state with preview/revert cycle, observes ThemeAutoSwitchEngine.isDarkActive and UserPreferencesRepository preferences, derives displayTheme from preview/current
- DashboardHaptics provides 8 semantic haptic methods (editModeEnter, editModeExit, dragStart, snapToGrid, boundaryHit, resizeStart, widgetFocus, buttonPress) with reduced motion awareness
- ReducedMotionHelper detects system reduced motion via animator_duration_scale == 0
- Quick theme toggle cycles 5 modes: LIGHT -> DARK -> SYSTEM -> SOLAR_AUTO -> ILLUMINANCE_AUTO -> LIGHT (F10.9)
- 22 tests passing across 3 test classes (12 ThemeCoordinator, 7 DashboardHaptics, 3 ReducedMotionHelper)

## Task Commits

Each task was committed atomically:

1. **Task 1: ThemeCoordinator + DashboardHaptics + ReducedMotionHelper** - `a1bbc45` (feat)
2. **Task 2: ThemeCoordinatorTest + DashboardHapticsTest + ReducedMotionHelperTest** - `d2e1ade` (test)

## Files Created/Modified
- `android/feature/dashboard/src/main/kotlin/.../coordinator/ThemeCoordinator.kt` - Theme state management with preview/revert, auto-switch integration, mode cycling
- `android/feature/dashboard/src/main/kotlin/.../gesture/DashboardHaptics.kt` - 8 semantic haptic methods with VibrationEffect.createPredefined, reduced motion variants
- `android/feature/dashboard/src/main/kotlin/.../gesture/ReducedMotionHelper.kt` - System reduced motion detection via Settings.Global.ANIMATOR_DURATION_SCALE
- `android/feature/dashboard/src/test/kotlin/.../coordinator/ThemeCoordinatorTest.kt` - 12 tests: initialize, setTheme, preview/revert, cycle modes, displayTheme derivation
- `android/feature/dashboard/src/test/kotlin/.../gesture/DashboardHapticsTest.kt` - 7 tests: all 8 methods, reduced motion, no vibrator
- `android/feature/dashboard/src/test/kotlin/.../gesture/ReducedMotionHelperTest.kt` - 3 tests: animator_duration_scale 0/1/default
- `android/feature/dashboard/build.gradle.kts` - Added testFixtures deps (compose-bom/runtime, junit-bom/jupiter-api, mockk, window)

## Decisions Made
- **mockkStatic(VibrationEffect::class)** for DashboardHaptics tests -- `VibrationEffect.createPredefined()` returns null in Android unit test stubs (isReturnDefaultValues=true returns null for objects). Mocking the static returns a stub effect for verification.
- **testFixtures dependency additions** -- compose-bom/runtime (Compose compiler processes testFixtures source set), junit-bom/jupiter-api (HarnessStateOnFailure extends TestWatcher), mockk/window (DashboardTestHarness uses mockk and WindowInfoTracker). All needed by parallel plan 07-01's testFixtures files.
- **Temporary file staging for parallel wave execution** -- Plan 07-01 (Wave 1 parallel) left uncommitted source files on disk that prevented compilation. Temporarily moved to /tmp, ran tests, restored. No files lost.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] testFixtures compilation failure from missing Compose Runtime**
- **Found during:** Task 2 (test compilation)
- **Issue:** Compose compiler plugin (applied via dqxn.android.feature convention) processes testFixtures source set, but compose-runtime was not on testFixtures classpath. Error: "The Compose Compiler requires the Compose Runtime to be on the class path"
- **Fix:** Added `testFixturesImplementation(platform(libs.compose.bom))` and `testFixturesImplementation(libs.compose.runtime)` to build.gradle.kts
- **Files modified:** android/feature/dashboard/build.gradle.kts
- **Committed in:** d2e1ade (Task 2 commit)

**2. [Rule 3 - Blocking] testFixtures + test compilation failure from parallel plan 07-01 uncommitted files**
- **Found during:** Task 2 (test compilation)
- **Issue:** Plan 07-01 (parallel Wave 1) left source files on disk referencing types not yet committed (LayoutCoordinator, GridPlacementEngine, SafeModeManager, etc.), preventing test compilation
- **Fix:** Temporarily moved 07-01 files to /tmp during test execution, restored after tests passed. Added missing testFixtures deps (junit-bom, junit-jupiter-api, mockk, window).
- **Files modified:** android/feature/dashboard/build.gradle.kts
- **Committed in:** d2e1ade (Task 2 commit)

**3. [Rule 1 - Bug] VibrationEffect.createPredefined returns null in unit tests**
- **Found during:** Task 2 (DashboardHapticsTest)
- **Issue:** Android stub `VibrationEffect.createPredefined()` returns null (default return for isReturnDefaultValues=true), causing NPE when passed to `Vibrator.vibrate()`
- **Fix:** Added `mockkStatic(VibrationEffect::class)` with `every { VibrationEffect.createPredefined(any()) } returns stubEffect` in test setup
- **Files modified:** DashboardHapticsTest.kt
- **Committed in:** d2e1ade (Task 2 commit)

---

**Total deviations:** 3 auto-fixed (1 bug, 2 blocking)
**Impact on plan:** All fixes necessary for test compilation and execution. No scope creep.

## Issues Encountered
None beyond the deviations above.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- ThemeCoordinator ready for DashboardViewModel integration (Plan 03+)
- ThemeState.displayTheme ready as single source for LocalDashboardTheme CompositionLocal
- DashboardHaptics ready for edit mode/drag/resize gesture handlers
- ReducedMotionHelper ready for animation controllers (wiggle disable, spring->snap)
- handleCycleThemeMode ready for quick theme toggle UI binding

## Self-Check: PASSED

- All 6 created source files verified present on disk
- Both task commits (a1bbc45, d2e1ade) verified in git log
- 22 tests passing in :feature:dashboard:testDebugUnitTest

---
*Phase: 07-dashboard-shell*
*Completed: 2026-02-24*
