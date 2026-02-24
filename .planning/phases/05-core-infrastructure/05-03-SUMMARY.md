---
phase: 05-core-infrastructure
plan: 03
subsystem: data
tags: [proto-datastore, preferences-datastore, layout-repository, migration, hilt-di, corruption-handler]

# Dependency graph
requires:
  - phase: 01-build-system
    provides: "dqxn.android.library, dqxn.android.hilt, dqxn.android.test convention plugins"
  - phase: 02-sdk-contracts
    provides: "WidgetStyle, AutoSwitchMode, @IoDispatcher, @ApplicationScope qualifiers"
  - phase: 03-sdk-observability
    provides: "DqxnLogger, ErrorReporter, NoOpLogger, LogTag, ErrorContext"
  - phase: 05-01
    provides: "3 proto schemas (DashboardStoreProto, PairedDeviceStoreProto, CustomThemeStoreProto)"
provides:
  - "LayoutRepository interface with profile CRUD, debounced 500ms save, widget mutations"
  - "LayoutRepositoryImpl backed by Proto DataStore with in-memory snapshot and conflated channel debounce"
  - "LayoutMigration chained N->N+1 transformer infrastructure with pre-backup restore on failure"
  - "FallbackLayout hardcoded essentials:clock widget (no I/O dependency)"
  - "UserPreferencesRepository with dual-theme (light/dark), auto-switch mode, orientation, screen settings"
  - "DataModule with 6 DataStore @Provides — all with ReplaceFileCorruptionHandler (NF43)"
  - "3 Proto DataStore serializers (DashboardStore, PairedDevice, CustomTheme)"
  - "DashboardWidgetInstance domain type with toProto/fromProto conversion"
  - "GridPosition/GridSize canvas coordinate types"
  - "Custom qualifier annotations (@UserPreferences, @ProviderSettings, @WidgetStyles)"
affects: [05-core-infrastructure, 06-deployable-app-agentic-framework, 07-dashboard-shell, 08-essentials-pack, 10-settings-foundation]

# Tech tracking
tech-stack:
  added: [datastore-proto 1.1.4, datastore-preferences 1.1.4, kotlinx-serialization-json 1.10.0]
  patterns: [conflated-channel debounced save, MutableStateFlow in-memory snapshot with async persist, chained versioned migration with pre-backup restore, proto<->domain conversion companion objects]

key-files:
  created:
    - android/data/src/main/kotlin/app/dqxn/android/data/layout/LayoutRepository.kt
    - android/data/src/main/kotlin/app/dqxn/android/data/layout/LayoutRepositoryImpl.kt
    - android/data/src/main/kotlin/app/dqxn/android/data/layout/LayoutMigration.kt
    - android/data/src/main/kotlin/app/dqxn/android/data/layout/FallbackLayout.kt
    - android/data/src/main/kotlin/app/dqxn/android/data/layout/DashboardWidgetInstance.kt
    - android/data/src/main/kotlin/app/dqxn/android/data/layout/GridTypes.kt
    - android/data/src/main/kotlin/app/dqxn/android/data/serializer/DashboardStoreSerializer.kt
    - android/data/src/main/kotlin/app/dqxn/android/data/serializer/PairedDeviceSerializer.kt
    - android/data/src/main/kotlin/app/dqxn/android/data/serializer/CustomThemeSerializer.kt
    - android/data/src/main/kotlin/app/dqxn/android/data/di/DataModule.kt
    - android/data/src/main/kotlin/app/dqxn/android/data/di/DataStoreQualifiers.kt
    - android/data/src/main/kotlin/app/dqxn/android/data/di/RepositoryBindingsModule.kt
    - android/data/src/main/kotlin/app/dqxn/android/data/preferences/UserPreferencesRepository.kt
    - android/data/src/main/kotlin/app/dqxn/android/data/preferences/UserPreferencesRepositoryImpl.kt
    - android/data/src/main/kotlin/app/dqxn/android/data/preferences/PreferenceKeys.kt
    - android/data/consumer-proguard-rules.pro
    - android/data/src/test/kotlin/app/dqxn/android/data/layout/LayoutRepositoryTest.kt
    - android/data/src/test/kotlin/app/dqxn/android/data/layout/LayoutMigrationTest.kt
    - android/data/src/test/kotlin/app/dqxn/android/data/layout/FallbackLayoutTest.kt
    - android/data/src/test/kotlin/app/dqxn/android/data/preferences/UserPreferencesRepositoryTest.kt
    - android/data/src/test/kotlin/app/dqxn/android/data/di/CorruptionHandlerTest.kt
  modified:
    - android/data/build.gradle.kts

key-decisions:
  - "@param:IoDispatcher for Kotlin 2.3 constructor parameter annotation targeting"
  - "Custom qualifier annotations (@UserPreferences, @ProviderSettings, @WidgetStyles) over @Named strings for type-safe Preferences DataStore disambiguation"
  - "Separate RepositoryBindingsModule (abstract class) from DataModule (object) for @Binds vs @Provides separation"
  - "In-memory MutableStateFlow snapshot with async DataStore persist via conflated Channel debounce"
  - "LayoutMigration as open class with protected open properties for test subclassing"

patterns-established:
  - "Proto<->Domain companion conversion: fromProto()/toProto() on domain data classes"
  - "Conflated Channel + receiveAsFlow().collect { delay; persist } for debounced writes"
  - "MutableStateFlow for immediate UI updates with async DataStore persistence"
  - "Open migration class with override-able currentVersion/transformers for test scenarios"
  - "ReplaceFileCorruptionHandler on every DataStore with logging + errorReporter.reportNonFatal()"

requirements-completed: [F7.1, F7.2, F7.3, F7.8, F7.12, F4.3, NF43]

# Metrics
duration: 9min
completed: 2026-02-24
---

# Phase 5 Plan 3: Data Module Repositories Summary

**LayoutRepository with profile CRUD and 500ms debounced save, UserPreferencesRepository with dual-theme auto-switch, 6 DataStore instances with corruption handlers (NF43), and LayoutMigration chained transformer infrastructure**

## Performance

- **Duration:** 9 min
- **Started:** 2026-02-24T02:57:42Z
- **Completed:** 2026-02-24T03:07:29Z
- **Tasks:** 3
- **Files modified:** 22

## Accomplishments
- Complete `:data` module with proto dependency, domain types, 3 serializers, and 6 DataStore providers
- LayoutRepository with profile CRUD (create, clone, switch, delete), widget mutations, and 500ms debounced save via conflated Channel
- LayoutMigration with chained N->N+1 versioned transformers, MAX_VERSION_GAP reset, and pre-backup restore on failure
- UserPreferencesRepository with dual-theme selections, auto-switch mode, orientation lock, and screen settings
- 45 unit tests across 5 test classes — all passing, 0 failures
- NF43 behaviorally verified: CorruptionHandlerTest proves both Proto and Preferences DataStore recover from garbage bytes

## Task Commits

Each task was committed atomically:

1. **Task 1: Data module build config + domain types + DataStore serializers + DataModule** - `e3fc8cd` (feat)
2. **Task 2: LayoutRepository + LayoutMigration + FallbackLayout + UserPreferencesRepository** - `7a992d6` (feat)
3. **Task 3: Unit tests** - `d54e010` (test)
4. **Formatting** - `f123599` (style)

## Files Created/Modified
- `android/data/build.gradle.kts` - Added proto, sdk, datastore, serialization dependencies
- `android/data/consumer-proguard-rules.pro` - Proto class retention rules
- `android/data/src/main/kotlin/.../layout/GridTypes.kt` - GridPosition, GridSize data classes
- `android/data/src/main/kotlin/.../layout/DashboardWidgetInstance.kt` - Domain type with toProto/fromProto
- `android/data/src/main/kotlin/.../serializer/DashboardStoreSerializer.kt` - Proto DataStore serializer
- `android/data/src/main/kotlin/.../serializer/PairedDeviceSerializer.kt` - Proto DataStore serializer
- `android/data/src/main/kotlin/.../serializer/CustomThemeSerializer.kt` - Proto DataStore serializer
- `android/data/src/main/kotlin/.../di/DataStoreQualifiers.kt` - @UserPreferences, @ProviderSettings, @WidgetStyles
- `android/data/src/main/kotlin/.../di/DataModule.kt` - 6 DataStore @Provides with ReplaceFileCorruptionHandler
- `android/data/src/main/kotlin/.../di/RepositoryBindingsModule.kt` - @Binds for both repository interfaces
- `android/data/src/main/kotlin/.../layout/LayoutRepository.kt` - Interface with profile CRUD + widget mutations
- `android/data/src/main/kotlin/.../layout/LayoutRepositoryImpl.kt` - Proto DataStore-backed impl with debounced save
- `android/data/src/main/kotlin/.../layout/LayoutMigration.kt` - Chained N->N+1 migration with backup/restore
- `android/data/src/main/kotlin/.../layout/FallbackLayout.kt` - Hardcoded essentials:clock fallback
- `android/data/src/main/kotlin/.../preferences/PreferenceKeys.kt` - Typed preference key constants
- `android/data/src/main/kotlin/.../preferences/UserPreferencesRepository.kt` - Interface for user prefs
- `android/data/src/main/kotlin/.../preferences/UserPreferencesRepositoryImpl.kt` - Preferences DataStore impl
- `android/data/src/test/kotlin/.../layout/LayoutRepositoryTest.kt` - 11 tests: CRUD, debounce, widgets
- `android/data/src/test/kotlin/.../layout/LayoutMigrationTest.kt` - 8 tests: NoOp, Reset, chained, failed
- `android/data/src/test/kotlin/.../layout/FallbackLayoutTest.kt` - 8 tests: constant validation
- `android/data/src/test/kotlin/.../preferences/UserPreferencesRepositoryTest.kt` - 15 tests: defaults, setters, round-trip
- `android/data/src/test/kotlin/.../di/CorruptionHandlerTest.kt` - 3 tests: proto/prefs recovery, write-after-corruption

## Decisions Made
- **@param:IoDispatcher for Kotlin 2.3**: Constructor parameter annotation target required explicit `@param:` prefix with Kotlin 2.3.10 to silence KT-73255 warning; non-property parameters (`scope`) use bare annotation since `@param:` is redundant
- **Custom qualifier annotations over @Named**: @UserPreferences, @ProviderSettings, @WidgetStyles provide compile-time type safety for disambiguating 3 Preferences DataStore instances
- **Separate RepositoryBindingsModule**: DataModule is an `object` for `@Provides`, RepositoryBindingsModule is an `abstract class` for `@Binds` -- Dagger requires abstract class/interface for @Binds
- **In-memory MutableStateFlow + async persist**: Mutations update the StateFlow immediately for responsive UI, then debounce DataStore writes via conflated Channel for I/O efficiency
- **LayoutMigration as open class**: Protected open `currentVersion`, `maxVersionGap`, and `transformers` properties enable test subclasses to inject custom migration scenarios without mocking

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Kotlin 2.3 annotation target warnings on constructor parameters**
- **Found during:** Task 2 (compilation)
- **Issue:** `@IoDispatcher` and `@UserPreferences` on constructor `val` parameters produced KT-73255 warning about future behavior change
- **Fix:** Added `@param:` annotation target prefix on property parameters (`private val`); left bare annotation on non-property parameter (`scope`)
- **Files modified:** LayoutRepositoryImpl.kt, UserPreferencesRepositoryImpl.kt
- **Verification:** Clean compile with no warnings
- **Committed in:** 7a992d6 (Task 2 commit)

**2. [Rule 1 - Bug] Fixed Turbine test helper function return type mismatch**
- **Found during:** Task 3 (test compilation)
- **Issue:** Custom `Flow<T>.test {}` extension conflicted with Turbine's own extension; return type mismatch in LayoutRepositoryTest
- **Fix:** Removed custom helper, used `Flow.first()` for single-value collection and Turbine `test { }` directly with `cancelAndIgnoreRemainingEvents()`
- **Files modified:** LayoutRepositoryTest.kt
- **Verification:** All 11 LayoutRepository tests pass
- **Committed in:** d54e010 (Task 3 commit)

---

**Total deviations:** 2 auto-fixed (2 bugs)
**Impact on plan:** Both were straightforward compile-time fixes. No scope change.

## Issues Encountered
None beyond the auto-fixed deviations.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- LayoutRepository ready for Phase 7 dashboard coordinator consumption
- UserPreferencesRepository ready for Plan 04 (ThemeAutoSwitchEngine) and Phase 10 (Settings UI)
- DataModule provides all 6 DataStore instances for any downstream module
- LayoutMigration infrastructure ready for Phase 7+ schema evolution
- FallbackLayout provides guaranteed non-null fallback for first-launch and corruption recovery

## Self-Check: PASSED

All 22 created files verified on disk. All 4 commit hashes (e3fc8cd, 7a992d6, d54e010, f123599) found in git log.

---
*Phase: 05-core-infrastructure*
*Completed: 2026-02-24*
