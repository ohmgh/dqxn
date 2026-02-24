---
phase: 07-dashboard-shell
plan: 15
subsystem: testing
tags: [mockk, mockkStatic, alertEmitter, tautological-tests, flow-cancellation, reduced-motion]

# Dependency graph
requires:
  - phase: 07-dashboard-shell plan 14
    provides: StandardTestDispatcher migration for NotificationCoordinatorTest
provides:
  - Non-tautological ReducedMotionIntegrationTest exercising production ReducedMotionHelper
  - WidgetSlotTest crash test using real WidgetBindingCoordinator -> SafeModeManager delegation
  - alertEmitter.fire() production invocation on VIBRATE alert profile banners
  - Unbind-cancels-provider-flow test proving flow lifecycle management
affects: [08-essentials-pack]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "mockkStatic(Settings.Global::class) for testing ReducedMotionHelper"
    - "flow { awaitCancellation() } + finally for tracking flow collection lifecycle"
    - "coordinatorScope.launch for firing suspend side-effects from non-suspend functions"

key-files:
  created: []
  modified:
    - android/feature/dashboard/src/test/kotlin/app/dqxn/android/feature/dashboard/ReducedMotionIntegrationTest.kt
    - android/feature/dashboard/src/test/kotlin/app/dqxn/android/feature/dashboard/binding/WidgetSlotTest.kt
    - android/feature/dashboard/src/main/kotlin/app/dqxn/android/feature/dashboard/coordinator/NotificationCoordinator.kt
    - android/feature/dashboard/src/test/kotlin/app/dqxn/android/feature/dashboard/coordinator/NotificationCoordinatorTest.kt
    - android/feature/dashboard/src/test/kotlin/app/dqxn/android/feature/dashboard/coordinator/WidgetBindingCoordinatorTest.kt

key-decisions:
  - "mockkStatic(Settings.Global::class) for ReducedMotionHelper tests -- avoids Robolectric while testing real production code"
  - "Real WidgetBindingCoordinator + real SafeModeManager in crash delegation test -- tests actual delegation, not mock mechanics"
  - "coordinatorScope stored from initialize() for alertEmitter.fire() launch -- fire() is suspend, showBanner() is not"
  - "::coordinatorScope.isInitialized guard for showBanner() calls before initialize() -- defensive null-safety"

patterns-established:
  - "mockkStatic for Android Settings.Global testing without Robolectric"
  - "flow { awaitCancellation() } + finally pattern for verifying flow cancellation"

requirements-completed: [F9.2, F2.14, NF39, NF5]

# Metrics
duration: 4min
completed: 2026-02-24
---

# Phase 07 Plan 15: Gap Closure Summary

**Rewrite 3 tautological tests to exercise production code, wire alertEmitter.fire() side-effect on VIBRATE banners, add unbind-cancels-flow test**

## Performance

- **Duration:** 4 min
- **Started:** 2026-02-24T15:22:00Z
- **Completed:** 2026-02-24T15:25:57Z
- **Tasks:** 2
- **Files modified:** 5

## Accomplishments
- Replaced 3 tautological tests that tested mock mechanics with tests exercising production code (Q2 closed)
- Wired alertEmitter.fire() in NotificationCoordinator.showBanner() and added test verifying VIBRATE profile invocation (Q3 closed)
- Added unbind-cancels-provider-flow test proving flow lifecycle management equivalent to WhileSubscribed (Q4 closed)
- All 3 new/rewritten tests pass alongside all existing dashboard tests (no regressions)

## Task Commits

Each task was committed atomically:

1. **Task 1: Rewrite tautological tests + alertEmitter.fire()** - `b06943a` (fix)
2. **Task 2: Add unbind-cancels-provider-flow test** - `f5c4db9` (test)

## Files Created/Modified
- `android/feature/dashboard/src/test/kotlin/.../ReducedMotionIntegrationTest.kt` - Rewritten to test production ReducedMotionHelper via mockkStatic(Settings.Global) instead of replicated gate logic
- `android/feature/dashboard/src/test/kotlin/.../binding/WidgetSlotTest.kt` - Crash test replaced with real WidgetBindingCoordinator -> SafeModeManager delegation
- `android/feature/dashboard/src/main/kotlin/.../coordinator/NotificationCoordinator.kt` - Added coordinatorScope + alertEmitter.fire() invocation in showBanner()
- `android/feature/dashboard/src/test/kotlin/.../coordinator/NotificationCoordinatorTest.kt` - Added 2 tests: alertEmitter.fire() positive (VIBRATE) and negative (no profile)
- `android/feature/dashboard/src/test/kotlin/.../coordinator/WidgetBindingCoordinatorTest.kt` - Added unbind-cancels-provider-flow test with collection tracking

## Decisions Made
- Used `mockkStatic(Settings.Global::class)` for ReducedMotionHelper tests instead of Robolectric -- JUnit5 @Test is incompatible with @RunWith(RobolectricTestRunner), and mockkStatic cleanly intercepts the static method call
- Stored `coordinatorScope` from `initialize()` with `::coordinatorScope.isInitialized` guard -- defensive against showBanner() being called before initialize(), which can happen during test setup
- Used real WidgetBindingCoordinator + real SafeModeManager for crash delegation test instead of double-mocking -- proves the actual production delegation chain works

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- All Q2/Q3/Q4 quality gaps from 07-VERIFICATION.md are now closed
- Phase 7 gap closure plans (07-14, 07-15, 07-16) are all complete
- Ready for Phase 8: Essentials Pack

## Self-Check: PASSED

- All 5 modified files exist on disk
- Task 1 commit `b06943a` exists in git log
- Task 2 commit `f5c4db9` exists in git log
- must_haves artifact content checks: all 5 patterns found in expected files
- All dashboard tests pass (BUILD SUCCESSFUL, 0 failures)

---
*Phase: 07-dashboard-shell*
*Completed: 2026-02-24*
