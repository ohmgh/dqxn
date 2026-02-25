---
phase: 11-theme-ui-diagnostics-onboarding
plan: 09
subsystem: ui
tags: [compose, navigation, overlay, transitions, source-varying, onboarding, diagnostics, theme-selector, nf-d1]

# Dependency graph
requires:
  - phase: 11-05
    provides: ThemeSelector composable with free-first ordering and preview lifecycle
  - phase: 11-06
    provides: Theme editing composable suite (InlineColorPicker, ThemeStudio, etc.)
  - phase: 11-07
    provides: DiagnosticsViewModel + 5 diagnostic composables
  - phase: 11-08
    provides: OnboardingViewModel + FirstRunFlow + ProgressiveTip
provides:
  - All 7 OverlayNavHost routes populated with correct transitions
  - Source-varying transitions per replication advisory section 4
  - DiagnosticsScreen entry-point composable for diagnostics overlay
  - NfD1Disclaimer speed disclaimer composable for widget info page
  - First-run onboarding navigation in DashboardScreen
  - 10 route integration tests + F11.5 GPS-exclusion assertion
affects: [11-10, 13-e2e-integration]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Source-varying transitions via targetState.destination.route pattern matching in NavHost composable lambdas"
    - "Caller-managed preview: set preview theme BEFORE navigating to ThemeSelector, clear in LaunchedEffect(Unit) on return"
    - "Route pattern matching via KClass.qualifiedName for transition target/source detection"

key-files:
  created:
    - android/feature/diagnostics/src/main/kotlin/app/dqxn/android/feature/diagnostics/DiagnosticsScreen.kt
    - android/feature/settings/src/main/kotlin/app/dqxn/android/feature/settings/theme/NfD1Disclaimer.kt
    - android/feature/dashboard/src/test/kotlin/app/dqxn/android/feature/dashboard/layer/OverlayNavHostRouteTest.kt
  modified:
    - android/feature/dashboard/src/main/kotlin/app/dqxn/android/feature/dashboard/layer/OverlayNavHost.kt
    - android/feature/dashboard/src/main/kotlin/app/dqxn/android/feature/dashboard/layer/OverlayRoutes.kt
    - android/feature/dashboard/src/main/kotlin/app/dqxn/android/feature/dashboard/DashboardScreen.kt
    - android/feature/dashboard/src/main/kotlin/app/dqxn/android/feature/dashboard/DashboardViewModel.kt
    - android/feature/dashboard/build.gradle.kts
    - android/data/src/test/kotlin/app/dqxn/android/data/preset/PresetLoaderTest.kt

key-decisions:
  - "onCommand(DashboardCommand.SetTheme/PreviewTheme) over direct ThemeCoordinator method calls -- handleSetTheme is suspend, callback lambdas are non-suspend; command channel routing avoids coroutine scope issues"
  - "BuiltInThemes.freeThemes as allThemes for ThemeSelector -- full theme list from pack-provided ThemeProviders is future work; built-in free themes are sufficient for integration wiring"
  - "DiagnosticsScreen as entry-point composable in :feature:diagnostics -- hiltViewModel() inside composable for ViewModel injection, stateless diagnostic viewers receive data as params"
  - "LaunchedEffect(Unit) for Settings preview clear -- prevents re-clearing on every recomposition while still executing on first composition"

patterns-established:
  - "Source-varying transitions: use targetState.destination.route string matching in exitTransition lambda, initialState.destination.route in popEnterTransition lambda"
  - "Route pattern constants: KClass.qualifiedName!! for reliable route pattern matching across Navigation Compose"

requirements-completed: [F4.6, F4.10, F11.5, F11.7, NF-D1]

# Metrics
duration: 10min
completed: 2026-02-25
---

# Phase 11 Plan 09: Overlay Route Integration Summary

**All 7 OverlayNavHost routes wired with source-varying transitions (ThemeSelector popEnter=fadeIn(150ms)), first-run onboarding navigation, NF-D1 speed disclaimer, and 10 route integration tests**

## Performance

- **Duration:** 10 min
- **Started:** 2026-02-25T08:35:39Z
- **Completed:** 2026-02-25T08:46:00Z
- **Tasks:** 2
- **Files modified:** 12

## Accomplishments
- All 8 OverlayNavHost routes (including EmptyRoute) wired with type-safe @Serializable route classes and correct transition specs per replication advisory section 4
- Source-varying transitions on Settings route: fadeOut(100ms)/fadeIn(150ms) to ThemeSelector, ExitTransition.None/EnterTransition.None to hub routes (diagnostics, onboarding)
- ThemeSelector popEnter uses fadeIn(150ms) NOT previewEnter -- prevents double-slide when returning from theme sub-screen (CRITICAL per advisory)
- First-run onboarding: DashboardScreen LaunchedEffect(hasCompletedOnboarding) navigates to OnboardingRoute with launchSingleTop exactly once
- NfD1Disclaimer composable with Unicode warning sign and speed disclaimer string resource
- DiagnosticsScreen entry-point composable aggregating all 5 diagnostic viewers via hiltViewModel()
- 10 route integration tests: route uniqueness, type categorization, pattern distinguishability, data class params, singleton verification
- F11.5 GPS-exclusion assertion added to PresetLoaderTest (speed-limit-circle, speed-limit-rect)

## Task Commits

Each task was committed atomically:

1. **Task 1: OverlayNavHost 7-route wiring + source-varying transitions** - `257292e` (feat)
2. **Task 2: Route integration tests + GPS-exclusion assertion** - `9ff392b` (test)

## Files Created/Modified
- `android/feature/dashboard/src/main/kotlin/.../layer/OverlayNavHost.kt` - 7 routes wired with source-varying transitions
- `android/feature/dashboard/src/main/kotlin/.../layer/OverlayRoutes.kt` - ThemeSelectorRoute, DiagnosticsRoute, OnboardingRoute added
- `android/feature/dashboard/src/main/kotlin/.../DashboardScreen.kt` - First-run onboarding navigation + new OverlayNavHost params
- `android/feature/dashboard/src/main/kotlin/.../DashboardViewModel.kt` - BuiltInThemes constructor param for theme list access
- `android/feature/dashboard/build.gradle.kts` - Added :feature:diagnostics and :feature:onboarding dependencies
- `android/feature/diagnostics/src/main/kotlin/.../DiagnosticsScreen.kt` - Entry-point composable for diagnostics overlay
- `android/feature/settings/src/main/kotlin/.../theme/NfD1Disclaimer.kt` - NF-D1 speed disclaimer composable
- `android/feature/dashboard/src/test/kotlin/.../layer/OverlayNavHostRouteTest.kt` - 10 route integration tests
- `android/data/src/test/kotlin/.../preset/PresetLoaderTest.kt` - F11.5 GPS-exclusion assertion added
- `android/feature/dashboard/src/test/kotlin/.../DashboardViewModelTest.kt` - Updated for builtInThemes param
- `android/feature/dashboard/src/test/kotlin/.../layer/OverlayNavHostTest.kt` - Updated for new OverlayNavHost signature
- `android/feature/dashboard/src/test/kotlin/.../session/SessionEventEmissionTest.kt` - Updated for builtInThemes param

## Decisions Made
- **onCommand routing over direct ThemeCoordinator calls**: ThemeCoordinator.handleSetTheme is a suspend function, but ThemeSelector's onApplyTheme callback is non-suspend. Using `onCommand(DashboardCommand.SetTheme(themeId))` routes through the existing sequential command channel in DashboardViewModel, which has proper coroutine scope handling.
- **BuiltInThemes.freeThemes as allThemes**: The full theme list (built-in + pack-provided ThemeProvider multibinding) isn't wired yet. Using freeThemes provides the correct data for initial integration. Pack themes will be added when ThemeProvider aggregation is implemented.
- **DiagnosticsScreen as composable entry point**: Created in :feature:diagnostics to encapsulate hiltViewModel() injection and diagnostic viewer composition, keeping OverlayNavHost routing clean.
- **LaunchedEffect(Unit) for Settings preview clear**: Replaces the original direct onCommand call in composable body (which fires on every recomposition) with a single-execution LaunchedEffect.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Created DiagnosticsScreen entry-point composable**
- **Found during:** Task 1 (OverlayNavHost route wiring)
- **Issue:** OverlayNavHost references DiagnosticsScreen but it didn't exist -- the diagnostic composables are stateless viewers, no screen-level composable was created in Plan 11-07
- **Fix:** Created DiagnosticsScreen.kt in :feature:diagnostics that uses hiltViewModel() and composes all 5 diagnostic viewers
- **Files modified:** DiagnosticsScreen.kt
- **Verification:** Compilation succeeds, route navigates correctly
- **Committed in:** 257292e (Task 1 commit)

**2. [Rule 1 - Bug] Fixed Settings preview clear to use LaunchedEffect(Unit)**
- **Found during:** Task 1 (Settings route review)
- **Issue:** Original code called onCommand(DashboardCommand.PreviewTheme(null)) directly in composable body, firing on every recomposition
- **Fix:** Wrapped in LaunchedEffect(Unit) for single-execution behavior
- **Files modified:** OverlayNavHost.kt
- **Verification:** Settings route correctly clears preview once on enter
- **Committed in:** 257292e (Task 1 commit)

**3. [Rule 3 - Blocking] Updated existing tests for new DashboardViewModel constructor param**
- **Found during:** Task 2 (test compilation)
- **Issue:** Adding builtInThemes to DashboardViewModel broke 3 existing test files that construct it directly
- **Fix:** Added `builtInThemes = mockk(relaxed = true)` to all DashboardViewModel construction sites in tests
- **Files modified:** DashboardViewModelTest.kt, SessionEventEmissionTest.kt, OverlayNavHostTest.kt
- **Verification:** All existing tests pass
- **Committed in:** 9ff392b (Task 2 commit)

---

**Total deviations:** 3 auto-fixed (1 bug, 2 blocking)
**Impact on plan:** All auto-fixes necessary for correctness and compilation. No scope creep.

## Issues Encountered
None beyond the auto-fixed deviations.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- All 7 OverlayNavHost routes wired and navigable from DashboardScreen
- ThemeSelector integrated with caller-managed preview pattern
- Diagnostics route renders all 5 diagnostic viewers
- Onboarding route triggers on first launch via hasCompletedOnboarding check
- Plan 11-10 (final wiring) has all prerequisites met
- No blockers

## Self-Check: PASSED

All 3 created files verified present. Both task commits (257292e, 9ff392b) verified in git log.

---
*Phase: 11-theme-ui-diagnostics-onboarding*
*Completed: 2026-02-25*
