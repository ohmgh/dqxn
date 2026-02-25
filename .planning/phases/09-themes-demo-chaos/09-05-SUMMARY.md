---
phase: 09-themes-demo-chaos
plan: 05
subsystem: testing
tags: [chaos-testing, fault-injection, agentic-handlers, deterministic-testing, debug-tooling]

# Dependency graph
requires:
  - phase: 09-themes-demo-chaos
    provides: ChaosProviderInterceptor, ChaosProfile (7 profiles), ChaosSession, ScheduledFault
  - phase: 06-app-shell-agentic
    provides: CommandHandler, CommandParams, CommandResult, AgenticCommand, DebugModule
affects: [09-06-chaos-correlation]

provides:
  - ChaosEngine seed-based deterministic fault orchestration
  - ChaosStartHandler agentic command for starting chaos sessions
  - ChaosStopHandler agentic command for stopping sessions with injection summary
  - ChaosInjectHandler agentic command for single-fault injection
  - DebugModule @Binds @IntoSet ChaosProviderInterceptor as DataProviderInterceptor

# Tech tracking
tech-stack:
  added: []
  patterns: [delta-computed delays from absolute ScheduledFault timestamps, error-code-based CommandResult.Error for handler error categorization]

key-files:
  created:
    - android/core/agentic/src/main/kotlin/app/dqxn/android/core/agentic/chaos/ChaosEngine.kt
    - android/app/src/debug/kotlin/app/dqxn/android/debug/handlers/ChaosStartHandler.kt
    - android/app/src/debug/kotlin/app/dqxn/android/debug/handlers/ChaosStopHandler.kt
    - android/app/src/debug/kotlin/app/dqxn/android/debug/handlers/ChaosInjectHandler.kt
    - android/core/agentic/src/test/kotlin/app/dqxn/android/core/agentic/chaos/ChaosEngineTest.kt
    - android/app/src/test/kotlin/app/dqxn/android/debug/handlers/ChaosStartHandlerTest.kt
    - android/app/src/test/kotlin/app/dqxn/android/debug/handlers/ChaosStopHandlerTest.kt
    - android/app/src/test/kotlin/app/dqxn/android/debug/handlers/ChaosInjectHandlerTest.kt
  modified:
    - android/app/src/debug/kotlin/app/dqxn/android/debug/DebugModule.kt

key-decisions:
  - "Delta-computed delays from absolute ScheduledFault.delayMs timestamps -- plan's delayMs is cumulative offset from session start, engine computes inter-fault delta"

patterns-established:
  - "Error-code-based handler errors: ALREADY_ACTIVE, NO_SESSION, MISSING_PARAM, UNKNOWN_FAULT, INVALID_PROFILE for machine-readable error routing"

requirements-completed: [F8.5, F13.1]

# Metrics
duration: 5min
completed: 2026-02-25
---

# Phase 09 Plan 05: ChaosEngine + Agentic Handlers Summary

**ChaosEngine deterministic fault orchestration with seed-based Random and 3 ADB-driven agentic handlers (chaos-start, chaos-stop, chaos-inject) wired via DebugModule**

## Performance

- **Duration:** 5 min
- **Started:** 2026-02-25T02:03:02Z
- **Completed:** 2026-02-25T02:08:05Z
- **Tasks:** 2
- **Files modified:** 9

## Accomplishments
- ChaosEngine orchestrates deterministic fault sessions: same seed + same profile produces identical fault sequences across runs (SC4 verified)
- 3 agentic handlers enable ADB-driven chaos testing: chaos-start (session with seed/profile), chaos-stop (summary with all injected faults), chaos-inject (single fault into specific provider)
- ChaosProviderInterceptor registered as DataProviderInterceptor in debug builds via DebugModule @Binds @IntoSet
- 27 total tests: 12 ChaosEngine tests (determinism, profiles, lifecycle, errors) + 15 handler tests (success/error/edge cases)

## Task Commits

Each task was committed atomically:

1. **Task 1: ChaosEngine + determinism tests** - `afe55cc` (feat)
2. **Task 2: Chaos agentic handlers + DebugModule wiring + handler tests** - `aae7676` (feat)

## Files Created/Modified
- `android/core/agentic/src/main/kotlin/app/dqxn/android/core/agentic/chaos/ChaosEngine.kt` - Seed-based deterministic fault orchestration with single-session guard
- `android/app/src/debug/kotlin/app/dqxn/android/debug/handlers/ChaosStartHandler.kt` - Starts chaos session with seed/profile params, returns session info JSON
- `android/app/src/debug/kotlin/app/dqxn/android/debug/handlers/ChaosStopHandler.kt` - Stops session, returns injection summary with all fault details
- `android/app/src/debug/kotlin/app/dqxn/android/debug/handlers/ChaosInjectHandler.kt` - Injects single fault (kill/stall/delay/error/flap) into specific provider
- `android/app/src/debug/kotlin/app/dqxn/android/debug/DebugModule.kt` - Added @Binds @IntoSet for ChaosProviderInterceptor as DataProviderInterceptor
- `android/core/agentic/src/test/kotlin/app/dqxn/android/core/agentic/chaos/ChaosEngineTest.kt` - 12 tests covering determinism, all profiles, lifecycle, error cases
- `android/app/src/test/kotlin/app/dqxn/android/debug/handlers/ChaosStartHandlerTest.kt` - 5 tests: default params, explicit seed/profile, session info, already-active error
- `android/app/src/test/kotlin/app/dqxn/android/debug/handlers/ChaosStopHandlerTest.kt` - 4 tests: stop/summary, no-session error, name/category, alias
- `android/app/src/test/kotlin/app/dqxn/android/debug/handlers/ChaosInjectHandlerTest.kt` - 10 tests: all 5 fault types, missing params, unknown fault, aliases

## Decisions Made
- Delta-computed delays from absolute ScheduledFault.delayMs timestamps -- the profile's delayMs is a cumulative offset from session start (e.g., fault 1 at 1000ms, fault 2 at 2500ms), so the engine computes inter-fault deltas (1000ms, then 1500ms) rather than treating each delayMs as an independent wait

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed ChaosEngine delay computation from cumulative to delta**
- **Found during:** Task 1
- **Issue:** ScheduledFault.delayMs represents absolute offset from session start (cumulative), but the engine loop treated each as an independent delay, causing faults to be injected much later than intended (sum of cumulative delays instead of individual offsets)
- **Fix:** Compute delta between consecutive fault delays: `val delta = scheduledFault.delayMs - previousDelayMs`
- **Files modified:** android/core/agentic/src/main/kotlin/app/dqxn/android/core/agentic/chaos/ChaosEngine.kt
- **Verification:** All 12 ChaosEngine tests pass, including `session summary includes all injected faults` (10 faults within 30s advance)
- **Committed in:** afe55cc (Task 1 commit)

---

**Total deviations:** 1 auto-fixed (1 bug)
**Impact on plan:** Essential correctness fix for fault timing. No scope creep.

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- ChaosEngine and all 3 agentic handlers ready for chaos correlation tests (Plan 06)
- ChaosProviderInterceptor wired into WidgetDataBinder's interceptor set in debug builds
- All fault types (kill, stall, delay, error, flap) injectable via ADB commands

## Self-Check: PASSED

All 9 created/modified files verified present. Both task commits (afe55cc, aae7676) confirmed in git log.

---
*Phase: 09-themes-demo-chaos*
*Completed: 2026-02-25*
