---
phase: 05-core-infrastructure
plan: 01
subsystem: infra
tags: [protobuf, proto-datastore, thermal, frame-pacing, powermanager]

# Dependency graph
requires:
  - phase: 01-build-system
    provides: "dqxn.kotlin.jvm convention plugin, protobuf-gradle-plugin, version catalog"
  - phase: 02-sdk-contracts
    provides: "@ApplicationScope qualifier, CoroutineScope DI, DqxnLogger"
  - phase: 03-sdk-observability
    provides: "DqxnLogger interface, LogTag, NoOpLogger"
provides:
  - "3 proto schemas generating Kotlin/Java classes (DashboardStoreProto, PairedDeviceStoreProto, CustomThemeStoreProto)"
  - "ThermalMonitor interface with ThermalLevel/RenderConfig state flows"
  - "ThermalManager: PowerManager status listener + headroom-based preemptive detection"
  - "FramePacer: preferredRefreshRate window attribute control"
  - "FakeThermalManager: test/chaos-injection double"
affects: [05-core-infrastructure, 07-dashboard-shell, 08-essentials-pack]

# Tech tracking
tech-stack:
  added: [protobuf-kotlin-lite 4.30.2 (proto schemas)]
  patterns: [ThermalMonitor interface extraction for DI, FakeThermalManager for chaos injection, headroom-based preemptive thermal detection]

key-files:
  created:
    - android/data/proto/src/main/proto/dashboard_layout.proto
    - android/data/proto/src/main/proto/paired_devices.proto
    - android/data/proto/src/main/proto/custom_themes.proto
    - android/core/thermal/src/main/kotlin/app/dqxn/android/core/thermal/ThermalMonitor.kt
    - android/core/thermal/src/main/kotlin/app/dqxn/android/core/thermal/ThermalManager.kt
    - android/core/thermal/src/main/kotlin/app/dqxn/android/core/thermal/FramePacer.kt
    - android/core/thermal/src/main/kotlin/app/dqxn/android/core/thermal/FakeThermalManager.kt
    - android/core/thermal/src/main/kotlin/app/dqxn/android/core/thermal/RenderConfig.kt
    - android/core/thermal/src/main/kotlin/app/dqxn/android/core/thermal/ThermalLevel.kt
    - android/core/thermal/src/main/kotlin/app/dqxn/android/core/thermal/di/ThermalModule.kt
  modified:
    - android/core/thermal/build.gradle.kts

key-decisions:
  - "ThermalMonitor interface extracted for DI and testability (not open class inheritance)"
  - "FakeThermalManager supports both scoped (flow derivation) and scopeless (synchronous) modes"
  - "Proto messages use Proto suffix to avoid name clashes with domain types in :data"
  - "FramePacer uses preferredRefreshRate on WindowManager.LayoutParams (simplest cross-API approach)"

patterns-established:
  - "ThermalMonitor interface with @Binds in ThermalModule for production/test swapping"
  - "FakeThermalManager dual-mode pattern: scope for flow derivation, null scope for synchronous test updates"
  - "Proto suffix convention for top-level store messages (DashboardStoreProto, not DashboardStore)"

requirements-completed: [NF12, NF13, F7.1]

# Metrics
duration: 5min
completed: 2026-02-24
---

# Phase 5 Plan 1: Proto DataStore Schemas + Thermal Management Summary

**Proto schemas for dashboard layout/devices/themes generating Kotlin/Java classes, plus complete thermal module with 4-tier degradation (60/45/30/24 fps), headroom-based preemptive detection, and FramePacer**

## Performance

- **Duration:** 5 min
- **Started:** 2026-02-24T02:42:58Z
- **Completed:** 2026-02-24T02:48:15Z
- **Tasks:** 2
- **Files modified:** 13

## Accomplishments
- 3 proto schemas compile and generate full Java/Kotlin lite classes for `:data` module consumption
- Complete `:core:thermal` module: ThermalManager, FramePacer, RenderConfig, FakeThermalManager with 23 passing tests
- ThermalMonitor interface extracted for DI binding and test doubles
- Preemptive WARM detection via `getThermalHeadroom()` when status is NORMAL but headroom < 0.3

## Task Commits

Each task was committed atomically:

1. **Task 1: Proto DataStore schemas for layout, devices, and themes** - `3b5730d` (feat)
2. **Task 2: Thermal management module** - `f8a4012` (feat)

## Files Created/Modified
- `android/data/proto/src/main/proto/dashboard_layout.proto` - DashboardStoreProto, ProfileCanvasProto, SavedWidgetProto messages
- `android/data/proto/src/main/proto/paired_devices.proto` - PairedDeviceStoreProto, PairedDeviceMetadataProto messages
- `android/data/proto/src/main/proto/custom_themes.proto` - CustomThemeStoreProto, CustomThemeProto messages
- `android/core/thermal/build.gradle.kts` - Added Hilt, observability, common dependencies
- `android/core/thermal/src/main/kotlin/.../ThermalLevel.kt` - NORMAL/WARM/DEGRADED/CRITICAL enum
- `android/core/thermal/src/main/kotlin/.../RenderConfig.kt` - targetFps/glowEnabled/useGradientFallback data class
- `android/core/thermal/src/main/kotlin/.../ThermalMonitor.kt` - Interface for DI abstraction
- `android/core/thermal/src/main/kotlin/.../ThermalManager.kt` - Production impl with PowerManager listener + headroom polling
- `android/core/thermal/src/main/kotlin/.../FramePacer.kt` - Window preferredRefreshRate control
- `android/core/thermal/src/main/kotlin/.../FakeThermalManager.kt` - Test/chaos-injection double
- `android/core/thermal/src/main/kotlin/.../di/ThermalModule.kt` - Hilt @Binds ThermalMonitor to ThermalManager
- `android/core/thermal/src/test/.../ThermalManagerTest.kt` - 17 tests: status mapping, RenderConfig derivation, FakeThermalManager flows
- `android/core/thermal/src/test/.../FramePacerTest.kt` - 6 tests: frame rate setting, no-op dedup, reset behavior

## Decisions Made
- **ThermalMonitor interface** over open class: cleaner DI, allows FakeThermalManager without mocking
- **FakeThermalManager dual-mode**: optional CoroutineScope parameter -- null scope gives synchronous updates for simple tests, provided scope enables proper flow derivation via stateIn
- **Proto suffix on store messages**: `DashboardStoreProto` not `DashboardStore` to avoid clashes with domain types in `:data` repositories
- **preferredRefreshRate** over Surface.setFrameRate(): simpler cross-API approach, works via WindowManager.LayoutParams on all API 31+ targets

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed FakeThermalManager property initialization order**
- **Found during:** Task 2 (compilation)
- **Issue:** `_renderConfig` MutableStateFlow was declared after its use in `renderConfig` property initializer, causing "must be initialized" error
- **Fix:** Moved `_renderConfig` declaration before `renderConfig`
- **Files modified:** FakeThermalManager.kt
- **Verification:** Compilation succeeds, all tests pass
- **Committed in:** f8a4012 (Task 2 commit)

**2. [Rule 1 - Bug] Fixed MockK slot capture for multi-invocation verify**
- **Found during:** Task 2 (FramePacerTest)
- **Issue:** `slot<T>()` only captures last invocation; using it with `verify(atLeast = 1)` on multi-call scenario throws MockKException
- **Fix:** Changed to `mutableListOf<T>()` capture and asserted on `last()` element
- **Files modified:** FramePacerTest.kt
- **Verification:** All 6 FramePacer tests pass
- **Committed in:** f8a4012 (Task 2 commit)

---

**Total deviations:** 2 auto-fixed (2 bugs)
**Impact on plan:** Both were straightforward compile/test fixes. No scope change.

## Issues Encountered
None beyond the auto-fixed deviations.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Proto-generated classes ready for `:data` module repositories (Plan 03)
- ThermalMonitor ready for Phase 7 dashboard coordinator consumption
- FramePacer ready for Phase 7 integration with Compose rendering pipeline
- FakeThermalManager available for chaos injection testing in Phase 8+

## Self-Check: PASSED

All 12 created files verified on disk. Both commit hashes (3b5730d, f8a4012) found in git log.

---
*Phase: 05-core-infrastructure*
*Completed: 2026-02-24*
