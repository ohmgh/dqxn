---
phase: 08-essentials-pack
plan: 11
subsystem: dashboard
tags: [command-bus, shared-flow, agentic, widget-placement, hilt-singleton]

# Dependency graph
requires:
  - phase: 07-dashboard-shell
    provides: DashboardViewModel with command channel, DashboardCommand sealed interface
  - phase: 06-deployable-app
    provides: AgenticContentProvider, AddWidgetHandler, CommandHandler infrastructure
provides:
  - DashboardCommandBus @Singleton relay between SingletonComponent and ViewModelRetainedComponent
  - AddWidgetHandler canvas placement via DashboardCommand.AddWidget dispatch
  - End-to-end agentic add-widget pipeline (typeId validation -> widget construction -> bus dispatch -> command channel -> LayoutCoordinator)
affects: [09-themes-demo, agentic-testing, on-device-verification]

# Tech tracking
tech-stack:
  added: []
  patterns: [singleton-scoped-command-bus, shared-flow-bridge-across-hilt-components]

key-files:
  created:
    - android/feature/dashboard/src/main/kotlin/app/dqxn/android/feature/dashboard/command/DashboardCommandBus.kt
    - android/feature/dashboard/src/test/kotlin/app/dqxn/android/feature/dashboard/command/DashboardCommandBusTest.kt
  modified:
    - android/feature/dashboard/src/main/kotlin/app/dqxn/android/feature/dashboard/DashboardViewModel.kt
    - android/feature/dashboard/src/test/kotlin/app/dqxn/android/feature/dashboard/DashboardViewModelTest.kt
    - android/app/src/debug/kotlin/app/dqxn/android/debug/handlers/AddWidgetHandler.kt
    - android/app/src/test/kotlin/app/dqxn/android/debug/handlers/AddWidgetHandlerTest.kt

key-decisions:
  - "advanceUntilIdle() before bus.dispatch() in ViewModel test -- StandardTestDispatcher requires explicit advancement for init coroutines to start collecting before emission"

patterns-established:
  - "Singleton SharedFlow bus for cross-Hilt-component command relay: @Singleton DashboardCommandBus bridges SingletonComponent callers to ViewModelRetainedComponent consumers"

requirements-completed: [F5.1, F5.2, F5.3, F5.4, F5.5, F5.6, F5.7, F5.8, F5.9, F5.10, F5.11, NF14, NF40, NF-I2, NF-P1]

# Metrics
duration: 5min
completed: 2026-02-25
---

# Phase 08 Plan 11: DashboardCommandBus + AddWidgetHandler SC3 Fix Summary

**Singleton DashboardCommandBus bridging agentic AddWidgetHandler to DashboardViewModel command channel, enabling on-device add-widget to place widgets on canvas**

## Performance

- **Duration:** 5 min
- **Started:** 2026-02-25T00:39:01Z
- **Completed:** 2026-02-25T00:44:50Z
- **Tasks:** 2
- **Files modified:** 6

## Accomplishments
- DashboardCommandBus created as @Singleton with MutableSharedFlow (capacity 64, DROP_OLDEST, no replay) bridging SingletonComponent handlers to ViewModelRetainedComponent
- DashboardViewModel wired to collect from bus and funnel commands into existing sequential commandChannel processing loop
- AddWidgetHandler now constructs DashboardWidgetInstance from renderer defaults and dispatches DashboardCommand.AddWidget through the bus
- 9 new tests across 3 test files: 4 bus tests (emit/collect, multi-subscriber, no-replay, buffer-overflow), 1 ViewModel bus relay test, 3 handler dispatch tests (success, unknown typeId, missing typeId)

## Task Commits

Each task was committed atomically:

1. **Task 1: Create DashboardCommandBus and wire into DashboardViewModel** - `33b9aa3` (feat)
2. **Task 2: Update AddWidgetHandler to construct DashboardWidgetInstance and dispatch via bus** - `11535bc` (feat)

## Files Created/Modified
- `android/feature/dashboard/src/main/kotlin/app/dqxn/android/feature/dashboard/command/DashboardCommandBus.kt` - @Singleton SharedFlow relay with dispatch() and commands flow
- `android/feature/dashboard/src/main/kotlin/app/dqxn/android/feature/dashboard/DashboardViewModel.kt` - Added commandBus constructor param and init block collector
- `android/feature/dashboard/src/test/kotlin/app/dqxn/android/feature/dashboard/command/DashboardCommandBusTest.kt` - 4 tests for bus behavior
- `android/feature/dashboard/src/test/kotlin/app/dqxn/android/feature/dashboard/DashboardViewModelTest.kt` - Added bus relay test and updated createViewModel
- `android/app/src/debug/kotlin/app/dqxn/android/debug/handlers/AddWidgetHandler.kt` - Added bus injection, widget construction, and dispatch
- `android/app/src/test/kotlin/app/dqxn/android/debug/handlers/AddWidgetHandlerTest.kt` - Added bus dispatch verification tests

## Decisions Made
- advanceUntilIdle() before bus.dispatch() in ViewModel bus relay test: StandardTestDispatcher does not eagerly execute coroutines launched in init; the bus collector must be running before emission or commands are lost (no replay on SharedFlow)
- Real DashboardCommandBus instance (not mock) in both ViewModel and handler tests: the class is a stateless no-arg concrete class, mocking adds complexity without value

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed Turbine import path**
- **Found during:** Task 1 (DashboardCommandBusTest)
- **Issue:** Plan specified `app.turbine.test` import but correct package is `app.cash.turbine.test`
- **Fix:** Changed import to `app.cash.turbine.test`
- **Files modified:** DashboardCommandBusTest.kt
- **Verification:** Compilation successful, all 4 bus tests pass
- **Committed in:** 33b9aa3 (Task 1 commit)

**2. [Rule 1 - Bug] Fixed bus relay test timing**
- **Found during:** Task 1 (DashboardViewModelTest bus relay test)
- **Issue:** Bus dispatch before advanceUntilIdle() meant ViewModel's bus collector coroutine hadn't started yet; SharedFlow emission was lost
- **Fix:** Added advanceUntilIdle() after ViewModel construction (before bus dispatch) to let init coroutines start collecting
- **Files modified:** DashboardViewModelTest.kt
- **Verification:** Bus relay test passes, enterEditMode verified on coordinator
- **Committed in:** 33b9aa3 (Task 1 commit)

---

**Total deviations:** 2 auto-fixed (2 bugs)
**Impact on plan:** Both fixes necessary for test correctness. No scope creep.

## Issues Encountered
- Pre-existing `:feature:diagnostics:testDebugUnitTest` failure (no test sources but failOnNoDiscoveredTests=true) -- unrelated to this plan, excluded from regression gate

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- SC3 from 08-VERIFICATION.md is now structurally wired: add-widget agentic command constructs widgets and dispatches through bus to ViewModel
- On-device verification (add-widget + dump-health showing ACTIVE) requires a connected device
- Phase 8 gap closure complete, ready for Phase 9 (Themes, Demo + Chaos)

---
*Phase: 08-essentials-pack*
*Completed: 2026-02-25*

## Self-Check: PASSED
- DashboardCommandBus.kt: FOUND
- DashboardCommandBusTest.kt: FOUND
- Commit 33b9aa3: FOUND
- Commit 11535bc: FOUND
