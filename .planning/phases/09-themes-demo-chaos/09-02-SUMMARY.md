---
phase: 09-themes-demo-chaos
plan: 02
subsystem: pack
tags: [demo, providers, snapshots, deterministic, simulated]

# Dependency graph
requires:
  - phase: 08-essentials-pack
    provides: "8 snapshot types in :pack:essentials:snapshots, DataProviderContractTest in :sdk:contracts testFixtures"
provides:
  - "SolarSnapshot + SpeedLimitSnapshot relocated to cross-boundary :pack:essentials:snapshots"
  - "4 deterministic demo providers (Time, Speed, Orientation, Battery)"
  - "Demo pack build config with :pack:essentials:snapshots dependency"
affects: [09-03, 09-04, 09-05, 09-06]

# Tech tracking
tech-stack:
  added: []
  patterns: ["tick-based deterministic provider pattern (zero randomness)", "triangle wave speed simulation", "sawtooth battery drain simulation"]

key-files:
  created:
    - android/pack/demo/src/main/kotlin/app/dqxn/android/pack/demo/providers/DemoTimeProvider.kt
    - android/pack/demo/src/main/kotlin/app/dqxn/android/pack/demo/providers/DemoSpeedProvider.kt
    - android/pack/demo/src/main/kotlin/app/dqxn/android/pack/demo/providers/DemoOrientationProvider.kt
    - android/pack/demo/src/main/kotlin/app/dqxn/android/pack/demo/providers/DemoBatteryProvider.kt
    - android/pack/demo/src/test/kotlin/app/dqxn/android/pack/demo/providers/DemoTimeProviderContractTest.kt
    - android/pack/demo/src/test/kotlin/app/dqxn/android/pack/demo/providers/DemoSpeedProviderContractTest.kt
    - android/pack/demo/src/test/kotlin/app/dqxn/android/pack/demo/providers/DemoOrientationProviderContractTest.kt
    - android/pack/demo/src/test/kotlin/app/dqxn/android/pack/demo/providers/DemoBatteryProviderContractTest.kt
  modified:
    - android/pack/essentials/snapshots/src/main/kotlin/app/dqxn/android/pack/essentials/snapshots/SolarSnapshot.kt
    - android/pack/essentials/snapshots/src/main/kotlin/app/dqxn/android/pack/essentials/snapshots/SpeedLimitSnapshot.kt
    - android/pack/demo/build.gradle.kts

key-decisions:
  - "Same package preserved during snapshot relocation -- no import changes needed in essentials pack consumers"

patterns-established:
  - "Demo provider pattern: tick-based deterministic math, ProviderPriority.SIMULATED, zero randomness, no constructor deps"

requirements-completed: [F13.1]

# Metrics
duration: 4min
completed: 2026-02-25
---

# Phase 9 Plan 02: Snapshot Relocation + Demo Providers Summary

**Relocated SolarSnapshot/SpeedLimitSnapshot to cross-boundary snapshots module and created 4 deterministic demo providers (Time, Speed, Orientation, Battery) with 48 passing contract tests**

## Performance

- **Duration:** 4 min
- **Started:** 2026-02-25T01:51:12Z
- **Completed:** 2026-02-25T01:55:56Z
- **Tasks:** 3
- **Files modified:** 11

## Accomplishments
- Relocated SolarSnapshot and SpeedLimitSnapshot from pack-local to cross-boundary :pack:essentials:snapshots, enabling demo pack (and future packs) to import all 8 snapshot types
- Created 4 deterministic demo providers with tick-based math: DemoTimeProvider (1s fixed epoch), DemoSpeedProvider (200ms triangle wave 0-140 km/h), DemoOrientationProvider (500ms 5deg/tick rotation), DemoBatteryProvider (5s sawtooth drain 100->1%)
- All 4 providers pass DataProviderContractTest (12 assertions each = 48 total, 0 failures)

## Task Commits

Each task was committed atomically:

1. **Task 1: Relocate SolarSnapshot + SpeedLimitSnapshot** - `966da61` (refactor)
2. **Task 2: Create demo pack build config + 4 providers** - `eb7b85d` (feat)
3. **Task 3: Contract tests for 4 demo providers** - `5518ef1` (test)

## Files Created/Modified
- `android/pack/essentials/snapshots/.../SolarSnapshot.kt` - Relocated from pack-local to cross-boundary
- `android/pack/essentials/snapshots/.../SpeedLimitSnapshot.kt` - Relocated from pack-local to cross-boundary
- `android/pack/demo/build.gradle.kts` - Added :pack:essentials:snapshots dep + testFixtures
- `android/pack/demo/.../DemoTimeProvider.kt` - Deterministic time (1s tick, fixed epoch + zone)
- `android/pack/demo/.../DemoSpeedProvider.kt` - Triangle wave speed (200ms, 0-140 km/h)
- `android/pack/demo/.../DemoOrientationProvider.kt` - Slow heading rotation (500ms, 5deg/tick)
- `android/pack/demo/.../DemoBatteryProvider.kt` - Sawtooth drain (5s, 100->1%, 28.5C)
- `android/pack/demo/.../DemoTimeProviderContractTest.kt` - 12/12 contract tests pass
- `android/pack/demo/.../DemoSpeedProviderContractTest.kt` - 12/12 contract tests pass
- `android/pack/demo/.../DemoOrientationProviderContractTest.kt` - 12/12 contract tests pass
- `android/pack/demo/.../DemoBatteryProviderContractTest.kt` - 12/12 contract tests pass

## Decisions Made
- Same package preserved during snapshot relocation (app.dqxn.android.pack.essentials.snapshots) -- no import changes needed in any essentials pack consumer

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- All 8 snapshot types now available via :pack:essentials:snapshots for any pack
- Demo pack pattern established for remaining 4 providers (Plans 03-04)
- Demo pack build config includes testFixtures for contract testing

## Self-Check: PASSED

All 11 files verified present. All 3 task commits verified (966da61, eb7b85d, 5518ef1). Old pack-local snapshot files confirmed deleted. 48/48 contract tests pass.

---
*Phase: 09-themes-demo-chaos*
*Completed: 2026-02-25*
