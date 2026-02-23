# Phase 9: Themes, Demo + Chaos

## `:pack:themes`

- 22 premium theme JSON files (port verbatim)
- `ThemeProvider` implementation
- Entitlement gating (`themes` entitlement)

## `:pack:demo`

- Deterministic stub providers for every snapshot type in `:pack:essentials:snapshots` — each emits realistic fake data on a fixed timeline (no randomness, reproducible screenshots)
- Provider list mirrors essentials: `DemoTimeProvider`, `DemoSpeedProvider`, `DemoOrientationProvider`, `DemoSolarProvider`, `DemoAmbientLightProvider`, `DemoBatteryProvider`, `DemoAccelerationProvider`, `DemoSpeedLimitProvider`
- `ProviderPriority.SIMULATED` — always loses to real providers when both are available
- Used for: Play Store screenshots, development without hardware, CI E2E tests (deterministic data = deterministic assertions), demo mode for retail displays
- No widgets — demo pack provides data only, rendered by essentials widgets

## `:pack:sg-erp2` (contingent on EXTOL SDK compatibility — validated in Phase 1)

- `ObuConnectionManager` wrapping EXTOL SDK
- `ObuConnectionStateMachine` (port — already uses `ConnectionStateMachine` base from Phase 2)
- 8 providers → typed snapshots in `:pack:sg-erp2:snapshots` (cross-boundary: `ErpBalanceSnapshot`, `ErpTransactionSnapshot`, etc.)
- 4 widgets → new contracts
- `CompanionDeviceHandler` — CDM association for BLE OBU pairing
- **SetupSheet dependency resolved:** BLE device pairing UI (`DeviceScanCard`, `PairedDeviceCard`, etc.) is delivered in Phase 10 (Settings Foundation + Setup UI). Phase 10 is now a predecessor of Phase 9 — no stub needed. The `SetupDefinition` schema (Phase 2) and `SetupEvaluator` (Phase 2) provide the contracts; Phase 10 provides the visual overlay

## Chaos infrastructure

- `ChaosProviderInterceptor` in `:core:agentic` (debug only) — implements `DataProviderInterceptor`, applies `ProviderFault`. **Note:** `ProviderFault` must live in `:sdk:contracts` main source set (not testFixtures) — `ChaosProviderInterceptor` needs it at debug runtime, not just test time. `TestDataProvider` remains in testFixtures
- Extend `StubEntitlementManager` (Phase 6) with `simulateRevocation()` / `simulateGrant()` for chaos testing
- Chaos profiles: `provider-stress`, `provider-flap`, `thermal-ramp`, `entitlement-churn`, `widget-storm`, `process-death`, `combined`
- `ChaosEngine` with seed-based deterministic reproduction (`seed: Long`)
- `inject-fault` handler wired to `ChaosProviderInterceptor`
- `chaos-stop` session summary with `injected_faults` + `system_responses` + `resultingSnapshots`
- Chaos ↔ diagnostic temporal correlation via `list-diagnostics since=<timestamp>`

## `AgenticTestClient`

- Programmatic wrapper around `adb shell content call` for instrumented tests
- `assertChaosCorrelation()` — validates every injected fault produced an expected downstream diagnostic snapshot
- Used in CI chaos gate (deterministic `seed = 42`)

**Tests:** Contract tests for all pack widgets/providers. Connection state machine exhaustive tests. Chaos E2E: `inject-fault` → `list-diagnostics since=` → verify correlated snapshot. `assertChaosCorrelation()` integration test. Seed determinism: same seed → same fault sequence → same diagnostics.
