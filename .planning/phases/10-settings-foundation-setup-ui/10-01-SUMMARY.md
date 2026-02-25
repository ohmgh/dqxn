---
phase: 10-settings-foundation-setup-ui
plan: 01
subsystem: data
tags: [datastore, preferences, semantic-colors, clearall, analytics-consent]

# Dependency graph
requires:
  - phase: 05-core-infrastructure
    provides: DataStore repositories, ProviderSettingsStore, WidgetStyleStore, PairedDeviceStore
  - phase: 02-sdk-contracts-common
    provides: ProviderSettingsStore interface in :sdk:contracts
provides:
  - clearAll() on all 5 data interfaces (UserPreferences, ProviderSettings, Layout, WidgetStyle, PairedDevice)
  - analyticsConsent preference flow in UserPreferencesRepository
  - SemanticColors object with Info/Warning/Success/Error tokens in :core:design
affects: [10-settings-foundation-setup-ui, 11-theme-ui-diagnostics-onboarding]

# Tech tracking
tech-stack:
  added: []
  patterns: [clearAll-via-datastore-clear, semantic-color-tokens-static-not-themed]

key-files:
  created:
    - android/core/design/src/main/kotlin/app/dqxn/android/core/design/token/SemanticColors.kt
    - android/core/design/src/test/kotlin/app/dqxn/android/core/design/token/SemanticColorsTest.kt
  modified:
    - android/data/src/main/kotlin/app/dqxn/android/data/preferences/UserPreferencesRepository.kt
    - android/data/src/main/kotlin/app/dqxn/android/data/preferences/UserPreferencesRepositoryImpl.kt
    - android/data/src/main/kotlin/app/dqxn/android/data/preferences/PreferenceKeys.kt
    - android/sdk/contracts/src/main/kotlin/app/dqxn/android/sdk/contracts/settings/ProviderSettingsStore.kt
    - android/data/src/main/kotlin/app/dqxn/android/data/provider/ProviderSettingsStoreImpl.kt
    - android/data/src/main/kotlin/app/dqxn/android/data/layout/LayoutRepository.kt
    - android/data/src/main/kotlin/app/dqxn/android/data/layout/LayoutRepositoryImpl.kt
    - android/data/src/main/kotlin/app/dqxn/android/data/style/WidgetStyleStore.kt
    - android/data/src/main/kotlin/app/dqxn/android/data/style/WidgetStyleStoreImpl.kt
    - android/data/src/main/kotlin/app/dqxn/android/data/device/PairedDeviceStore.kt
    - android/data/src/main/kotlin/app/dqxn/android/data/device/PairedDeviceStoreImpl.kt
    - android/feature/dashboard/src/testFixtures/kotlin/app/dqxn/android/feature/dashboard/test/FakeLayoutRepository.kt

key-decisions:
  - "FakeLayoutRepository updated alongside real impl to keep interface contract consistent"
  - "PairedDeviceStore clearAll uses Proto DataStore getDefaultInstance() (empty proto) not Preferences clear()"
  - "LayoutRepository clearAll resets to FallbackLayout.createFallbackStore() maintaining the invariant of never having zero profiles"

patterns-established:
  - "clearAll pattern: Preferences DataStore uses edit { it.clear() }, Proto DataStore uses updateData { DefaultInstance } or direct state reset"
  - "SemanticColors as static object (not per-theme) for consistent informational state meaning"

requirements-completed: [F12.5, F14.4]

# Metrics
duration: 4min
completed: 2026-02-25
---

# Phase 10 Plan 01: Data Layer Extensions + Semantic Colors Summary

**clearAll() on all 5 data stores for Delete All Data (F14.4), analyticsConsent opt-in preference (F12.5), and static semantic color tokens for settings UI**

## Performance

- **Duration:** 4 min
- **Started:** 2026-02-25T03:51:08Z
- **Completed:** 2026-02-25T03:56:01Z
- **Tasks:** 2
- **Files modified:** 19

## Accomplishments
- Added `clearAll()` to all 5 data interfaces: UserPreferencesRepository, ProviderSettingsStore, LayoutRepository, WidgetStyleStore, PairedDeviceStore
- Added `analyticsConsent` flow (default false, opt-in per PDPA/GDPR) to UserPreferencesRepository
- Created SemanticColors object with Info/Warning/Success/Error static color tokens in `:core:design`
- All new methods fully tested with 10+ new test cases across 7 test files
- Updated FakeLayoutRepository testFixture to maintain interface contract

## Task Commits

Each task was committed atomically:

1. **Task 1: Data layer clearAll + analyticsConsent extensions** - `e6a7f4c` (feat)
2. **Task 2: SemanticColors + tests for all data extensions** - `a8bfb15` (test)

## Files Created/Modified
- `android/core/design/src/main/kotlin/app/dqxn/android/core/design/token/SemanticColors.kt` - Static Info/Warning/Success/Error color tokens
- `android/core/design/src/test/kotlin/app/dqxn/android/core/design/token/SemanticColorsTest.kt` - Non-transparency and distinctness tests
- `android/data/src/main/kotlin/app/dqxn/android/data/preferences/UserPreferencesRepository.kt` - Added analyticsConsent flow + setAnalyticsConsent + clearAll
- `android/data/src/main/kotlin/app/dqxn/android/data/preferences/UserPreferencesRepositoryImpl.kt` - Implemented analyticsConsent + clearAll
- `android/data/src/main/kotlin/app/dqxn/android/data/preferences/PreferenceKeys.kt` - Added ANALYTICS_CONSENT key
- `android/sdk/contracts/src/main/kotlin/app/dqxn/android/sdk/contracts/settings/ProviderSettingsStore.kt` - Added clearAll to interface
- `android/data/src/main/kotlin/app/dqxn/android/data/provider/ProviderSettingsStoreImpl.kt` - Implemented clearAll
- `android/data/src/main/kotlin/app/dqxn/android/data/layout/LayoutRepository.kt` - Added clearAll to interface
- `android/data/src/main/kotlin/app/dqxn/android/data/layout/LayoutRepositoryImpl.kt` - Implemented clearAll with fallback store recreation
- `android/data/src/main/kotlin/app/dqxn/android/data/style/WidgetStyleStore.kt` - Added clearAll to interface
- `android/data/src/main/kotlin/app/dqxn/android/data/style/WidgetStyleStoreImpl.kt` - Implemented clearAll
- `android/data/src/main/kotlin/app/dqxn/android/data/device/PairedDeviceStore.kt` - Added clearAll to interface
- `android/data/src/main/kotlin/app/dqxn/android/data/device/PairedDeviceStoreImpl.kt` - Implemented clearAll
- `android/feature/dashboard/src/testFixtures/kotlin/app/dqxn/android/feature/dashboard/test/FakeLayoutRepository.kt` - Added clearAll to fake
- `android/data/src/test/kotlin/app/dqxn/android/data/preferences/UserPreferencesRepositoryTest.kt` - analyticsConsent + clearAll tests
- `android/data/src/test/kotlin/app/dqxn/android/data/layout/LayoutRepositoryTest.kt` - clearAll test with default profile recreation
- `android/data/src/test/kotlin/app/dqxn/android/data/provider/ProviderSettingsStoreTest.kt` - clearAll test across all packs
- `android/data/src/test/kotlin/app/dqxn/android/data/style/WidgetStyleStoreTest.kt` - clearAll test for multiple widgets
- `android/data/src/test/kotlin/app/dqxn/android/data/device/PairedDeviceStoreTest.kt` - clearAll test for multiple devices

## Decisions Made
- FakeLayoutRepository updated alongside real impl to keep interface contract consistent across all consumers
- PairedDeviceStore.clearAll() uses `PairedDeviceStoreProto.getDefaultInstance()` (empty proto message) rather than Preferences-style `clear()` since it's Proto DataStore
- LayoutRepository.clearAll() resets state to `FallbackLayout.createFallbackStore()` maintaining the invariant that zero profiles never exists

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Updated FakeLayoutRepository with clearAll()**
- **Found during:** Task 1 (Data layer clearAll extensions)
- **Issue:** FakeLayoutRepository in `:feature:dashboard` testFixtures implements LayoutRepository interface -- adding clearAll() to the interface broke compilation
- **Fix:** Added clearAll() implementation to FakeLayoutRepository that resets to default profile state
- **Files modified:** android/feature/dashboard/src/testFixtures/kotlin/app/dqxn/android/feature/dashboard/test/FakeLayoutRepository.kt
- **Verification:** `:feature:dashboard:compileDebugUnitTestKotlin` succeeds
- **Committed in:** e6a7f4c (Task 1 commit)

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** Necessary for compilation -- adding a method to an interface requires updating all implementations. No scope creep.

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- All 5 data store `clearAll()` methods ready for Plan 09's MainSettingsViewModel.deleteAllData()
- `analyticsConsent` preference ready for Plan 07's analytics consent UI
- SemanticColors ready for all Phase 10 UI plans (InfoSettingRow, setup cards, status indicators)

## Self-Check: PASSED

- All created/modified files verified on disk
- Commit e6a7f4c verified in git log
- Commit a8bfb15 verified in git log
- All tests pass (`:data:testDebugUnitTest`, `:core:design:testDebugUnitTest`, `:sdk:contracts:compileDebugKotlin`)

---
*Phase: 10-settings-foundation-setup-ui*
*Completed: 2026-02-25*
