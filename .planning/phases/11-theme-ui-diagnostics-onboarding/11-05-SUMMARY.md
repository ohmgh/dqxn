---
phase: 11-theme-ui-diagnostics-onboarding
plan: 05
subsystem: ui
tags: [compose, theme, preview, entitlement, state-holder]

# Dependency graph
requires:
  - phase: 11-01
    provides: ThemeCoordinator with preview/revert cycle
provides:
  - ThemeSelector composable with free-first ordering and preview lifecycle
  - ThemeStudioStateHolder with isDirty derivation and buildCustomTheme
affects: [11-06, 11-07, 11-08]

# Tech tracking
tech-stack:
  added: []
  patterns: [combinedClickable for tap/long-press/double-tap, Snapshot.withMutableSnapshot for derivedStateOf testing, mainClock.advanceTimeBy for preview timeout testing]

key-files:
  created:
    - android/feature/settings/src/main/kotlin/app/dqxn/android/feature/settings/theme/ThemeStudioStateHolder.kt
    - android/feature/settings/src/test/kotlin/app/dqxn/android/feature/settings/theme/ThemeStudioStateHolderTest.kt
    - android/feature/settings/src/main/kotlin/app/dqxn/android/feature/settings/theme/ThemeSelector.kt
    - android/feature/settings/src/test/kotlin/app/dqxn/android/feature/settings/theme/ThemeSelectorTest.kt
  modified: []

key-decisions:
  - "combinedClickable with onDoubleClick delays single-click by double-click detection window -- tests must advanceTimeBy(500) after click for callback to fire"
  - "Brush excluded from isDirty comparison -- Brush does not override equals meaningfully (gradient brushes are stateless lambda wrappers); color+isDark changes sufficient"
  - "Snapshot.withMutableSnapshot for derivedStateOf testing in JUnit5 -- enables state reads without Compose UI test rule"

patterns-established:
  - "combinedClickable double-click timeout: advanceTimeBy(500) after performTouchInput { click() } in tests"
  - "Snapshot.withMutableSnapshot {} for reading/writing Compose state in pure JUnit5 tests"
  - "Theme ordering: free -> custom -> premium classification via themeId prefix and requiredAnyEntitlement"

requirements-completed: [F4.6, F4.7, F4.9, F4.10, F4.12, F4.13]

# Metrics
duration: 8min
completed: 2026-02-25
---

# Phase 11 Plan 05: ThemeSelector + ThemeStudioStateHolder Summary

**ThemeSelector with free-first ordering, 60s preview timeout, clone-to-custom, and entitlement-gated apply; ThemeStudioStateHolder with derivedStateOf isDirty tracking**

## Performance

- **Duration:** 8 min
- **Started:** 2026-02-25T08:11:27Z
- **Completed:** 2026-02-25T08:19:27Z
- **Tasks:** 2
- **Files modified:** 4

## Accomplishments
- ThemeStudioStateHolder decomposes custom theme editing into 8 individual mutable state properties with derivedStateOf isDirty tracking against saved values
- ThemeSelector implements free-first ordering (free -> custom -> premium), 60s preview timeout via LaunchedEffect, preview-regardless-of-entitlement with gate-at-apply, clone built-in to custom via long-press, and DisposableEffect dual cleanup
- 11 tests total: 5 JUnit5 (state holder) + 6 JUnit4/Robolectric (selector composable)

## Task Commits

Each task was committed atomically:

1. **Task 1: ThemeStudioStateHolder + tests** - `991d3c9` (feat)
2. **Task 2: ThemeSelector composable + tests** - `92b6e9e` (feat)

## Files Created/Modified
- `android/feature/settings/src/main/kotlin/app/dqxn/android/feature/settings/theme/ThemeStudioStateHolder.kt` - Decomposed state holder with 8 mutable color/brush properties, isDirty derivation, and buildCustomTheme output
- `android/feature/settings/src/test/kotlin/app/dqxn/android/feature/settings/theme/ThemeStudioStateHolderTest.kt` - 5 tests: isDirty false/true, buildCustomTheme output, null defaults, themeId
- `android/feature/settings/src/main/kotlin/app/dqxn/android/feature/settings/theme/ThemeSelector.kt` - Theme browser with free-first ordering, preview lifecycle, clone, entitlement revocation, disposal cleanup
- `android/feature/settings/src/test/kotlin/app/dqxn/android/feature/settings/theme/ThemeSelectorTest.kt` - 6 tests: ordering, timeout, lock icon, clone, dispose cleanup, gated preview

## Decisions Made
- **combinedClickable double-click timeout**: `combinedClickable` with `onDoubleClick` parameter delays `onClick` by a double-click detection window (~300ms). Tests must advance the clock by 500ms after `performTouchInput { click() }` for the callback to fire. This is a general pattern for any composable using `combinedClickable` with `onDoubleClick`.
- **Brush excluded from isDirty**: `Brush` does not override `equals` meaningfully -- gradient brushes are stateless lambda wrappers. Color + isDark changes are sufficient to detect user edits. Brush changes are always coupled with color picker interactions.
- **Snapshot.withMutableSnapshot for JUnit5 testing**: `derivedStateOf` requires snapshot context for correct reads/writes. `Snapshot.withMutableSnapshot {}` enables state reads in pure JUnit5 without requiring a Compose UI test rule.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
- `combinedClickable` with `onDoubleClick` delays single-click callback. Initial test used `performClick()` which didn't trigger the callback. Fixed by using `performTouchInput { click() }` followed by `mainClock.advanceTimeBy(500)` to advance past the double-click detection window.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- ThemeSelector and ThemeStudioStateHolder ready for integration with ThemeSettingsSheet (Plan 06)
- sortThemes function exposed as internal for direct unit testing of ordering logic
- Preview timeout constant (PREVIEW_TIMEOUT_MS) and max theme limit (MAX_CUSTOM_THEMES) exposed as internal constants for test verification

## Self-Check: PASSED

All 4 created files verified present. Both task commits (991d3c9, 92b6e9e) verified in git log.

---
*Phase: 11-theme-ui-diagnostics-onboarding*
*Completed: 2026-02-25*
