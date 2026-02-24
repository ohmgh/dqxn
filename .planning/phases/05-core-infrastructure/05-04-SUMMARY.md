---
phase: 05-core-infrastructure
plan: 04
subsystem: data
tags: [preferences-datastore, proto-datastore, provider-settings, preset-loader, ble-device-store, widget-style, type-prefixed-serialization, region-detection]

# Dependency graph
requires:
  - phase: 05-01
    provides: "PairedDeviceStoreProto and PairedDeviceMetadataProto proto schemas"
  - phase: 05-03
    provides: "DataModule with 6 DataStore @Provides, DashboardWidgetInstance, GridTypes, FallbackLayout, DataStoreQualifiers"
  - phase: 02-sdk-contracts
    provides: "WidgetStyle, BackgroundStyle @Serializable types"
  - phase: 03-sdk-observability
    provides: "DqxnLogger, NoOpLogger, LogTag"
provides:
  - "ProviderSettingsStore with {packId}:{providerId}:{key} namespaced keys and type-prefixed serialization (7 types)"
  - "PairedDeviceStore with Proto DataStore persistence and duplicate MAC rejection"
  - "ConnectionEventStore with rolling 50-event window for BLE diagnostic logging"
  - "WidgetStyleStore with JSON-serialized per-widget container styles"
  - "PresetLoader with timezone-based region detection and FallbackLayout integration"
  - "SettingsSerialization type-prefixed encode/decode (s:/i:/b:/f:/d:/l:/j: + null + legacy fallback)"
  - "StoreBindingsModule Hilt wiring for all 4 store implementations"
  - "default.json preset with 3 permission-safe widgets (clock, battery, date-simple)"
affects: [07-dashboard-shell, 08-essentials-pack, 10-settings-foundation]

# Tech tracking
tech-stack:
  added: []
  patterns: [type-prefixed-serialization, timezone-based-region-detection, rolling-window-event-store, json-blob-in-preferences-datastore]

key-files:
  created:
    - android/data/src/main/kotlin/app/dqxn/android/data/provider/ProviderSettingsStore.kt
    - android/data/src/main/kotlin/app/dqxn/android/data/provider/ProviderSettingsStoreImpl.kt
    - android/data/src/main/kotlin/app/dqxn/android/data/provider/SettingsSerialization.kt
    - android/data/src/main/kotlin/app/dqxn/android/data/device/PairedDeviceStore.kt
    - android/data/src/main/kotlin/app/dqxn/android/data/device/PairedDeviceStoreImpl.kt
    - android/data/src/main/kotlin/app/dqxn/android/data/device/ConnectionEventStore.kt
    - android/data/src/main/kotlin/app/dqxn/android/data/device/ConnectionEventStoreImpl.kt
    - android/data/src/main/kotlin/app/dqxn/android/data/style/WidgetStyleStore.kt
    - android/data/src/main/kotlin/app/dqxn/android/data/style/WidgetStyleStoreImpl.kt
    - android/data/src/main/kotlin/app/dqxn/android/data/di/StoreBindingsModule.kt
    - android/data/src/main/kotlin/app/dqxn/android/data/preset/PresetLoader.kt
    - android/data/src/main/kotlin/app/dqxn/android/data/preset/PresetModels.kt
    - android/data/src/main/kotlin/app/dqxn/android/data/preset/FallbackRegionDetector.kt
    - android/data/src/main/assets/presets/default.json
    - android/data/src/test/kotlin/app/dqxn/android/data/provider/ProviderSettingsStoreTest.kt
    - android/data/src/test/kotlin/app/dqxn/android/data/device/PairedDeviceStoreTest.kt
    - android/data/src/test/kotlin/app/dqxn/android/data/device/ConnectionEventStoreTest.kt
    - android/data/src/test/kotlin/app/dqxn/android/data/style/WidgetStyleStoreTest.kt
    - android/data/src/test/kotlin/app/dqxn/android/data/preset/PresetLoaderTest.kt
  modified: []

key-decisions:
  - "ConnectionEventStore shares @ProviderSettings DataStore with dedicated __connection_events__ key prefix to avoid too-many-DataStore-files anti-pattern"
  - "@param:ApplicationContext for Kotlin 2.3 constructor parameter annotation targeting in PresetLoader (same KT-73255 pattern as Plan 03)"

patterns-established:
  - "Type-prefixed serialization: s:/i:/b:/f:/d:/l:/j: encode/decode with legacy raw-string fallback"
  - "Rolling-window event store: JSON list in Preferences DataStore with max-size enforcement on write"
  - "Timezone-based region detection: TimeZone.getDefault().id prefix matching for preset selection"
  - "PresetLoader fallback chain: region.json -> default.json -> FallbackLayout.FALLBACK_WIDGET"

requirements-completed: [F7.4, F7.5, F7.7]

# Metrics
duration: 6min
completed: 2026-02-24
---

# Phase 5 Plan 4: Remaining Data Stores and Preset System Summary

**ProviderSettingsStore with type-prefixed serialization, PairedDeviceStore with Proto DataStore, ConnectionEventStore with 50-event rolling window, WidgetStyleStore with JSON persistence, and PresetLoader with timezone-based region detection and GPS-widget filtering**

## Performance

- **Duration:** 6 min
- **Started:** 2026-02-24T03:10:48Z
- **Completed:** 2026-02-24T03:16:57Z
- **Tasks:** 3
- **Files modified:** 19

## Accomplishments
- 5 store interface/implementation pairs completing the `:data` persistence layer
- Type-prefixed serialization (SettingsSerialization) supporting 7 types with legacy raw-string fallback
- PresetLoader with timezone-based region detection covering US/GB/SG/JP/EU regions, GPS-widget filtering per F11.5, and `free:*` to `essentials:*` typeId remapping
- 33 unit tests across 5 test classes -- all passing, 0 failures
- default.json preset with 3 permission-safe widgets (no GPS)
- StoreBindingsModule wiring all 4 new store implementations via @Binds

## Task Commits

Each task was committed atomically:

1. **Task 1: ProviderSettingsStore + SettingsSerialization + PairedDeviceStore + ConnectionEventStore + WidgetStyleStore** - `71a7671` (feat)
2. **Task 2: PresetLoader + preset JSON + region detection** - `07fa2f9` (feat)
3. **Task 3: Tests for remaining stores and PresetLoader** - `c9a707f` (test)
4. **Formatting** - `bcb0361` (style)

## Files Created/Modified
- `android/data/src/main/kotlin/.../provider/ProviderSettingsStore.kt` - Interface for pack-namespaced provider settings
- `android/data/src/main/kotlin/.../provider/ProviderSettingsStoreImpl.kt` - Preferences DataStore impl with type-prefixed keys
- `android/data/src/main/kotlin/.../provider/SettingsSerialization.kt` - Type-prefixed encode/decode (s:/i:/b:/f:/d:/l:/j: + null)
- `android/data/src/main/kotlin/.../device/PairedDeviceStore.kt` - Interface + PairedDevice domain data class
- `android/data/src/main/kotlin/.../device/PairedDeviceStoreImpl.kt` - Proto DataStore impl with duplicate MAC rejection
- `android/data/src/main/kotlin/.../device/ConnectionEventStore.kt` - Interface + @Serializable ConnectionEvent
- `android/data/src/main/kotlin/.../device/ConnectionEventStoreImpl.kt` - Preferences DataStore impl with 50-event rolling window
- `android/data/src/main/kotlin/.../style/WidgetStyleStore.kt` - Interface for per-widget container styles
- `android/data/src/main/kotlin/.../style/WidgetStyleStoreImpl.kt` - Preferences DataStore impl with JSON-serialized WidgetStyle
- `android/data/src/main/kotlin/.../di/StoreBindingsModule.kt` - @Binds for 4 store implementations
- `android/data/src/main/kotlin/.../preset/PresetLoader.kt` - Region-aware preset loading with fallback chain
- `android/data/src/main/kotlin/.../preset/PresetModels.kt` - @Serializable PresetManifest/PresetWidget/PresetWidgetStyle
- `android/data/src/main/kotlin/.../preset/FallbackRegionDetector.kt` - Timezone-based region heuristic
- `android/data/src/main/assets/presets/default.json` - Default preset with clock, battery, date-simple
- `android/data/src/test/kotlin/.../provider/ProviderSettingsStoreTest.kt` - 12 tests
- `android/data/src/test/kotlin/.../device/PairedDeviceStoreTest.kt` - 5 tests
- `android/data/src/test/kotlin/.../device/ConnectionEventStoreTest.kt` - 4 tests
- `android/data/src/test/kotlin/.../style/WidgetStyleStoreTest.kt` - 4 tests
- `android/data/src/test/kotlin/.../preset/PresetLoaderTest.kt` - 8 tests

## Decisions Made
- **ConnectionEventStore shares @ProviderSettings DataStore**: Uses a dedicated `__connection_events__` key (double-underscore prefix) in the provider settings Preferences DataStore rather than creating a 7th DataStore instance, avoiding the too-many-files anti-pattern
- **@param:ApplicationContext for Kotlin 2.3**: Same KT-73255 pattern as Plan 03 -- constructor `val` parameters need explicit `@param:` annotation target prefix

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Kotlin 2.3 annotation target warning on @ApplicationContext in PresetLoader**
- **Found during:** Task 2 (compilation)
- **Issue:** `@ApplicationContext` on constructor `private val context` produced KT-73255 warning about future behavior change
- **Fix:** Added `@param:` annotation target prefix: `@param:ApplicationContext`
- **Files modified:** PresetLoader.kt
- **Verification:** Clean compile with no warnings
- **Committed in:** 07fa2f9 (Task 2 commit)

---

**Total deviations:** 1 auto-fixed (1 bug)
**Impact on plan:** Straightforward compile-time fix. No scope change.

## Issues Encountered
None beyond the auto-fixed deviation.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- ProviderSettingsStore ready for Phase 8 essentials pack data provider configuration
- PairedDeviceStore ready for Phase 9 sg-erp2 BLE device pairing
- ConnectionEventStore ready for diagnostic UI in Phase 11
- WidgetStyleStore ready for Phase 7 dashboard coordinator per-widget style persistence
- PresetLoader ready for Phase 7 first-launch experience
- All `:data` stores complete -- Phase 5 persistence layer fully delivered

## Self-Check: PASSED

All 19 created files verified on disk. All 4 commit hashes (71a7671, 07fa2f9, c9a707f, bcb0361) found in git log.

---
*Phase: 05-core-infrastructure*
*Completed: 2026-02-24*
