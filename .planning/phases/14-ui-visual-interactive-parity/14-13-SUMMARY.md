---
phase: 14-ui-visual-interactive-parity
plan: 13
subsystem: testing
tags: [source-scanning, design-tokens, regression-gate, truth, junit5]

# Dependency graph
requires:
  - phase: 11-theme-diagnostics-onboarding
    provides: MaterialTheme usage in feature modules
provides:
  - Source-scanning regression gate for MaterialTheme usage in 3 feature modules
  - Documented allowlists with owning plan references
affects: [14-02, 14-04, 14-05, 14-07, 15-setup-schema]

# Tech tracking
tech-stack:
  added: []
  patterns: [source-scanning test pattern, assertWithMessage for Truth assertions, module-root-relative File paths via user.dir]

key-files:
  created:
    - android/feature/settings/src/test/kotlin/app/dqxn/android/feature/settings/DesignTokenWiringTest.kt
    - android/feature/dashboard/src/test/kotlin/app/dqxn/android/feature/dashboard/DesignTokenWiringTest.kt
    - android/feature/onboarding/src/test/kotlin/app/dqxn/android/feature/onboarding/DesignTokenWiringTest.kt
  modified: []

key-decisions:
  - "assertWithMessage() over deprecated .named() for Truth assertions -- consistent with Phase 9 decision"
  - "JUnit5 @Test over @RunWith(RobolectricTestRunner) -- pure file I/O tests need no Android framework"
  - "File(user.dir, 'src/main/kotlin') over plan's 'feature/X/src/main/kotlin' -- Gradle sets user.dir to module root, not android/"

patterns-established:
  - "Source-scanning test pattern: File.walk() + filter + assertWithMessage().that().doesNotContain()"
  - "Module-root-relative paths: File(System.getProperty('user.dir'), 'src/main/kotlin') for source scanning"
  - "Shrinking allowlist pattern: allowlist entries documented with owning plan, validated for existence and staleness"

requirements-completed: []

# Metrics
duration: 58min
completed: 2026-02-27
---

# Phase 14 Plan 13: Design Token Wiring Tests Summary

**Source-scanning regression gate across 3 feature modules enforcing dashboard design tokens over MaterialTheme with documented shrinking allowlists**

## Performance

- **Duration:** 58 min
- **Started:** 2026-02-27T05:37:05Z
- **Completed:** 2026-02-27T06:35:00Z
- **Tasks:** 2
- **Files modified:** 3

## Accomplishments
- DesignTokenWiringTest in :feature:settings, :feature:dashboard, and :feature:onboarding
- All tests scan .kt files recursively under src/main/kotlin for MaterialTheme.typography, MaterialTheme.colorScheme, and MaterialTheme import
- Documented allowlists: settings (1 file), dashboard (6 files), onboarding (4 files) -- each with owning plan reference
- Allowlist validation tests ensure entries exist and are not stale (still use MaterialTheme)
- Dashboard and onboarding tests verified passing

## Task Commits

Each task was committed atomically:

1. **Task 1: Create DesignTokenWiringTest in :feature:settings** - `84d5616` (test)
2. **Task 2: Create DesignTokenWiringTest in :feature:dashboard and :feature:onboarding** - `d6b77be` (test)

## Files Created/Modified
- `android/feature/settings/src/test/kotlin/app/dqxn/android/feature/settings/DesignTokenWiringTest.kt` - Scans settings source for prohibited MaterialTheme usage, allowlists NfD1Disclaimer.kt
- `android/feature/dashboard/src/test/kotlin/app/dqxn/android/feature/dashboard/DesignTokenWiringTest.kt` - Scans dashboard source, allowlists 6 files owned by plans 14-02/04/05/07
- `android/feature/onboarding/src/test/kotlin/app/dqxn/android/feature/onboarding/DesignTokenWiringTest.kt` - Scans onboarding source, allowlists 4 files deferred to Phase 15

## Decisions Made
- **assertWithMessage() over .named()** -- .named() deprecated in current Truth version (consistent with Phase 9 decision)
- **JUnit5 @Test over @RunWith(RobolectricTestRunner)** -- pure file I/O tests need no Android framework, avoids unnecessary Robolectric overhead
- **File(user.dir, 'src/main/kotlin')** over plan's `feature/X/src/main/kotlin` -- Gradle sets user.dir to the module root (e.g., android/feature/settings), not the android/ project root. Plan's path was incorrect and would resolve to a nonexistent directory

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed source directory path resolution**
- **Found during:** Task 1
- **Issue:** Plan specified `File(System.getProperty("user.dir"), "feature/settings/src/main/kotlin")` but Gradle sets user.dir to the module root (android/feature/settings/), so the path would resolve to android/feature/settings/feature/settings/src/main/kotlin (nonexistent)
- **Fix:** Used `File(System.getProperty("user.dir"), "src/main/kotlin")` for all 3 modules
- **Files modified:** All 3 DesignTokenWiringTest.kt files
- **Verification:** Tests pass with correct source directory resolution

**2. [Rule 1 - Bug] Replaced deprecated .named() with assertWithMessage()**
- **Found during:** Task 2 (dashboard test compilation)
- **Issue:** Truth .named() is deprecated/unavailable in current version. Compilation error: "Unresolved reference 'named'"
- **Fix:** Replaced `assertThat(x).named("msg").doesNotContain()` with `assertWithMessage("msg").that(x).doesNotContain()` across all 3 files
- **Files modified:** All 3 DesignTokenWiringTest.kt files
- **Verification:** Dashboard and onboarding tests compile and pass

**3. [Rule 1 - Bug] Removed unnecessary Robolectric runner**
- **Found during:** Task 1
- **Issue:** Plan specified @RunWith(RobolectricTestRunner::class) but tests are pure file I/O -- no Android framework needed
- **Fix:** Used JUnit5 @Test (org.junit.jupiter.api.Test) without @RunWith
- **Files modified:** All 3 DesignTokenWiringTest.kt files
- **Verification:** Tests run as JUnit5 tests on JUnit Platform

---

**Total deviations:** 3 auto-fixed (3 bugs)
**Impact on plan:** All fixes necessary for correctness. No scope creep. Plan's intent fully preserved.

## Issues Encountered
- **Gradle daemon instability:** Daemon crashed repeatedly during verification (OOM with too many stopped daemons). Resolved by clearing daemon registry directory
- **Pre-existing compilation errors in :feature:settings test suite:** Other Phase 14 test files (ThemeSelectorTest, ThemeStudioTest, WidgetSettingsSheetTest, etc.) have compilation errors from unrealized Phase 14 plan API changes. These prevent running settings DesignTokenWiringTest but are out of scope. Dashboard and onboarding tests verified passing
- **Build cache corruption:** Test results binary files missing from cache. Resolved with --no-build-cache flag

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Regression gate active: any new MaterialTheme usage in non-allowlisted files will fail tests
- As Phase 14 plans execute and migrate allowlisted files, those entries should be removed from the respective allowlists
- Settings test will become runnable once other Phase 14 plans fix the pre-existing test compilation errors

## Self-Check: PASSED

All files verified present on disk. All commits verified in git history.

---
*Phase: 14-ui-visual-interactive-parity*
*Completed: 2026-02-27*
