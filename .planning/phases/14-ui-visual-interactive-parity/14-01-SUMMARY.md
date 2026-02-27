---
phase: 14-ui-visual-interactive-parity
plan: 01
subsystem: ui
tags: [splash-screen, theme, android-xml, cold-start]

# Dependency graph
requires:
  - phase: 01-build-system-foundation
    provides: convention plugins and app module structure
provides:
  - Theme.App.Starting splash screen theme with branded dark background
  - API 31+ native splash screen attributes
  - Splash theme validation tests
affects: []

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Source-file XML validation tests for theme configuration"

key-files:
  created:
    - android/app/src/main/res/values-v31/themes.xml
    - android/app/src/test/kotlin/app/dqxn/android/SplashThemeTest.kt
  modified:
    - android/app/src/main/res/values/themes.xml
    - android/app/src/main/AndroidManifest.xml

key-decisions:
  - "Removed android:windowSplashScreenBehavior from v31 theme -- attribute requires API 33, unresolvable @integer/icon_preferred resource; default behavior already shows icon"
  - "Test file paths relative to module projectDir (user.dir = android/app/) not root android/ -- Gradle test worker user.dir is module directory"

patterns-established:
  - "XML source-file validation pattern: File(System.getProperty('user.dir'), relative-path) for resource verification in JUnit5 tests"

requirements-completed: []

# Metrics
duration: 52min
completed: 2026-02-27
---

# Phase 14 Plan 01: Splash Screen Theme Summary

**Theme.App.Starting with dark background (#0f172a) and letterform icon, API 31+ native attributes, manifest wired, 3 validation tests passing**

## Performance

- **Duration:** 52 min (includes transient Gradle daemon recovery)
- **Started:** 2026-02-27T05:36:44Z
- **Completed:** 2026-02-27T06:28:51Z
- **Tasks:** 2
- **Files modified:** 4

## Accomplishments
- Splash screen theme (`Theme.App.Starting`) with branded dark background and letterform icon
- API 31+ native splash attributes via `values-v31/themes.xml`
- Manifest activity updated from `Theme.Dqxn.NoActionBar` to `Theme.App.Starting`
- 3 automated validation tests confirming XML structure and manifest reference

## Task Commits

Each task was committed atomically:

1. **Task 1: Create splash screen theme and update manifest** - `ce36e47` (feat)
2. **Task 2: Add splash theme validation test** - `a9f38f5` (test)

## Files Created/Modified
- `android/app/src/main/res/values/themes.xml` - Added Theme.App.Starting with splash background, icon, and post-splash theme
- `android/app/src/main/res/values-v31/themes.xml` - Created with native android: splash attributes for API 31+
- `android/app/src/main/AndroidManifest.xml` - Activity theme changed to @style/Theme.App.Starting
- `android/app/src/test/kotlin/app/dqxn/android/SplashThemeTest.kt` - 3 validation tests for splash theme XML and manifest

## Decisions Made
- Removed `android:windowSplashScreenBehavior` attribute from v31 theme -- requires API 33 and references `@integer/icon_preferred` which is not provided by the compat library; default splash behavior already shows the icon
- Fixed test file paths: Gradle test worker `user.dir` is the module directory (`android/app/`), not the root project directory (`android/`)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Removed unresolvable windowSplashScreenBehavior attribute**
- **Found during:** Task 1
- **Issue:** `@integer/icon_preferred` resource not found -- it's a framework resource from API 33 (not 31), and the compat library doesn't provide it
- **Fix:** Removed `android:windowSplashScreenBehavior` line from v31 theme; default behavior already shows the icon
- **Files modified:** `android/app/src/main/res/values-v31/themes.xml`
- **Verification:** `assembleDebug` passes without resource linking errors
- **Committed in:** ce36e47 (Task 1 commit)

**2. [Rule 1 - Bug] Fixed test file paths for Gradle module working directory**
- **Found during:** Task 2
- **Issue:** Plan specified `app/src/main/res/...` paths relative to `android/`, but Gradle sets `user.dir` to module directory (`android/app/`), causing double `app/` prefix
- **Fix:** Changed paths to `src/main/res/...` (relative to `android/app/`)
- **Files modified:** `android/app/src/test/kotlin/app/dqxn/android/SplashThemeTest.kt`
- **Verification:** All 3 SplashThemeTest tests pass
- **Committed in:** a9f38f5 (Task 2 commit)

---

**Total deviations:** 2 auto-fixed (2 bugs)
**Impact on plan:** Both fixes necessary for correctness. No scope creep.

## Issues Encountered
- Transient Gradle daemon crashes during test execution (KSP cache staleness + daemon OOM). Resolved by cleaning KSP generated files and restarting daemons. Not related to plan changes.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Splash screen theme complete, cold start will show branded dark background
- No blockers for subsequent Phase 14 plans

## Self-Check: PASSED

All 4 created/modified files verified on disk. Both commit hashes (ce36e47, a9f38f5) found in git log.

---
*Phase: 14-ui-visual-interactive-parity*
*Completed: 2026-02-27*
