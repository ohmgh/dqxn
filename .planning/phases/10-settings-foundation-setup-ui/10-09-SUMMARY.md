---
phase: 10-settings-foundation-setup-ui
plan: 09
subsystem: ui
tags: [compose, hilt, viewmodel, datastore, analytics, gdpr, pdpa, robolectric]

# Dependency graph
requires:
  - phase: 10-01
    provides: "clearAll() on all 6 DataStores + analyticsConsent preference"
  - phase: 10-04
    provides: "SettingRowDispatcher pattern for row composables"
  - phase: 10-02
    provides: "OverlayScaffold + OverlayType.Hub + DashboardMotion dialog animations"
provides:
  - "MainSettingsViewModel with deleteAllData (6 stores) and analytics consent toggle"
  - "MainSettings 4-section composable (Appearance, Behavior, Data & Privacy, Danger Zone)"
  - "AnalyticsConsentDialog with PDPA/GDPR explanation and opt-in flow"
  - "DeleteAllDataDialog with destructive confirmation"
  - "Diagnostics navigation stub for Phase 11"
affects: [11-theme-ui-diagnostics-onboarding, settings-integration]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Nested AnimatedVisibility for dialog scrim + card spring animations"
    - "useUnmergedTree=true for test tag assertions on semantics-merged dialog content"
    - "mainClock.advanceTimeBy(5000) for spring animation settlement in Robolectric compose tests"
    - "performScrollToNode for testing items below scroll fold"

key-files:
  created:
    - android/feature/settings/src/main/kotlin/app/dqxn/android/feature/settings/main/MainSettingsViewModel.kt
    - android/feature/settings/src/main/kotlin/app/dqxn/android/feature/settings/main/MainSettings.kt
    - android/feature/settings/src/main/kotlin/app/dqxn/android/feature/settings/main/AnalyticsConsentDialog.kt
    - android/feature/settings/src/main/kotlin/app/dqxn/android/feature/settings/main/DeleteAllDataDialog.kt
    - android/feature/settings/src/test/kotlin/app/dqxn/android/feature/settings/main/MainSettingsViewModelTest.kt
    - android/feature/settings/src/test/kotlin/app/dqxn/android/feature/settings/main/MainSettingsTest.kt
  modified:
    - android/feature/settings/build.gradle.kts
    - android/feature/settings/src/main/res/values/strings.xml

key-decisions:
  - "useUnmergedTree=true for dialog tag assertions -- clickable modifier merges child semantics, making testTag invisible in merged tree"
  - ":sdk:analytics dependency added to :feature:settings -- AnalyticsTracker.setEnabled() needed by ViewModel, not included by convention plugin"
  - "Disable tracker BEFORE persist on consent revoke, enable AFTER persist on consent grant -- ordering prevents data collection during failed persist"

patterns-established:
  - "Nested AnimatedVisibility dialog pattern: outer scrim (dialogScrimEnter/Exit) + inner card (dialogEnter/Exit)"
  - "mainClock.advanceTimeBy(5000) + waitForIdle() for DashboardMotion spring animation settlement in tests"
  - "performScrollToNode(hasText/hasTestTag) for testing scrollable settings content"

requirements-completed: [F12.5, F14.2, F14.4]

# Metrics
duration: 12min
completed: 2026-02-25
---

# Phase 10 Plan 09: Main Settings Summary

**MainSettingsViewModel with 6-store deleteAllData + PDPA/GDPR analytics consent toggle, 4-section MainSettings composable with animated consent/delete dialogs, 15 tests**

## Performance

- **Duration:** 12 min
- **Started:** 2026-02-25T04:22:00Z
- **Completed:** 2026-02-25T04:36:06Z
- **Tasks:** 2
- **Files modified:** 8

## Accomplishments
- MainSettingsViewModel with deleteAllData clearing all 6 DataStores (UserPreferences, ProviderSettings, Layout, WidgetStyle, PairedDevice, ConnectionEvent) plus disabling analytics tracker
- Analytics consent toggle: opt-in dialog before enabling (PDPA/GDPR), immediate disable without dialog, correct disable-before-persist / enable-after-persist ordering
- 4-section MainSettings composable (Appearance, Behavior, Data & Privacy, Danger Zone) in OverlayScaffold Hub
- AnalyticsConsentDialog explaining collected data types and right to revoke
- DeleteAllDataDialog with destructive red styling
- Diagnostics navigation row stub for Phase 11
- 15 automated tests: 5 ViewModel (JUnit5 + MockK) + 10 composable (Robolectric + JUnit4)

## Task Commits

Each task was committed atomically:

1. **Task 1: MainSettingsViewModel + delete all data + analytics consent** - `acb7f67` (feat)
2. **Task 2: MainSettings composable + dialogs + compose test** - `41552cf` (feat)

## Files Created/Modified
- `android/feature/settings/src/main/kotlin/.../main/MainSettingsViewModel.kt` - @HiltViewModel with 7 injected deps, analyticsConsent StateFlow, setAnalyticsConsent(), deleteAllData()
- `android/feature/settings/src/main/kotlin/.../main/MainSettings.kt` - 4-section settings screen with SectionHeader, ToggleRow, NavigationRow, AnalyticsToggleRow, DeleteAllDataButton composables
- `android/feature/settings/src/main/kotlin/.../main/AnalyticsConsentDialog.kt` - Consent explanation dialog with nested AnimatedVisibility, scrollable body, confirm/cancel buttons
- `android/feature/settings/src/main/kotlin/.../main/DeleteAllDataDialog.kt` - Destructive confirmation dialog with red styling, nested AnimatedVisibility
- `android/feature/settings/src/test/kotlin/.../main/MainSettingsViewModelTest.kt` - 5 tests: default consent, enable/disable ordering, deleteAllData all 6 stores, stateIn wiring
- `android/feature/settings/src/test/kotlin/.../main/MainSettingsTest.kt` - 10 Robolectric tests: 4 sections, analytics dialog flow, delete dialog flow, diagnostics nav, consent body content
- `android/feature/settings/build.gradle.kts` - Added `:sdk:analytics` dependency
- `android/feature/settings/src/main/res/values/strings.xml` - 25 string resources for sections, rows, dialogs

## Decisions Made
- **useUnmergedTree=true for dialog tag assertions**: clickable modifier on dialog scrim merges child semantics, making inner testTag nodes invisible to default finders. The compose-test error message explicitly suggests this fix.
- **:sdk:analytics added to :feature:settings**: The `dqxn.android.feature` convention plugin provides `:sdk:contracts`, `:sdk:common`, `:sdk:ui`, `:sdk:observability` but not `:sdk:analytics`. MainSettingsViewModel needs AnalyticsTracker.setEnabled().
- **Disable tracker BEFORE persist on consent revoke**: Prevents data collection during the window between tracker call and preference write. Enable AFTER persist on grant ensures persistence succeeded before activating collection.
- **assertExists() over assertIsDisplayed() for dialog content**: Dialog card rendered inside nested AnimatedVisibility may be clipped by Robolectric viewport bounds. assertExists() verifies the node is in the tree without requiring it to be within visible bounds.
- **performScrollToNode helpers for scrollable settings**: With 4 sections, items below the fold fail assertIsDisplayed(). scrollTo(text)/scrollToTag(tag) helpers use performScrollToNode on the main_settings_content column.
- **mainClock.advanceTimeBy(5000) for spring animations**: DashboardMotion dialog animations use spring specs (dampingRatio=0.65, stiffness=300) that need ~5s of virtual time to settle in tests.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Added :sdk:analytics dependency to :feature:settings**
- **Found during:** Task 1 (MainSettingsViewModel)
- **Issue:** AnalyticsTracker import failed -- :feature:settings didn't depend on :sdk:analytics
- **Fix:** Added `implementation(project(":sdk:analytics"))` to build.gradle.kts
- **Files modified:** android/feature/settings/build.gradle.kts
- **Verification:** Compilation succeeds, all tests pass
- **Committed in:** acb7f67

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** Necessary dependency addition. No scope creep.

## Issues Encountered
- **Semantics merging in compose tests**: Dialog tag assertions initially failed because clickable modifier on scrim Box merges child semantics. The error message helpfully suggested useUnmergedTree=true. Applied to all dialog existence assertions.
- **Ambiguous substring match for consent body text**: "anonymous usage data" matched both the AnalyticsToggleRow description and dialog body. Fixed by using more specific substring "collects anonymous usage data" which only appears in the dialog.
- **Spring animation timing in Robolectric**: DashboardMotion spring animations don't auto-settle in test clock. Required explicit mainClock.advanceTimeBy(5000) before asserting dialog content existence.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- MainSettings screen ready for OverlayNavHost integration (Plan 10-10)
- Diagnostics navigation stub ready for Phase 11 implementation
- All 6 DataStore clearAll() paths tested and functional for GDPR compliance
- Analytics consent flow complete with proper PDPA/GDPR opt-in pattern

## Self-Check: PASSED

- All 6 created files verified on disk
- Both task commits (acb7f67, 41552cf) found in git log
- 15/15 tests pass (5 ViewModel + 10 composable)

---
*Phase: 10-settings-foundation-setup-ui*
*Completed: 2026-02-25*
