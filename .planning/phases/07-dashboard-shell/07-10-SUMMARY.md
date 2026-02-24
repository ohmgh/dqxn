---
phase: 07-dashboard-shell
plan: 10
subsystem: testing
tags: [StandardTestDispatcher, Turbine, coroutines-test, flow-testing]

# Dependency graph
requires:
  - phase: 07-dashboard-shell
    provides: "WidgetBindingCoordinator + WidgetDataBinder test suites"
provides:
  - "CLAUDE.md-compliant test dispatchers in coordinator and binder tests"
  - "Turbine-based flow testing pattern for WidgetDataBinder"
affects: [08-essentials-pack]

# Tech tracking
tech-stack:
  added: []
  patterns: ["child Job pattern for coordinator test scopes", "coordinator.destroy() cleanup before runTest exits", "Turbine test{} blocks for merge+scan flow verification", "testScheduler.runCurrent() over advanceUntilIdle() for infinite-loop coroutines"]

key-files:
  created: []
  modified:
    - "android/feature/dashboard/src/test/kotlin/app/dqxn/android/feature/dashboard/coordinator/WidgetBindingCoordinatorTest.kt"
    - "android/feature/dashboard/src/test/kotlin/app/dqxn/android/feature/dashboard/binding/WidgetDataBinderTest.kt"

key-decisions:
  - "testScheduler.runCurrent() over advanceUntilIdle() -- staleness watchdog (while(true) { delay() }) causes infinite loop with advanceUntilIdle; runCurrent() only dispatches currently-queued coroutines"
  - "coordinator.destroy() required in every test -- standalone SupervisorJob not child of test Job; runTest cleanup advanceUntilIdle() hangs without explicit cancellation"
  - "Child Job pattern (Job(coroutineContext[Job]) + this) replaces backgroundScope -- coroutines driven by test scheduler"

patterns-established:
  - "WidgetBindingCoordinator test cleanup: always call coordinator.destroy() then initJob.cancel() before runTest body exits"
  - "Flow testing with Turbine: test{} + awaitItem() for scan-based flows, asserting scan seed (WidgetData.Empty) as first item"
  - "testScheduler.runCurrent() for dispatching coroutines under StandardTestDispatcher when infinite delay loops exist"

requirements-completed: [NF19, F2.4]

# Metrics
duration: 62min
completed: 2026-02-24
---

# Phase 7 Plan 10: Dispatcher Compliance Summary

**Migrated WidgetBindingCoordinatorTest and WidgetDataBinderTest from UnconfinedTestDispatcher to StandardTestDispatcher + Turbine per CLAUDE.md rule**

## Performance

- **Duration:** 62 min
- **Started:** 2026-02-24T11:23:20Z
- **Completed:** 2026-02-24T12:25:26Z
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments
- All 7 WidgetBindingCoordinatorTest tests migrated from UnconfinedTestDispatcher to StandardTestDispatcher with child Job scoping pattern
- All 7 WidgetDataBinderTest tests migrated from launch(UnconfinedTestDispatcher()) to Turbine test{} blocks
- Zero UnconfinedTestDispatcher references remain in either file
- All 14 tests pass with deterministic scheduling

## Task Commits

Each task was committed atomically:

1. **Task 1: Migrate WidgetBindingCoordinatorTest to StandardTestDispatcher** - `879bf7b` (refactor)
2. **Task 2: Migrate WidgetDataBinderTest to Turbine + StandardTestDispatcher** - `9413496` (refactor)

## Files Created/Modified
- `android/feature/dashboard/src/test/kotlin/app/dqxn/android/feature/dashboard/coordinator/WidgetBindingCoordinatorTest.kt` - Replaced UnconfinedTestDispatcher with StandardTestDispatcher, child Job scoping, testScheduler.runCurrent(), coordinator.destroy() cleanup
- `android/feature/dashboard/src/test/kotlin/app/dqxn/android/feature/dashboard/binding/WidgetDataBinderTest.kt` - Replaced launch(UnconfinedTestDispatcher()) flow collection with Turbine test{} blocks and awaitItem()

## Decisions Made

1. **testScheduler.runCurrent() over advanceUntilIdle()** -- The staleness watchdog in WidgetBindingCoordinator runs `while(true) { delay(stalenessThresholdMs) }`. With StandardTestDispatcher, `advanceUntilIdle()` enters an infinite loop advancing through repeated delays. `runCurrent()` only dispatches coroutines ready at the current virtual time, avoiding the infinite loop.

2. **coordinator.destroy() required in every test** -- The coordinator's `bindingSupervisor = SupervisorJob()` is standalone (not a child of the test Job). When `runTest` exits, its cleanup calls `advanceUntilIdle()` which hits the infinite staleness watchdog. Calling `coordinator.destroy()` cancels the `bindingSupervisor` and all child coroutines before `runTest` cleanup.

3. **Child Job pattern replaces backgroundScope** -- `val initJob = Job(coroutineContext[Job]); val initScope = this + initJob` creates a scope whose coroutines are driven by the test's scheduler, unlike `backgroundScope` which doesn't advance with StandardTestDispatcher's `advanceUntilIdle()`.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed test hang caused by advanceUntilIdle() + infinite staleness watchdog**
- **Found during:** Task 1
- **Issue:** Plan specified `advanceUntilIdle()` after bind operations. The staleness watchdog (`while(true) { delay() }`) causes `advanceUntilIdle()` to loop infinitely with StandardTestDispatcher.
- **Fix:** Replaced all `advanceUntilIdle()` with `testScheduler.runCurrent()` which only dispatches currently-queued coroutines without advancing virtual time.
- **Files modified:** WidgetBindingCoordinatorTest.kt
- **Verification:** All 7 tests complete in <1s total
- **Committed in:** 879bf7b

**2. [Rule 1 - Bug] Added coordinator.destroy() cleanup to prevent runTest hang**
- **Found during:** Task 1
- **Issue:** Even with `runCurrent()` in the test body, `runTest`'s internal cleanup calls `advanceUntilIdle()` which hits the staleness watchdog. The standalone `SupervisorJob` is not cancelled by `initJob.cancel()`.
- **Fix:** Added `coordinator.destroy()` before `initJob.cancel()` in all 7 tests to cancel the `bindingSupervisor` and all child coroutines.
- **Files modified:** WidgetBindingCoordinatorTest.kt
- **Verification:** All 7 tests complete without timeout
- **Committed in:** 879bf7b

---

**Total deviations:** 2 auto-fixed (2 bugs)
**Impact on plan:** Both fixes necessary for correct StandardTestDispatcher migration. No scope creep.

## Issues Encountered
- Initial migration attempt with `advanceUntilIdle()` caused test hang due to infinite staleness watchdog loop. Root cause identified and fixed with `runCurrent()` + `destroy()` cleanup pattern. This is the fundamental reason the original author used `UnconfinedTestDispatcher` -- they encountered this same issue and chose the wrong workaround.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- CLAUDE.md compliance restored for all WidgetBindingCoordinator and WidgetDataBinder tests
- Pattern established for future coordinator tests: always use `testScheduler.runCurrent()` + `coordinator.destroy()` with StandardTestDispatcher
- Ready to proceed with remaining gap closure plans (07-11, 07-12) or Phase 8

---
*Phase: 07-dashboard-shell*
*Completed: 2026-02-24*
