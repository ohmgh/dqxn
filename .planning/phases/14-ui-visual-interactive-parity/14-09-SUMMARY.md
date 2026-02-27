---
phase: 14-ui-visual-interactive-parity
plan: 09
subsystem: ui
tags: [compose, navigation, settings, overlay-routes, theme-routing]

# Dependency graph
requires:
  - phase: 10-settings-pack-browser
    provides: MainSettings composable, OverlayNavHost wiring, OverlayRoutes
provides:
  - Restructured MainSettings with old codebase item order (About Banner, Dash Packs, Theme Items, Status Bar, Reset Dash, Advanced)
  - AutoSwitchModeRoute for theme mode selection
  - ThemeSelectorRoute(isDark) parameterization for light/dark theme filtering
  - AboutAppBanner, SettingsItemRow, ResetDashRow composables
  - SetAutoSwitchMode and SetIlluminanceThreshold DashboardCommands
affects: [14-07, 14-08, 14-12, phase-15]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Source-varying transitions include AutoSwitchModeRoute alongside ThemeSelectorRoute"
    - "Route parameterization (data class instead of data object) for filtering"
    - "Source file verification test pattern (File.readText assertions)"

key-files:
  created: []
  modified:
    - android/feature/settings/src/main/kotlin/app/dqxn/android/feature/settings/main/MainSettings.kt
    - android/feature/dashboard/src/main/kotlin/app/dqxn/android/feature/dashboard/layer/OverlayRoutes.kt
    - android/feature/dashboard/src/main/kotlin/app/dqxn/android/feature/dashboard/layer/OverlayNavHost.kt
    - android/feature/dashboard/src/main/kotlin/app/dqxn/android/feature/dashboard/command/DashboardCommand.kt
    - android/feature/dashboard/src/main/kotlin/app/dqxn/android/feature/dashboard/coordinator/ThemeCoordinator.kt
    - android/feature/dashboard/src/main/kotlin/app/dqxn/android/feature/dashboard/DashboardViewModel.kt
    - android/feature/settings/src/main/res/values/strings.xml
    - android/feature/settings/src/test/kotlin/app/dqxn/android/feature/settings/main/MainSettingsTest.kt
    - android/feature/dashboard/src/test/kotlin/app/dqxn/android/feature/dashboard/layer/OverlayNavHostRouteTest.kt
    - android/feature/settings/src/test/kotlin/app/dqxn/android/feature/settings/accessibility/TalkBackAccessibilityTest.kt
    - android/feature/settings/src/test/kotlin/app/dqxn/android/feature/settings/accessibility/FontScaleTest.kt

key-decisions:
  - "ThemeSelectorRoute changed from data object to data class(isDark: Boolean = false) for light/dark theme filtering"
  - "AutoSwitchModeRoute added as data object with preview-type transitions matching ThemeSelectorRoute"
  - "Old codebase flat list layout replaces 4-section layout (APPEARANCE/BEHAVIOR/DATA & PRIVACY/DANGER ZONE removed)"
  - "Advanced section retained for Keep Screen On, Analytics, Diagnostics, Delete All Data"

patterns-established:
  - "SettingsItemRow: 40dp rounded-square icon box, accentColor.copy(alpha=0.1f) background, 24dp icon tinted accentColor"
  - "ResetDashRow: SemanticColors.Error text, no icon, destructive action styling"
  - "AboutAppBanner: centered column with circular avatar, tagline, attribution, app name, version"

requirements-completed: [F1.9, F4.6]

# Metrics
duration: ~45min
completed: 2026-02-27
---

# Phase 14 Plan 09: Settings UI Parity Summary

**Restructured MainSettings to old codebase item order with About banner, icon rows, dynamic subtitles, Reset Dash, and fixed theme routing via AutoSwitchModeRoute + ThemeSelectorRoute(isDark)**

## Performance

- **Duration:** ~45 min (across 2 conversation sessions due to parallel executor interference)
- **Started:** 2026-02-27T06:00:00Z
- **Completed:** 2026-02-27T06:49:00Z
- **Tasks:** 2/2
- **Files modified:** 11

## Accomplishments
- MainSettings restructured from 4-section layout to old codebase flat list: About Banner -> Dash Packs -> Theme Items -> Status Bar -> Reset Dash -> Advanced
- AutoSwitchModeRoute added with preview-type transitions, wired to AutoSwitchModeContent in OverlayNavHost
- ThemeSelectorRoute parameterized with isDark for light/dark theme filtering
- 23 MainSettings tests (12 new + 11 updated), 16 OverlayNavHostRouteTest tests, updated accessibility and font scale tests
- SetAutoSwitchMode and SetIlluminanceThreshold DashboardCommands added with ThemeCoordinator + ViewModel routing

## Task Commits

Each task was committed atomically:

1. **Task 1: Add AutoSwitchModeRoute, update ThemeSelectorRoute, restructure MainSettings, fix routing** - `8de39ff` (feat)
2. **Task 2: Update all tests for restructured MainSettings and route changes** - `838e5d8` (test)

## Files Created/Modified
- `android/feature/settings/src/main/kotlin/app/dqxn/android/feature/settings/main/MainSettings.kt` - Restructured to old codebase item order with AboutAppBanner, SettingsItemRow, ResetDashRow, new params
- `android/feature/dashboard/src/main/kotlin/app/dqxn/android/feature/dashboard/layer/OverlayRoutes.kt` - Added AutoSwitchModeRoute, changed ThemeSelectorRoute to data class(isDark)
- `android/feature/dashboard/src/main/kotlin/app/dqxn/android/feature/dashboard/layer/OverlayNavHost.kt` - Wired AutoSwitchModeRoute, updated Settings transitions, added isDark + onCreateNewTheme to ThemeSelector
- `android/feature/dashboard/src/main/kotlin/app/dqxn/android/feature/dashboard/command/DashboardCommand.kt` - Added SetAutoSwitchMode and SetIlluminanceThreshold commands
- `android/feature/dashboard/src/main/kotlin/app/dqxn/android/feature/dashboard/coordinator/ThemeCoordinator.kt` - Added illuminanceThreshold to ThemeState, command handlers
- `android/feature/dashboard/src/main/kotlin/app/dqxn/android/feature/dashboard/DashboardViewModel.kt` - Routed new commands to ThemeCoordinator
- `android/feature/settings/src/main/res/values/strings.xml` - Added 7 string resources
- `android/feature/settings/src/test/kotlin/app/dqxn/android/feature/settings/main/MainSettingsTest.kt` - 23 tests covering item order, banner, subtitles, navigation, reset dash, icon styling
- `android/feature/dashboard/src/test/kotlin/app/dqxn/android/feature/dashboard/layer/OverlayNavHostRouteTest.kt` - 16 tests for 10 routes, AutoSwitchModeRoute, ThemeSelectorRoute(isDark)
- `android/feature/settings/src/test/kotlin/app/dqxn/android/feature/settings/accessibility/TalkBackAccessibilityTest.kt` - Updated for flat layout (ADVANCED section only), added light/dark theme navigation rows
- `android/feature/settings/src/test/kotlin/app/dqxn/android/feature/settings/accessibility/FontScaleTest.kt` - Updated expectedTextNodes for new layout, added new MainSettings params

## Decisions Made
- ThemeSelectorRoute changed from data object to data class(isDark: Boolean = false) -- enables separate light/dark theme selector screens from MainSettings
- AutoSwitchModeRoute uses preview-type transitions (previewEnter/previewExit) matching ThemeSelectorRoute pattern
- Old 4-section layout (APPEARANCE/BEHAVIOR/DATA & PRIVACY/DANGER ZONE) replaced with flat list; only "Advanced" section header retained for items not in old codebase

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] Added SetAutoSwitchMode and SetIlluminanceThreshold DashboardCommands**
- **Found during:** Task 1
- **Issue:** Plan specified wiring AutoSwitchModeContent but the required DashboardCommand subtypes didn't exist
- **Fix:** Added both command subtypes to DashboardCommand.kt, handlers in ThemeCoordinator, routing in DashboardViewModel
- **Files modified:** DashboardCommand.kt, ThemeCoordinator.kt, DashboardViewModel.kt
- **Verification:** Compilation passes
- **Committed in:** 8de39ff

**2. [Rule 1 - Bug] Fixed TalkBackAccessibilityTest and FontScaleTest for new layout**
- **Found during:** Task 2
- **Issue:** Tests referenced old 4-section headers (APPEARANCE, BEHAVIOR, DATA & PRIVACY, DANGER ZONE) that no longer exist
- **Fix:** Updated to check ADVANCED section header only, added light/dark theme navigation rows, updated expectedTextNodes
- **Files modified:** TalkBackAccessibilityTest.kt, FontScaleTest.kt
- **Verification:** All accessibility and font scale tests pass
- **Committed in:** 838e5d8

---

**Total deviations:** 2 auto-fixed (1 missing critical, 1 bug)
**Impact on plan:** Both auto-fixes necessary for correctness. No scope creep.

## Issues Encountered
- Parallel executor repeatedly deleted test files from disk during Task 2 execution, requiring multiple rewrites and immediate git staging to protect files
- Parallel executor's ThemeStudioStateHolderTest.kt and WidgetPickerTest.kt have pre-existing failures (5 total) -- out of scope, not caused by this plan's changes
- Build system race conditions (NoSuchFileException on test result files) when multiple Gradle processes compete

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- MainSettings fully restructured with old codebase parity
- Theme routing complete: AutoSwitchModeRoute for mode selection, ThemeSelectorRoute(isDark) for theme browsing
- Plans 14-07, 14-08, 14-12 can proceed without conflicts

## Self-Check: PASSED

- All 7 key files exist on disk
- Commit 8de39ff (Task 1) exists in git history
- Commit 838e5d8 (Task 2) exists in git history

---
*Phase: 14-ui-visual-interactive-parity*
*Completed: 2026-02-27*
