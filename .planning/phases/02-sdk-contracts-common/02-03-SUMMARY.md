---
phase: 02-sdk-contracts-common
plan: 03
subsystem: sdk
tags: [kotlin, sealed-interface, immutable-collections, compose-runtime, setup, settings, notification, theme, entitlements]

# Dependency graph
requires:
  - phase: 02-sdk-contracts-common
    plan: 02
    provides: WidgetSpec (settingsSchema), DataProvider (setupSchema), Gated, stub SetupDefinition/SettingDefinition/SetupPageDefinition
provides:
  - SetupDefinition sealed interface with 7 subtypes in 3 categories (requirement/display/input)
  - SettingDefinition sealed interface with 12 subtypes all implementing Gated
  - Three-layer visibility semantics (hidden, visibleWhen, requiredAnyEntitlement)
  - InAppNotification sealed interface (Toast + Banner) with priority and alert profile
  - WidgetRenderState sealed interface with 8 overlay state variants
  - ThemeSpec metadata interface extending Gated (no Compose types)
  - WidgetRegistry and DataProviderRegistry interfaces for Phase 7 implementation
  - DashboardPackManifest @Serializable with pack refs and category
  - SetupEvaluator interface, ServiceType enum, VerificationStrategy/VerificationResult
  - AlertEmitter interface + AlertProfile/AlertResult for F9.1-F9.4
  - Settings enums (SizeOption, DateFormatOption, InfoCardLayoutMode, SoundType, etc.)
  - InfoCardSettings factory helper
affects: [02-sdk-contracts-common/04, 02-sdk-contracts-common/05, 03-sdk-observability-analytics-ui, 04-ksp-codegen, 07-dashboard-shell, 08-essentials-pack, 09-themes-demo-chaos, 10-settings-setup-ui]

# Tech tracking
tech-stack:
  added: []
  patterns: [three-layer-visibility-gating, setting-wrapper-double-gating, declarative-service-type-enum, icon-name-string-pattern]

key-files:
  created:
    - android/sdk/contracts/src/main/kotlin/app/dqxn/android/sdk/contracts/setup/SetupDefinition.kt
    - android/sdk/contracts/src/main/kotlin/app/dqxn/android/sdk/contracts/setup/SetupPageDefinition.kt
    - android/sdk/contracts/src/main/kotlin/app/dqxn/android/sdk/contracts/setup/SetupEvaluator.kt
    - android/sdk/contracts/src/main/kotlin/app/dqxn/android/sdk/contracts/setup/ServiceType.kt
    - android/sdk/contracts/src/main/kotlin/app/dqxn/android/sdk/contracts/setup/VerificationStrategy.kt
    - android/sdk/contracts/src/main/kotlin/app/dqxn/android/sdk/contracts/settings/SettingDefinition.kt
    - android/sdk/contracts/src/main/kotlin/app/dqxn/android/sdk/contracts/settings/SettingsEnums.kt
    - android/sdk/contracts/src/main/kotlin/app/dqxn/android/sdk/contracts/settings/InfoCardSettings.kt
    - android/sdk/contracts/src/main/kotlin/app/dqxn/android/sdk/contracts/notification/InAppNotification.kt
    - android/sdk/contracts/src/main/kotlin/app/dqxn/android/sdk/contracts/notification/NotificationPriority.kt
    - android/sdk/contracts/src/main/kotlin/app/dqxn/android/sdk/contracts/notification/AlertProfile.kt
    - android/sdk/contracts/src/main/kotlin/app/dqxn/android/sdk/contracts/notification/AlertEmitter.kt
    - android/sdk/contracts/src/main/kotlin/app/dqxn/android/sdk/contracts/notification/AlertResult.kt
    - android/sdk/contracts/src/main/kotlin/app/dqxn/android/sdk/contracts/notification/NotificationAction.kt
    - android/sdk/contracts/src/main/kotlin/app/dqxn/android/sdk/contracts/status/WidgetRenderState.kt
    - android/sdk/contracts/src/main/kotlin/app/dqxn/android/sdk/contracts/status/WidgetIssue.kt
    - android/sdk/contracts/src/main/kotlin/app/dqxn/android/sdk/contracts/status/WidgetStatusCache.kt
    - android/sdk/contracts/src/main/kotlin/app/dqxn/android/sdk/contracts/theme/ThemeSpec.kt
    - android/sdk/contracts/src/main/kotlin/app/dqxn/android/sdk/contracts/theme/ThemeProvider.kt
    - android/sdk/contracts/src/main/kotlin/app/dqxn/android/sdk/contracts/theme/AutoSwitchMode.kt
    - android/sdk/contracts/src/main/kotlin/app/dqxn/android/sdk/contracts/pack/DashboardPackManifest.kt
    - android/sdk/contracts/src/main/kotlin/app/dqxn/android/sdk/contracts/registry/WidgetRegistry.kt
    - android/sdk/contracts/src/main/kotlin/app/dqxn/android/sdk/contracts/registry/DataProviderRegistry.kt
  modified: []

key-decisions:
  - "InfoStyle and InstructionAction in setup package — shared by both SetupDefinition and SettingDefinition (settings imports from setup)"
  - "SetupDefinition.Setting wrapper defaults delegated from inner SettingDefinition — callers can override any field"
  - "DashboardPackManifest uses @Serializable + @Immutable — KSP generates at build time, runtime deserializable"

patterns-established:
  - "Three-layer visibility gating: hidden (hard skip) -> visibleWhen (lambda) -> requiredAnyEntitlement (OR-logic)"
  - "Setting wrapper double-gating: wrapper's visibleWhen checked first, then inner definition's"
  - "Declarative ServiceType enum replacing Context-dependent lambdas in contracts"
  - "String icon names replacing ImageVector — resolution in :sdk:ui Phase 3"

requirements-completed: [F3.3, F3.4, F3.5, F9.1, F9.2, F9.3, F9.4]

# Metrics
duration: 6min
completed: 2026-02-24
---

# Phase 2 Plan 03: SDK Contracts Setup/Settings/Notification/Theme/Status/Pack Types Summary

**Complete :sdk:contracts type surface with 7-subtype SetupDefinition, 12-subtype SettingDefinition (three-layer visibility), InAppNotification Toast/Banner, 8-variant WidgetRenderState, ThemeSpec metadata, pack manifest, and registry interfaces**

## Performance

- **Duration:** 6 min
- **Started:** 2026-02-23T19:01:39Z
- **Completed:** 2026-02-23T19:08:00Z
- **Tasks:** 2
- **Files modified:** 23 (3 modified from stubs, 20 created)

## Accomplishments
- Setup types: SetupDefinition sealed interface with 7 subtypes in 3 categories (requirement: RuntimePermission, SystemServiceToggle, SystemService, DeviceScan; display: Instruction, Info; input: Setting wrapper). SetupPageDefinition with @Immutable. SetupEvaluator interface with two-variant semantics documented. ServiceType enum, VerificationStrategy/VerificationResult, InstructionAction sealed interface, InfoStyle enum.
- Settings types: SettingDefinition sealed interface with 12 subtypes (BooleanSetting, IntSetting with getEffectivePresets, FloatSetting, StringSetting, EnumSetting, TimezoneSetting, DateFormatSetting, UriSetting, AppPickerSetting, SoundPickerSetting, InstructionSetting, InfoSetting) all implementing Gated. Settings enums (SizeOption with toMultiplier, TimezonePosition, DateLayoutOption, DateFormatOption with patterns, InfoCardLayoutMode, SoundType). InfoCardSettings factory helper.
- Notification types: InAppNotification sealed interface (Toast + Banner), NotificationPriority enum (CRITICAL/HIGH/NORMAL/LOW), AlertProfile with AlertMode/soundUri/ttsMessage/vibrationPattern, AlertEmitter interface, AlertResult enum, NotificationAction data class.
- Status types: WidgetRenderState sealed interface with 8 variants (Ready, SetupRequired, ConnectionError, Disconnected, EntitlementRevoked, ProviderMissing, DataTimeout, DataStale). WidgetIssue with IssueType enum + ResolutionAction sealed interface. WidgetStatusCache with EMPTY companion.
- Theme types: ThemeSpec metadata interface extending Gated (no Compose types), ThemeProvider interface, AutoSwitchMode enum (5 modes).
- Pack manifest: DashboardPackManifest @Serializable @Immutable with PackWidgetRef/PackThemeRef/PackDataProviderRef, PackCategory enum.
- Registry interfaces: WidgetRegistry and DataProviderRegistry for Phase 7 implementation.

## Task Commits

Each task was committed atomically:

1. **Task 1: Setup + settings types (7 SetupDefinition subtypes, 12 SettingDefinition subtypes)** - `aa680a6` (feat)
2. **Task 2: Notification, status, theme, pack, and registry types** - `93056b4` (feat)

**Plan metadata:** (pending)

## Files Created/Modified
- `.../setup/SetupDefinition.kt` - 7-subtype sealed interface replacing stub (requirement/display/input categories)
- `.../setup/SetupPageDefinition.kt` - @Immutable data class replacing stub
- `.../setup/SetupEvaluator.kt` - Evaluator interface with two-variant KDoc
- `.../setup/ServiceType.kt` - BLUETOOTH, LOCATION, WIFI enum
- `.../setup/VerificationStrategy.kt` - Interface + VerificationResult sealed (Verified/Failed/Skipped)
- `.../settings/SettingDefinition.kt` - 12-subtype sealed interface replacing stub (all implementing Gated)
- `.../settings/SettingsEnums.kt` - SizeOption, TimezonePosition, DateLayoutOption, DateFormatOption, InfoCardLayoutMode, SoundType
- `.../settings/InfoCardSettings.kt` - Factory returning standard InfoCard settings schema
- `.../notification/InAppNotification.kt` - Toast + Banner sealed interface
- `.../notification/NotificationPriority.kt` - CRITICAL/HIGH/NORMAL/LOW
- `.../notification/AlertProfile.kt` - @Immutable with AlertMode, soundUri, ttsMessage, vibrationPattern
- `.../notification/AlertEmitter.kt` - Interface for Phase 7 AlertSoundManager
- `.../notification/AlertResult.kt` - PLAYED/SILENCED/FOCUS_DENIED/UNAVAILABLE
- `.../notification/NotificationAction.kt` - @Immutable label + actionId
- `.../status/WidgetRenderState.kt` - 8 overlay state variants
- `.../status/WidgetIssue.kt` - IssueType enum + ResolutionAction sealed interface
- `.../status/WidgetStatusCache.kt` - overlayState + issues with EMPTY companion
- `.../theme/ThemeSpec.kt` - Metadata interface extending Gated
- `.../theme/ThemeProvider.kt` - Pack theme provider interface
- `.../theme/AutoSwitchMode.kt` - 5-mode auto-switch enum
- `.../pack/DashboardPackManifest.kt` - @Serializable manifest with refs + category
- `.../registry/WidgetRegistry.kt` - Widget registry interface
- `.../registry/DataProviderRegistry.kt` - Data provider registry with entitlement filtering

## Decisions Made
- **InfoStyle and InstructionAction in setup package:** Both types are shared between `SetupDefinition.Instruction`/`SetupDefinition.Info` and `SettingDefinition.InstructionSetting`/`SettingDefinition.InfoSetting`. Placed in the `setup` package since that's where they originate; settings package imports from setup. This avoids duplication while keeping the types co-located with their primary consumers.
- **SetupDefinition.Setting wrapper defaults:** All fields (id, label, description, hidden, visibleWhen, requiredAnyEntitlement) default to the inner SettingDefinition's values but can be individually overridden. This preserves the double-gating semantics from the replication advisory while allowing callers to add setup-specific constraints.
- **DashboardPackManifest @Serializable:** Applied kotlinx.serialization to the manifest and its refs/category for KSP-generated manifest files. The @Immutable annotation ensures Compose stability for any UI consuming pack metadata.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- `:sdk:contracts` type surface is now complete -- no further modifications needed in downstream phases
- Plan 04 (`:sdk:contracts` unit tests) can proceed immediately with all types available for testing
- Plan 05 (`:sdk:contracts` testFixtures) has all types to build contract test bases
- Phase 3 (`:sdk:observability`, `:sdk:analytics`, `:sdk:ui`) has the full contract surface to consume
- Phase 4 (KSP codegen) has annotation types + pack manifest + all setup/settings types
- Phase 7 (Dashboard shell) has WidgetRenderState, notification, registry, and evaluator interfaces ready for implementation
- Phase 10 (Settings/Setup UI) has complete SetupDefinition and SettingDefinition schemas for UI rendering

## Self-Check: PASSED

- All 23 source files: FOUND
- Commit aa680a6 (Task 1 -- setup + settings types): FOUND
- Commit 93056b4 (Task 2 -- notification/status/theme/pack/registry types): FOUND
- `:sdk:contracts:assembleDebug`: BUILD SUCCESSFUL
- `spotlessCheck`: PASSED
- No ImageVector/Context dependencies in contracts: VERIFIED
- SetupDefinition subtypes: 7 (RuntimePermission, SystemServiceToggle, SystemService, DeviceScan, Instruction, Info, Setting)
- SettingDefinition subtypes: 12 (Boolean, Int, Float, String, Enum, Timezone, DateFormat, Uri, AppPicker, SoundPicker, Instruction, Info)
- WidgetRenderState variants: 8 (Ready, SetupRequired, ConnectionError, Disconnected, EntitlementRevoked, ProviderMissing, DataTimeout, DataStale)

---
*Phase: 02-sdk-contracts-common*
*Completed: 2026-02-24*
