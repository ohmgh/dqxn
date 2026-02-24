---
phase: 07-dashboard-shell
plan: 05
subsystem: dashboard
tags: [coordinator, profile, notification, banner, hilt, di, safe-mode, storage-monitor]

# Dependency graph
requires:
  - phase: 07-01
    provides: "LayoutCoordinator, SafeModeManager, DashboardCommand, testFixtures"
  - phase: 07-04
    provides: "WidgetBindingCoordinator, WidgetRegistryImpl, DataProviderRegistryImpl, StorageMonitor"
provides:
  - "ProfileCoordinator: profile CRUD with per-profile canvas independence (F1.29, F1.30)"
  - "NotificationCoordinator: condition-keyed banner derivation from SafeModeManager/StorageMonitor with priority ordering"
  - "DashboardModule: Hilt @Module wiring WidgetRegistry, DataProviderRegistry, WindowInfoTracker as singletons"
  - "Complete coordinator set (all 6): Layout, EditMode, Theme, WidgetBinding, Profile, Notification"
affects: [07-06, 07-07, 08-essentials-pack]

# Tech tracking
tech-stack:
  added: []
  patterns: ["condition-keyed banners (stable string IDs, not UUIDs) for flicker-free re-derivation", "singleton re-observation for process-death recovery", "synchronized banner map with priority-sorted state emission"]

key-files:
  created:
    - "android/feature/dashboard/src/main/kotlin/app/dqxn/android/feature/dashboard/coordinator/ProfileCoordinator.kt"
    - "android/feature/dashboard/src/main/kotlin/app/dqxn/android/feature/dashboard/coordinator/NotificationCoordinator.kt"
    - "android/feature/dashboard/src/main/kotlin/app/dqxn/android/feature/dashboard/di/DashboardModule.kt"
    - "android/feature/dashboard/src/test/kotlin/app/dqxn/android/feature/dashboard/coordinator/ProfileCoordinatorTest.kt"
    - "android/feature/dashboard/src/test/kotlin/app/dqxn/android/feature/dashboard/coordinator/NotificationCoordinatorTest.kt"
  modified: []

key-decisions:
  - "StateFlow.distinctUntilChanged() removed -- deprecated in Kotlin coroutines because StateFlow already guarantees structural equality dedup; treated as compile error in this project"
  - "Layout save failure via explicit reportLayoutSaveFailure() method instead of LayoutRepository flow -- LayoutRepository has no save failure flow; explicit method callable from LayoutCoordinator or ViewModel"

patterns-established:
  - "Condition-keyed banners: stable string IDs ('safe_mode', 'low_storage') prevent flicker vs UUID-keyed banners that would regenerate on re-derivation"
  - "Singleton re-observation: on ViewModel recreation, coordinators re-observe singleton StateFlows which re-emit current values, restoring all condition-based UI without explicit event replay"

requirements-completed: [F1.29, F1.30, F2.20, F9.1, F9.2, F9.3, F9.4, NF41, NF42]

# Metrics
duration: 5min
completed: 2026-02-24
---

# Phase 7 Plan 05: Profile + Notification Coordinators + DashboardModule Summary

**ProfileCoordinator with per-profile canvas independence, NotificationCoordinator with condition-keyed banner derivation and priority ordering, DashboardModule Hilt wiring for registries and WindowInfoTracker**

## Performance

- **Duration:** ~5 min
- **Started:** 2026-02-24T08:03:52Z
- **Completed:** 2026-02-24T08:09:30Z
- **Tasks:** 2
- **Files created:** 5

## Accomplishments

- ProfileCoordinator managing full profile lifecycle (create/clone/switch/delete) with per-profile canvas independence via LayoutRepository delegation
- NotificationCoordinator with condition-keyed banners from SafeModeManager (CRITICAL) and StorageMonitor (HIGH), priority-sorted state emission, toast channel, connection status banners, and layout save failure reporting
- DashboardModule Hilt module binding WidgetRegistry, DataProviderRegistry as singletons and providing WindowInfoTracker via @Provides
- Complete coordinator set (all 6 of 6): LayoutCoordinator, EditModeCoordinator, ThemeCoordinator, WidgetBindingCoordinator, ProfileCoordinator, NotificationCoordinator
- 20 unit tests across 2 test classes (9 profile + 11 notification), all passing

## Task Commits

Each task was committed atomically:

1. **Task 1: ProfileCoordinator + NotificationCoordinator + DashboardModule** - `3f15dc1` (feat)
2. **Task 2: ProfileCoordinator tests + NotificationCoordinator tests** - `cc3f4ea` (test)

## Files Created/Modified

### Production (Task 1)
- `feature/dashboard/.../coordinator/ProfileCoordinator.kt` - Profile CRUD with per-profile canvas independence, clone support, default protection
- `feature/dashboard/.../coordinator/NotificationCoordinator.kt` - Condition-keyed banner derivation, priority ordering, toast channel, connection status, save failure
- `feature/dashboard/.../di/DashboardModule.kt` - Hilt module for WidgetRegistry, DataProviderRegistry bindings and WindowInfoTracker @Provides

### Tests (Task 2)
- `feature/dashboard/.../coordinator/ProfileCoordinatorTest.kt` - 9 tests: init, switch, create, clone (F1.30), delete, default protection, active deletion, per-profile independence, profileCount
- `feature/dashboard/.../coordinator/NotificationCoordinatorTest.kt` - 11 tests: safe mode CRITICAL, dismiss, low storage HIGH, layout save failure, priority ordering, condition-keyed update, re-derivation on recreation, toast delivery, connection status, alert profile, clearLayoutSaveFailure

## Decisions Made

1. **StateFlow.distinctUntilChanged() removed** -- The plan specified `distinctUntilChanged()` on SafeModeManager and StorageMonitor flows, but `StateFlow` already provides distinct-until-changed semantics via structural equality. In Kotlin coroutines, calling `distinctUntilChanged()` on a `StateFlow` is deprecated and treated as a compile error in this project.

2. **Layout save failure via explicit method** -- The plan specified observing a LayoutRepository save failure flow, but `LayoutRepository` has no such flow. Implemented as an explicit `reportLayoutSaveFailure()` / `clearLayoutSaveFailure()` method pair callable from LayoutCoordinator or ViewModel when persistence fails. This is cleaner than adding a flow to the repository interface for what's ultimately an error callback.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Removed deprecated StateFlow.distinctUntilChanged()**
- **Found during:** Task 1 (compilation)
- **Issue:** `distinctUntilChanged()` on `StateFlow` is deprecated in kotlinx.coroutines and treated as a compile error in this project. StateFlow already guarantees structural equality dedup.
- **Fix:** Removed `distinctUntilChanged()` calls, added comments explaining StateFlow's built-in distinctness guarantee.
- **Files modified:** NotificationCoordinator.kt
- **Verification:** Compilation succeeds
- **Committed in:** `3f15dc1` (Task 1 commit)

**2. [Rule 3 - Blocking] Layout save failure as explicit method instead of missing flow**
- **Found during:** Task 1 (implementation)
- **Issue:** Plan specified observing `LayoutRepository` save failure flow, but no such flow exists in the interface
- **Fix:** Added `reportLayoutSaveFailure()` and `clearLayoutSaveFailure()` methods to NotificationCoordinator for explicit invocation
- **Files modified:** NotificationCoordinator.kt
- **Verification:** `layout save failure shows HIGH banner with specific message` test passes
- **Committed in:** `3f15dc1` (Task 1 commit)

---

**Total deviations:** 2 auto-fixed (1 bug, 1 blocking)
**Impact on plan:** Both auto-fixes necessary for compilation and correctness. No scope creep.

## Issues Encountered

None -- clean execution.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- All 6 coordinators complete, ready for DashboardViewModel composition (Plan 06)
- DashboardModule Hilt wiring provides singleton registries and WindowInfoTracker
- NotificationCoordinator ready for DashboardScreen banner/toast rendering (Plan 07)
- ProfileCoordinator ready for bottom bar profile icons and profile management UI
- 20 new unit tests provide regression safety

## Self-Check: PASSED

- All 5 created files verified on disk
- Both task commits (3f15dc1, cc3f4ea) verified in git log
- All 20 unit tests pass (0 failures)
- Production code compiles successfully
- All 6 coordinators present in coordinator package

---
*Phase: 07-dashboard-shell*
*Completed: 2026-02-24*
