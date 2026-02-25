---
phase: 11-theme-ui-diagnostics-onboarding
plan: 08
subsystem: onboarding
tags: [compose-ui, hilt-viewmodel, analytics-consent, first-run, progressive-tips, robolectric]

# Dependency graph
requires:
  - phase: 11-03
    provides: ProgressiveTipManager with shouldShowTip/dismissTip flows
  - phase: 05-core-infrastructure
    provides: UserPreferencesRepository with onboarding/consent preferences
  - phase: 10-09
    provides: MainSettingsViewModel analytics consent ordering pattern
provides:
  - OnboardingViewModel orchestrating first-run flow
  - AnalyticsConsentStep opt-IN composable (F12.5)
  - FirstLaunchDisclaimer informational notice (NF-D3)
  - FirstRunFlow 4-step paginated onboarding (consent, disclaimer, theme, tour)
  - ProgressiveTip reusable dismissable tip composable
  - String resources for all onboarding user-facing text
affects: [11-10-overlay-route-wiring]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Theme persistence via light/dark slot matching isDark property (same as ThemeCoordinator)"
    - "AnimatedContent horizontal slide transitions with direction tracking"
    - "ProgressiveTip flow-based visibility with DashboardMotion expand animations"

key-files:
  created:
    - android/feature/onboarding/src/main/kotlin/app/dqxn/android/feature/onboarding/OnboardingViewModel.kt
    - android/feature/onboarding/src/main/kotlin/app/dqxn/android/feature/onboarding/AnalyticsConsentStep.kt
    - android/feature/onboarding/src/main/kotlin/app/dqxn/android/feature/onboarding/FirstLaunchDisclaimer.kt
    - android/feature/onboarding/src/main/kotlin/app/dqxn/android/feature/onboarding/FirstRunFlow.kt
    - android/feature/onboarding/src/main/kotlin/app/dqxn/android/feature/onboarding/ProgressiveTip.kt
    - android/feature/onboarding/src/main/res/values/strings.xml
    - android/feature/onboarding/src/test/kotlin/app/dqxn/android/feature/onboarding/FirstLaunchDisclaimerTest.kt
    - android/feature/onboarding/src/test/kotlin/app/dqxn/android/feature/onboarding/FirstRunFlowTest.kt
  modified: []

key-decisions:
  - "selectTheme persists via setLightThemeId/setDarkThemeId based on isDark -- no setSelectedThemeId exists; matches ThemeCoordinator slot pattern"
  - "createAndroidComposeRule<ComponentActivity> over createComposeRule for BackHandler dismissal testing -- provides activity.onBackPressedDispatcher access"

patterns-established:
  - "FirstRunFlow pagination: AnimatedContent with direction-aware slide transitions + BackHandler per page"
  - "ProgressiveTip: collectAsState(initial=false) from shouldShowTip flow for reactive tip display"

requirements-completed: [F11.1, F11.2, F11.6, F11.7, F12.5, NF-D3]

# Metrics
duration: 6min
completed: 2026-02-25
---

# Phase 11 Plan 08: Onboarding Flow Composables Summary

**OnboardingViewModel + 4-step FirstRunFlow (consent, disclaimer, free-theme selection, edit tour) with ProgressiveTip reusable component and 10 Compose UI tests**

## Performance

- **Duration:** 6 min
- **Started:** 2026-02-25T08:11:58Z
- **Completed:** 2026-02-25T08:18:10Z
- **Tasks:** 2
- **Files modified:** 8

## Accomplishments
- OnboardingViewModel @HiltViewModel with consent ordering, theme slot persistence, and completion tracking
- AnalyticsConsentStep explains data collected and provides opt-IN enable/skip buttons (F12.5)
- FirstLaunchDisclaimer informational notice with single dismiss button (NF-D3)
- FirstRunFlow 4-step AnimatedContent pager: consent -> disclaimer -> free theme grid -> edit mode tour
- ProgressiveTip reusable composable observing ProgressiveTipManager flow with DashboardMotion animations
- 10 Compose UI tests passing (3 disclaimer + 7 flow navigation/completion/back)
- No permission requests in onboarding flow (F11.6)
- All user-facing strings in Android string resources

## Task Commits

Each task was committed atomically:

1. **Task 1: OnboardingViewModel + AnalyticsConsentStep + FirstLaunchDisclaimer** - `4501780` (feat)
2. **Task 2: FirstRunFlow + ProgressiveTip + tests** - `0b9f9e0` (feat)

## Files Created/Modified
- `android/feature/onboarding/src/main/kotlin/.../OnboardingViewModel.kt` - First-run orchestration ViewModel with consent/theme/completion actions
- `android/feature/onboarding/src/main/kotlin/.../AnalyticsConsentStep.kt` - Opt-IN analytics consent step with enable/skip buttons
- `android/feature/onboarding/src/main/kotlin/.../FirstLaunchDisclaimer.kt` - Informational disclaimer with dismiss button
- `android/feature/onboarding/src/main/kotlin/.../FirstRunFlow.kt` - 4-step paginated onboarding with AnimatedContent transitions
- `android/feature/onboarding/src/main/kotlin/.../ProgressiveTip.kt` - Reusable dismissable tip composable with flow observation
- `android/feature/onboarding/src/main/res/values/strings.xml` - All onboarding string resources
- `android/feature/onboarding/src/test/kotlin/.../FirstLaunchDisclaimerTest.kt` - 3 Compose UI tests for disclaimer
- `android/feature/onboarding/src/test/kotlin/.../FirstRunFlowTest.kt` - 7 Compose UI tests for flow navigation

## Decisions Made
- **selectTheme uses light/dark slot persistence** -- UserPreferencesRepository has no `setSelectedThemeId`; it uses `setLightThemeId`/`setDarkThemeId` split by mode. OnboardingViewModel resolves the theme from BuiltInThemes and persists to the appropriate slot based on `isDark`, matching ThemeCoordinator's `handleSetTheme` pattern.
- **createAndroidComposeRule for back navigation test** -- `createComposeRule()` doesn't expose activity for `onBackPressedDispatcher` access. Using `createAndroidComposeRule<ComponentActivity>()` provides the dispatcher for BackHandler testing (same pattern as Phase 10-07).

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- OnboardingViewModel ready for OverlayNavHost wiring (Plan 11-10)
- FirstRunFlow ready to show on first launch when hasCompletedOnboarding=false
- ProgressiveTip composable ready for integration in dashboard composables
- All 15 onboarding module tests passing (5 ProgressiveTipManager + 3 disclaimer + 7 flow)

## Self-Check: PASSED

All 8 files exist, both commits (4501780, 0b9f9e0) verified.

---
*Phase: 11-theme-ui-diagnostics-onboarding*
*Completed: 2026-02-25*
