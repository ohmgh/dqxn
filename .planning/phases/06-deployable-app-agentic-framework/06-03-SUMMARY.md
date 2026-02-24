---
phase: 06-deployable-app-agentic-framework
plan: 03
subsystem: agentic
tags: [agentic, content-provider, hilt-entrypoint, diagnostics, debug-only, response-file-protocol]

# Dependency graph
requires:
  - phase: 06-01
    provides: "CommandHandler interface, AgenticCommandRouter, SemanticsOwnerHolder, CommandParams/CommandResult"
  - phase: 06-02
    provides: "App shell with Hilt DI assembly, AppModule @Multibinds sets, CrashRecovery"
  - phase: 03-sdk-observability-analytics-ui
    provides: "WidgetHealthMonitor, MetricsCollector, CrashEvidenceWriter, DiagnosticSnapshotCapture"
  - phase: 04-ksp-codegen
    provides: "AgenticProcessor KSP generating AgenticHiltModule from @AgenticCommand annotations"
provides:
  - "AgenticContentProvider transport with @EntryPoint Hilt access and response-file protocol"
  - "15 diagnostic command handlers registered via KSP-generated AgenticHiltModule"
  - "DebugModule providing observability singletons for debug builds"
  - "Debug AndroidManifest registering ContentProvider (NF21)"
  - "Cold-start race handling, 8s timeout, concurrent call safety"
affects: [06-04, 07-dashboard-shell, 08-essentials-pack]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "@EntryPoint pattern for ContentProvider Hilt access (not @AndroidEntryPoint)"
    - "Response-file protocol: JSON written to cacheDir temp file, path in Bundle"
    - "Provider<Set<CommandHandler>> to break Dagger cycle in ListCommandsHandler"
    - "kspDebug configuration for debug-only KSP processor invocation"

key-files:
  created:
    - "android/app/src/debug/AndroidManifest.xml"
    - "android/app/src/debug/kotlin/app/dqxn/android/debug/AgenticContentProvider.kt"
    - "android/app/src/debug/kotlin/app/dqxn/android/debug/DebugModule.kt"
    - "android/app/src/debug/kotlin/app/dqxn/android/debug/handlers/PingHandler.kt"
    - "android/app/src/debug/kotlin/app/dqxn/android/debug/handlers/ListCommandsHandler.kt"
    - "android/app/src/debug/kotlin/app/dqxn/android/debug/handlers/DumpHealthHandler.kt"
    - "android/app/src/debug/kotlin/app/dqxn/android/debug/handlers/DumpLayoutHandler.kt"
    - "android/app/src/debug/kotlin/app/dqxn/android/debug/handlers/DumpSemanticsHandler.kt"
    - "android/app/src/debug/kotlin/app/dqxn/android/debug/handlers/QuerySemanticsHandler.kt"
    - "android/app/src/debug/kotlin/app/dqxn/android/debug/handlers/DiagnoseCrashHandler.kt"
    - "android/app/src/debug/kotlin/app/dqxn/android/debug/handlers/DiagnosePerformanceHandler.kt"
    - "android/app/src/debug/kotlin/app/dqxn/android/debug/handlers/ListDiagnosticsHandler.kt"
    - "android/app/src/debug/kotlin/app/dqxn/android/debug/handlers/GetMetricsHandler.kt"
    - "android/app/src/debug/kotlin/app/dqxn/android/debug/handlers/ListWidgetsHandler.kt"
    - "android/app/src/debug/kotlin/app/dqxn/android/debug/handlers/ListProvidersHandler.kt"
    - "android/app/src/debug/kotlin/app/dqxn/android/debug/handlers/ListThemesHandler.kt"
    - "android/app/src/debug/kotlin/app/dqxn/android/debug/handlers/TriggerAnomalyHandler.kt"
    - "android/app/src/debug/kotlin/app/dqxn/android/debug/handlers/CaptureSnapshotHandler.kt"
    - "android/app/src/test/kotlin/app/dqxn/android/debug/handlers/PingHandlerTest.kt"
    - "android/app/src/test/kotlin/app/dqxn/android/debug/handlers/DumpHealthHandlerTest.kt"
    - "android/app/src/test/kotlin/app/dqxn/android/debug/AgenticContentProviderTest.kt"
  modified:
    - "android/app/build.gradle.kts"
    - "android/core/agentic/src/main/kotlin/app/dqxn/android/core/agentic/AgenticCommandRouter.kt"
    - "android/build-logic/convention/src/main/kotlin/AndroidApplicationConventionPlugin.kt"

key-decisions:
  - "AgenticCommandRouter changed from internal to public for cross-module @EntryPoint access"
  - "Handler classes use public visibility for KSP-generated Hilt module compatibility"
  - "Provider<Set<CommandHandler>> breaks Dagger circular dependency in ListCommandsHandler"
  - "isReturnDefaultValues=true added to application convention for stub Bundle in unit tests"
  - "handleCall extracted with configurable timeoutMs for fast unit tests (200ms vs 8s production)"

patterns-established:
  - "kspDebug(project(':codegen:agentic')) for debug-only KSP processor invocation"
  - "DebugModule pattern for providing observability singletons in debug builds only"
  - "Response-file protocol eliminating Binder transaction size limits"
  - "File-based test verification for Android code without Robolectric"

requirements-completed: [F13.2, F13.5, F13.11, NF21]

# Metrics
duration: 12min
completed: 2026-02-24
---

# Phase 6 Plan 03: Agentic Diagnostic Handlers Summary

**AgenticContentProvider transport with response-file protocol and 15 diagnostic command handlers (ping, dump-health, dump-semantics, query-semantics, diagnose-crash, get-metrics, list-widgets, etc.) all routable via KSP-generated Hilt module**

## Performance

- **Duration:** 12 min
- **Started:** 2026-02-24T04:00:08Z
- **Completed:** 2026-02-24T04:12:00Z
- **Tasks:** 3
- **Files modified:** 24

## Accomplishments
- AgenticContentProvider with @EntryPoint Hilt access, response-file protocol, cold-start race handling, and 8s timeout
- All 15 diagnostic handlers compiled and registered via KSP-generated AgenticHiltModule with @Binds @IntoSet
- 16 new unit tests (8 AgenticContentProvider + 4 PingHandler + 4 DumpHealthHandler) all passing
- DebugModule providing all observability singletons (logger, metrics, health monitor, crash writer, diagnostic capture) for debug builds
- Debug-only manifest registration ensures zero agentic code in release APK (NF21)

## Task Commits

Each task was committed atomically:

1. **Task 1: AgenticContentProvider transport + DebugModule + debug manifest** - `e4d0c97` (feat)
2. **Task 2: All 15 diagnostic command handlers + handler tests** - `d684f6d` (feat)
3. **Task 3: AgenticContentProvider unit tests** - `3948a0d` (test)

## Files Created/Modified
- `android/app/src/debug/AndroidManifest.xml` - ContentProvider registration for debug builds (NF21)
- `android/app/src/debug/kotlin/app/dqxn/android/debug/AgenticContentProvider.kt` - Transport with @EntryPoint, response-file protocol, cold-start handling
- `android/app/src/debug/kotlin/app/dqxn/android/debug/DebugModule.kt` - Debug Hilt module providing observability singletons and @Multibinds CommandHandler set
- `android/app/src/debug/kotlin/app/dqxn/android/debug/handlers/PingHandler.kt` - E2E startup probe
- `android/app/src/debug/kotlin/app/dqxn/android/debug/handlers/ListCommandsHandler.kt` - Command discovery with Provider<Set<CommandHandler>>
- `android/app/src/debug/kotlin/app/dqxn/android/debug/handlers/DumpHealthHandler.kt` - Widget health snapshot
- `android/app/src/debug/kotlin/app/dqxn/android/debug/handlers/DumpLayoutHandler.kt` - Layout state placeholder
- `android/app/src/debug/kotlin/app/dqxn/android/debug/handlers/DumpSemanticsHandler.kt` - Full semantics tree dump
- `android/app/src/debug/kotlin/app/dqxn/android/debug/handlers/QuerySemanticsHandler.kt` - Filtered semantics query
- `android/app/src/debug/kotlin/app/dqxn/android/debug/handlers/DiagnoseCrashHandler.kt` - Last crash evidence
- `android/app/src/debug/kotlin/app/dqxn/android/debug/handlers/DiagnosePerformanceHandler.kt` - Frame histogram and jank stats
- `android/app/src/debug/kotlin/app/dqxn/android/debug/handlers/ListDiagnosticsHandler.kt` - Diagnostic snapshot listing with since filter
- `android/app/src/debug/kotlin/app/dqxn/android/debug/handlers/GetMetricsHandler.kt` - Per-widget draw times and provider latencies
- `android/app/src/debug/kotlin/app/dqxn/android/debug/handlers/ListWidgetsHandler.kt` - Registered widget typeIds
- `android/app/src/debug/kotlin/app/dqxn/android/debug/handlers/ListProvidersHandler.kt` - Registered provider sourceIds
- `android/app/src/debug/kotlin/app/dqxn/android/debug/handlers/ListThemesHandler.kt` - Registered theme IDs grouped by pack
- `android/app/src/debug/kotlin/app/dqxn/android/debug/handlers/TriggerAnomalyHandler.kt` - Synthetic anomaly trigger for testing
- `android/app/src/debug/kotlin/app/dqxn/android/debug/handlers/CaptureSnapshotHandler.kt` - Custom-reason diagnostic capture
- `android/app/src/test/kotlin/app/dqxn/android/debug/handlers/PingHandlerTest.kt` - 4 tests
- `android/app/src/test/kotlin/app/dqxn/android/debug/handlers/DumpHealthHandlerTest.kt` - 4 tests
- `android/app/src/test/kotlin/app/dqxn/android/debug/AgenticContentProviderTest.kt` - 8 tests
- `android/app/build.gradle.kts` - Added serialization, immutable collections, kspDebug, core-ktx deps
- `android/core/agentic/src/main/kotlin/.../AgenticCommandRouter.kt` - Changed internal to public
- `android/build-logic/convention/src/main/kotlin/AndroidApplicationConventionPlugin.kt` - Added isReturnDefaultValues=true

## Decisions Made
- **AgenticCommandRouter public visibility** -- internal was inaccessible from :app debug source set via @EntryPoint. Cross-module access requires public.
- **Handler classes public (not internal)** -- KSP-generated AgenticHiltModule has public bind functions that cannot expose internal parameter types. Debug source set already provides NF21 isolation.
- **Provider<Set<CommandHandler>> for ListCommandsHandler** -- breaks Dagger circular dependency (handler is itself in the set it injects). Lazy Provider defers resolution.
- **isReturnDefaultValues=true on application convention** -- Android stub Bundle doesn't store values in unit tests. Tests verify file I/O directly instead of Bundle getString.
- **Configurable timeoutMs on handleCall** -- production uses 8s, tests use 200ms to avoid slow test runs.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] AgenticCommandRouter internal->public visibility**
- **Found during:** Task 1 (AgenticContentProvider compilation)
- **Issue:** `AgenticCommandRouter` was `internal` to `:core:agentic`, inaccessible from `:app` debug source set via `@EntryPoint` interface
- **Fix:** Changed visibility from `internal` to `public`
- **Files modified:** `android/core/agentic/src/main/kotlin/.../AgenticCommandRouter.kt`
- **Verification:** `:app:compileDebugKotlin` succeeds
- **Committed in:** `e4d0c97` (Task 1 commit)

**2. [Rule 3 - Blocking] Handler visibility internal->public for KSP compatibility**
- **Found during:** Task 2 (all 15 handlers compilation)
- **Issue:** KSP-generated `AgenticHiltModule` has public `@Binds` functions that cannot reference internal parameter types
- **Fix:** Changed all 15 handler classes from `internal` to default (public) visibility
- **Files modified:** All 15 handler files in `src/debug/kotlin/app/dqxn/android/debug/handlers/`
- **Verification:** `:app:compileDebugKotlin` succeeds
- **Committed in:** `d684f6d` (Task 2 commit)

**3. [Rule 3 - Blocking] ImmutableMap not on :app classpath**
- **Found during:** Task 2 (GetMetricsHandler compilation)
- **Issue:** `MetricsSnapshot.widgetDrawTimes` returns `ImmutableMap` from kotlinx-collections-immutable, but `:sdk:observability` exposes it as `implementation` (not `api`)
- **Fix:** Added `implementation(libs.kotlinx.collections.immutable)` to `:app` build.gradle.kts
- **Files modified:** `android/app/build.gradle.kts`
- **Verification:** `:app:compileDebugKotlin` succeeds
- **Committed in:** `d684f6d` (Task 2 commit)

**4. [Rule 1 - Bug] Dagger circular dependency in ListCommandsHandler**
- **Found during:** Task 2 (Hilt compilation)
- **Issue:** `ListCommandsHandler` injects `Set<CommandHandler>` but is itself in that set, creating a Dagger cycle
- **Fix:** Changed to `Provider<Set<CommandHandler>>` for lazy resolution
- **Files modified:** `android/app/src/debug/kotlin/.../ListCommandsHandler.kt`
- **Verification:** `:app:testDebugUnitTest` passes (Hilt graph resolves)
- **Committed in:** `d684f6d` (Task 2 commit)

**5. [Rule 3 - Blocking] isReturnDefaultValues for application module tests**
- **Found during:** Task 3 (AgenticContentProvider tests)
- **Issue:** Application convention plugin didn't set `isReturnDefaultValues=true`, causing Android stub Bundle to throw in unit tests
- **Fix:** Added `testOptions { unitTests.isReturnDefaultValues = true }` to `AndroidApplicationConventionPlugin`
- **Files modified:** `android/build-logic/convention/src/main/kotlin/AndroidApplicationConventionPlugin.kt`
- **Verification:** `:app:testDebugUnitTest` passes (32 tests, 0 failures)
- **Committed in:** `3948a0d` (Task 3 commit)

---

**Total deviations:** 5 auto-fixed (4 blocking, 1 bug)
**Impact on plan:** All fixes necessary for compilation and testing. No scope creep.

## Issues Encountered
- None beyond the auto-fixed deviations above.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- All 15 agentic commands operational via `adb shell content call` for autonomous debugging from Phase 6 onward
- DumpLayoutHandler returns placeholder -- real LayoutRepository wired in Phase 7
- ListWidgets/ListProviders/ListThemes return empty sets -- populated when packs register in Phase 8
- DumpSemanticsHandler ready for semantics tree access once SemanticsOwner registered by dashboard (Phase 7)
- DebugModule provides all observability singletons -- production wiring in Phase 7+ will supersede

## Self-Check: PASSED

All 24 files verified present. All 3 task commits (e4d0c97, d684f6d, 3948a0d) verified in git log.

---
*Phase: 06-deployable-app-agentic-framework*
*Completed: 2026-02-24*
