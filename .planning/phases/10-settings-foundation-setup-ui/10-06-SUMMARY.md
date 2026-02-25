---
phase: 10-settings-foundation-setup-ui
plan: 06
subsystem: ui
tags: [compose, setup, evaluator, permission, ble, cdm, cards, semantic-colors]

# Dependency graph
requires:
  - phase: 10-settings-foundation-setup-ui
    provides: SemanticColors (Plan 01), OverlayScaffold + build config (Plan 02), DeviceScanStateMachine (Plan 03), SettingRowDispatcher (Plan 04)
  - phase: 02-sdk-contracts-common
    provides: SetupDefinition 7 subtypes, SetupEvaluator interface, SetupPageDefinition, ServiceType, VerificationStrategy
  - phase: 05-core-infrastructure
    provides: PairedDeviceStore, design tokens (DashboardSpacing, DashboardTypography, CardSize, TextEmphasis)
provides:
  - SetupEvaluatorImpl with evaluate() and evaluateWithPersistence() variants
  - SetupDefinitionRenderer 7-type dispatch with three-layer visibility
  - 7 setup card composables (Permission, Toggle, DeviceScan, PairedDevice, DeviceLimit, Instruction, Info)
  - Setting -> SettingRowDispatcher two-layer dispatch integration
affects: [10-07, 10-08, 10-09, 10-10]

# Tech tracking
tech-stack:
  added: []
  patterns: [setup-evaluator-real-time-vs-persistence, three-layer-visibility-dispatch, permission-request-guard-pattern]

key-files:
  created:
    - android/feature/settings/src/main/kotlin/app/dqxn/android/feature/settings/setup/SetupEvaluatorImpl.kt
    - android/feature/settings/src/main/kotlin/app/dqxn/android/feature/settings/setup/SetupDefinitionRenderer.kt
    - android/feature/settings/src/main/kotlin/app/dqxn/android/feature/settings/setup/SetupPermissionCard.kt
    - android/feature/settings/src/main/kotlin/app/dqxn/android/feature/settings/setup/SetupToggleCard.kt
    - android/feature/settings/src/main/kotlin/app/dqxn/android/feature/settings/setup/DeviceScanCard.kt
    - android/feature/settings/src/main/kotlin/app/dqxn/android/feature/settings/setup/PairedDeviceCard.kt
    - android/feature/settings/src/main/kotlin/app/dqxn/android/feature/settings/setup/DeviceLimitCounter.kt
    - android/feature/settings/src/main/kotlin/app/dqxn/android/feature/settings/setup/InstructionCard.kt
    - android/feature/settings/src/main/kotlin/app/dqxn/android/feature/settings/setup/InfoCard.kt
    - android/feature/settings/src/test/kotlin/app/dqxn/android/feature/settings/setup/SetupEvaluatorImplTest.kt
  modified: []

key-decisions:
  - "SetupEvaluatorImpl takes ImmutableList<PairedDevice> snapshot parameter instead of PairedDeviceStore Flow for evaluateWithPersistence() -- non-suspend evaluation against pre-collected state"
  - "EntitlementManager passed through SetupDefinitionRenderer to SettingRowDispatcher for consistent gating -- avoids lambda-based hasEntitlement mismatch"
  - "SettingRowDispatcher integrated directly in SetupDefinitionRenderer for Setting type -- Plans 04/05 executed in same wave, real API available"

patterns-established:
  - "Permission request guard: hasRequestedPermissions local state prevents false permanent-denial detection before first request (Pitfall 2)"
  - "Evaluator dual-mode: evaluate() for real-time status, evaluateWithPersistence() for setup flow progression"
  - "Three-layer visibility in setup: hidden -> visibleWhen -> entitlement, matching SettingRowDispatcher pattern"

requirements-completed: [F3.4, F3.5, F3.14]

# Metrics
duration: 8min
completed: 2026-02-25
---

# Phase 10 Plan 06: Setup Cards + Evaluator + Renderer Summary

**SetupEvaluatorImpl with real-time/persistence dual evaluation, 7-type SetupDefinitionRenderer dispatch, and 7 setup card composables including 3-state permission guard and CDM scan integration**

## Performance

- **Duration:** 8 min
- **Started:** 2026-02-25T04:01:17Z
- **Completed:** 2026-02-25T04:09:23Z
- **Tasks:** 2
- **Files modified:** 10

## Accomplishments
- SetupEvaluatorImpl with evaluate() (real-time BLE/permission/service checks) and evaluateWithPersistence() (paired device snapshot) dual modes
- SetupDefinitionRenderer dispatches all 7 SetupDefinition subtypes with three-layer visibility gating
- Setting -> SettingRowDispatcher two-layer dispatch integration (replication advisory section 7)
- 7 setup card composables: SetupPermissionCard (3-state with Pitfall 2 guard), SetupToggleCard, DeviceScanCard (wraps DeviceScanStateMachine), PairedDeviceCard (3-state border + forget dialog), DeviceLimitCounter, InstructionCard, InfoCard
- 17 unit tests covering all evaluator paths: permissions, service toggles, device scan real-time vs persistence, display-only types, multi-page evaluation

## Task Commits

Each task was committed atomically:

1. **Task 1: SetupEvaluatorImpl + SetupDefinitionRenderer + evaluator tests** - `e55887e` (feat)
2. **Task 2: Setup card composables (Permission, Toggle, DeviceScan, PairedDevice, DeviceLimit, Instruction, Info)** - `038da94` (feat)

## Files Created/Modified
- `android/feature/settings/src/main/kotlin/app/dqxn/android/feature/settings/setup/SetupEvaluatorImpl.kt` - Concrete evaluator with real-time + persistence dual evaluation
- `android/feature/settings/src/main/kotlin/app/dqxn/android/feature/settings/setup/SetupDefinitionRenderer.kt` - 7-type dispatch with three-layer visibility
- `android/feature/settings/src/main/kotlin/app/dqxn/android/feature/settings/setup/SetupPermissionCard.kt` - 3-state permission card with request guard
- `android/feature/settings/src/main/kotlin/app/dqxn/android/feature/settings/setup/SetupToggleCard.kt` - Binary toggle for service requirements
- `android/feature/settings/src/main/kotlin/app/dqxn/android/feature/settings/setup/DeviceScanCard.kt` - CDM scan wrapper with 5-state rendering
- `android/feature/settings/src/main/kotlin/app/dqxn/android/feature/settings/setup/PairedDeviceCard.kt` - 3-state border with forget confirmation
- `android/feature/settings/src/main/kotlin/app/dqxn/android/feature/settings/setup/DeviceLimitCounter.kt` - N/M device counter at 2+ devices
- `android/feature/settings/src/main/kotlin/app/dqxn/android/feature/settings/setup/InstructionCard.kt` - Step badge + verification state display
- `android/feature/settings/src/main/kotlin/app/dqxn/android/feature/settings/setup/InfoCard.kt` - SemanticColors-styled info card
- `android/feature/settings/src/test/kotlin/app/dqxn/android/feature/settings/setup/SetupEvaluatorImplTest.kt` - 17 tests across 6 nested groups

## Decisions Made
- **ImmutableList<PairedDevice> parameter over PairedDeviceStore injection for evaluateWithPersistence()**: evaluate() is non-suspend; PairedDeviceStore.devices is a Flow that requires collection. Caller provides snapshot (already collected in ViewModel scope).
- **EntitlementManager parameter in SetupDefinitionRenderer**: Consistent with SettingRowDispatcher API, enables direct delegation for Setting wrapper type without adapter functions.
- **Direct SettingRowDispatcher integration**: Plans 04/05 executed in same wave 2, so the real SettingRowDispatcher was available at compile time. No stub needed.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- SetupEvaluatorImpl ready for Plan 07's paginated SetupSheet flow (evaluate schema per page)
- SetupDefinitionRenderer ready for Plan 07's page rendering loop
- All 7 card composables ready for assembly into the setup wizard
- Setting two-layer dispatch tested and working via SettingRowDispatcher

## Self-Check: PASSED

- All 10 created files verified on disk
- Commit e55887e verified in git log
- Commit 038da94 verified in git log
- All 17 tests passing with 0 failures
- :feature:settings:compileDebugKotlin succeeds

---
*Phase: 10-settings-foundation-setup-ui*
*Completed: 2026-02-25*
