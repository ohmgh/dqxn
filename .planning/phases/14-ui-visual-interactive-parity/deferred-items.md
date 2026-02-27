# Phase 14 Deferred Items

## Pre-existing Test Compilation Errors (out of scope)

1. **ThemeSelectorTest.kt** - Missing parameters `isDark` and `onCreateNewTheme` in ThemeSelector call sites, unresolved `PREVIEW_TIMEOUT_MS` reference. Likely needs updates from plan 14-06 (theme selector rework).

2. **DesignTokenWiringTest.kt** - Unresolved reference `named` (5 occurrences). Likely needs Truth assertion API update.

3. **KSP SetupEvaluatorImpl resolution failure** - `:feature:dashboard:kspDebugKotlin` fails because `SetupEvaluatorImpl` (in `:feature:settings`) cannot be resolved during Hilt KSP processing of `DashboardViewModel`. This blocks running Compose UI tests (CornerBracketTest) that require KSP-generated Hilt code. The CornerBracketTest compiles successfully when KSP is skipped.

4. **WidgetPicker.kt** - `Unresolved reference 'aspectRatio'` in `:feature:settings:compileDebugKotlin`. Pre-existing.

5. **DashboardButtonBarAutoHideTest.kt** - Missing `isDefault` parameter. Pre-existing.

These errors pre-date plans 14-01 through 14-14 and are not caused by changes in these plans.

## Pre-existing Test Runtime Failures (discovered by 14-08 regression gate)

6. **DesignTokenWiringTest (dashboard)** - 2 failures: `FocusOverlayToolbar.kt` uses `MaterialTheme.colorScheme` instead of `LocalDashboardTheme`. Created by plan 14-07 without token migration. Needs `FocusOverlayToolbar.kt` added to allowlist or migrated to dashboard tokens.

7. **CornerBracketTest** - 3 failures: `ClassCastException: Object cannot be cast to Modifier` at `DashboardGrid.kt:501`. Robolectric classloader incompatibility with `materializeModifier`. Pre-existing Robolectric/Compose interop issue.

8. **WidgetPickerTest** - 1 failure (`widgets grouped under correct pack headers`): Expects 'Gauge' text in semantics tree but node not found. Pre-existing assertion mismatch.

9. **pack:plus:testDebugUnitTest** - 0 tests discovered, fails on `failOnNoDiscoveredTests`. Module has test sources but no test runner discovers them (likely JUnit5 config issue).
