---
phase: 11-theme-ui-diagnostics-onboarding
plan: 10
subsystem: analytics
tags: [analytics, session-tracking, consent-gating, quality-metrics, thermal, jank]

# Dependency graph
requires:
  - phase: 11-05
    provides: "AnalyticsTracker consent gating via isEnabled()"
  - phase: 11-08
    provides: "Analytics consent dialog + persistence"
  - phase: 03-02
    provides: "MetricsCollector + WidgetHealthMonitor + MetricsSnapshot"
  - phase: 05-02
    provides: "ThermalMonitor + ThermalLevel enum"
provides:
  - "AnalyticsEventCallSites.kt extension helpers for widget_add, theme_change, upsell_impression"
  - "UpsellTrigger constants (THEME_PREVIEW, WIDGET_PICKER, SETTINGS)"
  - "SessionLifecycleTracker with F12.7 session quality metrics"
affects: [feature-dashboard, feature-settings, app-integration]

# Tech tracking
tech-stack:
  added: []
  patterns: ["Extension function call sites on AnalyticsTracker", "Injectable clock for deterministic session timing", "Ordinal-based peak thermal tracking"]

key-files:
  created:
    - "android/sdk/analytics/src/main/kotlin/app/dqxn/android/sdk/analytics/AnalyticsEventCallSites.kt"
    - "android/sdk/analytics/src/test/kotlin/app/dqxn/android/sdk/analytics/AnalyticsEventCallSiteTest.kt"
    - "android/app/src/main/kotlin/app/dqxn/android/app/SessionLifecycleTracker.kt"
    - "android/app/src/test/kotlin/app/dqxn/android/app/SessionLifecycleTrackerTest.kt"
  modified: []

key-decisions:
  - "ThermalMonitor injected directly into SessionLifecycleTracker (not via MetricsCollector) -- MetricsCollector has no thermal data; peak tracked via ordinal comparison"
  - "Jank% computed inline from MetricsSnapshot frame histogram (buckets 3+4+5 / total * 100) -- same formula as ObservabilityDashboard"
  - "Render failures = CRASHED + STALLED_RENDER statuses; provider errors = STALE_DATA statuses from WidgetHealthMonitor"

patterns-established:
  - "Extension function call sites: typed helpers that delegate to AnalyticsTracker.track() with zero consent logic at call site"
  - "Injectable clock pattern: () -> Long constructor param defaulting to System::currentTimeMillis for deterministic testing"

requirements-completed: [F12.2, F12.3, F12.4, F12.6, F12.7]

# Metrics
duration: 4min
completed: 2026-02-25
---

# Phase 11 Plan 10: Analytics Event Call Sites + Session Lifecycle Tracker Summary

**Extension helpers for widget/theme/upsell analytics events with consent-gated session lifecycle tracking including jank%, thermal peak, and widget health metrics**

## Performance

- **Duration:** 4 min
- **Started:** 2026-02-25T08:49:33Z
- **Completed:** 2026-02-25T08:53:43Z
- **Tasks:** 2
- **Files modified:** 4

## Accomplishments
- AnalyticsEventCallSites with trackWidgetAdd, trackThemeChange, trackUpsellImpression extensions on AnalyticsTracker
- UpsellTrigger constants for F12.6 trigger_source parameter
- SessionLifecycleTracker @Singleton with injectable clock, computing F12.7 quality metrics from MetricsCollector and WidgetHealthMonitor
- 12 tests total (6 call site + 6 session) covering event params, consent gating, no-PII, duration calc, jank%, thermal peak, health statuses

## Task Commits

Each task was committed atomically:

1. **Task 1: Analytics event call site helpers + tests** - `c0fc1ca` (feat)
2. **Task 2: SessionLifecycleTracker + tests** - `c71112f` (feat)

## Files Created/Modified
- `android/sdk/analytics/src/main/kotlin/app/dqxn/android/sdk/analytics/AnalyticsEventCallSites.kt` - Extension helpers for firing analytics events at interaction points
- `android/sdk/analytics/src/test/kotlin/app/dqxn/android/sdk/analytics/AnalyticsEventCallSiteTest.kt` - 6 tests: event params, consent gating, no-PII, trigger uniqueness
- `android/app/src/main/kotlin/app/dqxn/android/app/SessionLifecycleTracker.kt` - Session start/end event firing with quality metrics
- `android/app/src/test/kotlin/app/dqxn/android/app/SessionLifecycleTrackerTest.kt` - 6 tests: session start/end, duration, jank%, thermal, health statuses

## Decisions Made
- ThermalMonitor injected directly into SessionLifecycleTracker -- MetricsCollector has no thermal data; peak tracked via ThermalLevel ordinal comparison during session
- Jank% computed inline from MetricsSnapshot frame histogram (buckets 3+4+5 / total * 100) -- same formula as ObservabilityDashboard.computeFrameMetrics()
- Render failures counted from CRASHED + STALLED_RENDER health statuses; provider errors from STALE_DATA statuses
- WidgetAdded event takes typeId only (not typeId + packId as plan suggested) -- matches actual AnalyticsEvent.WidgetAdded constructor
- UpsellImpression takes both trigger and packId -- matches actual AnalyticsEvent.UpsellImpression constructor

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] ThermalMonitor injection for peak thermal tracking**
- **Found during:** Task 2 (SessionLifecycleTracker)
- **Issue:** Plan specified peakThermalLevel from metricsCollector, but MetricsCollector has no thermal data
- **Fix:** Injected ThermalMonitor directly; added recordThermalLevel() for session peak tracking via ordinal comparison
- **Files modified:** SessionLifecycleTracker.kt
- **Verification:** Test verifies peak thermal stays at DEGRADED even after thermal drops back to WARM
- **Committed in:** c71112f (Task 2 commit)

**2. [Rule 1 - Bug] WidgetAdded constructor signature adaptation**
- **Found during:** Task 1 (call site helpers)
- **Issue:** Plan specified trackWidgetAdd(typeId, packId) but AnalyticsEvent.WidgetAdded only has typeId parameter
- **Fix:** trackWidgetAdd takes only typeId, matching actual event constructor
- **Files modified:** AnalyticsEventCallSites.kt
- **Verification:** Test verifies WidgetAdded event fires with correct typeId
- **Committed in:** c0fc1ca (Task 1 commit)

---

**Total deviations:** 2 auto-fixed (1 missing critical, 1 bug)
**Impact on plan:** Both necessary for correctness -- adapted to actual API shapes. No scope creep.

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Analytics event call sites ready for wiring at interaction points (widget picker, theme selector, settings)
- SessionLifecycleTracker ready for integration in DqxnApplication or MainActivity lifecycle callbacks
- Phase 11 complete -- all 10 plans executed

## Self-Check: PASSED

- All 4 created files exist on disk
- Both task commits (c0fc1ca, c71112f) verified in git log
- 12 tests pass (6 AnalyticsEventCallSiteTest + 6 SessionLifecycleTrackerTest)

---
*Phase: 11-theme-ui-diagnostics-onboarding*
*Completed: 2026-02-25*
