# TDD Policy

Not all code benefits equally from test-first. Mandatory TDD for code where the test IS the specification and getting it wrong has silent or safety-critical consequences. Test-concurrent for everything else — tests land in the same phase, same PR, but don't need to exist before the first line of implementation.

## TDD mandatory (test-first)

| Category | Why test-first | Phase |
|---|---|---|
| Contract interfaces (`WidgetRenderer`, `DataProvider<T>`, `WidgetData`) | Writing `WidgetRendererContractTest` before the interface forces the contract to be testable. If the test is hard to write, the interface is wrong. Highest-leverage TDD in the project. | 2 |
| State machines (`ConnectionStateMachine`, coordinator transitions) | Property-based testing (jqwik) defines valid transition sequences before implementation. Missing transitions and illegal states caught at design time. | 2, 7 |
| Banner derivation logic (`NotificationCoordinator` observer blocks) | Maps `(safeModeActive, bleAdapterOff, storageLow, ...)` → prioritized banner list. Incorrect derivation = CRITICAL banners invisible. Effectively a state machine — same rationale applies. | 7 |
| `merge()+scan()` accumulation in `WidgetDataBinder` | Test: "widget with 3 slots renders correctly when slot 2 is delayed." Prevents accidental `combine()` starvation — the architecture's most critical flow invariant. | 7 |
| Safe mode trigger logic | Boundary condition: ≥4 crashes in 60s rolling window (not total), cross-widget counting (4 different widgets each crashing once triggers it). Easy to get subtly wrong. | 7 |
| KSP validation rules | Compile-testing assertions for invalid `typeId`, missing `@Immutable`, duplicate `dataType` before writing the processor. The test IS the specification. | 4 |

## TDD not required (test-concurrent)

| Category | Why not test-first | Phase |
|---|---|---|
| Build system / convention plugins | Configuration, not logic. `./gradlew tasks` is the test. | 1 |
| UI rendering (widget `Render()` bodies) | Visual output verified via on-device semantics (text content, bounds, visibility) and draw-math unit tests. Contract tests (null data handling, accessibility) are test-first via `WidgetRendererContractTest` inheritance, but rendering itself isn't. | 8–9 |
| Overlay composables (settings rows, theme editor, diagnostics, onboarding) | UI porting / greenfield UI. Visual correctness verified via semantics assertions and manual on-device testing. Non-UI logic (color conversion, CDM state machine, lux mapping) extracted to testable utilities and test-concurrent. | 10–11 |
| Observability plumbing (`DqxnLogger`, `CrashEvidenceWriter`, `AnrWatchdog`) | Well-understood patterns ported from old codebase. Unit tests verify behavior but writing them first doesn't improve design. | 3 |
| `SupervisorJob` / `CoroutineExceptionHandler` error boundaries | Established boilerplate, not discovery work. The pattern is known before the test is written. Test-concurrent — verify isolation works, but test-first adds ceremony without catching real bugs. | 7 |
| Proto DataStore schemas | Declarative `.proto` files. Round-trip serialization tests should exist but don't benefit from being written first. | 5 |
| Theme JSON porting | Pure data, no logic. | 8-9 |
| Agentic handlers | Request-response adapters over coordinator APIs. Coordinator tests (test-first) already validate the logic. Handler tests verify serialization/routing — test-concurrent. | 6-7 |

## Policy enforcement

"Test-concurrent" means: the phase is not considered done until tests pass. Tests land in the same PR as implementation — no "add tests later" tracking issues. Every widget extends `WidgetRendererContractTest`, every provider extends `DataProviderContractTest` — this is non-negotiable regardless of TDD-mandatory classification.
