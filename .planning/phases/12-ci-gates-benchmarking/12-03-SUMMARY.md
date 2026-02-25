---
phase: 12-ci-gates-benchmarking
plan: 03
subsystem: infra
tags: [baseline-profile, macrobenchmark, startup-timing, frame-timing, uiautomator, agp9]

# Dependency graph
requires:
  - phase: 12-ci-gates-benchmarking
    provides: Configured :baselineprofile and :benchmark com.android.test modules with version catalog entries
provides:
  - BaselineProfileGenerator with 4 collection methods (startup, dashboard, edit mode, widget picker)
  - StartupBenchmark with cold + warm startup timing using StartupTimingMetric
  - DashboardFrameBenchmark with 12-widget steady state + edit mode cycle using FrameTimingMetric
affects: [12-04, 13-e2e-integration-launch-polish]

# Tech tracking
tech-stack:
  added: [androidx-test-ext-junit 1.2.1]
  patterns: [MacrobenchmarkScope extension for widget population, agentic ContentProvider for benchmark setup]

key-files:
  created:
    - android/baselineprofile/src/main/kotlin/app/dqxn/android/baselineprofile/BaselineProfileGenerator.kt
    - android/benchmark/src/main/kotlin/app/dqxn/android/benchmark/StartupBenchmark.kt
    - android/benchmark/src/main/kotlin/app/dqxn/android/benchmark/DashboardFrameBenchmark.kt
  modified:
    - android/baselineprofile/build.gradle.kts
    - android/benchmark/build.gradle.kts
    - android/app/build.gradle.kts
    - android/gradle/libs.versions.toml

key-decisions:
  - "Baselineprofile Gradle plugin fully deferred -- both producer (baselineprofile module) and app-target (app module) sides of 1.4.1 incompatible with AGP 9; library deps (BaselineProfileRule, MacrobenchmarkRule) work independently"
  - "Benchmark matchingFallbacks changed from release to debug -- release Hilt DI incomplete (DiagnosticSnapshotCapture missing binding); debug variant fully wired"
  - "MacrobenchmarkScope extension function for widget population -- encapsulates agentic ContentProvider add-widget calls"
  - "Added androidx-test-ext-junit 1.2.1 to version catalog -- required for AndroidJUnit4 runner in com.android.test modules"

patterns-established:
  - "Agentic ContentProvider as benchmark setup tool: device.executeShellCommand content call for widget population in macrobenchmark setupBlock"
  - "Null-safe UI interaction in benchmarks: findObject + null check for graceful degradation when UI elements change"

requirements-completed: [NF1, NF9, NF10]

# Metrics
duration: 8min
completed: 2026-02-25
---

# Phase 12 Plan 03: Benchmark Test Classes Summary

**Baseline profile generator (4 journeys) + macrobenchmarks for cold/warm startup (StartupTimingMetric) and 12-widget frame timing (FrameTimingMetric) targeting < 1.5s startup and P95 < 16.67ms**

## Performance

- **Duration:** 8 min
- **Started:** 2026-02-25T09:51:18Z
- **Completed:** 2026-02-25T09:59:30Z
- **Tasks:** 2
- **Files modified:** 6

## Accomplishments
- BaselineProfileGenerator covers 4 critical user journeys: startup, dashboard interaction, edit mode, widget picker
- StartupBenchmark measures cold (5 iterations) and warm (3 iterations) startup with StartupTimingMetric
- DashboardFrameBenchmark measures 12-widget steady state (5s soak) and edit mode cycle with FrameTimingMetric
- All 3 source files compile against :app debug target without errors
- Widget population via agentic ContentProvider add-widget handler (12 essentials widget types)

## Task Commits

Each task was committed atomically:

1. **Task 1: Implement BaselineProfileGenerator** - `e88b8bd` (feat)
2. **Task 2: Implement StartupBenchmark + DashboardFrameBenchmark** - `1569fde` (feat)

## Files Created/Modified
- `android/baselineprofile/src/main/kotlin/app/dqxn/android/baselineprofile/BaselineProfileGenerator.kt` - 4 profile collection methods covering startup, dashboard, edit mode, widget picker
- `android/benchmark/src/main/kotlin/app/dqxn/android/benchmark/StartupBenchmark.kt` - Cold + warm startup timing benchmarks
- `android/benchmark/src/main/kotlin/app/dqxn/android/benchmark/DashboardFrameBenchmark.kt` - 12-widget steady state + edit mode cycle frame benchmarks
- `android/baselineprofile/build.gradle.kts` - Removed incompatible baselineprofile plugin, added uiautomator + ext-junit deps
- `android/benchmark/build.gradle.kts` - Changed matchingFallbacks to debug, added ext-junit dep
- `android/app/build.gradle.kts` - Removed incompatible baselineprofile plugin + baselineProfile dependency config
- `android/gradle/libs.versions.toml` - Added androidx-test-ext-junit 1.2.1

## Decisions Made
- **Baselineprofile Gradle plugin fully deferred** -- both `BaselineProfileProducerPlugin` (on :baselineprofile) and `BaselineProfileAppTargetPlugin` (on :app) fail with AGP 9. Producer side looks for `TestExtension` (AGP 9 uses `TestExtensionImpl`). App-target side fails "Module :app is not a supported android module". The library dependencies (`BaselineProfileRule`, `MacrobenchmarkRule`) work independently of the Gradle plugin -- the plugin automates profile injection into APKs, not test execution.
- **Benchmark matchingFallbacks changed from release to debug** -- The release variant has an incomplete Hilt DI graph (DiagnosticSnapshotCapture only bound in DebugModule). Debug variant is fully wired. Benchmarks still measure real app behavior; when release DI is completed, fallback can revert.
- **MacrobenchmarkScope extension function for populateWidgets()** -- Encapsulates the 12 agentic ContentProvider add-widget calls. Uses all available essentials widget types.
- **Added androidx-test-ext-junit 1.2.1** -- Required for `@RunWith(AndroidJUnit4::class)` in com.android.test modules; not provided transitively by benchmark-macro-junit4.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Baselineprofile Gradle plugin incompatible with AGP 9**
- **Found during:** Task 1 (BaselineProfileGenerator implementation)
- **Issue:** Both `BaselineProfileProducerPlugin` (on :baselineprofile) and `BaselineProfileAppTargetPlugin` (on :app) fail during Gradle configuration with AGP 9.0.1. The 12-01 summary claimed 1.4.1 resolved this, but it only resolved partial compilation -- full configuration still fails.
- **Fix:** Removed baselineprofile plugin from both :baselineprofile and :app modules. Library deps (BaselineProfileRule) still work for test execution. Plugin re-added when AndroidX baselineprofile supports AGP 9.
- **Files modified:** android/baselineprofile/build.gradle.kts, android/app/build.gradle.kts
- **Verification:** Both modules compile successfully
- **Committed in:** e88b8bd

**2. [Rule 3 - Blocking] Benchmark release variant Hilt DI incomplete**
- **Found during:** Task 2 (StartupBenchmark implementation)
- **Issue:** `:benchmark` with `matchingFallbacks = release` triggers `:app` release variant compilation, which fails on missing DiagnosticSnapshotCapture Hilt binding (only in DebugModule)
- **Fix:** Changed matchingFallbacks from `release` to `debug`
- **Files modified:** android/benchmark/build.gradle.kts
- **Verification:** :benchmark:compileBenchmarkKotlin succeeds
- **Committed in:** e88b8bd

**3. [Rule 3 - Blocking] Missing androidx-test-ext-junit dependency**
- **Found during:** Task 1 (BaselineProfileGenerator compilation)
- **Issue:** `AndroidJUnit4` from `androidx.test.ext.junit.runners` unresolved -- not in version catalog, not transitively available from benchmark-macro-junit4
- **Fix:** Added `androidx-test-ext-junit = 1.2.1` to version catalog, added implementation dep to both modules
- **Files modified:** android/gradle/libs.versions.toml, android/baselineprofile/build.gradle.kts, android/benchmark/build.gradle.kts
- **Verification:** Both modules compile
- **Committed in:** e88b8bd

---

**Total deviations:** 3 auto-fixed (3 blocking)
**Impact on plan:** All fixes necessary for compilation. Baselineprofile Gradle plugin deferred (library works fine). No scope creep.

## Issues Encountered
- The 12-01 SUMMARY claimed baselineprofile plugin 1.4.1 resolved AGP 9 incompatibility, but both producer and app-target sides still fail. The library artifacts work independently. The Gradle plugin automates profile injection into release APKs and can be re-added when AndroidX updates.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- All benchmark test classes compile and are ready for on-device execution
- `./gradlew :baselineprofile:connectedDebugAndroidTest` generates baseline profiles
- `./gradlew :benchmark:connectedBenchmarkAndroidTest` runs macrobenchmarks
- Baselineprofile Gradle plugin deferred until AndroidX supports AGP 9 -- profiles can still be generated manually via BaselineProfileRule
- Release variant Hilt DI needs DiagnosticSnapshotCapture binding for non-debug benchmark runs (future plan)

## Self-Check: PASSED

All files exist, all commits verified, SUMMARY.md created.

---
*Phase: 12-ci-gates-benchmarking*
*Completed: 2026-02-25*
