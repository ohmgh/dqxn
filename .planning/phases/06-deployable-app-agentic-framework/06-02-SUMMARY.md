---
phase: 06-deployable-app-agentic-framework
plan: 02
subsystem: app
tags: [hilt, multibinding, edge-to-edge, crash-recovery, proguard, entitlement]

# Dependency graph
requires:
  - phase: 02-sdk-contracts
    provides: AlertEmitter, EntitlementManager, WidgetRenderer, DataProvider, ThemeProvider, DataProviderInterceptor, DashboardPackManifest contracts
  - phase: 03-sdk-observability
    provides: CrashEvidenceWriter for crash handler chain
provides:
  - "@HiltAndroidApp DqxnApplication with crash handler chain"
  - "Single-activity MainActivity with edge-to-edge blank canvas"
  - "AppModule with 5 empty multibinding sets and 3 singleton providers"
  - "CrashRecovery safe-mode detection (4+ crashes in 60s)"
  - "AlertSoundManager stub (UNAVAILABLE) fulfilling AlertEmitter contract"
  - "StubEntitlementManager (free-only) fulfilling EntitlementManager contract"
  - "AndroidManifest with BT neverForLocation, resizeableActivity=false, landscape"
  - "ProGuard rules for proto, KSP-generated, and serializable classes"
affects: [07-dashboard-shell, 08-essentials-pack, 10-settings-foundation]

# Tech tracking
tech-stack:
  added: [androidx-core-splashscreen, leakcanary-debug]
  patterns: [hilt-multibinds-empty-sets, crash-recovery-shared-prefs, entry-point-pattern]

key-files:
  created:
    - android/app/src/main/kotlin/app/dqxn/android/DqxnApplication.kt
    - android/app/src/main/kotlin/app/dqxn/android/MainActivity.kt
    - android/app/src/main/kotlin/app/dqxn/android/di/AppModule.kt
    - android/app/src/main/kotlin/app/dqxn/android/AlertSoundManager.kt
    - android/app/src/main/kotlin/app/dqxn/android/CrashRecovery.kt
    - android/app/src/main/kotlin/app/dqxn/android/StubEntitlementManager.kt
    - android/app/proguard-rules.pro
    - android/app/src/test/kotlin/app/dqxn/android/CrashRecoveryTest.kt
    - android/app/src/test/kotlin/app/dqxn/android/AlertSoundManagerTest.kt
    - android/app/src/test/kotlin/app/dqxn/android/StubEntitlementManagerTest.kt
  modified:
    - android/app/build.gradle.kts
    - android/app/src/main/AndroidManifest.xml

key-decisions:
  - "@ApplicationContext (not @param:ApplicationContext) on @Provides function params -- @param: target only applies to constructor val/var"
  - "FakeSharedPreferences inline in test instead of Robolectric -- JUnit5 compatible, no Android runtime dependency"

patterns-established:
  - "@Multibinds for empty sets that packs will contribute to via @IntoSet"
  - "@EntryPoint pattern for accessing Hilt graph from Application.onCreate()"
  - "CrashRecovery via SharedPreferences with commit() for process-death safety"

requirements-completed: [F1.1, F1.13, F1.23, F13.8, NF20, NF23]

# Metrics
duration: 7min
completed: 2026-02-24
---

# Phase 6 Plan 02: App Shell Summary

**Minimal deployable :app module with Hilt DI assembly, edge-to-edge blank canvas, 5 empty multibinding sets, CrashRecovery safe-mode, AlertSoundManager stub, and StubEntitlementManager**

## Performance

- **Duration:** 7 min
- **Started:** 2026-02-24T03:48:44Z
- **Completed:** 2026-02-24T03:55:40Z
- **Tasks:** 2
- **Files modified:** 12

## Accomplishments
- Compilable :app module linking all SDK/core/data/feature module stubs with Hilt DI graph fully resolving
- Edge-to-edge MainActivity with splash screen, dark canvas placeholder (dashboard_grid test tag), and landscape-locked singleTask
- CrashRecovery detecting 4+ crashes in 60s as safe mode, tested with 7 unit tests covering threshold, boundary conditions, and corruption handling
- All 16 unit tests passing (7 CrashRecovery + 4 AlertSoundManager + 5 StubEntitlementManager)

## Task Commits

Each task was committed atomically:

1. **Task 1: App shell -- Application, Activity, AppModule, manifest, and ProGuard** - `9d073f0` (feat)
2. **Task 2: CrashRecovery, AlertSoundManager, and StubEntitlementManager tests** - `9e89006` (test)

## Files Created/Modified
- `android/app/build.gradle.kts` - Added core:thermal, core:design, core:agentic(debug), sdk:contracts, splashscreen, leakcanary deps
- `android/app/src/main/kotlin/app/dqxn/android/DqxnApplication.kt` - @HiltAndroidApp with crash handler chain (CrashRecovery + CrashEvidenceWriter)
- `android/app/src/main/kotlin/app/dqxn/android/MainActivity.kt` - Single-activity with splash, edge-to-edge, WindowInsetsControllerCompat, blank canvas
- `android/app/src/main/kotlin/app/dqxn/android/di/AppModule.kt` - 5 @Multibinds sets + 3 @Provides @Singleton providers
- `android/app/src/main/kotlin/app/dqxn/android/AlertSoundManager.kt` - Stub AlertEmitter returning UNAVAILABLE
- `android/app/src/main/kotlin/app/dqxn/android/CrashRecovery.kt` - SharedPreferences crash timestamp tracking with commit() safety
- `android/app/src/main/kotlin/app/dqxn/android/StubEntitlementManager.kt` - Free-tier-only EntitlementManager
- `android/app/src/main/AndroidManifest.xml` - Permissions (BT neverForLocation, location, internet), features, resizeableActivity=false, landscape, MAIN/LAUNCHER
- `android/app/proguard-rules.pro` - Keep rules for proto, KSP manifests, widget/provider impls, kotlinx.serialization
- `android/app/src/test/kotlin/app/dqxn/android/CrashRecoveryTest.kt` - 7 tests with FakeSharedPreferences
- `android/app/src/test/kotlin/app/dqxn/android/AlertSoundManagerTest.kt` - 4 tests confirming stub behavior
- `android/app/src/test/kotlin/app/dqxn/android/StubEntitlementManagerTest.kt` - 5 tests for free-only entitlements

## Decisions Made
- `@ApplicationContext` (not `@param:ApplicationContext`) on `@Provides` function parameters -- `@param:` target only valid on primary constructor val/var parameters per KT-73255
- FakeSharedPreferences inlined in CrashRecoveryTest instead of using Robolectric -- keeps tests pure JUnit5 without Android runtime, consistent with Phase 3 pattern

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Fixed core:agentic SemanticsOwnerHolder missing getOrNull import**
- **Found during:** Task 1 (compile verification)
- **Issue:** `SemanticsConfiguration.getOrNull` is an extension function requiring explicit import; code used fully-qualified references which don't work for extension functions
- **Fix:** Added `import androidx.compose.ui.semantics.getOrNull` and `import androidx.compose.ui.semantics.SemanticsProperties`, replaced FQN calls with imported names
- **Files modified:** `android/core/agentic/src/main/kotlin/app/dqxn/android/core/agentic/SemanticsOwnerHolder.kt`
- **Verification:** `:core:agentic:compileDebugKotlin` succeeds
- **Committed in:** `9d073f0` (part of Task 1 commit)

**2. [Rule 1 - Bug] Fixed XML comment double-dash in AndroidManifest**
- **Found during:** Task 2 (test run triggered manifest merge)
- **Issue:** Comment `<!-- BLE is optional -- app works without BLE hardware -->` contains `--` which is illegal in XML comments (SAX parser violation)
- **Fix:** Changed to `<!-- BLE is optional, app works without BLE hardware -->`
- **Files modified:** `android/app/src/main/AndroidManifest.xml`
- **Verification:** `:app:processDebugMainManifest` succeeds
- **Committed in:** `9e89006` (part of Task 2 commit)

---

**Total deviations:** 2 auto-fixed (1 blocking, 1 bug)
**Impact on plan:** Both fixes necessary for compilation. No scope creep.

## Issues Encountered
- `dqxn.android.application` convention plugin already applies `dqxn.android.compose`, so the plan's instruction to add it was correctly skipped (no duplication)
- `@param:ApplicationContext` annotation target error on `@Provides` function parameter -- fixed by using `@ApplicationContext` directly (KT-73255 `@param:` only for constructor params)

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- :app module compiles and links all module stubs, ready for Phase 7 dashboard shell integration
- Empty multibinding sets resolve correctly, packs can contribute via @IntoSet when Phase 8 lands
- CrashRecovery safe-mode detection ready for Phase 7 safe-mode UI
- AlertSoundManager stub ready for Phase 7 NotificationCoordinator wiring
- StubEntitlementManager ready until Phase 10 Play Billing replacement

## Self-Check: PASSED

- All 11 files verified present on disk
- Both task commits (9d073f0, 9e89006) verified in git history
- `:app:compileDebugKotlin` BUILD SUCCESSFUL
- `:app:testDebugUnitTest` BUILD SUCCESSFUL (16 tests, 0 failures)

---
*Phase: 06-deployable-app-agentic-framework*
*Completed: 2026-02-24*
