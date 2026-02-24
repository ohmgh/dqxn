---
phase: 05-core-infrastructure
plan: 05
subsystem: design-system
tags: [compose, theme-engine, design-tokens, motion, kotlinx-serialization, hilt]

# Dependency graph
requires:
  - phase: 02-sdk-contracts
    provides: AutoSwitchMode, ThemeSpec, DashboardThemeDefinition, GradientSpec
  - phase: 03-sdk-observability
    provides: DqxnLogger, LogTags, NoOpLogger
provides:
  - DashboardSpacing object (7 sizes + 10 semantic aliases)
  - DashboardTypography object (8 named text styles)
  - TextEmphasis object (4 alpha constants)
  - CardSize enum (3 corner radii)
  - DashboardMotion object (3 spring configs + 14 named transitions)
  - ThemeJsonParser (JSON -> DashboardThemeDefinition)
  - ThemeAutoSwitchEngine (5-mode auto-switch with late-binding inputs)
  - BuiltInThemes registry (free themes + bundled asset loading)
  - DesignModule Hilt module
affects: [phase-07-dashboard-shell, phase-08-essentials-pack, phase-09-themes]

# Tech tracking
tech-stack:
  added: [kotlinx-serialization-json in core:design]
  patterns: [late-binding StateFlow inputs, pure-Kotlin hex color parsing, ComponentCallbacks2 for system dark mode reactivity]

key-files:
  created:
    - android/core/design/src/main/kotlin/app/dqxn/android/core/design/token/DashboardSpacing.kt
    - android/core/design/src/main/kotlin/app/dqxn/android/core/design/token/DashboardTypography.kt
    - android/core/design/src/main/kotlin/app/dqxn/android/core/design/token/TextEmphasis.kt
    - android/core/design/src/main/kotlin/app/dqxn/android/core/design/token/CardSize.kt
    - android/core/design/src/main/kotlin/app/dqxn/android/core/design/motion/DashboardMotion.kt
    - android/core/design/src/main/kotlin/app/dqxn/android/core/design/theme/ThemeSchema.kt
    - android/core/design/src/main/kotlin/app/dqxn/android/core/design/theme/ThemeJsonParser.kt
    - android/core/design/src/main/kotlin/app/dqxn/android/core/design/theme/BuiltInThemes.kt
    - android/core/design/src/main/kotlin/app/dqxn/android/core/design/theme/ThemeAutoSwitchEngine.kt
    - android/core/design/src/main/kotlin/app/dqxn/android/core/design/di/DesignModule.kt
    - android/core/design/src/test/kotlin/app/dqxn/android/core/design/theme/ThemeAutoSwitchEngineTest.kt
    - android/core/design/src/test/kotlin/app/dqxn/android/core/design/theme/ThemeJsonParserTest.kt
    - android/core/design/src/test/kotlin/app/dqxn/android/core/design/token/DashboardSpacingTest.kt
  modified:
    - android/core/design/build.gradle.kts

key-decisions:
  - "Pure-Kotlin hex color parser instead of android.graphics.Color.parseColor -- enables unit testing without Robolectric"
  - "ComponentCallbacks2 for reactive system dark mode detection -- fires on uiMode changes without Activity restart"
  - "CoroutineScope + TestCoroutineScheduler for engine tests instead of TestScope.backgroundScope -- direct scheduler control"

patterns-established:
  - "Late-binding StateFlow inputs: inject MutableStateFlow defaults, bind external sources via scope.launch collectors"
  - "parseHexColor internal fun: pure-Kotlin #RRGGBB/#AARRGGBB parser, reusable across modules"

requirements-completed: [F4.1, F4.2, F4.4, F4.5]

# Metrics
duration: 9min
completed: 2026-02-24
---

# Phase 5 Plan 5: Core Design Module Summary

**Design tokens ported verbatim from old codebase, DashboardMotion with 14 transitions, ThemeJsonParser with pure-Kotlin color parsing, and 5-mode ThemeAutoSwitchEngine with late-binding sensor inputs**

## Performance

- **Duration:** 9 min
- **Started:** 2026-02-24T02:43:58Z
- **Completed:** 2026-02-24T02:53:19Z
- **Tasks:** 2
- **Files modified:** 14

## Accomplishments
- All design tokens ported verbatim from old codebase DashboardThemeExtensions.kt (spacing, typography, emphasis, card radii)
- DashboardMotion spring configs (0.65/300, 0.50/300, 0.75/380) and all 14 named transitions ported verbatim from DashboardAnimations.kt
- ThemeAutoSwitchEngine: 5 modes working with correct fallback (SOLAR_AUTO/ILLUMINANCE_AUTO -> SYSTEM when unbound), SharingStarted.Eagerly for cold start
- 44 tests passing across 3 test classes (15 engine, 8 parser, 21 spacing)

## Task Commits

Each task was committed atomically:

1. **Task 1: Design tokens + motion specs + theme JSON parser + BuiltInThemes** - `3f7eb3a` (feat)
2. **Task 2: ThemeAutoSwitchEngine + Hilt module + tests** - `8731d7c` (feat)

## Files Created/Modified
- `android/core/design/build.gradle.kts` - Added hilt, serialization, sdk deps
- `android/core/design/src/main/kotlin/.../token/DashboardSpacing.kt` - 7 sizes + 10 semantic aliases (verbatim port)
- `android/core/design/src/main/kotlin/.../token/DashboardTypography.kt` - 8 named TextStyles with M3 overrides (verbatim port)
- `android/core/design/src/main/kotlin/.../token/TextEmphasis.kt` - 4 alpha constants (verbatim port)
- `android/core/design/src/main/kotlin/.../token/CardSize.kt` - SMALL/MEDIUM/LARGE corner radii (verbatim port)
- `android/core/design/src/main/kotlin/.../motion/DashboardMotion.kt` - 3 springs + 14 transitions (verbatim port)
- `android/core/design/src/main/kotlin/.../theme/ThemeSchema.kt` - @Serializable JSON schema types
- `android/core/design/src/main/kotlin/.../theme/ThemeJsonParser.kt` - JSON -> DashboardThemeDefinition with pure-Kotlin hex parsing
- `android/core/design/src/main/kotlin/.../theme/BuiltInThemes.kt` - Free theme registry + bundled asset loading
- `android/core/design/src/main/kotlin/.../theme/ThemeAutoSwitchEngine.kt` - 5-mode engine with late-binding inputs
- `android/core/design/src/main/kotlin/.../di/DesignModule.kt` - Hilt module providing Json
- `android/core/design/src/test/.../theme/ThemeAutoSwitchEngineTest.kt` - 15 tests for all modes/fallbacks
- `android/core/design/src/test/.../theme/ThemeJsonParserTest.kt` - 8 tests for JSON parsing
- `android/core/design/src/test/.../token/DashboardSpacingTest.kt` - 21 tests verifying all spacing values

## Decisions Made
- **Pure-Kotlin hex color parser** instead of `android.graphics.Color.parseColor` -- Android framework APIs return 0 in unit tests without Robolectric. The `parseHexColor` function handles both `#RRGGBB` and `#AARRGGBB` formats via `Long.toInt()` conversion to Compose `Color(Int)` constructor.
- **ComponentCallbacks2 for system dark mode** -- `context.registerComponentCallbacks` fires `onConfigurationChanged` when uiMode changes, providing reactive updates without requiring an Activity or BroadcastReceiver.
- **CoroutineScope + TestCoroutineScheduler** for engine tests -- using a plain `CoroutineScope(testDispatcher)` with explicit `scheduler.advanceUntilIdle()` instead of `TestScope.backgroundScope` provides more predictable control over coroutine execution in tests.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] ThemeJsonParser android.graphics.Color.parseColor returns 0 in unit tests**
- **Found during:** Task 2 (test execution)
- **Issue:** `android.graphics.Color.parseColor` is a framework API that returns 0 in plain JUnit tests (no Robolectric), causing all color assertions to fail
- **Fix:** Replaced with pure-Kotlin `parseHexColor` internal function that parses `#RRGGBB`/`#AARRGGBB` via `String.toLong(16)` and `Color(Int)` constructor
- **Files modified:** ThemeJsonParser.kt
- **Verification:** All 8 ThemeJsonParser tests pass including alpha channel parsing
- **Committed in:** 8731d7c (Task 2 commit)

**2. [Rule 1 - Bug] Turbine import typo in ThemeAutoSwitchEngineTest**
- **Found during:** Task 2 (test compilation)
- **Issue:** `import app.turbine.test` should be `import app.cash.turbine.test` -- removed entirely since not used
- **Fix:** Removed unused import
- **Files modified:** ThemeAutoSwitchEngineTest.kt
- **Committed in:** 8731d7c (Task 2 commit)

**3. [Rule 1 - Bug] TestScope.backgroundScope not advancing coroutines predictably**
- **Found during:** Task 2 (ThemeAutoSwitchEngine tests failing)
- **Issue:** `advanceUntilIdle()` within `testScope.runTest` was not processing coroutines launched on `testScope.backgroundScope`, causing `isDarkActive` to stay at its initial value
- **Fix:** Switched to plain `CoroutineScope(testDispatcher)` with a shared `TestCoroutineScheduler` and direct `scheduler.advanceUntilIdle()` calls. Added `@AfterEach` teardown to cancel the scope.
- **Files modified:** ThemeAutoSwitchEngineTest.kt
- **Committed in:** 8731d7c (Task 2 commit)

---

**Total deviations:** 3 auto-fixed (3 bugs)
**Impact on plan:** All fixes necessary for test correctness. No scope creep.

## Issues Encountered
None beyond the deviations above.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- `:core:design` module complete with all design tokens, motion specs, and theme engine
- ThemeAutoSwitchEngine ready for Phase 8 sensor binding (bindIlluminance/bindSolarDaytime)
- BuiltInThemes infrastructure ready for Phase 9 premium theme JSON files
- Phase 7 dashboard shell can consume DashboardSpacing, DashboardTypography, DashboardMotion
- `:app` DI assembly in Phase 6 can wire bindPreferences from UserPreferencesRepository

## Self-Check: PASSED

- All 14 created files verified present on disk
- Both task commits (3f7eb3a, 8731d7c) verified in git log
- 44 tests passing in :core:design:testDebugUnitTest

---
*Phase: 05-core-infrastructure*
*Completed: 2026-02-24*
