---
phase: 12-ci-gates-benchmarking
plan: 05
subsystem: infra
tags: [pitest, mutation-testing, ci, gradle, agp9, incompatibility]

# Dependency graph
requires:
  - phase: 01-build-system-foundation
    provides: Gradle build infrastructure, version catalog
provides:
  - Documented Pitest incompatibility with AGP 9 / Gradle 9.3 stack
  - Commented-out Pitest configuration ready for re-enablement
affects: []

# Tech tracking
tech-stack:
  added: []
  patterns: [commented-config-with-incompatibility-documentation]

key-files:
  created: []
  modified:
    - android/gradle/libs.versions.toml
    - android/sdk/common/build.gradle.kts

key-decisions:
  - "Pitest incompatible with AGP 9 / Gradle 9.3 Android library modules -- both info.solidsoft.pitest 1.19.0-rc.3 and pl.droidsonroids.pitest 0.2.25 fail"
  - "Documented as commented-out config with specific error details for future re-enablement"

patterns-established:
  - "Incompatible tooling documented as commented config blocks with error details, not removed"

requirements-completed: []

# Metrics
duration: 12min
completed: 2026-02-25
---

# Phase 12 Plan 05: Pitest Mutation Testing Summary

**Best-effort Pitest setup attempted on :sdk:common -- both plugin variants incompatible with AGP 9 / Gradle 9.3 Android library modules; documented with commented-out config for future re-enablement**

## Performance

- **Duration:** 12 min
- **Started:** 2026-02-25T09:36:03Z
- **Completed:** 2026-02-25T09:48:47Z
- **Tasks:** 1
- **Files modified:** 2

## Accomplishments
- Attempted `info.solidsoft.pitest` 1.19.0-rc.3 -- plugin loads on Android library but does NOT register the `pitest` extension (confirmed via extensionsSchema dump: 15 extensions registered, none is pitest)
- Attempted `pl.droidsonroids.pitest` 0.2.25 -- fails immediately on Gradle 9.3 with "Cannot mutate configuration container for buildscript of project" (buildscript classpath mutation not allowed in Gradle 9.x)
- Version catalog has commented pitest entries with specific version numbers and error descriptions
- sdk:common has commented-out pitest configuration block ready for re-enablement
- Module still compiles (blocked only by pre-existing baselineprofile plugin issue from plan 12-01, not by pitest changes)

## Task Commits

Each task was committed atomically:

1. **Task 1: Add Pitest version catalog entries + configure on :sdk:common** - `4ad016c` (chore)

## Files Created/Modified
- `android/gradle/libs.versions.toml` - Commented pitest version/plugin entries documenting 3 attempted versions
- `android/sdk/common/build.gradle.kts` - Commented-out pitest plugin + config block with incompatibility notes

## Decisions Made
- **info.solidsoft.pitest 1.19.0-rc.3 incompatible:** Plugin applies without error but does NOT register the `pitest` extension on Android library modules. Extension schema inspection confirmed only 15 standard extensions (android, kotlin, ksp, hilt, etc.) -- no pitest. The `pitest {}` DSL block in build.gradle.kts resolves to the `pitest` dependency configuration instead, causing "Unresolved reference" on all properties (targetClasses, threads, etc.).
- **pl.droidsonroids.pitest 0.2.25 incompatible:** Fails at plugin apply time with Gradle 9.3 error "Cannot mutate configuration container for buildscript." The plugin attempts to add dependencies to the buildscript classpath, which Gradle 9.x prohibits.
- **Deferred to post-AGP-9-support release:** Both plugin paths exhausted. Pitest mutation testing tracking deferred until either plugin gains AGP 9 compatibility or a new Kotlin-native mutation testing tool emerges.

## Deviations from Plan
None - plan anticipated LOW confidence outcome and prescribed the exact fallback protocol followed.

## Issues Encountered
- **Pre-existing: baselineprofile plugin breaks ALL Gradle tasks** (committed in 77dfee8 from plan 12-01). `androidx.baselineprofile` 1.4.1 fails on `:app` with "Module `:app` is not a supported android module" and on `:baselineprofile` with "Extension of type 'TestExtension' does not exist." Required temporary disabling of both to verify pitest work. Logged to `deferred-items.md`.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Pitest is tracking-only (not a blocking gate) -- no impact on CI pipeline or launch readiness
- Configuration is ready to uncomment when plugin compatibility lands
- Pre-existing baselineprofile incompatibility (plan 12-01) should be resolved before any Gradle-dependent verification

## Self-Check: PASSED

All 5 verification points confirmed:
- Both modified files exist on disk
- Commit 4ad016c verified in git log
- Pitest entries present in version catalog (as comments)
- Pitest references present in sdk:common build file (as comments)

---
*Phase: 12-ci-gates-benchmarking*
*Completed: 2026-02-25*
