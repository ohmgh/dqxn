---
phase: 09-themes-demo-chaos
verified: 2026-02-25T02:22:33Z
status: gaps_found
score: 9/12 must-haves verified
gaps:
  - truth: "ChaosProviderInterceptor + inject-fault → list-diagnostics since= produces correlated snapshot (SC3)"
    status: failed
    reason: "No test exists that integrates ChaosProviderInterceptor fault injection with DiagnosticSnapshotCapture and verifies correlated output from list-diagnostics. ChaosProviderInterceptorTest only verifies fault transforms on raw Flow<T>, not the diagnostics pipeline downstream."
    artifacts:
      - path: "android/core/agentic/src/test/kotlin/app/dqxn/android/core/agentic/chaos/ChaosProviderInterceptorTest.kt"
        issue: "Tests fault flow behavior only; no DiagnosticSnapshotCapture wiring or since= correlation check"
    missing:
      - "A test (integration or unit with fakes) that injects a fault, triggers a diagnostic capture event, then asserts list-diagnostics output contains a fault-correlated snapshot entry"
  - truth: "F6.2 Theme Studio access gating is complete (create/edit custom themes UI gated by themes entitlement)"
    status: partial
    reason: "Phase 9 delivers the prerequisite entitlement data (all 22 themes have requiredAnyEntitlement = setOf('themes')), but F6.2 requires Theme Studio UI which is explicitly Phase 11. The requirement as stated ('Theme Studio access (create/edit custom themes)') is not fully satisfied by this phase — only the entitlement foundation is present."
    artifacts:
      - path: "android/pack/themes/src/main/kotlin/app/dqxn/android/pack/themes/ThemesPackThemeProvider.kt"
        issue: "Provides entitlement-gated theme data but Theme Studio UI is not part of this phase"
    missing:
      - "Theme Studio UI (Phase 11 scope) — this gap is expected per research notes; not a defect in Phase 9 execution"
  - truth: "F8.5 Debug Simulate Free User toggle is complete"
    status: partial
    reason: "simulateRevocation/simulateGrant/reset API is implemented and tested via StubEntitlementManager. The 'toggle' UI surface referenced in the requirement is Phase 10/11 per research notes. The programmatic API exists but there is no debug UI toggle surfacing it yet."
    artifacts:
      - path: "android/app/src/main/kotlin/app/dqxn/android/StubEntitlementManager.kt"
        issue: "API delivered and tested; UI toggle not present (scoped to Phase 10/11)"
    missing:
      - "Debug UI toggle for simulateRevocation/simulateGrant (Phase 10/11 scope per research notes)"
---

# Phase 9: Themes, Demo + Chaos Verification Report

**Phase Goal:** Themes pack, demo pack (deterministic stub providers), chaos testing infrastructure.
**Verified:** 2026-02-25T02:22:33Z
**Status:** gaps_found
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| #  | Truth | Status | Evidence |
|----|-------|--------|----------|
| 1  | 22 premium themes load without error from JSON resource files | VERIFIED | 22 files in `android/pack/themes/src/main/resources/themes/`; `ThemesPackThemeProviderTest.getThemes returns exactly 22 themes` |
| 2  | All 22 themes are gated behind the 'themes' entitlement | VERIFIED | `ThemesPackThemeProvider` calls `.copy(requiredAnyEntitlement = setOf("themes"))` post-parse; `ThemeEntitlementGatingChainTest` proves all 22 gated |
| 3  | Each theme has valid hex colors and gradient stops in [0.0, 1.0] range | VERIFIED | `ThemeJsonValidationTest` runs `@ParameterizedTest` on all 22 files; gradient stop range enforced |
| 4  | Themes pack compiles with dqxn.pack plugin and no :core:* dependencies | VERIFIED | `android/pack/themes/build.gradle.kts` uses only `id("dqxn.pack")`; no core: imports found in pack source |
| 5  | F6.3/F6.4 entitlement gating chain: SOLAR_AUTO/ILLUMINANCE_AUTO modes covered by theme-level gating | VERIFIED | `ThemeEntitlementGatingChainTest.gating chain completeness` documents and asserts the architectural invariant |
| 6  | SolarSnapshot and SpeedLimitSnapshot are cross-boundary in :pack:essentials:snapshots | VERIFIED | Both files present at `android/pack/essentials/snapshots/src/main/kotlin/.../snapshots/`; old pack-local location is empty |
| 7  | 8 demo providers (Time, Speed, Orientation, Battery, AmbientLight, Acceleration, Solar, SpeedLimit) emit deterministic data | VERIFIED | All 8 providers present in `android/pack/demo/src/main/kotlin/.../providers/`; `DemoDeterminismTest` proves same-instance reproducibility for all 8 |
| 8  | All 8 demo providers use ProviderPriority.SIMULATED | VERIFIED | All providers declare `override val priority: ProviderPriority = ProviderPriority.SIMULATED`; confirmed in DemoTimeProvider and DemoSpeedProvider source |
| 9  | All 8 demo providers pass DataProviderContractTest | VERIFIED | 8 contract test classes present; summary reports 96 contract assertions across all 8 |
| 10 | ChaosProviderInterceptor applies all 7 ProviderFault types | VERIFIED | `ChaosProviderInterceptorTest` covers Kill, Delay, Error, ErrorOnNext, Stall, Flap, Corrupt (7 fault tests) |
| 11 | ChaosEngine starts deterministic fault sessions; same seed produces same sequence | VERIFIED | `ChaosEngineTest.same seed produces same fault sequence` asserts identical fault type + provider ID sequences for seed=42 |
| 12 | ChaosProviderInterceptor + inject-fault → list-diagnostics since= produces correlated snapshot (SC3) | FAILED | No test exists that integrates fault injection with DiagnosticSnapshotCapture. Coverage ends at Flow<T> behavior, not the diagnostics pipeline |

**Score:** 9/12 truths verified (10 verified, 2 partial/failed)

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `android/pack/themes/build.gradle.kts` | dqxn.pack plugin, no core deps | VERIFIED | Contains only `id("dqxn.pack")`; no serialization plugin needed (already in dqxn.pack) |
| `android/pack/themes/src/main/kotlin/.../ThemeFileParser.kt` | Pack-local JSON parser with `parseHexColor` | VERIFIED | Contains `parseHexColor`, `parseThemeJson`, `@Serializable` schema types |
| `android/pack/themes/src/main/kotlin/.../ThemesPackThemeProvider.kt` | ThemeProvider loading 22 gated themes | VERIFIED | Loads 22 themes via `THEME_FILES` list; all get `.copy(requiredAnyEntitlement = setOf("themes"))` |
| `android/pack/themes/src/main/kotlin/.../ThemesThemeModule.kt` | Hilt @Binds @IntoSet ThemeProvider | VERIFIED | `@Binds @IntoSet fun bindThemesThemeProvider(impl: ThemesPackThemeProvider): ThemeProvider` |
| `android/pack/themes/src/main/resources/themes/*.theme.json` (22 files) | 22 migrated theme JSON files | VERIFIED | Exactly 22 files confirmed present |
| `android/pack/themes/src/test/.../ThemesPackThemeProviderTest.kt` | Provider contract: 22 themes, gated, no duplicates | VERIFIED | 10 test methods covering count, gating, IDs, displayName, dark/light mix |
| `android/pack/themes/src/test/.../ThemeEntitlementGatingChainTest.kt` | F6.3/F6.4 gating chain proof | VERIFIED | 3 tests document and assert architectural gating invariant |
| `android/pack/themes/src/test/.../ThemeJsonValidationTest.kt` | Per-file parameterized structural validation | VERIFIED | 6 `@ParameterizedTest` methods (22 invocations each) + 2 standalone tests = 134 parameterized cases |
| `android/pack/essentials/snapshots/src/main/kotlin/.../SolarSnapshot.kt` | Cross-boundary snapshot type | VERIFIED | Present in snapshots sub-module; `@DashboardSnapshot` annotation present |
| `android/pack/essentials/snapshots/src/main/kotlin/.../SpeedLimitSnapshot.kt` | Cross-boundary snapshot type | VERIFIED | Present in snapshots sub-module; `@DashboardSnapshot` annotation present |
| `android/pack/demo/build.gradle.kts` | dqxn.pack + :pack:essentials:snapshots dep | VERIFIED | `implementation(project(":pack:essentials:snapshots"))` + `testImplementation(testFixtures(project(":sdk:contracts")))` |
| `android/pack/demo/src/main/kotlin/.../DemoTimeProvider.kt` | Deterministic time provider | VERIFIED | `ProviderPriority.SIMULATED`, tick-based fixed epoch, zero randomness |
| `android/pack/demo/src/main/kotlin/.../DemoSpeedProvider.kt` | Deterministic speed provider | VERIFIED | Triangle wave, `ProviderPriority.SIMULATED`, `DataProvider<SpeedSnapshot>` |
| `android/pack/demo/src/main/kotlin/.../DemoSolarProvider.kt` | Deterministic solar provider | VERIFIED | Fixed Singapore coordinates, `ProviderPriority.SIMULATED`, `DataProvider<SolarSnapshot>` |
| `android/pack/demo/src/main/kotlin/.../DemoSpeedLimitProvider.kt` | Deterministic speed limit provider | VERIFIED | Cycling 50/60/80/90/110 km/h, `ProviderPriority.SIMULATED`, `DataProvider<SpeedLimitSnapshot>` |
| `android/pack/demo/src/test/.../DemoDeterminismTest.kt` | Determinism verification all 8 providers | VERIFIED | 8 same-instance determinism tests + 3 known-value assertions = 11 tests |
| `android/core/agentic/src/main/kotlin/.../chaos/ChaosProviderInterceptor.kt` | DataProviderInterceptor with 7 fault types | VERIFIED | Implements `DataProviderInterceptor`; handles Kill, Delay, Error, ErrorOnNext, Corrupt, Flap, Stall |
| `android/core/agentic/src/main/kotlin/.../chaos/ChaosProfile.kt` | 7 chaos profiles + ScheduledFault | VERIFIED | `sealed interface ChaosProfile` with 7 data objects; `fromName()` companion lookup |
| `android/core/agentic/src/main/kotlin/.../chaos/ChaosSession.kt` | Session state tracking + injection log | VERIFIED | `recordInjection()` + `toSummary()` with `ChaosSessionSummary` |
| `android/core/agentic/src/main/kotlin/.../chaos/ChaosEngine.kt` | Seed-based deterministic fault orchestration | VERIFIED | `Random(seed)` in `start()`; delta-computed delays; single-session guard |
| `android/app/src/main/kotlin/app/dqxn/android/StubEntitlementManager.kt` | MutableStateFlow-backed simulation API | VERIFIED | `simulateRevocation`, `simulateGrant`, `reset` backed by `MutableStateFlow` |
| `android/app/src/debug/kotlin/.../handlers/ChaosStartHandler.kt` | @AgenticCommand chaos-start | VERIFIED | `@AgenticCommand(name = "chaos-start")`, calls `engine.start()`, returns session JSON |
| `android/app/src/debug/kotlin/.../handlers/ChaosStopHandler.kt` | @AgenticCommand chaos-stop | VERIFIED | `@AgenticCommand(name = "chaos-stop")`, calls `engine.stop()`, returns summary JSON |
| `android/app/src/debug/kotlin/.../handlers/ChaosInjectHandler.kt` | @AgenticCommand chaos-inject | VERIFIED | `@AgenticCommand(name = "chaos-inject")`, calls `interceptor.injectFault()` directly |
| `android/app/src/debug/kotlin/app/dqxn/android/debug/DebugModule.kt` | @Binds @IntoSet ChaosProviderInterceptor as DataProviderInterceptor | VERIFIED | `bindChaosInterceptor(interceptor: ChaosProviderInterceptor): DataProviderInterceptor` present |
| `android/app/build.gradle.kts` | :pack:themes and :pack:demo in dependency graph | VERIFIED | `implementation(project(":pack:themes"))` and `implementation(project(":pack:demo"))` both present |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| ThemesPackThemeProvider | ThemeFileParser | `parseThemeJson` call | WIRED | `parseThemeJson(json)?.copy(requiredAnyEntitlement = setOf("themes"))` in lazy initializer |
| ThemesThemeModule | ThemesPackThemeProvider | `@Binds @IntoSet ThemeProvider` | WIRED | `bindThemesThemeProvider(impl: ThemesPackThemeProvider): ThemeProvider` |
| DemoSpeedProvider | SpeedSnapshot | `DataProvider<SpeedSnapshot>` | WIRED | Class declaration `class DemoSpeedProvider : DataProvider<SpeedSnapshot>` |
| :pack:demo build.gradle.kts | :pack:essentials:snapshots | `implementation(project)` | WIRED | `implementation(project(":pack:essentials:snapshots"))` confirmed |
| DemoSolarProvider | SolarSnapshot | `DataProvider<SolarSnapshot>` | WIRED | Class declaration `class DemoSolarProvider : DataProvider<SolarSnapshot>` |
| ChaosProviderInterceptor | DataProviderInterceptor | implements interface | WIRED | `class ChaosProviderInterceptor @Inject constructor() : DataProviderInterceptor` |
| ChaosProviderInterceptor | ProviderFault | applies fault transformations | WIRED | `when (fault)` branches for all 7 ProviderFault subtypes |
| ChaosEngine | ChaosProviderInterceptor | `interceptor.injectFault()` | WIRED | `interceptor.injectFault(scheduledFault.providerId, scheduledFault.fault)` in launch coroutine |
| ChaosStartHandler | ChaosEngine | `engine.start()` | WIRED | `val session = engine.start(seed, profile, providerIds, scope)` |
| ChaosStopHandler | ChaosEngine | `engine.stop()` | WIRED | `val summary = engine.stop()` |
| ChaosInjectHandler | ChaosProviderInterceptor | `interceptor.injectFault()` | WIRED | `interceptor.injectFault(providerId, fault)` |
| StubEntitlementManager | entitlementChanges | MutableStateFlow emission | WIRED | `override val entitlementChanges: Flow<Set<String>> = _entitlements` backed by MutableStateFlow |
| :app build.gradle.kts | :pack:themes | `implementation(project)` | WIRED | `implementation(project(":pack:themes"))` |
| :app build.gradle.kts | :pack:demo | `implementation(project)` | WIRED | `implementation(project(":pack:demo"))` |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|---------|
| F6.1 | 09-01 | 22 premium themes | SATISFIED | 22 JSON files present, ThemesPackThemeProvider loads exactly 22, ThemeJsonValidationTest validates each |
| F6.2 | 09-01 | Theme Studio access (create/edit custom themes) | PARTIAL | Phase 9 delivers prerequisite: all 22 themes gated with `requiredAnyEntitlement = setOf("themes")`. Theme Studio UI is explicitly Phase 11 per research notes. ROADMAP maps F6.2 to Phase 9 requirements list but Theme Studio construction is not Phase 9 scope. |
| F6.3 | 09-01 | SOLAR_AUTO mode entitlement gating | SATISFIED | Gating chain documented and tested: themes have `requiredAnyEntitlement`, ThemeAutoSwitchEngine (Phase 5) only resolves between already-entitled selections |
| F6.4 | 09-01 | ILLUMINANCE_AUTO mode entitlement gating | SATISFIED | Same gating chain as F6.3; `ThemeEntitlementGatingChainTest.gating chain completeness` covers both modes |
| F8.5 | 09-04 | Debug "Simulate Free User" toggle | PARTIAL | `simulateRevocation/simulateGrant/reset` API implemented and tested (11 test methods in StubEntitlementManagerTest). Debug toggle UI is Phase 10/11 per research notes. Programmatic API fully functional. |
| F13.1 | 09-02, 09-03, 09-05 | Demo pack with simulated providers for all data types | SATISFIED | 8 deterministic providers covering all data types (Time, Speed, Orientation, Battery, AmbientLight, Acceleration, Solar, SpeedLimit); all ProviderPriority.SIMULATED; 96 contract assertions pass; DemoDeterminismTest proves reproducibility |

**Orphaned requirements check:** ROADMAP specifies `F6.1-F6.4, F8.5, F13.1` for Phase 9. All 6 IDs are claimed across plans. No orphaned requirements.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `android/core/agentic/src/main/kotlin/.../chaos/ChaosProfile.kt` | 81-88 | `ThermalRamp.generatePlan()` returns `emptyList()` — placeholder with no fault output | Warning | The test explicitly acknowledges this as a placeholder; `ChaosEngineTest` verifies empty plan is expected for `thermal-ramp`. Not a defect — intentional design. |
| `android/core/agentic/src/main/kotlin/.../chaos/ChaosProfile.kt` | 91-101 | `EntitlementChurn.generatePlan()` returns `emptyList()` — placeholder | Warning | Same as ThermalRamp — intentional, acknowledged in tests. |

**No blocker anti-patterns found.** Both placeholders are intentional and explicitly tested.

### Human Verification Required

None. All automated verification items are covered by unit tests. On-device chaos testing (ADB `chaos-start`, `chaos-inject`, `chaos-stop` commands) is a developer tool, not a requirement needing human verification for this phase.

---

## Gaps Summary

**SC3 is the only genuine unresolved gap.**

The ROADMAP SC3 states: "`ChaosProviderInterceptor` + `inject-fault` → `list-diagnostics since=` produces correlated snapshot." This requires a test that:
1. Injects a fault via `ChaosProviderInterceptor` or `chaos-inject` handler
2. Triggers a `DiagnosticSnapshotCapture` event (which the dashboard shell would normally do on anomaly detection)
3. Verifies the `list-diagnostics` output contains a snapshot timestamped after the fault injection

`ChaosProviderInterceptorTest` only covers steps up to fault-transformed flow output. There is no test that exercises the `DiagnosticSnapshotCapture → ListDiagnosticsHandler` pipeline in combination with fault injection. The `ListDiagnosticsHandler` and `CaptureSnapshotHandler` exist in debug handlers but are tested independently.

**F6.2 and F8.5 partial gaps are expected and scoped:**

The research notes for this phase explicitly state that Theme Studio UI (F6.2) is Phase 11 and the entitlement toggle UI (F8.5) is Phase 10/11. ROADMAP maps these requirements to Phase 9's requirements list because Phase 9 delivers the **prerequisite foundation** — the entitlement data infrastructure — which Phase 11 UI depends on. The Phase 9 scope is correctly delivered. These partials are architectural sequencing, not implementation gaps.

**sc-erp2 (SC5) not pursued:** ROADMAP SC5 is contingent on EXTOL SDK compatibility. No sg-erp2 pack work was scoped or attempted. This is a known deliberate skip.

---

_Verified: 2026-02-25T02:22:33Z_
_Verifier: Claude (gsd-verifier)_
