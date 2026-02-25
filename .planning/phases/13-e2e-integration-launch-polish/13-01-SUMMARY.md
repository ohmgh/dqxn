---
phase: 13-e2e-integration-launch-polish
plan: 01
subsystem: lifecycle
tags: [play-app-update, play-review, in-app-update, in-app-review, preferences-datastore]

# Dependency graph
requires:
  - phase: 05-core-infrastructure
    provides: UserPreferencesRepository interface + DataStore implementation
  - phase: 06-deployable-app-agentic
    provides: CrashRecovery, AppModule, MainActivity
provides:
  - AppUpdateCoordinator with IMMEDIATE/FLEXIBLE priority routing
  - AppReviewCoordinator with 5-condition gating and 90-day frequency cap
  - UserPreferencesRepository sessionCount and lastReviewPromptTimestamp flows
  - AppUpdateManager and ReviewManager Hilt @Provides bindings
affects: [13-02-export-privacy, 13-07-battery-soak]

# Tech tracking
tech-stack:
  added: [play-app-update-ktx 2.1.0, play-review-ktx 2.0.1, kotlinx-coroutines-play-services]
  patterns: [MockK-based Play Core testing (FakeAppUpdateManager requires real Context), injectable timeProvider for time-dependent tests]

key-files:
  created:
    - android/app/src/main/kotlin/app/dqxn/android/app/lifecycle/AppUpdateCoordinator.kt
    - android/app/src/main/kotlin/app/dqxn/android/app/lifecycle/AppReviewCoordinator.kt
    - android/app/src/test/kotlin/app/dqxn/android/app/lifecycle/AppUpdateCoordinatorTest.kt
    - android/app/src/test/kotlin/app/dqxn/android/app/lifecycle/AppReviewCoordinatorTest.kt
  modified:
    - android/gradle/libs.versions.toml
    - android/app/build.gradle.kts
    - android/data/src/main/kotlin/app/dqxn/android/data/preferences/UserPreferencesRepository.kt
    - android/data/src/main/kotlin/app/dqxn/android/data/preferences/UserPreferencesRepositoryImpl.kt
    - android/data/src/main/kotlin/app/dqxn/android/data/preferences/PreferenceKeys.kt
    - android/app/src/main/kotlin/app/dqxn/android/di/AppModule.kt
    - android/app/src/main/kotlin/app/dqxn/android/MainActivity.kt

key-decisions:
  - "MockK-based Play Core testing over FakeAppUpdateManager/FakeReviewManager -- fakes require real Android Context, incompatible with JUnit5 pure JVM tests"
  - "CrashRecovery.isInSafeMode() as hasCrashedThisSession proxy -- conservative heuristic, suppresses review only when safe mode active (4+ crashes in 60s)"
  - "Injectable timeProvider on AppReviewCoordinator -- enables deterministic 90-day cap testing without wall clock"

patterns-established:
  - "Play Core API testing via MockK Task<T> capture pattern: mock addOnSuccessListener, capture slot, invoke immediately"
  - "Time-injectable coordinators: constructor param timeProvider: () -> Long = System::currentTimeMillis"

requirements-completed: [NF-L2, NF-L3]

# Metrics
duration: 28min
completed: 2026-02-25
---

# Phase 13 Plan 01: Google Play App Lifecycle APIs Summary

**Play In-App Update (IMMEDIATE/FLEXIBLE by priority) + In-App Review (5-condition gating with 90-day cap) with 17 unit tests via MockK Play Core mocking**

## Performance

- **Duration:** 28 min
- **Started:** 2026-02-25T11:21:41Z
- **Completed:** 2026-02-25T11:50:17Z
- **Tasks:** 2
- **Files modified:** 24

## Accomplishments
- AppUpdateCoordinator routes critical updates (priority >= 4) to IMMEDIATE flow, others to FLEXIBLE, with InstallStateUpdatedListener for auto-complete on download
- AppReviewCoordinator enforces all 5 gating conditions: session count >= 3, layout customized, no crash this session, first-time or 90+ days since last prompt
- UserPreferencesRepository extended with sessionCount (Int) and lastReviewPromptTimestamp (Long) backed by Preferences DataStore
- 17 tests total (8 update + 9 review) covering all priority/condition paths including boundary cases

## Task Commits

Each task was committed atomically:

1. **Task 1: Version catalog + UserPreferencesRepository extensions + AppUpdateCoordinator + AppReviewCoordinator** - `8ddc2fd` (feat)
2. **Task 2: AppUpdateCoordinator and AppReviewCoordinator unit tests** - `8363ceb` (test)

## Files Created/Modified
- `android/app/src/main/kotlin/app/dqxn/android/app/lifecycle/AppUpdateCoordinator.kt` - In-app update coordinator with IMMEDIATE/FLEXIBLE routing
- `android/app/src/main/kotlin/app/dqxn/android/app/lifecycle/AppReviewCoordinator.kt` - In-app review coordinator with 5-condition gating
- `android/app/src/test/kotlin/app/dqxn/android/app/lifecycle/AppUpdateCoordinatorTest.kt` - 8 tests: priority routing, no-update, type-not-allowed, listener management, completeUpdate
- `android/app/src/test/kotlin/app/dqxn/android/app/lifecycle/AppReviewCoordinatorTest.kt` - 9 tests: all gating conditions, 90-day boundary, timestamp persistence
- `android/gradle/libs.versions.toml` - play-app-update 2.1.0, play-review 2.0.1, kotlinx-coroutines-play-services
- `android/app/build.gradle.kts` - play-app-update-ktx, play-review-ktx, coroutines-play-services deps + Hilt testing deps
- `android/data/src/main/kotlin/app/dqxn/android/data/preferences/UserPreferencesRepository.kt` - sessionCount, incrementSessionCount, lastReviewPromptTimestamp, setLastReviewPromptTimestamp
- `android/data/src/main/kotlin/app/dqxn/android/data/preferences/UserPreferencesRepositoryImpl.kt` - DataStore-backed implementations
- `android/data/src/main/kotlin/app/dqxn/android/data/preferences/PreferenceKeys.kt` - SESSION_COUNT, LAST_REVIEW_PROMPT_TIMESTAMP keys
- `android/app/src/main/kotlin/app/dqxn/android/di/AppModule.kt` - @Provides AppUpdateManager + ReviewManager
- `android/app/src/main/kotlin/app/dqxn/android/MainActivity.kt` - onResume update check, LaunchedEffect review prompt

## Decisions Made
- **MockK over FakeAppUpdateManager/FakeReviewManager:** Both fakes require real Android Context (constructor param). JUnit5 pure JVM tests cannot provide one without Robolectric. MockK `Task<T>` capture pattern works cleanly: mock `addOnSuccessListener`, capture the slot, invoke immediately.
- **CrashRecovery.isInSafeMode() as hasCrashedThisSession:** The plan specified passing a boolean. `isInSafeMode()` (4+ crashes in 60s) is a conservative proxy -- it suppresses reviews when the app is clearly unstable. A more sensitive check could be added later.
- **Injectable timeProvider:** Constructor parameter `timeProvider: () -> Long = System::currentTimeMillis` on AppReviewCoordinator enables deterministic 90-day frequency cap testing without wall clock dependency.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Added missing interface methods for pre-existing DataExporter compilation**
- **Found during:** Task 1 (compilation verification)
- **Issue:** DataExporter (from plan 13-02 scaffolding by linter) referenced getAllStyles(), getAllProviderSettings(), getProfileWidgets(), resetAnalyticsData() which did not exist on their interfaces
- **Fix:** Added interface methods + implementations to WidgetStyleStore, ProviderSettingsStore, LayoutRepository, AnalyticsTracker (with NoOpAnalyticsTracker + FirebaseAnalyticsTracker impls)
- **Files modified:** WidgetStyleStore.kt, WidgetStyleStoreImpl.kt, ProviderSettingsStore.kt, ProviderSettingsStoreImpl.kt, LayoutRepository.kt, LayoutRepositoryImpl.kt, FakeLayoutRepository.kt, AnalyticsTracker.kt, FirebaseAnalyticsTracker.kt, MainSettingsViewModel.kt
- **Verification:** Full project compiles successfully
- **Committed in:** 8ddc2fd (Task 1 commit)

**2. [Rule 3 - Blocking] Added kotlinx-coroutines-play-services dependency**
- **Found during:** Task 1 (AppReviewCoordinator uses Task.await() from coroutines-play-services)
- **Issue:** `kotlinx.coroutines.tasks.await` extension requires `kotlinx-coroutines-play-services` which was not in the version catalog
- **Fix:** Added library entry and implementation dependency in :app
- **Files modified:** libs.versions.toml, app/build.gradle.kts
- **Verification:** AppReviewCoordinator compiles with `Task.await()`
- **Committed in:** 8ddc2fd (Task 1 commit)

---

**Total deviations:** 2 auto-fixed (2 blocking)
**Impact on plan:** Both fixes necessary for compilation. Interface methods are forward-looking (future plans will consume them). No scope creep.

## Issues Encountered
- KSP stale cache errors across multiple modules (NoSuchFileException for generated Dagger factories). Resolved by cleaning build directories and configuration cache. Recurring issue with AGP 9 + KSP on this codebase.
- Gradle daemon killed repeatedly during test runs (memory pressure with 25+ module project). Resolved by using `GRADLE_OPTS="-Xmx4g"`.
- Pre-existing SessionLifecycleTrackerTest.kt compilation errors (AnalyticsEvent API changes) -- out of scope for this plan, not caused by our changes.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Play In-App Update and Review APIs fully wired and tested
- UserPreferencesRepository extended with session tracking for review gating
- Ready for plan 13-02 (Export My Data + Privacy verification)

---
*Phase: 13-e2e-integration-launch-polish*
*Completed: 2026-02-25*
