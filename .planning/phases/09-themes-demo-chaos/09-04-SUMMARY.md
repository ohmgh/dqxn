---
phase: 09-themes-demo-chaos
plan: 04
subsystem: testing
tags: [chaos-testing, provider-interceptor, entitlement-simulation, debug-tooling]

# Dependency graph
requires:
  - phase: 02-sdk-contracts
    provides: DataProviderInterceptor, ProviderFault, DataProvider, DataSnapshot, EntitlementManager
provides:
  - ChaosProviderInterceptor applying all 7 ProviderFault types
  - 7 ChaosProfile definitions for deterministic fault plan generation
  - ChaosSession with injection tracking and summary generation
  - StubEntitlementManager programmatic simulation (simulateRevocation/simulateGrant/reset)
affects: [09-05-chaos-engine, 09-06-chaos-handlers]

# Tech tracking
tech-stack:
  added: []
  patterns: [ConcurrentHashMap fault registry, AtomicBoolean time-window gating, backgroundScope flow testing]

key-files:
  created:
    - android/core/agentic/src/main/kotlin/app/dqxn/android/core/agentic/chaos/ChaosProviderInterceptor.kt
    - android/core/agentic/src/main/kotlin/app/dqxn/android/core/agentic/chaos/ChaosProfile.kt
    - android/core/agentic/src/main/kotlin/app/dqxn/android/core/agentic/chaos/ChaosSession.kt
    - android/core/agentic/src/test/kotlin/app/dqxn/android/core/agentic/chaos/ChaosProviderInterceptorTest.kt
  modified:
    - android/core/agentic/build.gradle.kts
    - android/app/src/main/kotlin/app/dqxn/android/StubEntitlementManager.kt
    - android/app/src/test/kotlin/app/dqxn/android/StubEntitlementManagerTest.kt

key-decisions:
  - "Added :sdk:contracts dependency to :core:agentic for DataProviderInterceptor/ProviderFault access"
  - "backgroundScope + mutableListOf collector pattern for Delay/Flap/Stall flow tests instead of Turbine -- Turbine timeout conflicts with StandardTestDispatcher virtual time"
  - "AtomicBoolean time-window gating for Flap fault -- coroutineScope launch toggles on/off, upstream collect checks AtomicBoolean"

patterns-established:
  - "backgroundScope flow collection pattern: launch in backgroundScope, advanceTimeBy/runCurrent, assert collected list -- avoids Turbine timeout issues with virtual time delays"

requirements-completed: [F8.5]

# Metrics
duration: 7min
completed: 2026-02-25
---

# Phase 09 Plan 04: Chaos Infrastructure + Entitlement Simulation Summary

**ChaosProviderInterceptor with all 7 ProviderFault types, 7 chaos profiles, session tracking, and StubEntitlementManager programmatic entitlement simulation**

## Performance

- **Duration:** 7 min
- **Started:** 2026-02-25T01:51:46Z
- **Completed:** 2026-02-25T01:59:20Z
- **Tasks:** 2
- **Files modified:** 7

## Accomplishments
- ChaosProviderInterceptor intercepts provider flows and applies Kill, Delay, Error, ErrorOnNext, Corrupt, Flap, and Stall fault transformations
- 7 chaos profiles (ProviderStress, ProviderFlap, ThermalRamp, EntitlementChurn, WidgetStorm, ProcessDeath, Combined) generate deterministic fault plans from seed
- ChaosSession tracks injection history with summary generation for post-mortem analysis
- StubEntitlementManager extended with simulateRevocation/simulateGrant/reset backed by MutableStateFlow for flow-based emission

## Task Commits

Each task was committed atomically:

1. **Task 1: ChaosProviderInterceptor + ChaosProfile + ChaosSession** - `b46367b` (feat)
2. **Task 2: Extend StubEntitlementManager + tests** - `b72c586` (feat)

## Files Created/Modified
- `android/core/agentic/src/main/kotlin/app/dqxn/android/core/agentic/chaos/ChaosProviderInterceptor.kt` - DataProviderInterceptor implementation with all 7 fault types
- `android/core/agentic/src/main/kotlin/app/dqxn/android/core/agentic/chaos/ChaosProfile.kt` - 7 sealed interface profiles with deterministic fault plan generation
- `android/core/agentic/src/main/kotlin/app/dqxn/android/core/agentic/chaos/ChaosSession.kt` - Session state tracking with injection log and summary
- `android/core/agentic/src/test/kotlin/app/dqxn/android/core/agentic/chaos/ChaosProviderInterceptorTest.kt` - 11 tests covering all fault types
- `android/core/agentic/build.gradle.kts` - Added :sdk:contracts dependency
- `android/app/src/main/kotlin/app/dqxn/android/StubEntitlementManager.kt` - MutableStateFlow-backed entitlements with simulation methods
- `android/app/src/test/kotlin/app/dqxn/android/StubEntitlementManagerTest.kt` - Expanded from 5 to 11 tests with flow emission verification

## Decisions Made
- Added :sdk:contracts to :core:agentic deps -- ChaosProviderInterceptor needs DataProviderInterceptor, DataProvider, DataSnapshot, and ProviderFault interfaces
- backgroundScope + mutableListOf collector pattern for virtual-time-dependent flow tests -- Turbine's internal timeout conflicts with StandardTestDispatcher advanceTimeBy
- AtomicBoolean time-window gating for Flap fault implementation -- coroutineScope launches toggling coroutine alongside upstream collection

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Added :sdk:contracts dependency to :core:agentic**
- **Found during:** Task 1
- **Issue:** ChaosProviderInterceptor needs DataProviderInterceptor, DataProvider, DataSnapshot from :sdk:contracts but :core:agentic didn't have that dependency
- **Fix:** Added `implementation(project(":sdk:contracts"))` to build.gradle.kts
- **Files modified:** android/core/agentic/build.gradle.kts
- **Verification:** Compilation succeeds
- **Committed in:** b46367b (Task 1 commit)

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** Necessary dependency addition for compilation. No scope creep.

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- ChaosProviderInterceptor and profiles ready for ChaosEngine (Plan 05) to orchestrate fault injection sessions
- StubEntitlementManager.simulateRevocation/simulateGrant ready for agentic "Simulate Free User" handler

## Self-Check: PASSED

All 6 created/modified files verified present. Both task commits (b46367b, b72c586) confirmed in git log.

---
*Phase: 09-themes-demo-chaos*
*Completed: 2026-02-25*
