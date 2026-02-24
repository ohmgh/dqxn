---
phase: 07-dashboard-shell
plan: 12
subsystem: binding
tags: [staleness, F3.11, timeProvider, thermal-throttle]

# Dependency graph
requires:
  - phase: 07-dashboard-shell
    provides: "WidgetDataBinder + WidgetBindingCoordinator with staleness infrastructure"
provides:
  - "Tests for F3.11 staleness detection"
  - "Tests for thermal throttle with injectable timeProvider"
affects: []

# Tech tracking
tech-stack:
  added: []
  patterns: ["injectable timeProvider for deterministic time-based tests", "fake clock + advanceTimeBy for staleness watchdog testing"]
---

## Summary

Production code (staleness watchdog, injectable timeProvider, minStalenessThresholdMs) was already implemented from prior gap closure. This plan added 3 tests verifying the behavior:

1. **`data staleness marks widget DataStale when no emission within threshold`** — proves watchdog emits DataStale after provider schema threshold exceeded
2. **`staleness watchdog resets when new data arrives before threshold`** — proves fresh emission prevents DataStale
3. **`thermal throttle reduces emission rate under DEGRADED config`** — proves WidgetDataBinder throttle respects thermal fps target via injectable timeProvider

## Key Files

### key-files
created:
  - .planning/phases/07-dashboard-shell/07-12-SUMMARY.md

modified:
  - android/feature/dashboard/src/test/kotlin/app/dqxn/android/feature/dashboard/coordinator/WidgetBindingCoordinatorTest.kt
  - android/feature/dashboard/src/test/kotlin/app/dqxn/android/feature/dashboard/binding/WidgetDataBinderTest.kt

## Deviations

1. **Production code already implemented** — staleness watchdog, timeProvider, and minStalenessThresholdMs were already in place from prior work. Plan only needed test coverage.

## Self-Check: PASSED

- [x] Staleness detection test passes
- [x] Staleness reset test passes
- [x] Thermal throttle test passes
- [x] All existing tests pass
