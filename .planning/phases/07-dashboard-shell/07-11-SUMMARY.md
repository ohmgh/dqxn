---
phase: 07-dashboard-shell
plan: 11
subsystem: testing
tags: [exponential-backoff, retry-logic, MockK, CoroutineExceptionHandler]

# Dependency graph
requires:
  - phase: 07-dashboard-shell
    provides: "WidgetBindingCoordinator with StandardTestDispatcher (07-10)"
provides:
  - "3 retry tests proving exponential backoff timing, max retries, and error count reset"
affects: []

# Tech tracking
tech-stack:
  added: []
  patterns: ["mocked WidgetDataBinder isolates coordinator retry logic from merge+scan pipeline", "awaitCancellation() over delay(Long.MAX_VALUE) in test flows to avoid TestCoroutineScheduler event at MAX_VALUE"]
---

## Summary

Added 3 exponential backoff retry tests to WidgetBindingCoordinatorTest (NF16):

1. **`exponential backoff retry timing 1s 2s 4s`** — proves backoff schedule via advanceTimeBy
2. **`max 3 retries then ConnectionError status`** — proves ProviderError status after retry exhaustion
3. **`successful emission resets error count`** — proves recovery after transient failure

## Key Files

### key-files
created:
  - .planning/phases/07-dashboard-shell/07-11-SUMMARY.md

modified:
  - android/feature/dashboard/src/test/kotlin/app/dqxn/android/feature/dashboard/coordinator/WidgetBindingCoordinatorTest.kt

## Deviations

1. **Mocked binder instead of real provider** — The plan proposed a `CountingCrashProvider` flowing through the real binder pipeline. The `merge()` operator's internal `channelFlow` creates multi-dispatch layers that interact badly with `StandardTestDispatcher + CoroutineExceptionHandler + SupervisorJob`, causing test hangs. Solution: mock `WidgetDataBinder.bind()` to return `flow { throw }`, testing ONLY the coordinator's retry logic.

2. **`awaitCancellation()` over `delay(Long.MAX_VALUE)`** — `delay(Long.MAX_VALUE)` schedules a test scheduler event at virtual time `Long.MAX_VALUE`. Even after `coordinator.destroy()` cancels the supervisor, `runTest`'s final `advanceUntilIdle()` tries to advance to that event, hanging forever. `awaitCancellation()` suspends without scheduling anything.

3. **`minStalenessThresholdMs` returns null** — Disables the staleness watchdog in retry tests. The watchdog's `while(true) { delay() }` loop is an independent concern and interacts with the retry timing assertions. Null threshold means no watchdog launched.

## Self-Check: PASSED

- [x] 3 new retry tests passing
- [x] All 10 WidgetBindingCoordinatorTest tests pass (7 existing + 3 new)
- [x] No `UnconfinedTestDispatcher` references
- [x] Committed atomically
