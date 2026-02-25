---
phase: 13-e2e-integration-launch-polish
plan: 07
subsystem: testing
tags: [battery-soak, sensor-lifecycle, callbackFlow, dumpsys, uiautomator, nf11, nf37]

# Dependency graph
requires:
  - phase: 13-05
    provides: AgenticTestClient for instrumented E2E tests
  - phase: 08
    provides: callbackFlow-based sensor providers in essentials pack
provides:
  - BatterySoakTest: 30-min screen-on battery drain measurement with 12 widgets (NF11)
  - BackgroundBatterySoakTest: 15-min background drain measurement + sensor unregistration verification (NF37)
  - SensorUnregistrationTest: source-code analysis verifying all 8 callbackFlow providers have proper awaitClose
affects: [ci-pipeline, battery-optimization, sensor-lifecycle]

# Tech tracking
tech-stack:
  added: [uiautomator (androidTest)]
  patterns: [source-code analysis tests, dumpsys battery level delta measurement, callbackFlow lifecycle verification]

key-files:
  created:
    - android/app/src/test/kotlin/app/dqxn/android/app/performance/SensorUnregistrationTest.kt
    - android/app/src/androidTest/kotlin/app/dqxn/android/e2e/BatterySoakTest.kt
    - android/app/src/androidTest/kotlin/app/dqxn/android/e2e/BackgroundBatterySoakTest.kt
  modified:
    - android/app/build.gradle.kts

key-decisions:
  - "All 8 callbackFlow providers verified (not just 5 from plan) -- plan referenced non-existent file names; actual provider names include DataProvider suffix"
  - "UiAutomator added as androidTestImplementation for UiDevice.pressHome() and executeShellCommand()"
  - "Clock typeId is essentials:clock (not essentials:clock-digital) per actual @DashboardWidget annotation"

patterns-established:
  - "Source-code analysis tests: JUnit5 tests that read .kt source files and verify structural patterns (callbackFlow/awaitClose pairing)"
  - "Battery soak pattern: dumpsys battery level delta over timed window, extrapolated to hourly rate"

requirements-completed: [NF11, NF37]

# Metrics
duration: 3min
completed: 2026-02-25
---

# Phase 13 Plan 07: Battery Soak & Sensor Lifecycle Summary

**Battery drain measurement infrastructure (30-min soak + background soak via dumpsys) and source-code analysis test verifying all 8 callbackFlow providers have correct awaitClose unregistration**

## Performance

- **Duration:** 3 min
- **Started:** 2026-02-25T12:09:01Z
- **Completed:** 2026-02-25T12:12:47Z
- **Tasks:** 2
- **Files modified:** 4

## Accomplishments
- SensorUnregistrationTest verifies all 8 callbackFlow providers (GpsSpeed, Accelerometer, AmbientLight, Orientation, Battery, SolarLocation, SolarTimezone, Time) have matching awaitClose with sensor unregistration calls
- BatterySoakTest adds 12 widgets via agentic commands, soaks 30 minutes, measures battery delta, asserts < 5%/hr (NF11)
- BackgroundBatterySoakTest backgrounds app, soaks 15 minutes, asserts < 1%/hr (NF37), plus verifies sensor unregistration via dumpsys sensorservice

## Task Commits

Each task was committed atomically:

1. **Task 1: SensorUnregistrationTest** - `951f778` (test)
2. **Task 2: BatterySoakTest + BackgroundBatterySoakTest** - `72f4fc2` (feat)

## Files Created/Modified
- `android/app/src/test/kotlin/app/dqxn/android/app/performance/SensorUnregistrationTest.kt` - Source-code analysis test for callbackFlow/awaitClose lifecycle
- `android/app/src/androidTest/kotlin/app/dqxn/android/e2e/BatterySoakTest.kt` - 30-min screen-on soak with 12 widgets
- `android/app/src/androidTest/kotlin/app/dqxn/android/e2e/BackgroundBatterySoakTest.kt` - Background soak + sensor unregistration check
- `android/app/build.gradle.kts` - Added uiautomator androidTestImplementation

## Decisions Made
- All 8 callbackFlow providers verified (plan referenced 5 with incorrect file names -- actual files use DataProvider suffix: AmbientLightDataProvider, OrientationDataProvider, etc.)
- Clock widget typeId is `essentials:clock` not `essentials:clock-digital` per actual annotation
- UiAutomator added to app androidTest deps for UiDevice shell command execution and HOME press

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Corrected provider file names and expanded coverage**
- **Found during:** Task 1 (SensorUnregistrationTest)
- **Issue:** Plan referenced `AmbientLightProvider.kt` and `OrientationProvider.kt` which don't exist; actual names are `AmbientLightDataProvider.kt` and `OrientationDataProvider.kt`. Plan also listed only 5 providers but 8 exist with callbackFlow.
- **Fix:** Used actual file names and included all 8 callbackFlow providers (added SolarLocationDataProvider, SolarTimezoneDataProvider, TimeDataProvider)
- **Files modified:** SensorUnregistrationTest.kt
- **Verification:** Test passes, all 8 providers verified

**2. [Rule 3 - Blocking] Added uiautomator dependency**
- **Found during:** Task 2 (BatterySoakTest)
- **Issue:** UiDevice requires uiautomator which was in version catalog but not in app androidTest deps
- **Fix:** Added `androidTestImplementation(libs.uiautomator)` to app build.gradle.kts
- **Files modified:** android/app/build.gradle.kts
- **Verification:** Compilation succeeds

**3. [Rule 1 - Bug] Corrected widget typeId**
- **Found during:** Task 2 (BatterySoakTest)
- **Issue:** Plan used `essentials:clock-digital` but actual typeId from @DashboardWidget is `essentials:clock`
- **Fix:** Used correct typeId `essentials:clock` in BatterySoakTest widget list
- **Files modified:** BatterySoakTest.kt
- **Verification:** Compilation succeeds

---

**Total deviations:** 3 auto-fixed (1 bug, 2 blocking)
**Impact on plan:** All auto-fixes necessary for correctness. No scope creep.

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Battery drain measurement infrastructure complete for CI device runs
- All callbackFlow providers verified for correct sensor lifecycle management
- Phase 13 plan 07 is the final plan -- phase complete

## Self-Check: PASSED

All files exist. All commits verified.

---
*Phase: 13-e2e-integration-launch-polish*
*Completed: 2026-02-25*
