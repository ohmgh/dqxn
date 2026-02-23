# Phase 3: SDK Observability + Analytics + UI

**What:** Remaining SDK modules that packs and features import. Observability is the foundation for all autonomous debugging — it must be solid before the agentic framework wires to it in Phase 6.

## `:sdk:observability` (Android library — uses `SharedPreferences`, `StatFs`, `PowerManager`; no Compose compiler)

- `ErrorContext` sealed hierarchy — `Coordinator(command)`, `Widget(typeId, widgetId)`, `Provider(sourceId)`, `System(component)`. Used by `ErrorReporter.reportNonFatal(e, context: ErrorContext)`. `WidgetErrorContext` subtype of `ErrorContext.Widget` adds crash count and stack trace summary
- `CrashReporter` interface: `log(message)`, `logException(e)`, `setKey(key, value)`, `setUserId(id)`. Implementation in `:core:firebase` (Phase 5)
- `ErrorReporter` interface: `reportNonFatal(e, context: ErrorContext)`, `reportWidgetCrash(typeId, widgetId, context: WidgetErrorContext)`
- `DqxnLogger` with inline extensions (migrate from `core:common`, adapt to zero-allocation when disabled)
- `JsonLinesLogSink` — machine-readable JSON-lines file log sink (F13.7). Rotated 10MB, max 3 files. Debug builds only. Wired as a `DqxnLogger` sink
- `LogTag` as `@JvmInline value class`
- `DqxnTracer` — structured tracing with span IDs, `agenticTraceId` field for causal correlation
- `MetricsCollector` — `AtomicLongArray(6)` frame histogram, `ConcurrentHashMap` + `LongArrayRingBuffer(64)` per-widget draw time and per-provider latency. Pre-populated from `WidgetRegistry` and `DataProviderRegistry` (Phase 2 interfaces) at construction. Phase 3 unit tests use fake registries; real DI binding in Phase 6/7. Performance budget: record < 25ns (`System.nanoTime()` + ring buffer write)
- `JankDetector` — wired between `FrameMetrics` callbacks and `MetricsCollector`. Fires `DiagnosticSnapshotCapture` at exponential thresholds (5, 20, 100 consecutive janky frames). Distinct class, not folded into MetricsCollector
- `DiagnosticSnapshotCapture` — anomaly auto-capture with `AtomicBoolean` capture guard (drops concurrent captures). `AnomalyTrigger` sealed hierarchy: `WidgetCrash`, `AnrDetected`, `ThermalEscalation`, `JankSpike`, `ProviderTimeout`, `EscalatedStaleness`, `BindingStalled`, `DataStoreCorruption`. Three separate rotation pools (crash: 20, thermal: 10, perf: 10) — prevents thermal churn evicting crash snapshots. `StatFs` storage pressure check (skip if <10MB free). `capture()` accepts `agenticTraceId`. Release build: only trigger type + timestamp forwarded to `CrashReporter`, no full dump
- `CrashEvidenceWriter` — sync `SharedPreferences.commit()` in `UncaughtExceptionHandler`. Captures: `last_crash_type_id`, `last_crash_exception`, `last_crash_stack_top5`, `last_crash_timestamp`. `extractWidgetTypeId()` to pull widget type from exception chain. Fallback for `diagnose-crash` when no snapshot file exists
- `AnrWatchdog` — dedicated daemon thread with `CountDownLatch`, 2.5s timeout, 2-consecutive-miss trigger. `Thread.getAllStackTraces()` + fdCount in ANR file. `writeAnrFile()` via direct `FileOutputStream` on watchdog thread (no `Dispatchers.IO` — process may die before coroutine dispatches). `Debug.isDebuggerConnected()` guard. Exposed via lock-free `query()` path in `AgenticContentProvider` (`/anr`)
- `WidgetHealthMonitor` — periodic liveness checks (10s), stale data detection (last data timestamp > staleness threshold), stalled render detection (last draw timestamp > 2x target frame interval). Reports to `CrashContextProvider`. Exposed via lock-free `query()` path (`/health`) with `cachedHealthMonitor` pattern
- `ProviderStatusProvider` interface — `fun providerStatuses(): Flow<Map<String, ProviderStatus>>`. Implemented by `WidgetBindingCoordinator` (Phase 7). Consumed by `:feature:diagnostics` (Phase 11) without `:feature:diagnostics` → `:feature:dashboard` dependency

## `:sdk:analytics`

- Event tracking contracts (new — old codebase has none)
- Domain-free API surface
- Funnel event contracts (F12.2): `install`, `onboarding_complete`, `first_widget_added`, `first_customization` (theme change or widget settings edit)
- Engagement metric contracts (F12.3): `session_duration`, `widgets_per_layout`, `edit_frequency` (edit mode entries per session)
- Upsell event contracts (F12.6): impression/conversion events with `trigger_source` parameter (`theme_preview`, `widget_picker`, `settings`)
- Session quality metric contracts (F12.7): session end event carrying `jank_percent`, `peak_thermal_level`, `widget_render_failures`, `provider_errors`. Note: `:sdk:analytics` defines **contract interfaces only** (event data classes, `AnalyticsTracker` interface). No module dependency on `:sdk:observability` — the caller (`:feature:dashboard` or `:app`) reads from `MetricsCollector`/`WidgetHealthMonitor` and populates the event fields. `:core:firebase` implements `AnalyticsTracker` and forwards to Firebase

## `:sdk:ui` (has Compose compiler — applies `dqxn.android.compose`)

- `LocalWidgetData` CompositionLocal
- `LocalWidgetScope` CompositionLocal (supervised `WidgetCoroutineScope`)
- `DashboardThemeDefinition` — full runtime type extending `ThemeSpec` with `Brush`, `Color`, `TextStyle`. Must live here (not `:core:design`) because packs implement `ThemeProvider` returning `DashboardThemeDefinition`, and packs can only depend on `:sdk:*`
- `WidgetContainer` skeleton (migrate from `core:widget-primitives` — responsive sizing, border overlay, rim padding). Glow rendering (`RenderEffect.createBlurEffect()` rewrite) added in Phase 7
- Icon name resolution utility — maps `iconName: String` (from Phase 2 `SetupDefinition`) to `ImageVector`. Material icon lookup
- `GRID_UNIT_SIZE = 16.dp` constant
- `InfoCardLayout` — port from `core:widget-primitives` (536 lines). Deterministic weighted normalization for STACK/COMPACT/GRID modes, `getTightTextStyle` (font padding elimination). 5+ widgets in Phase 8 depend on this — deferring to Phase 8 creates a two-concern pile-up. Port now with tests: `SizeOption.toMultiplier()` mapping, normalization calc per layout mode — wrong weights cause text clipping
- `EnumSetting.optionPreviews` replacement — registry or extension function pattern for packs to register preview composables for `EnumSetting<E>` options. Old code had `(@Composable (E) -> Unit)?` lambda on `EnumSetting` which was stripped in Phase 2. Packs need a way to provide option previews
- **Deferred from Phase 3 to first consumer:** setup overlay composables (`SetupRequiredOverlay`, `DisconnectedOverlay` — Phase 10), `PermissionRequestPage` (Phase 10)

**Ported from old:** `core:widget-primitives/*` → `sdk:ui` (WidgetContainer, InfoCardLayout, SetupOverlays, LocalWidgetScale, LocalWidgetPreviewUnits). Analytics is entirely new. **Observability is entirely new** — exhaustive search of old codebase found zero observability code (no logger, no metrics, no crash evidence, no ANR watchdog, no diagnostic snapshots). Old code uses raw `android.util.Log` directly. The entire `:sdk:observability` module is greenfield.

**Tests:**
- `DqxnLogger`: disabled-path zero-allocation test, tag filtering test
- `MetricsCollector`: ring buffer overflow, histogram bucket boundaries, concurrent write correctness
- `JankDetector`: exponential threshold firing (5th, 20th, 100th frame triggers capture; 4th, 19th, 99th do not)
- `CrashEvidenceWriter`: simulated uncaught exception → verify `prefs.getString(...)` persisted. `extractWidgetTypeId()` from nested exception chain
- `AnrWatchdog`: single miss → no capture; two consecutive misses → capture + file written; debugger attached → no capture
- `DiagnosticSnapshotCapture`: concurrent capture guard (second capture dropped), rotation pool eviction (thermal doesn't evict crash), storage pressure skip
- `WidgetHealthMonitor`: stale vs stalled distinction, liveness check period
- `WidgetContainer`: composition tests
- `InfoCardLayout`: `SizeOption.toMultiplier()` mapping tests, normalization calculation per layout mode (STACK/COMPACT/GRID), `getTightTextStyle()` output verification
