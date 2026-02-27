# Debug: Themes Pack Not Wired Up

**Status:** ROOT CAUSE FOUND — integration gap, not bug
**Date:** 2026-02-27

## Summary

The themes pack (`pack/themes/`) is **structurally registered** — module included, Hilt multibinding generates `@Binds @IntoSet ThemeProvider`, 22 premium JSON themes parse at runtime. But the dashboard shell **never consumes** the `Set<ThemeProvider>` multibinding. The coordinator talks only to `BuiltInThemes` (2 free themes).

## Root Cause

`ThemeCoordinator` (`:feature:dashboard`) is injected with `BuiltInThemes` (`:core:design`), which only knows `slate` and `minimalist` (bare IDs, no pack prefix). It has no reference to `Set<ThemeProvider>` from Hilt. Lines 80, 96, 117 all call `builtInThemes.resolveById()` — pack-namespaced IDs like `"themes:cyberpunk"` silently fail to `null`.

## All Gaps Identified

### Gap 1: ThemeCoordinator only resolves built-in themes
- `handleSetTheme()` → `builtInThemes.resolveById()` — no pack theme resolution
- `initialize()` light/dark observers → same `builtInThemes.resolveById()` fallback
- **Fix:** Inject `Set<ThemeProvider>`, build a `themeId → DashboardThemeDefinition` lookup map

### Gap 2: Theme ID namespace collision
- `:sdk:ui` DefaultTheme.kt: `SlateTheme` (id=`"slate"`), `MinimalistTheme` (id=`"minimalist"`)
- `:pack:essentials`: `"essentials:slate"`, `"essentials:minimalist"` — different IDs AND slightly different colors
- `UserPreferencesRepositoryImpl` defaults to bare `"minimalist"` / `"slate"`
- At runtime: 4 "free" themes that are conceptually 2
- **Fix:** Decide canonical source. Likely: `:sdk:ui` defaults are fallbacks only (used when no packs loaded), essentials pack is the user-facing version. `BuiltInThemes` should delegate to pack themes when available.

### Gap 3: No ThemeRepository aggregation layer
- Old codebase: `ThemeRepositoryImpl` aggregated `Set<ThemeProvider>` + custom themes + entitlement revocation
- New codebase: `ThemeCoordinator` + `BuiltInThemes` + `ThemeAutoSwitchEngine` — no aggregation
- **Fix:** Either enhance `ThemeCoordinator` to aggregate, or add a `ThemeRepository` between coordinator and providers

### Gap 4: CustomThemeRepository missing
- `CustomThemeSerializer` exists (Proto DataStore serializer)
- `CustomThemeStoreProto` exists
- No repository class wrapping persistence
- **Fix:** Implement `CustomThemeRepository` to complete custom theme CRUD

### Gap 5: Entitlement revocation not wired
- Old codebase reverted premium themes to free defaults on entitlement loss
- New `ThemeCoordinator` has no such logic
- `EntitlementManager` is currently `StubEntitlementManager`
- **Fix:** Add entitlement observation in coordinator, revert to free theme on loss

### Gap 6: ThemeSelector data source unclear
- `ThemeSelector` exists in `:feature:settings` with full UI (free-first ordering, preview lifecycle)
- Need to verify it receives pack themes through the coordinator or directly
- Phase 11 plans (11-05, 11-06) cover this but are unexecuted

## Relationship to Existing Phases

- **Phase 9 (complete):** Built the pack. Registration works. ✅
- **Phase 10.1 (planned):** Added `@DashboardThemeProvider` KSP handler. ✅ (plan exists, some execution)
- **Phase 11 (planned, unexecuted):** Theme UI cluster. Plans 11-05 through 11-11 cover ThemeSelector, ThemeStudio, ThemeStudioRoute wiring. These plans assume a working theme aggregation layer but don't explicitly create one.
- **Missing from Phase 11:** Gap 1 (coordinator → Set<ThemeProvider> injection), Gap 2 (ID namespace resolution), Gap 3 (aggregation layer)

## Resolution Path

Create a phase (or insert into Phase 11) that:
1. Injects `Set<ThemeProvider>` into `ThemeCoordinator` (or intermediary `ThemeRepository`)
2. Resolves the ID namespace: pack-prefixed as canonical, bare IDs for fallback only
3. Adds `CustomThemeRepository` wrapping the existing Proto DataStore serializer
4. Adds entitlement revocation logic (observe `EntitlementManager.entitlementChanges`)
5. Connects `ThemeSelector` to the full theme catalog (all packs + custom)
