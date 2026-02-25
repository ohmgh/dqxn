---
phase: 11-theme-ui-diagnostics-onboarding
plan: 07
subsystem: diagnostics
tags: [diagnostics, observability, compose-ui, provider-health, session-recording, metrics, hilt-viewmodel]

# Dependency graph
requires:
  - phase: 11-theme-ui-diagnostics-onboarding
    provides: "SessionRecorder ring-buffer implementation in :feature:diagnostics (Plan 11-02)"
  - phase: 11-theme-ui-diagnostics-onboarding
    provides: "ProviderStatusBridge @Singleton + SessionEventEmitter wiring (Plan 11-04)"
  - phase: 03-sdk-observability-analytics-ui
    provides: "ProviderStatusProvider, DiagnosticSnapshotCapture, MetricsCollector, DiagnosticSnapshotDto in :sdk:observability"
  - phase: 05-core-infrastructure
    provides: "ConnectionEventStore in :data"
provides:
  - "DiagnosticsViewModel @HiltViewModel aggregating all observability data sources"
  - "ProviderHealthDashboard composable with staleness indicators and connection state"
  - "ProviderDetailScreen composable with connection event log and retry button"
  - "DiagnosticSnapshotViewer composable with type-based filter chips"
  - "SessionRecorderViewer composable with recording toggle, event count, clear"
  - "ObservabilityDashboard composable with P50/P95/P99 frame metrics and jank percentage"
  - "15 Compose UI tests across 3 test classes"
affects: [11-10, 13-e2e-integration]

# Tech tracking
tech-stack:
  added: []
  patterns: ["Stateless composable viewers with callback-based interaction (no ViewModel injection in composables)", "Histogram percentile estimation via bucket midpoints for frame time metrics"]

key-files:
  created:
    - android/feature/diagnostics/src/main/kotlin/app/dqxn/android/feature/diagnostics/DiagnosticsViewModel.kt
    - android/feature/diagnostics/src/main/kotlin/app/dqxn/android/feature/diagnostics/ProviderHealthDashboard.kt
    - android/feature/diagnostics/src/main/kotlin/app/dqxn/android/feature/diagnostics/ProviderDetailScreen.kt
    - android/feature/diagnostics/src/main/kotlin/app/dqxn/android/feature/diagnostics/DiagnosticSnapshotViewer.kt
    - android/feature/diagnostics/src/main/kotlin/app/dqxn/android/feature/diagnostics/SessionRecorderViewer.kt
    - android/feature/diagnostics/src/main/kotlin/app/dqxn/android/feature/diagnostics/ObservabilityDashboard.kt
    - android/feature/diagnostics/src/test/kotlin/app/dqxn/android/feature/diagnostics/ProviderHealthDashboardTest.kt
    - android/feature/diagnostics/src/test/kotlin/app/dqxn/android/feature/diagnostics/ProviderDetailScreenTest.kt
    - android/feature/diagnostics/src/test/kotlin/app/dqxn/android/feature/diagnostics/DiagnosticsViewerTest.kt
  modified: []

key-decisions:
  - "Text-based warning indicator (Unicode U+26A0) over Material Icons Warning -- material-icons-extended not in convention plugin dependencies, avoids ~30MB library for a single icon"
  - "useUnmergedTree=true for nested test tag assertions inside clickable containers -- clickable modifier merges child semantics, consistent with Phase 10 decisions"

patterns-established:
  - "Stateless diagnostic composables with callback params: all viewers take data + callbacks, no ViewModel injection, testable with createComposeRule and mock data"
  - "Histogram percentile computation: bucket midpoints (4,10,14,20,28,40ms) for P50/P95/P99 frame time estimation from MetricsSnapshot"

requirements-completed: [F3.13, F7.6, F13.3]

# Metrics
duration: 7min
completed: 2026-02-25
---

# Phase 11 Plan 07: Diagnostics UI Summary

**DiagnosticsViewModel + 5 diagnostic composables (provider health, detail, snapshots, session, observability) with 15 Compose UI tests covering staleness, event logs, filtering, recording toggle, and frame metrics**

## Performance

- **Duration:** 7 min
- **Started:** 2026-02-25T08:22:49Z
- **Completed:** 2026-02-25T08:30:32Z
- **Tasks:** 2
- **Files modified:** 9

## Accomplishments
- DiagnosticsViewModel @HiltViewModel aggregating ProviderStatusProvider, ConnectionEventStore, DiagnosticSnapshotCapture, MetricsCollector, and SessionRecorder with WhileSubscribed(5000) stateIn sharing
- ProviderHealthDashboard with green/red connection dots, relative time display, amber staleness indicators (>10s threshold), tap-to-detail navigation, and empty state
- ProviderDetailScreen with connection event log (rolling 50, newest first), retry button callback, and empty state
- DiagnosticSnapshotViewer with FilterChip-based type filtering (ALL/CRASH/ANR/ANOMALY/PERF), expandable detail rows, and empty state
- SessionRecorderViewer with recording toggle (red dot indicator), formatted event count ("50 / 10,000 events"), clear button, and text-based event timeline
- ObservabilityDashboard with P50/P95/P99 frame time percentiles computed from histogram, jank percentage, and memory usage display
- 15 Compose UI tests: 5 ProviderHealthDashboardTest + 4 ProviderDetailScreenTest + 6 DiagnosticsViewerTest

## Task Commits

Each task was committed atomically:

1. **Task 1: DiagnosticsViewModel + ProviderHealthDashboard + ProviderDetailScreen** - `21d52c5` (feat)
2. **Task 2: DiagnosticSnapshotViewer + SessionRecorderViewer + ObservabilityDashboard** - `914d47e` (feat)

## Files Created/Modified
- `android/feature/diagnostics/.../DiagnosticsViewModel.kt` - @HiltViewModel aggregating all observability data sources
- `android/feature/diagnostics/.../ProviderHealthDashboard.kt` - LazyColumn provider status list with staleness indicators
- `android/feature/diagnostics/.../ProviderDetailScreen.kt` - Connection event log with retry button (F7.6)
- `android/feature/diagnostics/.../DiagnosticSnapshotViewer.kt` - FilterChip-based snapshot browser with expand/collapse
- `android/feature/diagnostics/.../SessionRecorderViewer.kt` - Recording toggle, event timeline, count display (F13.3)
- `android/feature/diagnostics/.../ObservabilityDashboard.kt` - Frame time percentiles and jank percentage (F3.13)
- `android/feature/diagnostics/.../ProviderHealthDashboardTest.kt` - 5 UI tests: list rendering, staleness, green indicator, empty state, tap navigation
- `android/feature/diagnostics/.../ProviderDetailScreenTest.kt` - 4 UI tests: 50 events, empty state, retry callback, newest-first ordering
- `android/feature/diagnostics/.../DiagnosticsViewerTest.kt` - 6 UI tests: snapshot filtering, empty state, recording toggle, event count, clear callback, frame metrics

## Decisions Made
- **Text-based warning indicator over Material Icons** -- material-icons-extended (~30MB) not included in convention plugin dependencies; Unicode warning sign achieves same visual with zero dependency cost
- **useUnmergedTree=true for nested test tags** -- clickable modifier on Row merges child semantics making nested testTags invisible in merged tree; same pattern established in Phase 10

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Unicode warning sign instead of Icons.Filled.Warning**
- **Found during:** Task 1 (ProviderHealthDashboard)
- **Issue:** `Icons.Filled.Warning` from `material-icons-extended` not available -- library not in convention plugin dependencies
- **Fix:** Replaced Icon composable with Text using Unicode U+26A0 warning sign with amber tint
- **Files modified:** ProviderHealthDashboard.kt
- **Verification:** Compilation succeeds, staleness test passes
- **Committed in:** 21d52c5

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** Minor visual implementation change, same UX. No scope creep.

## Issues Encountered
None.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- All 5 diagnostic composables ready for integration into diagnostics navigation (Plan 11-10 or Phase 13)
- DiagnosticsViewModel injectable via Hilt for diagnostics screen composition
- No blockers

## Self-Check: PASSED

All 9 created files verified on disk. Both commit hashes (21d52c5, 914d47e) verified in git log.

---
*Phase: 11-theme-ui-diagnostics-onboarding*
*Completed: 2026-02-25*
