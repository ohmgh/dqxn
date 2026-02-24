---
phase: 06-deployable-app-agentic-framework
plan: 04
subsystem: app
tags: [debug-overlay, release-build, r8, proguard, validation-pipeline, thermal, metrics, widget-health]

# Dependency graph
requires:
  - phase: 06-02
    provides: "App shell with Hilt DI, AppModule, proguard-rules.pro"
  - phase: 06-03
    provides: "AgenticContentProvider, DebugModule, 15 diagnostic handlers"
  - phase: 03-sdk-observability-analytics-ui
    provides: "MetricsCollector, WidgetHealthMonitor, MetricsSnapshot"
  - phase: 05-core-infrastructure
    provides: "ThermalMonitor, RenderConfig, ThermalLevel, proto DataStore schemas"
provides:
  - "3 debug overlay composables (FrameStats, WidgetHealth, ThermalTrending)"
  - "ReleaseModule placeholder in release source set"
  - "Validated release APK with R8 (zero debug-only code)"
  - "6-tier validation pipeline documentation (F13.9)"
  - "protolite-well-known-types exclusion for Firebase/protobuf compatibility"
affects: [07-dashboard-shell, 08-essentials-pack, 12-ci-gates]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Debug overlay composables: derivedStateOf + graphicsLayer isolation + monospace text"
    - "Release source set placeholder for namespace reservation"
    - "Firebase protolite-well-known-types exclusion for protobuf-javalite coexistence"

key-files:
  created:
    - android/app/src/debug/kotlin/app/dqxn/android/debug/overlays/FrameStatsOverlay.kt
    - android/app/src/debug/kotlin/app/dqxn/android/debug/overlays/WidgetHealthOverlay.kt
    - android/app/src/debug/kotlin/app/dqxn/android/debug/overlays/ThermalTrendingOverlay.kt
    - android/app/src/release/kotlin/app/dqxn/android/release/ReleaseModule.kt
    - .planning/phases/06-deployable-app-agentic-framework/VALIDATION-PIPELINE.md
  modified:
    - android/core/firebase/build.gradle.kts

key-decisions:
  - "Exclude protolite-well-known-types from firebase-perf to resolve duplicate class conflict with protobuf-javalite:4.30.2"
  - "No ProGuard rule changes needed -- existing rules already cover proto, KSP-generated, and serializable classes"

patterns-established:
  - "Debug overlay pattern: internal @Composable, derivedStateOf for lazy reads, graphicsLayer for isolation, semi-transparent dark background"
  - "Release source set placeholder pattern for future release-only bindings"

requirements-completed: [F13.4, F13.5, F13.9, NF21, NF22]

# Metrics
duration: 6min
completed: 2026-02-24
---

# Phase 6 Plan 04: Debug Overlays + Release Validation Summary

**3 debug overlay composables (frame stats, widget health, thermal trending), validated release APK with R8 stripping all debug code, and 6-tier validation pipeline documentation**

## Performance

- **Duration:** 6 min
- **Started:** 2026-02-24T04:16:04Z
- **Completed:** 2026-02-24T04:22:30Z
- **Tasks:** 2
- **Files modified:** 6

## Accomplishments
- 3 debug overlay composables with derivedStateOf deferred reads, graphicsLayer isolation, and monospace text rendering
- Release APK builds successfully with R8 -- zero debug-only classes (AgenticContentProvider, DebugModule, overlays, handlers all absent)
- VALIDATION-PIPELINE.md documents 6 tiers from compile check (~8s) through full CI gate, with agentic command examples
- ReleaseModule placeholder ensures release source set has a Kotlin file for namespace registration
- All 32 Phase 6 unit tests pass, lint clean

## Task Commits

Each task was committed atomically:

1. **Task 1: Debug overlays + ReleaseModule + validation pipeline doc** - `5e5ae47` (feat)
2. **Task 2: Release build validation -- assembleRelease + R8 verification** - `b866d0b` (fix)

## Files Created/Modified
- `android/app/src/debug/kotlin/app/dqxn/android/debug/overlays/FrameStatsOverlay.kt` - FPS counter, P50/P95/P99 frame times, jank count from MetricsCollector snapshot
- `android/app/src/debug/kotlin/app/dqxn/android/debug/overlays/WidgetHealthOverlay.kt` - Per-widget health status with color-coded ACTIVE/STALE/STALLED/CRASHED indicators
- `android/app/src/debug/kotlin/app/dqxn/android/debug/overlays/ThermalTrendingOverlay.kt` - Thermal level, target FPS, glow state from ThermalMonitor flows
- `android/app/src/release/kotlin/app/dqxn/android/release/ReleaseModule.kt` - Empty @Module @InstallIn(SingletonComponent) placeholder
- `.planning/phases/06-deployable-app-agentic-framework/VALIDATION-PIPELINE.md` - 6-tier pipeline with agentic verification commands
- `android/core/firebase/build.gradle.kts` - Excluded protolite-well-known-types from firebase-perf

## Decisions Made
- **Exclude protolite-well-known-types from firebase-perf** -- `firebase-perf:21.0.4` depends on `protolite-well-known-types:18.0.0` which bundles protobuf descriptor classes already present in `protobuf-javalite:4.30.2` (from `:data:proto`). Exclusion resolves duplicate class errors during assembleRelease.
- **No ProGuard changes needed** -- existing rules in `proguard-rules.pro` (from Plan 02) already keep proto-generated, KSP-generated, and serializable classes. R8 processed successfully without additions.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Firebase protolite-well-known-types duplicate class conflict**
- **Found during:** Task 2 (assembleRelease)
- **Issue:** `firebase-perf` transitively depends on `protolite-well-known-types:18.0.0` which bundles protobuf descriptor classes, colliding with `protobuf-javalite:4.30.2` from `:data:proto` (our Proto DataStore dependency)
- **Fix:** Added `exclude(group = "com.google.firebase", module = "protolite-well-known-types")` to the `firebase.perf` dependency in `:core:firebase/build.gradle.kts`
- **Files modified:** `android/core/firebase/build.gradle.kts`
- **Verification:** `assembleRelease` succeeds, all tests pass
- **Committed in:** `b866d0b` (Task 2 commit)

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** Fix necessary for release build to succeed. No scope creep.

## Issues Encountered
None beyond the auto-fixed deviation above.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Phase 6 complete (4/4 plans) -- deployable debug APK with agentic framework operational
- Release build validated -- ready for CI pipeline in Phase 12
- Debug overlays ready for Phase 7 dashboard shell to wire in via toggle handler
- Source set separation pattern validated for Phase 9 demo providers (F13.4/NF22)
- All observability singletons available in debug builds via DebugModule; production wiring in Phase 7+

## Self-Check: PASSED

- All 6 files verified present on disk
- Both task commits (5e5ae47, b866d0b) verified in git history
- `:app:compileDebugKotlin` BUILD SUCCESSFUL
- `assembleRelease` BUILD SUCCESSFUL
- Release APK: 0 matches for AgenticContentProvider, FrameStatsOverlay, WidgetHealthOverlay, ThermalTrendingOverlay, DebugModule
- All Phase 6 unit tests pass, lint clean

---
*Phase: 06-deployable-app-agentic-framework*
*Completed: 2026-02-24*
