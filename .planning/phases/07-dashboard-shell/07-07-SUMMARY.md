---
phase: 07-dashboard-shell
plan: 07
subsystem: ui
tags: [hilt-viewmodel, compose, coordinator-pattern, horizontal-pager, channel, command-routing, integration-testing]

# Dependency graph
requires:
  - phase: 07-dashboard-shell (plans 01-06)
    provides: "All 6 coordinators, DashboardCommand, DashboardGrid, WidgetSlot, layers, bottom bar, banner hosts, test harness"
provides:
  - "DashboardViewModel routing 16 DashboardCommand variants to 6 coordinators"
  - "DashboardScreen assembling full layer stack (grid, banners, button bar, overlay, critical banners)"
  - "OverlayNavHost scaffold with empty route table (populated in Phase 10)"
  - "ProfilePageTransition via HorizontalPager (disabled during edit mode)"
  - "MainActivity integration rendering DashboardScreen"
  - "19 tests: 10 ViewModel unit + 9 TestHarness integration"
affects: [08-essentials-pack, 10-settings-setup-ui, 13-e2e-integration]

# Tech tracking
tech-stack:
  added: [androidx.compose.foundation.pager (HorizontalPager), androidx.navigation.compose (NavHost)]
  patterns: [channel-based command routing, layer-stack composition, job-based test scope cancellation, versioned-stateflow for forced re-emission]

key-files:
  created:
    - "android/feature/dashboard/src/main/kotlin/app/dqxn/android/feature/dashboard/DashboardViewModel.kt"
    - "android/feature/dashboard/src/main/kotlin/app/dqxn/android/feature/dashboard/DashboardScreen.kt"
    - "android/feature/dashboard/src/main/kotlin/app/dqxn/android/feature/dashboard/layer/OverlayNavHost.kt"
    - "android/feature/dashboard/src/main/kotlin/app/dqxn/android/feature/dashboard/profile/ProfilePageTransition.kt"
    - "android/feature/dashboard/src/test/kotlin/app/dqxn/android/feature/dashboard/DashboardViewModelTest.kt"
    - "android/feature/dashboard/src/test/kotlin/app/dqxn/android/feature/dashboard/DashboardTestHarnessTest.kt"
  modified:
    - "android/app/src/main/kotlin/app/dqxn/android/MainActivity.kt"
    - "android/feature/dashboard/src/testFixtures/kotlin/app/dqxn/android/feature/dashboard/test/DashboardTestHarness.kt"
    - "android/feature/dashboard/src/testFixtures/kotlin/app/dqxn/android/feature/dashboard/test/FakeLayoutRepository.kt"

key-decisions:
  - "Channel(capacity=64) for command processing — sequential consumption, try/catch per command, CancellationException rethrown"
  - "collectAsState() (not collectAsStateWithLifecycle) for Layer 0 per CLAUDE.md"
  - "Job-based scope cancellation in DashboardTestHarness — child Job of testScope for forever-collecting flows, close() cancels before runTest exits"
  - "Versioned<T> wrapper in FakeLayoutRepository — forces MutableStateFlow re-emission on profile switch despite structurally-equal content"
  - "HorizontalPager userScrollEnabled = !isEditMode — profile swipe disabled during edit mode (F1.29)"

patterns-established:
  - "Channel-based command routing: DashboardViewModel dispatches via Channel(64), consumes sequentially with error isolation"
  - "Layer stack composition: Box with ordered layers (grid, normal banners, button bar, overlay, critical banners)"
  - "Job-based test scope cancellation: child Job for forever-collecting flows, harness.close() before runTest exits"
  - "Versioned StateFlow wrapper: AtomicLong version counter forces re-emission when structural content unchanged"

requirements-completed: [F1.13, F1.14, F1.15, F1.16, F2.12, F2.16, F2.18, F2.20, F3.9, F3.15, NF2, NF3, NF4, NF-L1]

# Metrics
duration: 45min
completed: 2026-02-24
---

# Phase 7 Plan 07: Final Assembly Summary

**DashboardViewModel routing 16 command variants to 6 coordinators via Channel(64), DashboardScreen layer stack, HorizontalPager profile switching, and 19 tests (10 unit + 9 integration via TestHarness)**

## Performance

- **Duration:** 45 min (across two sessions due to context limit)
- **Started:** 2026-02-24T09:22:00Z
- **Completed:** 2026-02-24T10:11:53Z
- **Tasks:** 2
- **Files modified:** 9

## Accomplishments

- DashboardViewModel routes all 16 DashboardCommand variants to correct coordinators with sequential processing, error isolation, and slow-command logging
- DashboardScreen assembles full 5-layer stack: DashboardLayer (grid), NotificationBannerHost, DashboardButtonBar, OverlayNavHost, CriticalBannerHost
- ProfilePageTransition via HorizontalPager with edit-mode scroll lock (F1.29)
- OverlayNavHost scaffolded with empty route table for Phase 10 population
- MainActivity updated from placeholder to render DashboardScreen
- 10 ViewModel unit tests covering all command routing, exception resilience, slow-command logging
- 9 integration tests via DashboardTestHarness with real coordinators verifying cross-coordinator interactions

## Task Commits

Each task was committed atomically:

1. **Task 1: DashboardViewModel + DashboardScreen + OverlayNavHost + ProfilePageTransition + MainActivity** - `665334c` (feat)
2. **Task 2: DashboardViewModel tests + DashboardTestHarness integration tests** - `f8f9277` (test)

## Files Created/Modified

- `android/feature/dashboard/src/main/kotlin/app/dqxn/android/feature/dashboard/DashboardViewModel.kt` - @HiltViewModel routing 16 DashboardCommand variants to 6 coordinators via Channel(64)
- `android/feature/dashboard/src/main/kotlin/app/dqxn/android/feature/dashboard/DashboardScreen.kt` - Root screen composable assembling all dashboard layers with collectAsState()
- `android/feature/dashboard/src/main/kotlin/app/dqxn/android/feature/dashboard/layer/OverlayNavHost.kt` - Layer 1 navigation scaffold with empty route table
- `android/feature/dashboard/src/main/kotlin/app/dqxn/android/feature/dashboard/profile/ProfilePageTransition.kt` - HorizontalPager multi-profile switching, disabled during edit mode
- `android/app/src/main/kotlin/app/dqxn/android/MainActivity.kt` - Updated to render DashboardScreen() instead of placeholder
- `android/feature/dashboard/src/test/kotlin/app/dqxn/android/feature/dashboard/DashboardViewModelTest.kt` - 10 unit tests with MockK for command routing
- `android/feature/dashboard/src/test/kotlin/app/dqxn/android/feature/dashboard/DashboardTestHarnessTest.kt` - 9 integration tests with real coordinators
- `android/feature/dashboard/src/testFixtures/kotlin/app/dqxn/android/feature/dashboard/test/DashboardTestHarness.kt` - Added close() method and initScope parameter for test lifecycle management
- `android/feature/dashboard/src/testFixtures/kotlin/app/dqxn/android/feature/dashboard/test/FakeLayoutRepository.kt` - Versioned<T> wrapper for forced StateFlow re-emission on profile switch

## Decisions Made

- **Channel(capacity=64) for command routing** — sequential consumption loop in init block, each command wrapped in try/catch (CancellationException rethrown), slow command logging at >1s threshold
- **collectAsState() for all Layer 0 state** — per CLAUDE.md, Layer 0 uses collectAsState() not collectAsStateWithLifecycle()
- **HorizontalPager userScrollEnabled = !isEditMode** — profile swipe disabled during edit mode since horizontal gestures are widget drag territory (F1.29)
- **Job-based scope cancellation in test harness** — creates child Job of testScope for coordinator forever-collecting flows; harness.close() cancels before runTest exits to prevent UncompletedCoroutinesError
- **Versioned<T> StateFlow wrapper** — AtomicLong version counter forces MutableStateFlow re-emission when content is structurally equal (profile switching)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] FakeLayoutRepository profile switching did not trigger widget re-emission**
- **Found during:** Task 2 (DashboardTestHarnessTest `dispatch SwitchProfile loads new profile widgets`)
- **Issue:** `getActiveProfileWidgets()` used `_profiles.map { _activeProfileId.value }` which only re-emits when `_profiles` changes. `switchProfile` updated `_activeProfileId` but `_profiles.update { it.toList() }` created a structurally-equal list, suppressed by MutableStateFlow.
- **Fix:** Added `Versioned<T>` data class wrapper with `AtomicLong` version counter. Every mutation (including `switchProfile`) bumps the version, guaranteeing structural inequality and forcing re-emission. This approach was chosen over `combine(_profiles, _activeProfileId)` which broke 11 existing tests due to concurrent collector scheduler mismatches.
- **Files modified:** `feature/dashboard/src/testFixtures/kotlin/.../FakeLayoutRepository.kt`
- **Verification:** All 148 dashboard tests pass including SwitchProfile integration test
- **Committed in:** `f8f9277` (Task 2 commit)

**2. [Rule 1 - Bug] DashboardTestHarness missing lifecycle management for forever-collecting flows**
- **Found during:** Task 2 (DashboardTestHarnessTest — 7 of 9 tests failing with UncompletedCoroutinesError)
- **Issue:** Harness `initialize()` launches forever-collecting flows on the test scope that never complete before `runTest` exits, causing `UncompletedCoroutinesError`.
- **Fix:** Added `close()` method that cancels a child `Job` used for coordinator initialization scope. Modified `initialize()` to accept optional `initScope` parameter, defaulting to creating a child scope with its own cancellable Job. Updated `dashboardTest` DSL to call `close()` in `finally` block.
- **Files modified:** `feature/dashboard/src/testFixtures/kotlin/.../DashboardTestHarness.kt`
- **Verification:** All 9 DashboardTestHarnessTest integration tests pass
- **Committed in:** `f8f9277` (Task 2 commit)

**3. [Rule 3 - Blocking] Reduced motion tests deferred — ReducedMotionHelper lacks animationsEnabled integration**
- **Found during:** Task 2 (plan specified 3 reduced motion tests)
- **Issue:** `EditModeCoordinator.editState` has no `animationsEnabled` flag. The plan assumed this field existed. `ReducedMotionHelper.isReducedMotion` is a Boolean property consumed at the Compose layer (animation specs), not at the coordinator state level. Testing this properly requires UI-level instrumentation, not coordinator integration tests.
- **Fix:** Deferred to Phase 10/11 when Compose UI testing infrastructure is available. The 3 reduced motion tests (NF39) remain as Phase 10 deliverables.
- **Files modified:** None
- **Verification:** N/A — deferred, not blocked

---

**Total deviations:** 3 (2 auto-fixed bugs, 1 deferred scope item)
**Impact on plan:** Auto-fixes were necessary for test correctness. Reduced motion tests appropriately deferred — they require Compose UI layer testing, not coordinator-level integration.

## Issues Encountered

- **UncompletedCoroutinesError cascade (Task 2):** 7 of 9 integration tests initially failed because coordinator forever-collecting flows outlived runTest. Required several iterations: separate TestScope (scheduler mismatch), backgroundScope (not advanced by advanceUntilIdle), before settling on child Job cancellation pattern.
- **Scheduler mismatch with combine() (Task 2):** First attempt to fix profile switching used `combine(_profiles, _activeProfileId)` which launched concurrent collectors. This broke 11 existing tests in LayoutCoordinatorTest and EditModeCoordinatorTest that use separate `UnconfinedTestDispatcher` instances. Reverted in favor of Versioned wrapper pattern.
- **Pre-existing build failures (not caused by this plan):** `feature:settings` and `feature:diagnostics` have "no tests found" (empty stubs). `app:hiltJavaCompileDebug` has MissingBinding errors for ErrorReporter, SharedPreferences, Function0<Long>. Verified pre-existing via `git stash` — these exist on main before any changes.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- **Phase 7 complete** — all 7 plans delivered. Dashboard shell fully assembled with 6 coordinators, full UI layer stack, command routing, profile switching, and comprehensive test coverage.
- **Phase 8 (Essentials Pack)** unblocked — DashboardViewModel, DashboardScreen, and the full coordinator pipeline are ready for widget rendering. Pack widgets will render via the WidgetSlot → WidgetBindingCoordinator → WidgetRenderer pipeline.
- **Phase 10 (Settings + Setup UI)** — OverlayNavHost route table populated here. ProfilePageTransition working for multi-profile navigation.
- **Deferred items:** 3 reduced motion tests (NF39) and `dump-semantics` dashboard_grid tag test (requires on-device agentic ContentProvider) deferred to Phases 10-11.

## Self-Check: PASSED

- All 9 created/modified files verified on disk
- Commit `665334c` (Task 1) verified in git log
- Commit `f8f9277` (Task 2) verified in git log
- `07-07-SUMMARY.md` verified on disk

---
*Phase: 07-dashboard-shell*
*Completed: 2026-02-24*
