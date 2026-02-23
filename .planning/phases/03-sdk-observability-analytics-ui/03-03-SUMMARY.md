---
phase: 03-sdk-observability-analytics-ui
plan: 03
subsystem: ui
tags: [compose, theme, widget-container, info-card-layout, composition-local, hilt-multibinding, icon-resolver]

# Dependency graph
requires:
  - phase: 02-sdk-contracts-common
    provides: ThemeSpec, WidgetStyle, BackgroundStyle, WidgetData, DataSnapshot, SizeOption, InfoCardLayoutMode, Gated, SettingDefinition
provides:
  - DashboardThemeDefinition (6 core + 3 semantic color tokens, implements ThemeSpec)
  - GradientSpec with 5 gradient types and Brush conversion
  - SlateTheme (dark) and MinimalistTheme (light) defaults
  - WidgetContainer with graphicsLayer isolation, border, rim, background
  - LocalDashboardTheme, LocalWidgetData, LocalWidgetScope CompositionLocals
  - InfoCardLayout weighted normalization across STANDARD/COMPACT/WIDE modes
  - EnumPreviewRegistry with Hilt @IntoMap pattern
  - IconResolver mapping string names to Material ImageVector via reflection
  - GridConstants (GRID_UNIT_SIZE = 16.dp)
affects: [phase-07-dashboard-shell, phase-08-essentials-pack, phase-09-themes, phase-11-theme-ui]

# Tech tracking
tech-stack:
  added: [compose-material-icons-extended, compose-ui-test-junit4, hilt-android (annotations only)]
  patterns: [static-composition-local-for-theme, graphicsLayer-per-widget, weighted-normalization-layout, concurrent-cache-with-null-wrapper, enum-preview-hilt-multibinding]

key-files:
  created:
    - android/sdk/ui/src/main/kotlin/app/dqxn/android/sdk/ui/theme/DashboardThemeDefinition.kt
    - android/sdk/ui/src/main/kotlin/app/dqxn/android/sdk/ui/theme/GradientSpec.kt
    - android/sdk/ui/src/main/kotlin/app/dqxn/android/sdk/ui/theme/DefaultTheme.kt
    - android/sdk/ui/src/main/kotlin/app/dqxn/android/sdk/ui/theme/LocalDashboardTheme.kt
    - android/sdk/ui/src/main/kotlin/app/dqxn/android/sdk/ui/widget/WidgetContainer.kt
    - android/sdk/ui/src/main/kotlin/app/dqxn/android/sdk/ui/widget/LocalWidgetData.kt
    - android/sdk/ui/src/main/kotlin/app/dqxn/android/sdk/ui/widget/LocalWidgetScope.kt
    - android/sdk/ui/src/main/kotlin/app/dqxn/android/sdk/ui/widget/GridConstants.kt
    - android/sdk/ui/src/main/kotlin/app/dqxn/android/sdk/ui/layout/InfoCardLayout.kt
    - android/sdk/ui/src/main/kotlin/app/dqxn/android/sdk/ui/layout/InfoCardSettings.kt
    - android/sdk/ui/src/main/kotlin/app/dqxn/android/sdk/ui/settings/EnumPreviewRegistry.kt
    - android/sdk/ui/src/main/kotlin/app/dqxn/android/sdk/ui/icon/IconResolver.kt
    - android/sdk/ui/src/test/kotlin/app/dqxn/android/sdk/ui/widget/WidgetContainerTest.kt
    - android/sdk/ui/src/test/kotlin/app/dqxn/android/sdk/ui/layout/InfoCardLayoutTest.kt
    - android/sdk/ui/src/test/kotlin/app/dqxn/android/sdk/ui/settings/EnumPreviewRegistryTest.kt
    - android/sdk/ui/src/test/kotlin/app/dqxn/android/sdk/ui/icon/IconResolverTest.kt
  modified:
    - android/sdk/ui/build.gradle.kts

key-decisions:
  - "Adapted InfoCardLayout to Phase 2 SizeOption (SMALL/MEDIUM/LARGE/EXTRA_LARGE with 0.75/1.0/1.25/1.5 multipliers) instead of plan's old-codebase values (NONE=0/SMALL=0.3/MEDIUM=0.5/LARGE=0.7/XL=1.0)"
  - "Adapted InfoCardLayoutMode to Phase 2 enum (STANDARD/COMPACT/WIDE) instead of plan's old-codebase values (STACK/GRID/COMPACT)"
  - "ConcurrentHashMap null-value workaround via CacheEntry wrapper in IconResolver"
  - "hilt-android added as implementation dep (not full Hilt plugin) for @Inject/@MapKey annotations in EnumPreviewRegistry"
  - "Lazy initialization for IconResolver searchTargets to avoid class-loading issues in test environments"
  - "Gated.requiredAnyEntitlement typed as Set<String>? (matching Phase 2 contracts) not ImmutableSet or List"

patterns-established:
  - "Static CompositionLocal for theme (LocalDashboardTheme) -- infrequent changes, full recomposition correct"
  - "Dynamic compositionLocalOf for per-widget data (LocalWidgetData) -- changes per widget independently"
  - "Slot API pattern for InfoCardLayout: composable lambdas receiving computed TextStyle"
  - "Weighted normalization to 80% target for layout algorithms"
  - "CacheEntry wrapper for ConcurrentHashMap when null values need caching"

requirements-completed: [F12.1, F13.5, F13.6]

# Metrics
duration: 10min
completed: 2026-02-24
---

# Phase 3 Plan 03: SDK UI Module Summary

**DashboardThemeDefinition with 9-token color model, WidgetContainer with graphicsLayer isolation, InfoCardLayout weighted normalization across 3 modes, and 37 unit tests**

## Performance

- **Duration:** 10 min
- **Started:** 2026-02-23T19:35:37Z
- **Completed:** 2026-02-23T19:45:37Z
- **Tasks:** 2
- **Files modified:** 17

## Accomplishments
- DashboardThemeDefinition implementing ThemeSpec with 6 core + 3 semantic color tokens, emphasis levels, gradient specs
- WidgetContainer composable with graphicsLayer (isolated RenderNode), clip, border, background, and rim padding
- InfoCardLayout with deterministic weighted normalization (80% target) across STANDARD/COMPACT/WIDE modes
- EnumPreviewRegistry enabling pack-contributed enum previews via Hilt @IntoMap multibinding
- IconResolver mapping string icon names to Material ImageVector via reflection with null-safe caching
- 37 unit tests passing: 8 WidgetContainer Compose tests, 19 InfoCardLayout math tests, 5 EnumPreviewRegistry tests, 5 IconResolver tests

## Task Commits

Each task was committed atomically:

1. **Task 1: DashboardThemeDefinition + WidgetContainer + CompositionLocals + GridConstants + IconResolver** - `deef59d` (feat)
2. **Task 2: InfoCardLayout + EnumPreviewRegistry + unit tests** - `1851ce8` (absorbed by parallel 03-01 Spotless commit)

Note: Task 2 files were included in commit `1851ce8` due to parallel plan 03-01 running `spotlessApply` which staged all untracked files in the workspace. The content is correct and verified.

## Files Created/Modified
- `android/sdk/ui/build.gradle.kts` - Added :sdk:contracts, :sdk:common, material-icons-extended, hilt-android, compose test deps
- `android/sdk/ui/src/main/kotlin/app/dqxn/android/sdk/ui/theme/DashboardThemeDefinition.kt` - @Immutable data class with 6+3 color tokens implementing ThemeSpec
- `android/sdk/ui/src/main/kotlin/app/dqxn/android/sdk/ui/theme/GradientSpec.kt` - Gradient types (VERTICAL/HORIZONTAL/LINEAR/RADIAL/SWEEP) with Brush conversion
- `android/sdk/ui/src/main/kotlin/app/dqxn/android/sdk/ui/theme/DefaultTheme.kt` - SlateTheme (dark) and MinimalistTheme (light) defaults
- `android/sdk/ui/src/main/kotlin/app/dqxn/android/sdk/ui/theme/LocalDashboardTheme.kt` - Static CompositionLocal defaulting to SlateTheme
- `android/sdk/ui/src/main/kotlin/app/dqxn/android/sdk/ui/widget/WidgetContainer.kt` - Composable with graphicsLayer, clip, border, background, rim
- `android/sdk/ui/src/main/kotlin/app/dqxn/android/sdk/ui/widget/LocalWidgetData.kt` - Dynamic CompositionLocal for per-widget data
- `android/sdk/ui/src/main/kotlin/app/dqxn/android/sdk/ui/widget/LocalWidgetScope.kt` - Static CompositionLocal for supervised coroutine scope
- `android/sdk/ui/src/main/kotlin/app/dqxn/android/sdk/ui/widget/GridConstants.kt` - GRID_UNIT_SIZE = 16.dp
- `android/sdk/ui/src/main/kotlin/app/dqxn/android/sdk/ui/layout/InfoCardLayout.kt` - Weighted normalization layout (STANDARD/COMPACT/WIDE modes)
- `android/sdk/ui/src/main/kotlin/app/dqxn/android/sdk/ui/layout/InfoCardSettings.kt` - Safe parsing helpers for layout mode and size option
- `android/sdk/ui/src/main/kotlin/app/dqxn/android/sdk/ui/settings/EnumPreviewRegistry.kt` - Hilt @IntoMap registry with text fallback
- `android/sdk/ui/src/main/kotlin/app/dqxn/android/sdk/ui/icon/IconResolver.kt` - Reflection-based Material icon resolution with CacheEntry null wrapper
- `android/sdk/ui/src/test/kotlin/app/dqxn/android/sdk/ui/widget/WidgetContainerTest.kt` - 8 Compose tests (background, border, rim, opacity, accessibility)
- `android/sdk/ui/src/test/kotlin/app/dqxn/android/sdk/ui/layout/InfoCardLayoutTest.kt` - 19 tests (normalization, multipliers, parsing, text style)
- `android/sdk/ui/src/test/kotlin/app/dqxn/android/sdk/ui/settings/EnumPreviewRegistryTest.kt` - 5 tests (registration, preview, fallback)
- `android/sdk/ui/src/test/kotlin/app/dqxn/android/sdk/ui/icon/IconResolverTest.kt` - 5 tests (resolution, caching, edge cases)

## Decisions Made
- **Adapted to Phase 2 SizeOption/InfoCardLayoutMode**: The plan specified old-codebase enum values (NONE/SMALL/MEDIUM/LARGE/XL with 0.0-1.0 multipliers, and STACK/GRID/COMPACT layout modes). The Phase 2 contracts define SMALL/MEDIUM/LARGE/EXTRA_LARGE with 0.75-1.5 multipliers and STANDARD/COMPACT/WIDE. Used the actual contract types.
- **ConcurrentHashMap null handling**: Java's ConcurrentHashMap throws NPE on null values. IconResolver wraps results in CacheEntry to cache null (icon-not-found) results without exceptions.
- **Hilt annotations only**: Added `libs.hilt.android` as implementation dep (not full `dqxn.android.hilt` convention plugin) since EnumPreviewRegistry only needs `@Inject`, `@MapKey` annotations, not KSP processing.
- **Lazy searchTargets in IconResolver**: Deferred Icons.Default/Rounded class loading to avoid initialization issues in JVM test environments.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] ConcurrentHashMap NPE on null values in IconResolver**
- **Found during:** Task 2 (IconResolverTest)
- **Issue:** `ConcurrentHashMap.getOrPut()` calls `computeIfAbsent()` internally, which throws NPE when the mapping function returns null. IconResolver returns null for unknown icons.
- **Fix:** Introduced `CacheEntry` wrapper class to store nullable `ImageVector?` values in the non-null ConcurrentHashMap.
- **Files modified:** `android/sdk/ui/src/main/kotlin/app/dqxn/android/sdk/ui/icon/IconResolver.kt`
- **Verification:** All 5 IconResolver tests pass, including null-returning resolve calls.
- **Committed in:** `1851ce8`

**2. [Rule 3 - Blocking] Missing hilt-android dependency for @Inject/@MapKey**
- **Found during:** Task 2 (EnumPreviewRegistry compilation)
- **Issue:** EnumPreviewRegistry uses `javax.inject.Inject` and `dagger.MapKey` which require hilt-android on the classpath.
- **Fix:** Added `implementation(libs.hilt.android)` to sdk:ui build.gradle.kts.
- **Files modified:** `android/sdk/ui/build.gradle.kts`
- **Verification:** Compilation succeeds, EnumPreviewRegistry tests pass.
- **Committed in:** `1851ce8`

---

**Total deviations:** 2 auto-fixed (1 bug, 1 blocking)
**Impact on plan:** Both auto-fixes necessary for correctness. No scope creep.

## Issues Encountered
- **Parallel plan collision**: Plan 03-01 ran `spotlessApply` on the workspace which reformatted Task 1 files and committed Task 2 untracked files under its Spotless commit (`1851ce8`). All code content is correct; only the commit attribution is split.
- **kotlin-reflect warning**: Initial IconResolver used `clazz.kotlin.objectInstance` which requires kotlin-reflect. Fixed by passing known object instances directly instead of using Kotlin reflection.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- `:sdk:ui` module fully operational with Compose compiler, 37 tests passing
- DashboardThemeDefinition ready for consumption by `:core:design` (Phase 5) and pack theme providers (Phase 9)
- WidgetContainer ready for integration into `WidgetSlot` (Phase 7)
- InfoCardLayout ready for 5+ info card widgets in Essentials Pack (Phase 8)
- EnumPreviewRegistry ready for pack-contributed previews via Hilt multibinding
- No blockers identified

## Self-Check: PASSED

- All 16 created files: FOUND
- Commit deef59d (Task 1): FOUND
- Commit 1851ce8 (Task 2 content): FOUND
- compileDebugKotlin: BUILD SUCCESSFUL
- testDebugUnitTest: 37 tests, 0 failures

---
*Phase: 03-sdk-observability-analytics-ui*
*Completed: 2026-02-24*
