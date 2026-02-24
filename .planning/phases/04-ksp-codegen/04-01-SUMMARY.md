---
phase: 04-ksp-codegen
plan: 01
subsystem: codegen
tags: [ksp, kotlinpoet, hilt-multibinding, compose-stability, annotation-processing]

# Dependency graph
requires:
  - phase: 02-sdk-contracts
    provides: "@DashboardWidget, @DashboardDataProvider, @DashboardSnapshot annotations, WidgetRenderer, DataProvider, DashboardPackManifest types"
  - phase: 01-build-system
    provides: "dqxn.kotlin.jvm convention plugin, KSP 2.3.6 + KotlinPoet 1.18.1 in version catalog, codegen:plugin module stub"
provides:
  - "KSP processor for @DashboardWidget -> Hilt @Binds @IntoSet WidgetRenderer generation"
  - "KSP processor for @DashboardDataProvider -> Hilt @Binds @IntoSet DataProvider<*> generation"
  - "KSP processor for @DashboardSnapshot -> compose_stability_config.txt generation"
  - "PackManifest generation with PackWidgetRef and PackDataProviderRef entries"
  - "TypeId validation ({packId}:{widget-name} format enforcement)"
  - "Snapshot validation (data class + DataSnapshot + @Immutable + val-only)"
  - "PackConventionPlugin packId KSP arg + stability config path wiring"
  - "kctfork 0.8.0 in version catalog for compile-testing (Plan 02)"
affects: [04-02-ksp-codegen, 04-03-ksp-codegen, 08-essentials-pack]

# Tech tracking
tech-stack:
  added: [kctfork-0.8.0]
  patterns: [hub-and-spoke-ksp-processor, kotlinpoet-hilt-generation, ksp-stability-config-generation]

key-files:
  created:
    - "android/codegen/plugin/src/main/kotlin/app/dqxn/android/codegen/plugin/PluginProcessor.kt"
    - "android/codegen/plugin/src/main/kotlin/app/dqxn/android/codegen/plugin/PluginProcessorProvider.kt"
    - "android/codegen/plugin/src/main/kotlin/app/dqxn/android/codegen/plugin/handlers/WidgetHandler.kt"
    - "android/codegen/plugin/src/main/kotlin/app/dqxn/android/codegen/plugin/handlers/DataProviderHandler.kt"
    - "android/codegen/plugin/src/main/kotlin/app/dqxn/android/codegen/plugin/handlers/SnapshotHandler.kt"
    - "android/codegen/plugin/src/main/kotlin/app/dqxn/android/codegen/plugin/generation/HiltModuleGenerator.kt"
    - "android/codegen/plugin/src/main/kotlin/app/dqxn/android/codegen/plugin/generation/ManifestGenerator.kt"
    - "android/codegen/plugin/src/main/kotlin/app/dqxn/android/codegen/plugin/generation/StabilityConfigGenerator.kt"
    - "android/codegen/plugin/src/main/kotlin/app/dqxn/android/codegen/plugin/validation/TypeIdValidator.kt"
    - "android/codegen/plugin/src/main/kotlin/app/dqxn/android/codegen/plugin/validation/SnapshotValidator.kt"
    - "android/codegen/plugin/src/main/kotlin/app/dqxn/android/codegen/plugin/model/WidgetInfo.kt"
    - "android/codegen/plugin/src/main/kotlin/app/dqxn/android/codegen/plugin/model/ProviderInfo.kt"
    - "android/codegen/plugin/src/main/kotlin/app/dqxn/android/codegen/plugin/model/SnapshotInfo.kt"
    - "android/codegen/plugin/src/main/resources/META-INF/services/com.google.devtools.ksp.processing.SymbolProcessorProvider"
  modified:
    - "android/gradle/libs.versions.toml"
    - "android/codegen/plugin/build.gradle.kts"
    - "android/build-logic/convention/src/main/kotlin/PackConventionPlugin.kt"

key-decisions:
  - "Hub-and-spoke processor: single PluginProcessor delegates to WidgetHandler, DataProviderHandler, SnapshotHandler"
  - "Single-pass execution with invoked flag -- no multi-round KSP processing needed"
  - "DataProvider dataType resolved from snapshot type argument's @DashboardSnapshot annotation, empty string fallback for cross-module"
  - "HiltModuleGenerator produces interface (not abstract class) for @Binds efficiency"
  - "ManifestGenerator uses aggregating=true (depends on ALL annotated symbols), HiltModuleGenerator uses aggregating=false (per-class isolation)"
  - "StabilityConfigGenerator writes to KSP resource output via codeGenerator.createNewFile with txt extension"
  - "PackConventionPlugin uses stabilityConfigurationFiles (plural, additive) to add generated config alongside base config"

patterns-established:
  - "Hub-and-spoke KSP processor: single SymbolProcessorProvider -> SymbolProcessor with handler delegation per annotation type"
  - "KotlinPoet Hilt module generation: @Module @InstallIn interface with @Binds @IntoSet abstract functions"
  - "Annotation FQN string constants: processor reads FQNs as strings, no compile dependency on annotated module"
  - "KSP stability config generation: plain text file via codeGenerator.createNewFile + convention plugin wiring"

requirements-completed: [F2.12, F3.8]

# Metrics
duration: 4min
completed: 2026-02-24
---

# Phase 4 Plan 01: KSP Plugin Processor Summary

**Hub-and-spoke KSP processor with 3 handlers, 3 generators, 2 validators generating Hilt multibinding modules, pack manifests, and Compose stability configs from @DashboardWidget/@DashboardDataProvider/@DashboardSnapshot annotations**

## Performance

- **Duration:** 4 min
- **Started:** 2026-02-24T01:33:16Z
- **Completed:** 2026-02-24T01:37:29Z
- **Tasks:** 2
- **Files modified:** 17

## Accomplishments

- Complete `:codegen:plugin` KSP processor compiling as pure JVM module (no :sdk:contracts dependency)
- Hub processor with 3 annotation handlers: Widget, DataProvider, Snapshot
- 3 generators: Hilt @Module with @Binds @IntoSet, PackManifest object with widget/provider refs, Compose stability config txt
- 2 validators: TypeId format regex enforcement, Snapshot structure validation (data class + DataSnapshot + @Immutable + val-only)
- PackConventionPlugin passes packId KSP arg and wires generated stability config into Compose compiler
- kctfork 0.8.0 added to version catalog for Plan 02 compile-testing

## Task Commits

Each task was committed atomically:

1. **Task 1: Build infrastructure** - `7f8290e` (chore)
2. **Task 2: Implement processor** - `5265049` (feat)

## Files Created/Modified

- `android/gradle/libs.versions.toml` - Added kctfork 0.8.0 version + core/ksp library entries
- `android/codegen/plugin/build.gradle.kts` - Full dependencies: ksp-api, kotlinpoet, kotlinpoet-ksp, test deps
- `android/build-logic/convention/src/main/kotlin/PackConventionPlugin.kt` - packId KSP arg + stability config wiring
- `android/codegen/plugin/src/main/kotlin/.../PluginProcessorProvider.kt` - KSP service entry point
- `android/codegen/plugin/src/main/kotlin/.../PluginProcessor.kt` - Hub processor with single-pass execution
- `android/codegen/plugin/src/main/kotlin/.../model/WidgetInfo.kt` - Widget metadata data class
- `android/codegen/plugin/src/main/kotlin/.../model/ProviderInfo.kt` - Provider metadata data class
- `android/codegen/plugin/src/main/kotlin/.../model/SnapshotInfo.kt` - Snapshot metadata data class
- `android/codegen/plugin/src/main/kotlin/.../handlers/WidgetHandler.kt` - @DashboardWidget handler
- `android/codegen/plugin/src/main/kotlin/.../handlers/DataProviderHandler.kt` - @DashboardDataProvider handler
- `android/codegen/plugin/src/main/kotlin/.../handlers/SnapshotHandler.kt` - @DashboardSnapshot handler
- `android/codegen/plugin/src/main/kotlin/.../generation/HiltModuleGenerator.kt` - Hilt @Module generation
- `android/codegen/plugin/src/main/kotlin/.../generation/ManifestGenerator.kt` - PackManifest generation
- `android/codegen/plugin/src/main/kotlin/.../generation/StabilityConfigGenerator.kt` - Compose stability config
- `android/codegen/plugin/src/main/kotlin/.../validation/TypeIdValidator.kt` - TypeId format validator
- `android/codegen/plugin/src/main/kotlin/.../validation/SnapshotValidator.kt` - Snapshot structure validator
- `android/codegen/plugin/src/main/resources/META-INF/services/...SymbolProcessorProvider` - Service loader registration

## Decisions Made

- **Hub-and-spoke architecture:** Single PluginProcessor delegates to WidgetHandler, DataProviderHandler, SnapshotHandler rather than separate SymbolProcessorProviders. Coordinated output (manifest needs both widget + provider info). Single-pass via `invoked` flag.
- **HiltModuleGenerator produces interface:** Interfaces are preferred for `@Binds` -- Dagger generates no implementation code for pure `@Binds` interface methods, reducing dex size vs abstract class approach.
- **DataProvider dataType from snapshot type argument:** The processor attempts to resolve `DataProvider<T>`'s type argument, looks for `@DashboardSnapshot` on it, and extracts `dataType`. Falls back to empty string for cross-module snapshots -- documented limitation since the runtime `DataProviderRegistry` has the actual `dataType` from the provider instance.
- **Aggregating vs isolating:** ManifestGenerator uses `aggregating = true` (manifest depends on ALL annotated symbols). HiltModuleGenerator uses `aggregating = false` (per-class isolation for incremental processing).
- **stabilityConfigurationFiles (plural):** PackConventionPlugin uses the additive API to add KSP-generated config alongside the base config from AndroidComposeConventionPlugin.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Processor compiles and is ready for Plan 02 compile-testing via kctfork
- PackConventionPlugin wiring complete for pack modules to consume the processor
- kctfork version compatibility with Kotlin 2.3.10 still unverified -- will surface in Plan 02 tests

## Self-Check: PASSED

- All 15 created files verified present on disk
- Both task commits (7f8290e, 5265049) verified in git log
- `:codegen:plugin:compileKotlin` BUILD SUCCESSFUL
- Service file contains correct FQN
- No `:sdk:contracts` dependency in `:codegen:plugin` build.gradle.kts

---
*Phase: 04-ksp-codegen*
*Completed: 2026-02-24*
