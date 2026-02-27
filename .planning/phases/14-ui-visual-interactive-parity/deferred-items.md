# Phase 14 Deferred Items

## Pre-existing Test Compilation Errors (out of scope)

1. **ThemeSelectorTest.kt** - Missing parameters `isDark` and `onCreateNewTheme` in ThemeSelector call sites, unresolved `PREVIEW_TIMEOUT_MS` reference. Likely needs updates from plan 14-06 (theme selector rework).

2. **DesignTokenWiringTest.kt** - Unresolved reference `named` (5 occurrences). Likely needs Truth assertion API update.

3. **KSP SetupEvaluatorImpl resolution failure** - `:feature:dashboard:kspDebugKotlin` fails because `SetupEvaluatorImpl` (in `:feature:settings`) cannot be resolved during Hilt KSP processing of `DashboardViewModel`. This blocks running Compose UI tests (CornerBracketTest) that require KSP-generated Hilt code. The CornerBracketTest compiles successfully when KSP is skipped.

4. **WidgetPicker.kt** - `Unresolved reference 'aspectRatio'` in `:feature:settings:compileDebugKotlin`. Pre-existing.

5. **DashboardButtonBarAutoHideTest.kt** - Missing `isDefault` parameter. Pre-existing.

These errors pre-date plans 14-01 through 14-14 and are not caused by changes in these plans.
