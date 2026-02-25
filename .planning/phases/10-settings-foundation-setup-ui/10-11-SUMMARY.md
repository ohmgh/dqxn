---
phase: 10-settings-foundation-setup-ui
plan: 11
subsystem: ui
tags: [compose, widget-picker, live-preview, hardware-icons, compose-compiler]

# Dependency graph
requires:
  - phase: 10-08
    provides: WidgetPicker composable with FlowRow grid layout, entitlement badges
  - phase: 02
    provides: WidgetRenderer interface with @Composable Render(), WidgetSpec.compatibleSnapshots
  - phase: 08
    provides: Concrete widget renderers (SpeedometerRenderer, SolarRenderer) with compatibleSnapshots
provides:
  - Live widget preview in WidgetPicker via widget.Render() with CompositionLocalProvider
  - Hardware requirement icon badges (GPS/Bluetooth) derived from WidgetSpec.compatibleSnapshots
  - Compose compiler on :sdk:contracts for @Composable interface method bytecode correctness
affects: [11-theme-ui-diagnostics-onboarding, 13-e2e-integration]

# Tech tracking
tech-stack:
  added: [compose-compiler on sdk:contracts]
  patterns: [snapshot-class-name-heuristic for hardware requirements, concrete WidgetRenderer test implementations over MockK for @Composable methods]

key-files:
  created: []
  modified:
    - android/feature/settings/src/main/kotlin/app/dqxn/android/feature/settings/WidgetPicker.kt
    - android/feature/settings/src/test/kotlin/app/dqxn/android/feature/settings/WidgetPickerTest.kt
    - android/feature/settings/src/main/res/values/strings.xml
    - android/sdk/contracts/build.gradle.kts

key-decisions:
  - "Compose compiler plugin added to :sdk:contracts -- @Composable interface methods require Compose compiler for correct bytecode signature (Composer/int params). Without it, invokeinterface throws NoSuchMethodError at runtime"
  - "Concrete WidgetRenderer test implementations over MockK relaxed mocks -- MockK proxies for interfaces compiled without Compose compiler get untransformed method signatures, causing NoSuchMethodError"
  - "Snapshot class name heuristic for hardware requirement derivation -- Speed/Solar class names indicate GPS, Ble/Bluetooth indicate Bluetooth, all others NONE"

patterns-established:
  - "Concrete WidgetRenderer in test sources: when testing composables that call widget.Render(), use anonymous object implementations compiled in the test source (with Compose compiler), not MockK relaxed mocks"
  - "HardwareRequirement derivation via compatibleSnapshots class names: simple heuristic avoids coupling to specific snapshot types"

requirements-completed: [F2.7, F8.7]

# Metrics
duration: 13min
completed: 2026-02-25
---

# Phase 10 Plan 11: WidgetPicker Gap Closure Summary

**Live widget preview via Render() + graphicsLayer 0.5x scale-down, GPS/Bluetooth hardware icon badges from snapshot class name heuristic, Compose compiler fix for @Composable interface methods**

## Performance

- **Duration:** 13 min
- **Started:** 2026-02-25T05:18:16Z
- **Completed:** 2026-02-25T05:31:00Z
- **Tasks:** 2
- **Files modified:** 4

## Accomplishments
- WidgetPickerCard now calls widget.Render() inside CompositionLocalProvider(LocalWidgetData provides WidgetData.Empty) with graphicsLayer 0.5x scale-down for live preview
- GPS icon badge renders for widgets with Speed/Solar-compatible snapshots, Bluetooth icon for BLE snapshots, no icon otherwise
- Lock icon overlay preserved and coexists with hardware icon badge (centered vs bottom-end)
- 12 tests pass in WidgetPickerTest (8 existing + 4 new)
- Fixed production-critical bug: added Compose compiler to :sdk:contracts for @Composable interface method bytecode

## Task Commits

Each task was committed atomically:

1. **Task 1: Add live widget preview rendering + hardware icon badges** - `a87297c` (feat)
2. **Task 2: Add tests for live preview + hardware icon badges** - `d86e1f7` (test)

**Plan metadata:** [pending] (docs: complete plan)

## Files Created/Modified
- `android/feature/settings/src/main/kotlin/app/dqxn/android/feature/settings/WidgetPicker.kt` - Added live preview via widget.Render() with CompositionLocalProvider, HardwareRequirement enum + deriveHardwareRequirement(), GPS/Bluetooth icon badges
- `android/feature/settings/src/test/kotlin/app/dqxn/android/feature/settings/WidgetPickerTest.kt` - 4 new tests (Render call verification, GPS icon for speed, no icon for time, GPS icon for solar), replaced MockK mocks with concrete WidgetRenderer implementations
- `android/feature/settings/src/main/res/values/strings.xml` - Added widget_picker_requires_gps and widget_picker_requires_bluetooth string resources
- `android/sdk/contracts/build.gradle.kts` - Added Compose compiler plugin + testCompileOnly compose.runtime

## Decisions Made
- **Compose compiler on :sdk:contracts**: @Composable interface methods produce 4-param bytecode without Compose compiler, but callers emit invokeinterface with 6-param (Composer, int) signature. JVM invokeinterface resolves method on interface type first, throws NoSuchMethodError if signature missing. This affected both WidgetPicker (new code) and WidgetSlot (existing code that would crash at production runtime).
- **Concrete test WidgetRenderer over MockK**: MockK relaxed mocks for interfaces compiled without Compose compiler generate proxies with untransformed method signatures. Concrete implementations in test sources (compiled with Compose compiler) get correct signatures.
- **Snapshot class name heuristic**: deriveHardwareRequirement inspects KClass.simpleName for Speed/Solar -> GPS, Ble/Bluetooth -> BLUETOOTH. Simple, decoupled, extensible. SolarSnapshot mapped to GPS because SolarLocationDataProvider requires location permissions.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Compose compiler missing on :sdk:contracts for @Composable interface methods**
- **Found during:** Task 2 (test execution)
- **Issue:** WidgetRenderer.Render() declared @Composable in :sdk:contracts (compiled without Compose compiler). Interface bytecode had 4-param signature. Callers in Compose-compiled modules emitted invokeinterface with 6-param signature. JVM threw NoSuchMethodError at runtime.
- **Fix:** Applied `org.jetbrains.kotlin.plugin.compose` plugin to :sdk:contracts. Added testCompileOnly compose.runtime for test source compilation. Interface bytecode now includes Composer/int params.
- **Files modified:** android/sdk/contracts/build.gradle.kts
- **Verification:** All 12 WidgetPickerTest pass. sdk:contracts own tests pass. feature:dashboard compiles successfully.
- **Committed in:** d86e1f7 (Task 2 commit)

**2. [Rule 3 - Blocking] MockK relaxed mocks incompatible with @Composable interface dispatch**
- **Found during:** Task 2 (test execution)
- **Issue:** MockK generates bytecode proxies based on interface bytecode. Even after fixing the interface, MockK proxies for WidgetRenderer still lacked Compose-transformed Render signature.
- **Fix:** Replaced MockK-based createTestWidget helper with concrete anonymous object implementation compiled in test source (with Compose compiler). Added onRender callback parameter for Render() call verification.
- **Files modified:** android/feature/settings/src/test/kotlin/app/dqxn/android/feature/settings/WidgetPickerTest.kt
- **Verification:** All 12 tests pass.
- **Committed in:** d86e1f7 (Task 2 commit)

---

**Total deviations:** 2 auto-fixed (1 bug, 1 blocking)
**Impact on plan:** Both fixes necessary for correctness. Compose compiler on :sdk:contracts is a production-critical fix (WidgetSlot.kt would also crash without it). No scope creep.

## Issues Encountered
None beyond the deviations documented above.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Phase 10 fully complete (11/11 plans). All settings foundation, setup UI, widget picker, pack browser, main settings, overlay navigation wiring, and gap closures done.
- Ready for Phase 11 (Theme UI + Diagnostics + Onboarding).
- The Compose compiler addition to :sdk:contracts is backward-compatible -- no existing code breaks, only enables correct @Composable interface dispatch.

---
*Phase: 10-settings-foundation-setup-ui*
*Completed: 2026-02-25*
