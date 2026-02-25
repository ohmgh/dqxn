---
phase: 09-themes-demo-chaos
plan: 03
subsystem: pack
tags: [demo, providers, deterministic, simulated, ambient-light, acceleration, solar, speed-limit]

# Dependency graph
requires:
  - phase: 09-themes-demo-chaos
    provides: "4 demo providers (Time, Speed, Orientation, Battery) + relocated SolarSnapshot/SpeedLimitSnapshot"
provides:
  - "4 additional deterministic demo providers (AmbientLight, Acceleration, Solar, SpeedLimit)"
  - "Full 8-provider demo pack with 96 contract assertions + 11 determinism tests"
affects: [09-04, 09-05, 09-06]

# Tech tracking
tech-stack:
  added: []
  patterns: ["sine wave ambient light simulation (day/night cycle)", "square wave acceleration simulation", "fixed solar data pattern", "cycling speed limit pattern"]

key-files:
  created:
    - android/pack/demo/src/main/kotlin/app/dqxn/android/pack/demo/providers/DemoAmbientLightProvider.kt
    - android/pack/demo/src/main/kotlin/app/dqxn/android/pack/demo/providers/DemoAccelerationProvider.kt
    - android/pack/demo/src/main/kotlin/app/dqxn/android/pack/demo/providers/DemoSolarProvider.kt
    - android/pack/demo/src/main/kotlin/app/dqxn/android/pack/demo/providers/DemoSpeedLimitProvider.kt
    - android/pack/demo/src/test/kotlin/app/dqxn/android/pack/demo/providers/DemoAmbientLightProviderContractTest.kt
    - android/pack/demo/src/test/kotlin/app/dqxn/android/pack/demo/providers/DemoAccelerationProviderContractTest.kt
    - android/pack/demo/src/test/kotlin/app/dqxn/android/pack/demo/providers/DemoSolarProviderContractTest.kt
    - android/pack/demo/src/test/kotlin/app/dqxn/android/pack/demo/providers/DemoSpeedLimitProviderContractTest.kt
    - android/pack/demo/src/test/kotlin/app/dqxn/android/pack/demo/providers/DemoDeterminismTest.kt
  modified: []

key-decisions:
  - "AccelerationSnapshot uses acceleration/lateralAcceleration fields (not longitudinalG/lateralG) -- matched actual snapshot schema"
  - "Solar noon offset corrected to 46_320_000ms (12:52 SGT) -- 12h52m = 46320s"

patterns-established:
  - "Determinism test pattern: collect N emissions from two fresh instances, strip timestamps, assert equality"
  - "Known-value assertion pattern: compute expected value from companion constants, assert at specific tick indices"

requirements-completed: [F13.1]

# Metrics
duration: 4min
completed: 2026-02-25
---

# Phase 9 Plan 03: Remaining Demo Providers + Determinism Tests Summary

**Created 4 additional demo providers (AmbientLight, Acceleration, Solar, SpeedLimit) completing all 8 data types, with 48 new contract assertions and 11 determinism tests proving tick-reproducibility**

## Performance

- **Duration:** 4 min
- **Started:** 2026-02-25T02:02:42Z
- **Completed:** 2026-02-25T02:06:37Z
- **Tasks:** 3
- **Files modified:** 9

## Accomplishments
- Created 4 deterministic demo providers: DemoAmbientLightProvider (sine wave day/night lux, 2s tick), DemoAccelerationProvider (lateral sine + longitudinal square wave, 200ms tick), DemoSolarProvider (fixed Singapore coordinates, 30s tick), DemoSpeedLimitProvider (cycling 50/60/80/90/110 km/h, 10s tick)
- All 8 demo providers pass DataProviderContractTest (12 assertions each = 96 total, 0 failures)
- Determinism verified: 8 same-instance reproducibility tests + 3 known-value assertion tests (11 total, all passing)

## Task Commits

Each task was committed atomically:

1. **Task 1: Create remaining 4 demo providers** - `070a441` (feat)
2. **Task 2: Contract tests for remaining 4 providers** - `7525cc5` (test)
3. **Task 3: Determinism verification for all 8 providers** - `42eaa16` (test)

## Files Created/Modified
- `android/pack/demo/.../DemoAmbientLightProvider.kt` - Sine wave lux 1-999 lx, 120s full day/night cycle
- `android/pack/demo/.../DemoAccelerationProvider.kt` - Lateral sine 0.3G amplitude + longitudinal 0.15G square wave
- `android/pack/demo/.../DemoSolarProvider.kt` - Fixed Singapore sunrise 06:45 / sunset 19:00 SGT
- `android/pack/demo/.../DemoSpeedLimitProvider.kt` - Cycling 50/60/80/90/110 km/h limits
- `android/pack/demo/.../DemoAmbientLightProviderContractTest.kt` - 12/12 contract tests pass
- `android/pack/demo/.../DemoAccelerationProviderContractTest.kt` - 12/12 contract tests pass
- `android/pack/demo/.../DemoSolarProviderContractTest.kt` - 12/12 contract tests pass
- `android/pack/demo/.../DemoSpeedLimitProviderContractTest.kt` - 12/12 contract tests pass
- `android/pack/demo/.../DemoDeterminismTest.kt` - 11 determinism + known-value tests

## Decisions Made
- AccelerationSnapshot uses `acceleration`/`lateralAcceleration` fields per actual snapshot schema (plan referenced `longitudinalG`/`lateralG`)
- Solar noon offset corrected to 46_320_000ms (12h52m in ms) from plan's 46_350_000ms (12h52m30s)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed DemoSpeedProvider known-value assertion at tick 24**
- **Found during:** Task 3 (determinism tests)
- **Issue:** Test asserted tick 24 = 0 m/s, but triangle wave peaks at tick 24 (descending ramp starts at peak, not trough)
- **Fix:** Changed assertion to expect peak speed (140 km/h = ~38.89 m/s) at tick 24
- **Files modified:** DemoDeterminismTest.kt
- **Committed in:** 42eaa16

---

**Total deviations:** 1 auto-fixed (1 bug in test assertion)
**Impact on plan:** Test logic fix, no scope change.

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- All 8 demo data types covered with deterministic providers
- Demo pack complete: F13.1 fully satisfied
- Ready for Plan 04 (ChaosEngine fault injection) and Plan 05 (ChaosEngine orchestration)

## Self-Check: PASSED

All 9 files verified present. All 3 task commits verified (070a441, 7525cc5, 42eaa16). 107 total tests pass (96 contract + 11 determinism).

---
*Phase: 09-themes-demo-chaos*
*Completed: 2026-02-25*
