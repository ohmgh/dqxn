---
phase: 09-themes-demo-chaos
plan: 01
subsystem: themes
tags: [themes, json-parsing, kotlinx-serialization, entitlement-gating, hilt-multibinding]

# Dependency graph
requires:
  - phase: 08-essentials-pack
    provides: ThemeProvider interface, EssentialsThemeProvider pattern, DashboardThemeDefinition type
  - phase: 05-core-infrastructure
    provides: ThemeAutoSwitchEngine (SOLAR_AUTO/ILLUMINANCE_AUTO modes), BackgroundStyle enum
provides:
  - 22 premium theme JSON resource files in new schema format
  - ThemesPackThemeProvider loading 22 themes with entitlement gating
  - ThemesThemeModule Hilt @Binds @IntoSet for ThemeProvider
  - Pack-local ThemeFileParser with parseHexColor and parseThemeJson
  - F6.3/F6.4 gating chain verification tests
affects: [09-themes-demo-chaos, 11-theme-ui, theme-studio]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Pack-local JSON parser duplicating parseHexColor from :core:design (packs cannot depend on :core:*)"
    - "Post-parse entitlement injection via .copy(requiredAnyEntitlement = setOf('themes'))"
    - "JUnit5 @ParameterizedTest @MethodSource for per-file validation"

key-files:
  created:
    - android/pack/themes/src/main/kotlin/app/dqxn/android/pack/themes/ThemeFileParser.kt
    - android/pack/themes/src/main/kotlin/app/dqxn/android/pack/themes/ThemesPackThemeProvider.kt
    - android/pack/themes/src/main/kotlin/app/dqxn/android/pack/themes/ThemesThemeModule.kt
    - android/pack/themes/src/main/resources/themes/*.theme.json (22 files)
    - android/pack/themes/src/test/kotlin/app/dqxn/android/pack/themes/ThemesPackThemeProviderTest.kt
    - android/pack/themes/src/test/kotlin/app/dqxn/android/pack/themes/ThemeEntitlementGatingChainTest.kt
    - android/pack/themes/src/test/kotlin/app/dqxn/android/pack/themes/ThemeJsonValidationTest.kt
  modified: []

key-decisions:
  - "Pack-local ThemeFileParser over inline DashboardThemeDefinition construction -- JSON files as source of truth reduces error surface of manually transcribing 22 themes"
  - "assertWithMessage() over .named() for Truth assertions -- .named() deprecated in current Truth version"
  - "dqxn.pack convention plugin already provides kotlinx.serialization plugin and dependency -- no explicit additions needed in build.gradle.kts"

patterns-established:
  - "Pack-local JSON theme parser pattern: duplicate minimal parseHexColor + @Serializable schema from :core:design, parse to DashboardThemeDefinition"
  - "Post-parse entitlement injection: parseThemeJson returns ungated definition, provider applies .copy(requiredAnyEntitlement) for separation of concerns"

requirements-completed: [F6.1, F6.2, F6.3, F6.4]

# Metrics
duration: 7min
completed: 2026-02-25
---

# Phase 9 Plan 01: Themes Pack Summary

**22 premium themes loaded from migrated JSON resources with themes-entitlement gating, pack-local parser, and 147 validation tests**

## Performance

- **Duration:** 7 min
- **Started:** 2026-02-25T01:51:58Z
- **Completed:** 2026-02-25T01:59:10Z
- **Tasks:** 2
- **Files modified:** 28

## Accomplishments
- Migrated 22 theme JSON files from old format (simple gradient stop arrays, colors.highlight/widgetBorder) to new schema (explicit {color, position} gradient stops, colors.background/surface/onSurface)
- Created pack-local ThemeFileParser with @Serializable schema types and parseHexColor (duplicated from :core:design since packs cannot depend on :core:*)
- ThemesPackThemeProvider loads all 22 themes with requiredAnyEntitlement = setOf("themes") ensuring F6.2/F6.3/F6.4 gating chain completeness
- 147 tests across 3 test classes: provider contract tests, entitlement gating chain tests, and per-file structural validation

## Task Commits

Each task was committed atomically:

1. **Task 1: Migrate 22 theme JSON files + create pack-local parser** - `bcdce4f` (feat)
2. **Task 2: ThemesPackThemeProvider + Hilt module + all tests** - `09eda82` (feat)

## Files Created/Modified
- `android/pack/themes/src/main/resources/themes/*.theme.json` (22 files) - Migrated premium theme JSON definitions
- `android/pack/themes/src/main/kotlin/.../ThemeFileParser.kt` - Pack-local @Serializable schema + parseHexColor + parseThemeJson
- `android/pack/themes/src/main/kotlin/.../ThemesPackThemeProvider.kt` - ThemeProvider impl loading 22 gated themes
- `android/pack/themes/src/main/kotlin/.../ThemesThemeModule.kt` - Hilt @Binds @IntoSet ThemeProvider binding
- `android/pack/themes/src/test/kotlin/.../ThemesPackThemeProviderTest.kt` - 10 provider contract tests
- `android/pack/themes/src/test/kotlin/.../ThemeEntitlementGatingChainTest.kt` - 3 F6.3/F6.4 gating chain proofs
- `android/pack/themes/src/test/kotlin/.../ThemeJsonValidationTest.kt` - 134 parameterized per-file validation tests

## Decisions Made
- Pack-local ThemeFileParser over inline construction: JSON files as source of truth reduces error surface
- assertWithMessage() over .named(): .named() deprecated in current Truth version used by this project
- dqxn.pack convention plugin already provides kotlinx.serialization: no explicit build.gradle.kts additions needed

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed Truth .named() usage to assertWithMessage()**
- **Found during:** Task 2 (test compilation)
- **Issue:** Truth's `.named()` method is unavailable/deprecated in the project's Truth version, causing compilation errors
- **Fix:** Replaced all `.named("description")` chains with `assertWithMessage("description").that(value)` pattern
- **Files modified:** ThemeEntitlementGatingChainTest.kt, ThemeJsonValidationTest.kt
- **Verification:** All 147 tests compile and pass
- **Committed in:** 09eda82 (Task 2 commit)

---

**Total deviations:** 1 auto-fixed (1 bug)
**Impact on plan:** Minor API adjustment. No scope creep.

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Themes pack complete and compilable, ready for integration with Theme Studio (Phase 11)
- F6.1-F6.4 requirements fully covered with entitlement gating chain proven by tests
- Demo pack (09-02) and chaos infrastructure (09-03+) can proceed independently

## Self-Check: PASSED

All files verified present. Both commits (bcdce4f, 09eda82) found in git log. 22 theme JSON files confirmed.

---
*Phase: 09-themes-demo-chaos*
*Completed: 2026-02-25*
