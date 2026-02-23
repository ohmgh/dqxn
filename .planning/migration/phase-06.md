# Phase 6: Deployable App + Agentic Framework

**What:** First deployable APK with the agentic debug framework. Every subsequent phase can deploy to a device and use structured `adb shell content call` queries for autonomous debugging. This is debugging infrastructure — it lands before the code it will debug.

**Depends on:** Phases 3, 4, 5 (Phase 3 direct: `:app` imports `:sdk:observability`, `:sdk:analytics`, `:sdk:ui`)

## `:app` (minimal shell)

- `MainActivity` — single activity, `enableEdgeToEdge()`, `WindowInsetsControllerCompat`
- `DqxnApplication` — Hilt application
- `AppModule` — DI assembly with:
  - Empty `Set<WidgetRenderer>` (packs not yet migrated)
  - Empty `Set<DataProvider<*>>`
  - Empty `Set<ThemeProvider>`
  - Empty `Set<DataProviderInterceptor>` (Phase 2 interface, chaos adds to it in Phase 9)
  - Empty `Set<DashboardPackManifest>`
  - `AlertSoundManager : AlertEmitter` — `@Singleton` with `SoundPool`, `AudioManager`, `Vibrator`. Phase 2 defines the `AlertEmitter` interface; Phase 6 provides the implementation. Required by `NotificationCoordinator` in Phase 7
  - `CrashRecovery` — `@Singleton`, synchronous `SharedPreferences` read in `Application.onCreate()` before DataStore. Tracks crash timestamps for safe mode trigger (≥4 crashes in 60s). Phase 7 wires safe mode response into coordinators/UI
  - `StubEntitlementManager : EntitlementManager` — returns `free` only. Phase 10 replaces with Play Billing implementation
- Blank dashboard canvas (placeholder composable — real dashboard lands in Phase 7)
- AndroidManifest: `resizeableActivity="false"` (F1.23), `android:permission="android.permission.BLUETOOTH_SCAN" android:usesPermissionFlags="neverForLocation"` (NF23)
- ProGuard/R8 rules — `:app` aggregates `consumer-proguard-rules.pro` from `:data` (proto classes), `:codegen:plugin` (KSP-generated manifests), `:sdk:contracts` (serializable classes). Phase 6 validates via `assembleRelease` + install

**Asset migration checklist:**

- [ ] Adaptive launcher icon foreground: **`ic_launcher_runner_foreground.xml`** (the current DQXN runner/wordmark in cyan — NOT `ic_launcher_foreground.xml` which is the old ring+arc design) → `app/src/main/res/drawable/`
- [ ] Adaptive launcher icon background: `ic_launcher_background.xml` (solid `#0f172a`) → `app/src/main/res/drawable/`
- [ ] Monochrome launcher icon (API 33+): `ic_launcher_monochrome.xml` → `app/src/main/res/drawable/` — **Note: old monochrome uses the ring+arc geometry, not the runner. Verify desired monochrome design before porting**
- [ ] Adaptive icon manifests: `ic_launcher.xml`, `ic_launcher_round.xml` → `app/src/main/res/mipmap-anydpi-v26/` (these already reference `runner_foreground` in old codebase)
- [ ] Vector logo: `ic_logo_letterform.xml` (app version with cyan secondary) → `app/src/main/res/drawable/`
- [ ] Logo asset: `dqxn_logo_cyber_dog.webp` (700KB) → decide: `:app` or `:core:design` res (needed by onboarding in Phase 11)
- [ ] Default layout preset: `preset_demo_default.json` → `app/src/main/assets/presets/` — **update all typeIds from `free:*` to `essentials:*` prefix**

## `:core:agentic`

- `@AgenticCommand` annotation — `@Retention(SOURCE)`, defined here for `:codegen:agentic` (Phase 4) to process. Annotation schema: `name: String`, `description: String`, `params: Array<AgenticParam>`
- `AgenticEngine` — command dispatch (migrate from old, adapt to coordinator APIs as they land)
- **Handler placement:** Engine/dispatch infrastructure lives in `:core:agentic`. Handlers that need shell dependencies (`:data`, `:core:thermal`, coordinators) are defined in `:app:src/debug/` where they can access all modules via Hilt. Handler interfaces/annotations stay in `:core:agentic`
- `AgenticContentProvider` — ContentProvider transport on binder thread:
  - `call()` with `runBlocking(Dispatchers.Default)` + `withTimeout(8_000)`
  - `query()` lock-free read paths (`/health`, `/anr`) — deadlock-safe escape hatches when main thread is blocked
  - `@EntryPoint` + Hilt cold-start race handling with retry-after-ping contract
  - Response file protocol (file path in Bundle, not inline JSON)
  - `onCreate()` cleanup of previous session response files
  - 8-second timeout with error envelope semantics
  - `Binder.getCallingUid()` security validation
- Agentic trace ID generation and propagation into `DashboardCommand` (wired when coordinators land in Phase 7)
- `SemanticsOwnerHolder` — debug-only `@Singleton` for Compose semantics tree access. Registered by `DashboardLayer` via `RootForTest.semanticsOwner`. Enables `dump-semantics`/`query-semantics` commands for pixel-accurate UI verification

**Starter diagnostic handlers (read-only — query state, don't mutate it):**

| Handler | Queries | Available from |
|---|---|---|
| `dump-health` | `WidgetHealthMonitor` | Phase 6 (empty initially, populates as widgets land) |
| `diagnose-crash` | `CrashEvidenceWriter` SharedPrefs, `DiagnosticSnapshotCapture` files | Phase 6 |
| `diagnose-performance` | `MetricsCollector` snapshot | Phase 6 |
| `list-diagnostics` | Diagnostic snapshot files with metadata, stale file filtering | Phase 6 |
| `get-metrics` | `MetricsCollector` frame histogram + per-widget draw times | Phase 6 |
| `dump-layout` | `:data` layout repository | Phase 6 |
| `list-widgets` | `Set<WidgetRenderer>` from Hilt | Phase 6 (empty until Phase 8) |
| `list-providers` | `Set<DataProvider<*>>` from Hilt | Phase 6 (empty until Phase 8) |
| `list-themes` | `Set<ThemeProvider>` (via Hilt multibinding) | Phase 6 |
| `list-commands` | KSP-generated schema from `:codegen:agentic` | Phase 6 |
| `dump-semantics` | `SemanticsOwnerHolder` — full Compose semantics tree (bounds, test tags, text, actions) | Phase 6 (empty until DashboardLayer registers in Phase 7) |
| `query-semantics` | `SemanticsOwnerHolder` — filtered semantics query by test tag, text, bounds | Phase 6 (empty until Phase 7) |
| `trigger-anomaly` | Fires `DiagnosticSnapshotCapture` for pipeline self-testing | Phase 6 |

**Mutation handlers (land incrementally as their targets are built):**

| Handler | Target | Available from |
|---|---|---|
| `add-widget`, `remove-widget`, `move-widget`, `resize-widget` | `LayoutCoordinator` | Phase 7 |
| `set-theme` | `ThemeCoordinator` | Phase 7 |
| `set-data-source` | `WidgetBindingCoordinator` | Phase 7 |
| `set-setting` | Per-widget settings | Phase 7 |
| `get-layout` | `LayoutCoordinator` | Phase 7 |
| `get-widget-status` | `WidgetBindingCoordinator` | Phase 7 |
| `get-entitlements` | `EntitlementManager` | Phase 7 |
| `reset-layout` | `LayoutCoordinator` | Phase 7 |
| `import-preset` | `:data` preset system | Phase 7 |
| `inject-fault` | `ChaosProviderInterceptor` | Phase 9 |
| `capture-snapshot` | `DiagnosticSnapshotCapture` | Phase 6 |

**Debug overlays (`:app:src/debug/`):**

- Frame Stats overlay
- Widget Health overlay
- Thermal Trending overlay
- Recomposition Visualization overlay (F13.6)
- Provider Data Flow DAG overlay (F13.6)
- State Machine Viewer overlay (F13.6)

**Ported from old:** `AgenticEngine`, `CommandDispatcher`, handler structure (adapt from BroadcastReceiver to ContentProvider transport). `AgenticReceiver` deleted — replaced by `AgenticContentProvider`. Old handlers adapted to new coordinator APIs incrementally. Debug overlays ported with UI adaptation.

**Tests:**
- `AgenticContentProvider`: cold-start race (ping before Hilt ready → retry), timeout (8s exceeded → error envelope), concurrent calls, `query()` lock-free path works when `call()` would block
- `AgenticEngine`: command routing, unknown command error, trace ID propagation
- Handler tests for all starter diagnostic handlers against faked observability state
- `SemanticsOwnerHolder`: registration/deregistration, `snapshot()` returns tree when owner set, `query()` filter matching
- `dump-semantics`/`query-semantics` handlers: empty response when no owner registered, tree serialization correctness
- E2E: `adb shell content call` round-trip on connected device
- App startup: blank canvas renders without crash

**Validation:** `./gradlew :app:installDebug` succeeds. `adb shell content call --uri content://app.dqxn.android.debug.agentic --method list-commands` returns handler schema. `trigger-anomaly` creates a diagnostic snapshot file. `diagnose-crash` returns crash evidence (or empty state). `dump-semantics` returns a tree with at least the blank canvas root node. Debug overlays toggle on/off. `./gradlew assembleRelease` succeeds and R8-processed APK installs without `ClassNotFoundException` — validates ProGuard rules don't strip KSP-generated classes or proto-generated code.

**CI pipeline deliverable:** Configure CI (GitHub Actions or equivalent) running `./gradlew assembleDebug test lintDebug --console=plain`. From this phase forward, every phase must pass all prior-phase tests before merging. This is the regression gate — not just "current phase tests pass" but "nothing broke."

## Autonomous debug bootstrapping

From this point forward, the agent can autonomously debug on a connected device:

1. **Deploy:** `./gradlew :app:installDebug`
2. **Detect:** `adb logcat` for crashes/ANRs, `diagnose-crash` for structured crash evidence, `list-diagnostics since=<timestamp>` for anomaly snapshots
3. **Investigate:** `dump-health` for widget liveness, `get-metrics` for frame timing, `diagnose-performance` for jank analysis, `query-semantics` for visual verification (is the widget actually rendered? correct text? visible?)
4. **Fix:** Edit source code
5. **Verify:** Rebuild, redeploy, re-query — confirm anomaly resolved
6. **Fallback:** If main thread is deadlocked and `call()` stalls, `query()` paths (`/health`, `/anr`) still work. If binder pool is exhausted, `adb pull` the `anr_latest.json` written by `AnrWatchdog`'s dedicated thread
