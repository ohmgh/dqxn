---
phase: 02-sdk-contracts-common
plan: 02
subsystem: sdk
tags: [kotlin, compose-runtime, sealed-interface, immutable-collections, ksp-annotations, entitlements, data-provider]

# Dependency graph
requires:
  - phase: 02-sdk-contracts-common
    plan: 01
    provides: AppResult<T>, AppError (7 variants), ConnectionStateMachine, dispatcher qualifiers, flow extensions
provides:
  - WidgetRenderer contract extending WidgetSpec + Gated with @Composable Render()
  - WidgetData with KClass-keyed ImmutableMap for type-safe multi-slot snapshot access
  - DataProvider<T : DataSnapshot> generic interface enforcing compile-time type safety
  - Gated interface with OR-logic isAccessible extension (null/empty = free)
  - ProviderFault sealed interface (7 variants) in main source set for debug-runtime chaos injection
  - KSP annotations (@DashboardWidget, @DashboardDataProvider, @DashboardSnapshot) with SOURCE retention
  - Entitlement contracts (Gated, EntitlementManager, Entitlements constants)
  - DataSchema with stalenessThresholdMs for provider health monitoring
  - UnitSnapshot sentinel for action-only providers
  - Stub SettingDefinition, SetupPageDefinition, SetupDefinition for Plan 03
affects: [02-sdk-contracts-common/03, 02-sdk-contracts-common/04, 02-sdk-contracts-common/05, 03-sdk-observability-analytics-ui, 04-ksp-codegen, 07-dashboard-shell, 08-essentials-pack]

# Tech tracking
tech-stack:
  added: [kotlinx-collections-immutable, kotlinx-serialization-json, compose-runtime-compileOnly, compose-ui-compileOnly]
  patterns: [compileOnly-compose-bom-for-annotation-only, kclass-keyed-immutable-map, gated-or-logic-extension, sealed-interface-fault-types]

key-files:
  created:
    - android/sdk/contracts/src/main/kotlin/app/dqxn/android/sdk/contracts/widget/WidgetRenderer.kt
    - android/sdk/contracts/src/main/kotlin/app/dqxn/android/sdk/contracts/widget/WidgetSpec.kt
    - android/sdk/contracts/src/main/kotlin/app/dqxn/android/sdk/contracts/widget/WidgetData.kt
    - android/sdk/contracts/src/main/kotlin/app/dqxn/android/sdk/contracts/widget/WidgetStyle.kt
    - android/sdk/contracts/src/main/kotlin/app/dqxn/android/sdk/contracts/widget/WidgetContext.kt
    - android/sdk/contracts/src/main/kotlin/app/dqxn/android/sdk/contracts/widget/WidgetDefaults.kt
    - android/sdk/contracts/src/main/kotlin/app/dqxn/android/sdk/contracts/widget/WidgetAction.kt
    - android/sdk/contracts/src/main/kotlin/app/dqxn/android/sdk/contracts/widget/SettingsAwareSizer.kt
    - android/sdk/contracts/src/main/kotlin/app/dqxn/android/sdk/contracts/entitlement/Gated.kt
    - android/sdk/contracts/src/main/kotlin/app/dqxn/android/sdk/contracts/entitlement/EntitlementManager.kt
    - android/sdk/contracts/src/main/kotlin/app/dqxn/android/sdk/contracts/entitlement/Entitlements.kt
    - android/sdk/contracts/src/main/kotlin/app/dqxn/android/sdk/contracts/annotation/DashboardWidget.kt
    - android/sdk/contracts/src/main/kotlin/app/dqxn/android/sdk/contracts/annotation/DashboardDataProvider.kt
    - android/sdk/contracts/src/main/kotlin/app/dqxn/android/sdk/contracts/annotation/DashboardSnapshot.kt
    - android/sdk/contracts/src/main/kotlin/app/dqxn/android/sdk/contracts/provider/DataSnapshot.kt
    - android/sdk/contracts/src/main/kotlin/app/dqxn/android/sdk/contracts/provider/DataProvider.kt
    - android/sdk/contracts/src/main/kotlin/app/dqxn/android/sdk/contracts/provider/DataProviderSpec.kt
    - android/sdk/contracts/src/main/kotlin/app/dqxn/android/sdk/contracts/provider/ProviderPriority.kt
    - android/sdk/contracts/src/main/kotlin/app/dqxn/android/sdk/contracts/provider/ActionableProvider.kt
    - android/sdk/contracts/src/main/kotlin/app/dqxn/android/sdk/contracts/provider/DataProviderInterceptor.kt
    - android/sdk/contracts/src/main/kotlin/app/dqxn/android/sdk/contracts/provider/DataSchema.kt
    - android/sdk/contracts/src/main/kotlin/app/dqxn/android/sdk/contracts/provider/DataTypes.kt
    - android/sdk/contracts/src/main/kotlin/app/dqxn/android/sdk/contracts/provider/UnitSnapshot.kt
    - android/sdk/contracts/src/main/kotlin/app/dqxn/android/sdk/contracts/fault/ProviderFault.kt
    - android/sdk/contracts/src/main/kotlin/app/dqxn/android/sdk/contracts/setup/SetupPageDefinition.kt
    - android/sdk/contracts/src/main/kotlin/app/dqxn/android/sdk/contracts/setup/SetupDefinition.kt
    - android/sdk/contracts/src/main/kotlin/app/dqxn/android/sdk/contracts/settings/SettingDefinition.kt
  modified:
    - android/sdk/contracts/build.gradle.kts

key-decisions:
  - "compileOnly(platform(libs.compose.bom)) resolves compose-runtime/ui versions without Compose compiler"
  - "AGP 9 testFixtures via android { testFixtures { enable = true } } instead of java-test-fixtures plugin"
  - "WidgetData.withSlot handles PersistentMap.put via runtime type check to preserve ImmutableMap public API"
  - "DataSnapshot created in Task 1 (not Task 2) to unblock WidgetSpec.compatibleSnapshots compilation"

patterns-established:
  - "compileOnly compose BOM pattern for annotation-only modules without Compose compiler"
  - "KClass-keyed ImmutableMap for type-safe multi-slot data delivery"
  - "Gated.isAccessible extension with OR-logic (null/empty = free)"
  - "ProviderFault in main source set (not testFixtures) for debug-runtime ChaosProviderInterceptor"
  - "Stub sealed interfaces for forward references between sequential plans"

requirements-completed: [F2.1, F2.2, F2.4, F2.5, F2.10, F2.11, F2.12, F2.16, F2.19, F2.20, F3.1, F3.2, F3.6, F3.8, F8.1, F8.3]

# Metrics
duration: 5min
completed: 2026-02-24
---

# Phase 2 Plan 02: SDK Contracts Widget/Provider Types Summary

**Widget/provider/entitlement/annotation type surface in :sdk:contracts with compileOnly compose-runtime via BOM, KClass-keyed WidgetData, 7-variant ProviderFault, and 3 KSP annotations**

## Performance

- **Duration:** 5 min
- **Started:** 2026-02-23T18:52:55Z
- **Completed:** 2026-02-23T18:57:53Z
- **Tasks:** 2
- **Files modified:** 28 (1 modified, 27 created)

## Accomplishments
- Full widget type surface: WidgetRenderer (extends WidgetSpec + Gated), WidgetData (KClass-keyed ImmutableMap), WidgetStyle (@Serializable), WidgetContext, WidgetDefaults, WidgetAction (sealed), SettingsAwareSizer
- Full provider type surface: DataProvider<T : DataSnapshot> (generic), DataProviderSpec, DataSchema, DataTypes (12 constants), UnitSnapshot sentinel, ActionableProvider, DataProviderInterceptor
- Entitlement gating: Gated with OR-logic isAccessible extension, EntitlementManager (minimal V1), Entitlements constants (free/plus/themes)
- KSP annotations: @DashboardWidget, @DashboardDataProvider, @DashboardSnapshot (all SOURCE retention)
- ProviderFault sealed interface (7 variants: Kill, Delay, Error, ErrorOnNext, Corrupt, Flap, Stall) in main source set
- compileOnly compose-runtime+ui resolved via platform(compose-bom) without Compose compiler
- Stub types (SettingDefinition, SetupPageDefinition, SetupDefinition) ready for Plan 03 to flesh out
- testFixtures enabled via AGP 9 `android { testFixtures { enable = true } }`

## Task Commits

Each task was committed atomically:

1. **Task 1: Configure build + widget/entitlement/annotation types** - `410cd2b` (feat)
2. **Task 2: Provider, fault, and data schema types** - `ec143b5` (feat)

**Plan metadata:** (pending)

## Files Created/Modified
- `android/sdk/contracts/build.gradle.kts` - compileOnly compose BOM, testFixtures, serialization, api(:sdk:common)
- `.../widget/WidgetRenderer.kt` - Core rendering contract extending WidgetSpec + Gated
- `.../widget/WidgetSpec.kt` - Widget metadata (typeId, displayName, compatibleSnapshots, settingsSchema, aspectRatio)
- `.../widget/WidgetData.kt` - KClass-keyed ImmutableMap with snapshot<T>() reified accessor
- `.../widget/WidgetStyle.kt` - @Serializable style with BackgroundStyle enum
- `.../widget/WidgetContext.kt` - Timezone, locale, region context for widget defaults
- `.../widget/WidgetDefaults.kt` - Default size and settings for widgets
- `.../widget/WidgetAction.kt` - Sealed interface: Tap, MediaControl, TripReset, Custom
- `.../widget/SettingsAwareSizer.kt` - Dynamic sizing based on settings
- `.../entitlement/Gated.kt` - Interface + isAccessible extension with OR-logic
- `.../entitlement/EntitlementManager.kt` - Minimal V1 entitlement contract
- `.../entitlement/Entitlements.kt` - Constants: FREE, THEMES, PLUS
- `.../annotation/DashboardWidget.kt` - KSP annotation (SOURCE retention)
- `.../annotation/DashboardDataProvider.kt` - KSP annotation (SOURCE retention)
- `.../annotation/DashboardSnapshot.kt` - KSP annotation (SOURCE retention)
- `.../provider/DataSnapshot.kt` - @Immutable interface with timestamp
- `.../provider/DataProvider.kt` - Generic DataProvider<T : DataSnapshot> contract
- `.../provider/DataProviderSpec.kt` - Provider metadata extending Gated
- `.../provider/ProviderPriority.kt` - Enum: HARDWARE, DEVICE_SENSOR, NETWORK, SIMULATED
- `.../provider/ActionableProvider.kt` - Bidirectional provider with onAction(WidgetAction)
- `.../provider/DataProviderInterceptor.kt` - Debug/chaos flow interception contract
- `.../provider/DataSchema.kt` - Schema with stalenessThresholdMs + DataFieldSpec
- `.../provider/DataTypes.kt` - 12 core data type constants
- `.../provider/UnitSnapshot.kt` - Sentinel for action-only providers
- `.../fault/ProviderFault.kt` - 7 fault variants in main source set
- `.../setup/SetupPageDefinition.kt` - Stub for Plan 03
- `.../setup/SetupDefinition.kt` - Stub sealed interface for Plan 03
- `.../settings/SettingDefinition.kt` - Stub sealed interface for Plan 03

## Decisions Made
- **compileOnly(platform(compose-bom)) pattern:** Used `compileOnly(platform(libs.compose.bom))` to resolve compose-runtime and compose-ui versions without applying the Compose compiler. The catalog entries have no version (expected BOM resolution). This pattern works cleanly with Gradle 9.3.1.
- **AGP 9 testFixtures instead of java-test-fixtures:** The Gradle `java-test-fixtures` plugin conflicts with AGP (duplicate `testFixturesImplementation` configuration). AGP 9 uses `android { testFixtures { enable = true } }` instead.
- **WidgetData.withSlot PersistentMap handling:** `ImmutableMap` doesn't have `put()` — only `PersistentMap` does. Added runtime type check with fallback `toPersistentMap()` to keep the public API as `ImmutableMap` while preserving efficient structural sharing.
- **DataSnapshot in Task 1:** Moved DataSnapshot creation to Task 1 since WidgetSpec.compatibleSnapshots and WidgetData.snapshots require it for compilation. This is a forward-dependency that the plan's task boundary didn't account for.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] java-test-fixtures plugin incompatible with AGP 9**
- **Found during:** Task 1 (build.gradle.kts configuration)
- **Issue:** `id("java-test-fixtures")` plugin fails with "Cannot add a configuration with name 'testFixturesImplementation' as a configuration with that name already exists" — AGP 9 already provides testFixtures
- **Fix:** Replaced `id("java-test-fixtures")` with `android { testFixtures { enable = true } }`
- **Files modified:** android/sdk/contracts/build.gradle.kts
- **Verification:** Compilation succeeds
- **Committed in:** 410cd2b (Task 1 commit)

**2. [Rule 1 - Bug] ImmutableMap.put() does not exist**
- **Found during:** Task 1 (WidgetData compilation)
- **Issue:** Plan specified `snapshots.put(type, snapshot)` but `ImmutableMap` interface doesn't have `put()` — only `PersistentMap` subtype does
- **Fix:** Added runtime type check: `when (val current = snapshots) { is PersistentMap -> current.put(...); else -> current.toPersistentMap().put(...) }`
- **Files modified:** WidgetData.kt
- **Verification:** Compilation succeeds
- **Committed in:** 410cd2b (Task 1 commit)

**3. [Rule 3 - Blocking] DataSnapshot needed in Task 1 for WidgetSpec/WidgetData compilation**
- **Found during:** Task 1 (WidgetSpec.compatibleSnapshots references DataSnapshot)
- **Issue:** DataSnapshot is listed in Task 2 files but WidgetSpec.compatibleSnapshots and WidgetData.snapshots require it
- **Fix:** Created DataSnapshot interface in Task 1 instead of Task 2
- **Files modified:** DataSnapshot.kt
- **Verification:** Task 1 compiles successfully
- **Committed in:** 410cd2b (Task 1 commit)

---

**Total deviations:** 3 auto-fixed (1 bug, 2 blocking)
**Impact on plan:** All auto-fixes were compilation necessities. No scope creep. The java-test-fixtures issue was anticipated in research (Pitfall 2) but the resolution was different than expected — AGP 9 provides its own mechanism rather than needing a workaround.

## Issues Encountered
- **Pre-existing spotless violation:** `data/proto/build.gradle.kts` has formatting changes from Phase 1 that remain unstaged (out of scope for this plan).

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- `:sdk:contracts` compiles with all widget, provider, entitlement, annotation, and fault types
- `api(project(":sdk:common"))` exposes AppResult, AppError, ConnectionStateMachine transitively
- Stub types (SettingDefinition, SetupPageDefinition, SetupDefinition) ready for Plan 03 to flesh out with concrete subtypes
- testFixtures enabled for Plan 05's contract test bases (WidgetRendererContractTest, DataProviderContractTest)
- Plan 03 (settings/setup/notification/theme/status/pack types) can proceed immediately

## Self-Check: PASSED

- All 28 source files: FOUND
- Commit 410cd2b (Task 1 — widget/entitlement/annotation types): FOUND
- Commit ec143b5 (Task 2 — provider/fault/schema types): FOUND
- `:sdk:contracts:compileDebugKotlin`: BUILD SUCCESSFUL
- `spotlessCheck`: PASSED
- compose-runtime resolved: 1.10.3 via BOM 2026.02.00
- ProviderFault in main source set: VERIFIED

---
*Phase: 02-sdk-contracts-common*
*Completed: 2026-02-24*
