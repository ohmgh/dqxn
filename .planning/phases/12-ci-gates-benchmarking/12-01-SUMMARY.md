---
phase: 12-ci-gates-benchmarking
plan: 01
subsystem: infra
tags: [baseline-profile, macrobenchmark, benchmark, profileinstaller, kover, agp9]

# Dependency graph
requires:
  - phase: 01-build-system-foundation
    provides: Module stubs for :baselineprofile and :benchmark, version catalog, gradle.properties
provides:
  - Configured :baselineprofile com.android.test module with baselineprofile plugin targeting :app
  - Configured :benchmark com.android.test module with benchmark build type and matchingFallbacks
  - Version catalog entries for benchmark, profileinstaller, uiautomator, baselineprofile plugin, kover plugin
  - profileinstaller wired into :app with baselineProfile dependency
  - gradle.properties benchmark output flag
affects: [12-02, 12-03, 12-04, 12-05]

# Tech tracking
tech-stack:
  added: [benchmark-macro-junit4 1.4.1, profileinstaller 1.4.1, baselineprofile-plugin 1.4.1, uiautomator 2.3.0, kover 0.9.7]
  patterns: [com.android.test module type for benchmark/profile modules, matchingFallbacks for release-like benchmark builds]

key-files:
  created: []
  modified:
    - android/gradle/libs.versions.toml
    - android/gradle.properties
    - android/baselineprofile/build.gradle.kts
    - android/benchmark/build.gradle.kts
    - android/app/build.gradle.kts

key-decisions:
  - "Upgraded baselineprofile plugin from 1.3.1 to 1.4.1 -- 1.3.1 fails with AGP 9 (Module :app is not a supported android module)"

patterns-established:
  - "com.android.test modules use raw plugin ID (not convention plugin) with targetProjectPath = :app"
  - "benchmark build type with isDebuggable=true + matchingFallbacks to release for non-debuggable target"

requirements-completed: [NF9, NF10]

# Metrics
duration: 9min
completed: 2026-02-25
---

# Phase 12 Plan 01: Benchmark Infrastructure Summary

**Baseline profile + macrobenchmark module configuration with AGP 9-compatible plugin versions, profileinstaller wired into :app, version catalog complete**

## Performance

- **Duration:** 9 min
- **Started:** 2026-02-25T09:35:58Z
- **Completed:** 2026-02-25T09:45:08Z
- **Tasks:** 2
- **Files modified:** 5

## Accomplishments
- Version catalog has all 5 new entries: benchmark 1.4.1, profileinstaller 1.4.1, uiautomator 2.3.0, baselineprofile plugin 1.4.1, kover 0.9.7
- `:baselineprofile` compiles as `com.android.test` with `targetProjectPath = ":app"` and baselineprofile plugin
- `:benchmark` compiles as `com.android.test` with benchmark build type and `matchingFallbacks += listOf("release")`
- `:app` has `profileinstaller` implementation dependency and `baselineprofile` plugin applied
- `gradle.properties` has `android.enableAdditionalTestOutput=true`

## Task Commits

Each task was committed atomically:

1. **Task 1: Add version catalog entries + gradle.properties flag** - `0c5a806` (chore)
2. **Task 2: Configure baselineprofile + benchmark modules + wire app** - `2339a99` (feat) + `77dfee8` (feat, app wiring split due to linter interference)

## Files Created/Modified
- `android/gradle/libs.versions.toml` - Added benchmark, profileinstaller, uiautomator versions + baselineprofile, kover plugins
- `android/gradle.properties` - Added android.enableAdditionalTestOutput=true
- `android/baselineprofile/build.gradle.kts` - Replaced stub with com.android.test + baselineprofile plugin targeting :app
- `android/benchmark/build.gradle.kts` - Replaced stub with com.android.test + benchmark build type + matchingFallbacks
- `android/app/build.gradle.kts` - Added baselineprofile plugin + profileinstaller dep + baselineProfile(:baselineprofile)

## Decisions Made
- **Upgraded baselineprofile plugin from 1.3.1 to 1.4.1** -- v1.3.1 throws "Module :app is not a supported android module" with AGP 9.0.1. The plugin's internal check for `com.android.application` fails against AGP 9's new extension types. Version 1.4.1 resolves this.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Baselineprofile plugin 1.3.1 incompatible with AGP 9**
- **Found during:** Task 2 (module configuration)
- **Issue:** `androidx.baselineprofile:1.3.1` fails to apply on `:app` with error "Module :app is not a supported android module" -- AGP 9 removed/changed `BaseExtension` types the plugin checks for
- **Fix:** Upgraded `baselineprofile-plugin` from `1.3.1` to `1.4.1` in version catalog
- **Files modified:** android/gradle/libs.versions.toml
- **Verification:** Both `:baselineprofile:assembleDebug` and `:benchmark:assembleDebug` compile successfully
- **Committed in:** 2339a99

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** Version bump necessary for AGP 9 compatibility. No scope creep.

## Issues Encountered
- An external linter repeatedly injected Pitest version catalog entries, library entries, plugin entries, and even a full pitest configuration block into `sdk/common/build.gradle.kts`. These were reverted each time as the plan explicitly states NOT to add Pitest entries (deferred to a later plan due to LOW confidence in Kotlin 2.3 + JDK 25 compatibility).

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- Both `:baselineprofile` and `:benchmark` modules compile and are ready for test class implementation (Plan 12-02+)
- `:app` has profileinstaller for baseline profile installation at runtime
- Kover plugin version is in the catalog but not yet applied to any module (Plan 12-03+)

## Self-Check: PASSED

All files exist, all commits verified, SUMMARY.md created.

---
*Phase: 12-ci-gates-benchmarking*
*Completed: 2026-02-25*
