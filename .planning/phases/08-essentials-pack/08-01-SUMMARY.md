---
phase: 08-essentials-pack
plan: 01
subsystem: pack
tags: [snapshot, dataSnapshot, lint, widget-isolation, play-services-location]

requires:
  - phase: 02-sdk-contracts
    provides: DataSnapshot interface, @DashboardSnapshot annotation
  - phase: 01-build-system
    provides: dqxn.snapshot convention plugin, dqxn.pack convention plugin, lint-rules module
provides:
  - 6 cross-boundary snapshot types in :pack:essentials:snapshots (Speed, Acceleration, Battery, Time, Orientation, AmbientLight)
  - 2 pack-local snapshot types in :pack:essentials (Solar, SpeedLimit)
  - play-services-location dependency for SolarLocationDataProvider
  - WidgetScopeBypass lint rule enforcing LocalWidgetScope usage
  - SnapshotConventionPlugin compileOnly compose.runtime for @Immutable
affects: [08-essentials-pack, 09-themes-demo-chaos]

tech-stack:
  added: [play-services-location 21.3.0]
  patterns: [import-based lint detection for widget packages, compileOnly compose.runtime in snapshot modules]

key-files:
  created:
    - android/pack/essentials/snapshots/src/main/kotlin/app/dqxn/android/pack/essentials/snapshots/SpeedSnapshot.kt
    - android/pack/essentials/snapshots/src/main/kotlin/app/dqxn/android/pack/essentials/snapshots/AccelerationSnapshot.kt
    - android/pack/essentials/snapshots/src/main/kotlin/app/dqxn/android/pack/essentials/snapshots/BatterySnapshot.kt
    - android/pack/essentials/snapshots/src/main/kotlin/app/dqxn/android/pack/essentials/snapshots/TimeSnapshot.kt
    - android/pack/essentials/snapshots/src/main/kotlin/app/dqxn/android/pack/essentials/snapshots/OrientationSnapshot.kt
    - android/pack/essentials/snapshots/src/main/kotlin/app/dqxn/android/pack/essentials/snapshots/AmbientLightSnapshot.kt
    - android/pack/essentials/src/main/kotlin/app/dqxn/android/pack/essentials/snapshots/SolarSnapshot.kt
    - android/pack/essentials/src/main/kotlin/app/dqxn/android/pack/essentials/snapshots/SpeedLimitSnapshot.kt
    - android/lint-rules/src/main/kotlin/app/dqxn/android/lint/WidgetScopeBypassDetector.kt
    - android/lint-rules/src/test/kotlin/app/dqxn/android/lint/WidgetScopeBypassDetectorTest.kt
  modified:
    - android/pack/essentials/build.gradle.kts
    - android/lint-rules/src/main/kotlin/app/dqxn/android/lint/DqxnIssueRegistry.kt
    - android/build-logic/convention/src/main/kotlin/SnapshotConventionPlugin.kt

key-decisions:
  - "Import-based lint detection over UCallExpression for WidgetScopeBypass -- lint test infrastructure with allowCompilationErrors cannot resolve method PSI for visitMethodCall"
  - "Package-based widget detection (app.dqxn.android.pack.*.widgets.*) over file-path-based -- lint test infra uses temp dirs making file paths unreliable"
  - "SnapshotConventionPlugin provides compileOnly compose.runtime + compose-bom -- compileOnly in sdk:contracts does not propagate transitively via api()"

patterns-established:
  - "Snapshot pattern: @DashboardSnapshot + @Immutable + DataSnapshot implementation with val-only properties"
  - "Pack-local snapshots use internal visibility, cross-boundary snapshots use public visibility"
  - "Widget package lint enforcement via import-based detection scoped to app.dqxn.android.pack.*.widgets.*"

requirements-completed: [F5.1, F5.2, F5.3, F5.4, F5.5, F5.6, F5.7, F5.8, F5.9, F5.10, F5.11]

duration: 8min
completed: 2026-02-24
---

# Phase 8 Plan 01: Snapshot Types and Foundation Infrastructure Summary

**8 snapshot types (Speed/Acceleration/Battery/Time/Orientation/AmbientLight/Solar/SpeedLimit) implementing DataSnapshot with @DashboardSnapshot + @Immutable, plus WidgetScopeBypass lint rule enforcing LocalWidgetScope in widget render packages**

## Performance

- **Duration:** 8 min
- **Started:** 2026-02-24T15:39:53Z
- **Completed:** 2026-02-24T15:48:14Z
- **Tasks:** 2
- **Files modified:** 13

## Accomplishments
- 6 cross-boundary snapshot types in `:pack:essentials:snapshots` compile cleanly (SpeedSnapshot, AccelerationSnapshot, BatterySnapshot, TimeSnapshot, OrientationSnapshot, AmbientLightSnapshot)
- 2 pack-local snapshot types in `:pack:essentials` compile cleanly (SolarSnapshot, SpeedLimitSnapshot)
- WidgetScopeBypass lint rule detects `rememberCoroutineScope` and `GlobalScope` imports in widget packages, passes negative cases for `LocalWidgetScope`, `derivedStateOf`, and non-widget files
- Fixed SnapshotConventionPlugin to provide `compileOnly(compose.runtime)` so `@Immutable` is available in snapshot modules

## Task Commits

Each task was committed atomically:

1. **Task 1: Create all snapshot types and update build config** - `8e277e2` (feat)
2. **Task 2: Implement WidgetScopeBypass lint rule with tests** - `4764005` (feat)

## Files Created/Modified
- `android/pack/essentials/snapshots/src/.../SpeedSnapshot.kt` - Speed data: speedMps, accuracy, timestamp
- `android/pack/essentials/snapshots/src/.../AccelerationSnapshot.kt` - Acceleration: longitudinal + lateral
- `android/pack/essentials/snapshots/src/.../BatterySnapshot.kt` - Battery: level, charging, temperature
- `android/pack/essentials/snapshots/src/.../TimeSnapshot.kt` - Time: epochMillis, zoneId
- `android/pack/essentials/snapshots/src/.../OrientationSnapshot.kt` - Orientation: bearing, pitch, roll
- `android/pack/essentials/snapshots/src/.../AmbientLightSnapshot.kt` - Ambient light: lux, category
- `android/pack/essentials/src/.../SolarSnapshot.kt` - Solar: sunrise/sunset/noon epochs, isDaytime, sourceMode (internal)
- `android/pack/essentials/src/.../SpeedLimitSnapshot.kt` - Speed limit: speedLimitKph, source (internal)
- `android/lint-rules/src/.../WidgetScopeBypassDetector.kt` - Lint rule detecting coroutine scope bypass in widgets
- `android/lint-rules/src/.../WidgetScopeBypassDetectorTest.kt` - 5 test cases (2 positive, 3 negative)
- `android/pack/essentials/build.gradle.kts` - Added play-services-location dependency
- `android/lint-rules/src/.../DqxnIssueRegistry.kt` - Registered WidgetScopeBypassDetector as 6th rule
- `android/build-logic/convention/src/.../SnapshotConventionPlugin.kt` - Added compileOnly compose.runtime for @Immutable

## Decisions Made
- **Import-based lint detection** over UCallExpression for WidgetScopeBypass -- lint test infrastructure with `allowCompilationErrors()` cannot resolve method PSI, so `visitMethodCall` never fires. Import scanning via `UImportStatement` is reliable and consistent with existing detector patterns.
- **Package-based widget detection** (`app.dqxn.android.pack.*.widgets.*`) over file-path-based -- lint test infrastructure uses temp dirs making `context.file.path` unreliable for module classification. Package name is always available via `context.uastFile?.packageName`.
- **SnapshotConventionPlugin compileOnly compose.runtime** -- `compileOnly` in `:sdk:contracts` does not propagate transitively via `api()` dependency. Snapshot modules need their own `compileOnly(compose-bom)` + `compileOnly(compose.runtime)` for `@Immutable` annotation resolution.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] SnapshotConventionPlugin missing compose.runtime for @Immutable**
- **Found during:** Task 1 (Snapshot types compilation)
- **Issue:** `@Immutable` annotation from `compose.runtime` unresolved in snapshot modules. CLAUDE.md says "@Immutable available transitively via :sdk:contracts -> compose.runtime" but `compileOnly` doesn't propagate through `api()`.
- **Fix:** Added `compileOnly(compose-bom)` + `compileOnly(compose.runtime)` to SnapshotConventionPlugin
- **Files modified:** android/build-logic/convention/src/main/kotlin/SnapshotConventionPlugin.kt
- **Verification:** `:pack:essentials:snapshots:compileDebugKotlin` passes
- **Committed in:** 8e277e2 (Task 1 commit)

**2. [Rule 1 - Bug] KDoc unclosed comment from wildcard path**
- **Found during:** Task 2 (Lint rule compilation)
- **Issue:** KDoc `pack/*/widgets/` contains `*/` which the Kotlin lexer interprets as closing the `/** ... */` block comment
- **Fix:** Changed to `pack/{packId}/widgets/` in KDoc text
- **Verification:** `:lint-rules:compileKotlin` passes
- **Committed in:** 4764005 (Task 2 commit, fixed before commit)

---

**Total deviations:** 2 auto-fixed (1 blocking, 1 bug)
**Impact on plan:** Both fixes necessary for compilation. No scope creep.

## Issues Encountered
- Lint test infrastructure does not invoke `visitMethodCall` when `allowCompilationErrors()` is set because PSI method resolution fails. Switched to import-based detection using `UImportStatement` which is the established pattern in this codebase.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- All 8 snapshot types are the compile-time dependency for every provider and widget in subsequent plans
- WidgetScopeBypass lint rule will enforce widget coroutine isolation from the first renderer onward
- Ready for Plan 02 (data providers) which will emit these snapshot types

## Self-Check: PASSED

All 11 created/modified files verified present. Both task commits (8e277e2, 4764005) verified in git log.

---
*Phase: 08-essentials-pack*
*Completed: 2026-02-24*
