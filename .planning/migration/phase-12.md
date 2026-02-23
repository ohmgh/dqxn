# Phase 12: CI Gates + Benchmarking

**What:** Performance measurement infrastructure, Compose stability enforcement, and CI gate configuration. Deliberately decoupled from Phase 9/10/11 — starts as soon as Phase 8 provides enough running code to benchmark.

**Depends on:** Phase 8 (essentials pack provides 13 widgets for benchmarking) | **Concurrent with:** Phases 9, 10, 11

## Baseline Profiles (NF9)

- `:baselineprofile` module generates profiles for critical paths: app startup, dashboard render, edit mode enter/exit
- Theme switch profile added when `:pack:themes` exists (Phase 9) — conditional inclusion, doesn't block the module
- Profiles included in release build via `BaselineProfileRule`

## Benchmark module (NF10)

- `:benchmark` module runs macrobenchmarks measuring frame times, startup, and memory
- 12-widget soak benchmark: `DemoTimeProvider` + `DemoSpeedProvider` (from `:pack:demo` if available, otherwise `TestDataProvider` stubs)
- Startup benchmark: cold start to first meaningful paint
- Edit mode benchmark: enter → drag → resize → exit cycle

## CI Gates

All nine gates enforced:

| Gate | Threshold | Notes |
|---|---|---|
| P50 frame time | < 8ms | Macrobenchmark with 12 widgets active |
| P95 frame time | < 12ms | |
| P99 frame time | < 16ms | |
| Jank rate | < 2% | Measured over 60s soak |
| Cold startup | < 1.5s | To first frame with layout loaded |
| Compose stability | 0 unstable classes in app-owned modules | Third-party types excluded; `@Immutable`/`@Stable` on all UI-facing types |
| Non-skippable composables | max 5 in `:feature:dashboard` | Other modules tracked but not gated |
| Unit coverage (coordinators) | > 90% line | 6 coordinators in `:feature:dashboard` |
| Release smoke test | Dashboard renders with data | `assembleRelease` + install + `dump-health` via agentic |

P50 trend detection: alert when P50 increases > 20% from 7-day rolling average.

Mutation kill rate > 80% for critical modules (deferred to post-launch — tracked, not gated). **CI gate row exists for tracking only — pipeline must NOT fail on this metric at V1. Enforce post-launch when Pitest infrastructure is in place.**

## Compose stability audit

- Run `./gradlew assembleRelease -Pandroidx.compose.metrics=true -Pandroidx.compose.metrics.output=<path>` on all app-owned modules
- Parse stability report: zero unstable classes in app-owned modules, max 5 non-skippable in `:feature:dashboard`
- CI job fails on regression — new unstable class or non-skippable composable added without `@Immutable`/`@Stable` annotation

## APK size gate (NF34)

- CI step: `./gradlew assembleRelease`, measure APK size
- Gate: base APK < 30MB, with all packs < 50MB
- Alert on >5% increase from previous release

**Tests:**
- Benchmark stability: same benchmark run 3 times, P50 variance < 10%
- Benchmark data source consistency: verify benchmark uses `DemoTimeProvider`/`DemoSpeedProvider` when `:pack:demo` is in classpath, falls back to `TestDataProvider` stubs otherwise. Both paths produce deterministic data — assert P50 variance < 15% across demo/stub configurations to catch measurement drift
- Baseline profile generation: profile file present in release APK, covers startup + dashboard render methods
- CI gate script tests: threshold comparison logic, trend detection calculation
