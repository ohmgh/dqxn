# Integration Policy

Each phase must prove its output actually connects — compilation alone is insufficient for integration seams with silent failure modes.

## Regression invariant

From Phase 2 onward, every phase runs `./gradlew test` across all modules before merging. CI pipeline established in Phase 6 automates this, but the rule applies even before CI exists (manual `./gradlew test` gate). Adding a new module must not break existing tests.

## Phase-specific integration checks

| Phase | Integration check | What it catches |
|---|---|---|
| 1 | Proto toolchain + EXTOL SDK compat (throwaway modules) | Toolchain incompatibility discovered in Phase 5/9 instead of Phase 1 |
| 4 | `dqxn.pack` applied to stub module, resolved dependency graph verified | Convention plugin misconfiguration accumulating silently for 7 phases |
| 5 | `LayoutRepository` CRUD cycle (create/clone/switch/delete profile) + `ProviderSettingsStore` key format round-trip | Repository bugs masked by Phase 7 fakes — coordinator tests pass but production persistence is broken |
| 6 | `./gradlew assembleRelease` + install + dashboard renders | R8 stripping KSP-generated or proto-generated classes (passes all debug tests, crashes in release) |
| 6 | `trigger-anomaly` → `diagnose-crash` round-trip in CI | Observability pipeline broken with no signal |
| 7 | `DashboardTestHarness` with real coordinators (not fakes) | Coordinator-to-coordinator interactions (the decomposition actually works) |
| 7 | `FakeThermalManager` → DEGRADED → verify emission rate drops | Thermal throttle wiring to binding system |
| 7 | `NotificationCoordinator` re-derivation after ViewModel kill | CRITICAL banners silently lost on process death |
| 7 | `dump-semantics` returns widget nodes with test tags after `DashboardLayer` registration | `SemanticsOwnerHolder` not wired — semantics commands return empty, all UI verification silently fails |
| 8 | 4-criteria gate (contract tests, on-device wiring, stability soak, regression) | Architecture validation — contracts are usable, not just compilable |
| 10 | `SettingRowDispatcher` renders all 12 `SettingDefinition` subtypes from schema | Schema-driven rendering silently skips unsupported types — shows empty row |
| 10 | Overlay navigation round-trip: Phase 10 routes render, back returns to dashboard | Overlay routes registered but destination composables crash or never compose |
| 11 | Overlay navigation completion: all 7 routes render, back returns to dashboard | Phase 11 routes (ThemeSelector, Diagnostics, Onboarding) + Phase 10 routes |
| 11 | Analytics consent → event gating: opt-in fires events, opt-out stops | Analytics events fire without consent (PDPA/GDPR violation) |
| 13 | Full E2E: launch → bind → render → edit → add/remove/resize → theme → settings | End-to-end user journey — the whole system works, not just individual components |
| 13 | Multi-pack load: essentials + themes + demo simultaneously | Hilt binding conflicts, KSP collisions, R8 rule conflicts across packs |
| 13 | CI chaos gate: `seed=42` → `assertChaosCorrelation()` | Chaos infrastructure actually produces diagnosable anomalies |

## Silent failure seams

These seams produce no error on failure — they degrade to empty/default state. Automated checks are mandatory because manual testing won't surface them:

- **KClass equality for snapshot binding.** Widget declares `compatibleSnapshots = setOf(SpeedSnapshot::class)`, provider declares `snapshotType = SpeedSnapshot::class`. If they reference different class objects (different modules, duplicated type), the KClass won't match and the widget silently gets `WidgetData.Empty`. Caught by on-device `dump-health` showing widget as INACTIVE despite provider running.
- **`dqxn.pack` auto-wiring.** Convention plugin wires `:sdk:*` dependencies. If it uses wrong scope or misses a module, the pack compiles (transitive deps may satisfy it) but runtime behavior differs. Caught by Phase 4 stub module check.
- **`DataProviderInterceptor` chain.** `WidgetDataBinder` must apply all registered interceptors. If it skips them on fallback paths, chaos testing gives wrong results. Caught by `ProviderFault` tests using `TestDataProvider` that asserts interceptor invocation.
- **`merge()+scan()` vs `combine()`.** If someone "simplifies" the binder to `combine()`, any widget with a slow or missing provider silently receives no data (combine waits for all upstreams). Caught by test-first multi-slot delivery test (TDD Policy).
- **`SemanticsOwnerHolder` registration.** If `DashboardLayer` doesn't register (or registers too late), `dump-semantics`/`query-semantics` return empty results — all semantics-based E2E assertions silently pass with "no match found" instead of failing meaningfully. Caught by Phase 7 integration check: `dump-semantics` must return nodeCount > 0 after `DashboardLayer` composition.
- **Overlay route registration.** `OverlayNavHost` routes registered in Phases 10 and 11. If a route is registered but its destination composable throws during first composition, the overlay silently shows nothing (NavHost catches composition failures). Caught by Phase 10 and 11 integration checks: navigate to each route, verify content renders.
- **`SettingRowDispatcher` type coverage.** If a `SettingDefinition` subtype has no matching row renderer, `SettingRowDispatcher` silently skips it — the setting is invisible. Caught by Phase 10 test that creates one of each 12 subtypes and verifies all render non-empty.
- **Analytics consent gating.** If analytics events are wired (Phase 11) but consent check is bypassed, events fire without opt-in — PDPA/GDPR violation. Caught by Phase 11 integration check: verify `AnalyticsTracker.isEnabled()` returns false before consent, events suppressed.
- **`DataProviderContractTest` cancellation assertion.** The contract test #4 ("respects cancellation without leaking") must verify `testScheduler.isIdle` after cancellation — not just "no exception." A vacuous assertion (always-true condition) silently certifies leaking providers. Caught by Phase 2 code review of the abstract test base.
- **`FramePacer` API branching.** `Window.setFrameRate()` (API 34+) vs emission throttling (API 31-33). If the API check uses wrong constant or the throttling path is untested, frame pacing silently does nothing on API 31-33 devices (majority of minSdk 31 fleet). Caught by Phase 5 unit tests with mock `Window` at both API levels.
- **`SettingRowDispatcher` type coverage (12 vs 11).** `SettingDefinition` has 12 subtypes (Phase 2) but Phase 10 settings rows table lists 11 dedicated row renderers. Only `UriSetting` lacks a dedicated row renderer — it falls back to `SettingLabel(label, description, theme)` via the dispatcher's `else` branch. `AppPickerSetting` has a dedicated `AppPickerSettingRow` in Phase 10. Caught by Phase 10 parameterized test rendering all 12 subtypes.
- **`AgenticTestClient` module assignment.** `AgenticTestClient` is referenced in Phases 8-13 but has no module assignment. If placed in the wrong module (e.g., `:app:src/androidTest/` vs dedicated `:test-e2e`), instrumented tests may fail to resolve UiDevice or Hilt test dependencies. Assign to `:app:src/androidTest/kotlin/` — colocated with `HiltAndroidRule`-based tests.
