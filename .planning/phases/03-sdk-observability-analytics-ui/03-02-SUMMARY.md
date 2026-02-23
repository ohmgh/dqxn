---
phase: 03-sdk-observability-analytics-ui
plan: 02
subsystem: observability
tags: [metrics, jank-detection, anr-watchdog, diagnostics, analytics, health-monitoring, lock-free, atomic-operations]

# Dependency graph
requires:
  - phase: 03-01
    provides: "DqxnLogger, RingBufferSink, LongArrayRingBuffer, DqxnTracer, CrashReporter/ErrorReporter"
  - phase: 02-sdk-contracts-common
    provides: "sdk:contracts (@Immutable annotations), sdk:common"
provides:
  - "MetricsCollector lock-free frame histogram and per-widget/provider metrics (F13.5, F13.6)"
  - "JankDetector exponential threshold capture pipeline (5, 20, 100 consecutive janky frames)"
  - "DiagnosticSnapshotCapture with AtomicBoolean concurrent guard and 3 rotation pools"
  - "DiagnosticFileWriter with pool-based rotation (crash:20, thermal:10, perf:10)"
  - "AnrWatchdog daemon thread with 2-consecutive-miss trigger and debugger suppression"
  - "WidgetHealthMonitor staleness and stalled-render detection on configurable intervals"
  - "ProviderStatusProvider interface for Phase 7 WidgetBindingCoordinator"
  - "AnalyticsTracker + NoOpAnalyticsTracker contract interface (F12.1)"
  - "AnalyticsEvent sealed hierarchy: 20 event types across funnel/widget/theme/upsell/engagement/edit/profile"
  - "PackAnalytics per-pack event enrichment with pack_id attribution"
affects: [05-core-infrastructure, 07-dashboard-shell, 08-essentials-pack, 11-theme-diagnostics-onboarding]

# Tech tracking
tech-stack:
  added: []
  patterns: [lock-free-metrics-collection, atomic-boolean-concurrent-guard, exponential-threshold-capture, daemon-thread-anr-detection, pool-based-file-rotation, open-classes-for-testability]

key-files:
  created:
    - android/sdk/observability/src/main/kotlin/app/dqxn/android/sdk/observability/metrics/MetricsCollector.kt
    - android/sdk/observability/src/main/kotlin/app/dqxn/android/sdk/observability/metrics/MetricsSnapshot.kt
    - android/sdk/observability/src/main/kotlin/app/dqxn/android/sdk/observability/metrics/JankDetector.kt
    - android/sdk/observability/src/main/kotlin/app/dqxn/android/sdk/observability/health/WidgetHealthMonitor.kt
    - android/sdk/observability/src/main/kotlin/app/dqxn/android/sdk/observability/health/ProviderStatusProvider.kt
    - android/sdk/observability/src/main/kotlin/app/dqxn/android/sdk/observability/health/AnrWatchdog.kt
    - android/sdk/observability/src/main/kotlin/app/dqxn/android/sdk/observability/diagnostic/AnomalyTrigger.kt
    - android/sdk/observability/src/main/kotlin/app/dqxn/android/sdk/observability/diagnostic/DiagnosticSnapshot.kt
    - android/sdk/observability/src/main/kotlin/app/dqxn/android/sdk/observability/diagnostic/DiagnosticSnapshotCapture.kt
    - android/sdk/observability/src/main/kotlin/app/dqxn/android/sdk/observability/diagnostic/DiagnosticFileWriter.kt
    - android/sdk/analytics/src/main/kotlin/app/dqxn/android/sdk/analytics/AnalyticsTracker.kt
    - android/sdk/analytics/src/main/kotlin/app/dqxn/android/sdk/analytics/AnalyticsEvent.kt
    - android/sdk/analytics/src/main/kotlin/app/dqxn/android/sdk/analytics/PackAnalytics.kt
    - android/sdk/observability/src/test/kotlin/app/dqxn/android/sdk/observability/metrics/MetricsCollectorTest.kt
    - android/sdk/observability/src/test/kotlin/app/dqxn/android/sdk/observability/metrics/JankDetectorTest.kt
    - android/sdk/observability/src/test/kotlin/app/dqxn/android/sdk/observability/health/AnrWatchdogTest.kt
    - android/sdk/observability/src/test/kotlin/app/dqxn/android/sdk/observability/health/WidgetHealthMonitorTest.kt
    - android/sdk/observability/src/test/kotlin/app/dqxn/android/sdk/observability/diagnostic/DiagnosticSnapshotCaptureTest.kt
    - android/sdk/analytics/src/test/kotlin/app/dqxn/android/sdk/analytics/AnalyticsTrackerTest.kt
  modified:
    - android/sdk/observability/build.gradle.kts
    - android/sdk/analytics/build.gradle.kts

key-decisions:
  - "Open classes for testability over MockK class mocking -- MockK cannot mock final classes with constructor-initialized AtomicBoolean on JDK 25/Kotlin 2.3.10; test doubles via open subclasses"
  - "compileOnly(compose.runtime) for @Immutable in :sdk:observability -- same pattern as :sdk:contracts, no Compose compiler needed"
  - "EnrichedEvent as concrete AnalyticsEvent subtype for PackAnalytics -- anonymous object cannot extend sealed interface"
  - "WidgetHealthMonitor uses scope dispatcher over hardcoded Dispatchers.Default -- enables backgroundScope in tests"
  - "Test doubles over MockK for DiagnosticSnapshotCapture/DiagnosticFileWriter -- CopyOnWriteArrayList for thread-safe trigger recording in AnrWatchdog tests"

patterns-established:
  - "Lock-free metrics: AtomicLongArray histogram + ConcurrentHashMap<String, LongArrayRingBuffer> for per-entity recording"
  - "Concurrent guard: AtomicBoolean CAS in try/finally for exclusive access without locks"
  - "Exponential threshold: Set-based threshold check at recording site (5, 20, 100)"
  - "Pool-based rotation: File eviction by lastModified sort with configurable pool capacities"
  - "Test double pattern: Open production classes + anonymous object overrides for constructor-heavy dependencies"

requirements-completed: [F12.1, F13.5, F13.6, NF36]

# Metrics
duration: 23min
completed: 2026-02-24
---

# Phase 3 Plan 02: Metrics, Health, Diagnostics, Analytics Summary

**MetricsCollector lock-free frame histogram + per-widget metrics, JankDetector exponential threshold capture (5/20/100), DiagnosticSnapshotCapture with 3 rotation pools, AnrWatchdog 2-miss detection, WidgetHealthMonitor staleness checks, AnalyticsTracker/AnalyticsEvent contracts (F12.1)**

## Performance

- **Duration:** 23 min
- **Started:** 2026-02-23T19:50:53Z
- **Completed:** 2026-02-23T20:13:52Z
- **Tasks:** 2
- **Files modified:** 21

## Accomplishments
- MetricsCollector with AtomicLongArray frame histogram (6 buckets: <8ms through >33ms), ConcurrentHashMap per-widget draw times and per-provider latencies via LongArrayRingBuffer (capacity 64), and atomic recomposition counters
- JankDetector tracking consecutive janky frames (>16ms) with captures at exactly 5, 20, and 100 consecutive frames -- verified 4/19/99 do NOT trigger
- DiagnosticSnapshotCapture with AtomicBoolean concurrent guard (CAS-based, drops second capture), three rotation pools (crash:20, thermal:10, perf:10), storage pressure check, and metrics/trace/log assembly
- AnrWatchdog daemon thread posting CountDownLatch to main handler with 2500ms timeout, requiring 2 consecutive misses before capture, with Debug.isDebuggerConnected() suppression
- WidgetHealthMonitor tracking data freshness and render liveness with configurable staleness threshold (default 10s) and periodic check interval
- AnalyticsTracker interface with isEnabled() gate, AnalyticsEvent sealed hierarchy (20 event subtypes), and PackAnalytics per-pack enrichment
- 37 new unit tests (70 total across both modules) -- all passing

## Task Commits

Each task was committed atomically:

1. **Task 1: MetricsCollector + JankDetector + diagnostics + AnrWatchdog + WidgetHealthMonitor + analytics contracts** - `57b4f57` (feat)
2. **Task 2: Metrics + health + diagnostics + analytics unit tests** - `87d2b87` (test)

## Files Created/Modified
- `android/sdk/observability/build.gradle.kts` - Added compileOnly(compose.runtime) for @Immutable
- `android/sdk/observability/src/main/kotlin/.../metrics/MetricsCollector.kt` - Lock-free frame histogram + per-widget/provider metrics
- `android/sdk/observability/src/main/kotlin/.../metrics/MetricsSnapshot.kt` - @Immutable read-only metrics data class
- `android/sdk/observability/src/main/kotlin/.../metrics/JankDetector.kt` - Consecutive jank tracking with exponential thresholds
- `android/sdk/observability/src/main/kotlin/.../health/WidgetHealthMonitor.kt` - Periodic staleness + stalled render detection
- `android/sdk/observability/src/main/kotlin/.../health/ProviderStatusProvider.kt` - Interface for Phase 7 implementation
- `android/sdk/observability/src/main/kotlin/.../health/AnrWatchdog.kt` - Daemon thread ANR detection with debugger guard
- `android/sdk/observability/src/main/kotlin/.../diagnostic/AnomalyTrigger.kt` - 8-variant sealed interface hierarchy
- `android/sdk/observability/src/main/kotlin/.../diagnostic/DiagnosticSnapshot.kt` - @Immutable anomaly snapshot
- `android/sdk/observability/src/main/kotlin/.../diagnostic/DiagnosticSnapshotCapture.kt` - Concurrent guard + pool routing
- `android/sdk/observability/src/main/kotlin/.../diagnostic/DiagnosticFileWriter.kt` - Pool rotation + eviction + storage pressure
- `android/sdk/analytics/build.gradle.kts` - Added kotlinx-collections-immutable dependency
- `android/sdk/analytics/src/main/kotlin/.../analytics/AnalyticsTracker.kt` - Interface + NoOpAnalyticsTracker (F12.1)
- `android/sdk/analytics/src/main/kotlin/.../analytics/AnalyticsEvent.kt` - 20 sealed event subtypes (F12.2/3/6/7)
- `android/sdk/analytics/src/main/kotlin/.../analytics/PackAnalytics.kt` - Per-pack event enrichment + EnrichedEvent
- `android/sdk/observability/src/test/kotlin/.../metrics/MetricsCollectorTest.kt` - 6 tests
- `android/sdk/observability/src/test/kotlin/.../metrics/JankDetectorTest.kt` - 7 tests
- `android/sdk/observability/src/test/kotlin/.../diagnostic/DiagnosticSnapshotCaptureTest.kt` - 7 tests
- `android/sdk/observability/src/test/kotlin/.../health/AnrWatchdogTest.kt` - 4 tests
- `android/sdk/observability/src/test/kotlin/.../health/WidgetHealthMonitorTest.kt` - 6 tests
- `android/sdk/analytics/src/test/kotlin/.../analytics/AnalyticsTrackerTest.kt` - 7 tests

## Decisions Made
- **Open classes for testability over MockK class mocking**: DiagnosticSnapshotCapture and DiagnosticFileWriter made `open` with `open` methods for `capture()`, `write()`, and `checkStoragePressure()`. MockK cannot properly mock final classes with constructor-initialized `AtomicBoolean` fields on JDK 25 / Kotlin 2.3.10 -- the mock skips field initialization, causing NPE on `this.capturing`. Test doubles via anonymous object subclasses are simpler and more reliable.
- **compileOnly(compose.runtime) for @Immutable in :sdk:observability**: Same pattern established by :sdk:contracts. Provides `@Immutable` annotation at compile time without requiring Compose compiler plugin. No Compose function bodies in this module.
- **EnrichedEvent as concrete AnalyticsEvent subtype**: Anonymous objects cannot extend sealed interfaces in Kotlin. `EnrichedEvent` data class in the same module acts as a transient wrapper for PackAnalytics enrichment.
- **WidgetHealthMonitor uses scope dispatcher over Dispatchers.Default**: The original hardcoded `Dispatchers.Default` in the init block prevented `backgroundScope` from working in tests (real dispatcher not controlled by test scheduler). Using the scope's own dispatcher fixes testability. In production, the scope will be configured with `Dispatchers.Default`.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] compileOnly(compose.runtime) needed for @Immutable in :sdk:observability**
- **Found during:** Task 1 (compilation)
- **Issue:** MetricsSnapshot and DiagnosticSnapshot use `@Immutable` annotation from `compose.runtime`, but `:sdk:observability` has no Compose dependency
- **Fix:** Added `compileOnly(platform(libs.compose.bom))` and `compileOnly(libs.compose.runtime)` to build.gradle.kts
- **Files modified:** `android/sdk/observability/build.gradle.kts`
- **Verification:** Module compiles successfully
- **Committed in:** `57b4f57` (Task 1 commit)

**2. [Rule 1 - Bug] Anonymous object cannot extend sealed interface AnalyticsEvent**
- **Found during:** Task 1 (compilation)
- **Issue:** PackAnalytics used `object : AnalyticsEvent { ... }` which Kotlin rejects for sealed interfaces
- **Fix:** Created `EnrichedEvent` data class implementing `AnalyticsEvent` in the same module
- **Files modified:** `android/sdk/analytics/src/main/kotlin/.../PackAnalytics.kt`
- **Verification:** Module compiles; PackAnalytics test verifies event enrichment
- **Committed in:** `57b4f57` (Task 1 commit)

**3. [Rule 3 - Blocking] Open classes required for test doubles (MockK JDK 25 incompatibility)**
- **Found during:** Task 2 (test execution)
- **Issue:** MockK `relaxed = true` on final classes with constructor-initialized `AtomicBoolean` produces null fields on JDK 25. All 17+ tests using mocked `DiagnosticSnapshotCapture` / `DiagnosticFileWriter` failed with NPE.
- **Fix:** Made both classes `open`, made `capture()`, `write()`, `checkStoragePressure()` methods `open`. Tests use anonymous object subclasses as test doubles.
- **Files modified:** `DiagnosticSnapshotCapture.kt`, `DiagnosticFileWriter.kt`, all affected test files
- **Verification:** All 70 tests pass (0 failures)
- **Committed in:** `87d2b87` (Task 2 commit)

**4. [Rule 1 - Bug] WidgetHealthMonitor Dispatchers.Default blocked backgroundScope tests**
- **Found during:** Task 2 (test execution)
- **Issue:** `scope.launch(Dispatchers.Default)` in init block dispatches to real Default dispatcher, not controlled by TestScope. All 6 WidgetHealthMonitor tests failed with UncompletedCoroutinesError.
- **Fix:** Removed explicit `Dispatchers.Default`, uses scope's own dispatcher instead
- **Files modified:** `WidgetHealthMonitor.kt`
- **Verification:** All 6 WidgetHealthMonitor tests pass
- **Committed in:** `87d2b87` (Task 2 commit)

---

**Total deviations:** 4 auto-fixed (2 bugs, 2 blocking)
**Impact on plan:** All fixes necessary for compilation and test correctness. No scope creep. Open classes are a valid testability pattern.

## Issues Encountered
- MockK 1.13.16 on JDK 25 / Kotlin 2.3.10 cannot properly mock final classes with constructor-initialized fields. The mock proxy skips field initialization, leaving `AtomicBoolean` fields as null. This is a known limitation area of MockK with newer JDK versions. Test doubles via open subclasses are the reliable workaround.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- `:sdk:observability` module fully complete with all planned types (logging, tracing, crash, metrics, health, diagnostics)
- `:sdk:analytics` module complete with contract interfaces (AnalyticsTracker, AnalyticsEvent, PackAnalytics)
- MetricsCollector ready for integration with debug overlays (F13.6) and state dumps (F13.5) in Phase 7
- JankDetector + DiagnosticSnapshotCapture + AnrWatchdog form the complete anomaly auto-detection pipeline for NF36
- AnalyticsTracker interface ready for Firebase implementation in Phase 5 (`:core:firebase`)
- WidgetHealthMonitor ready for WidgetBindingCoordinator integration in Phase 7
- ProviderStatusProvider interface ready for `:feature:diagnostics` consumption

## Self-Check: PASSED

All 21 key files verified present. Both commit hashes verified in git log.

---
*Phase: 03-sdk-observability-analytics-ui*
*Completed: 2026-02-24*
