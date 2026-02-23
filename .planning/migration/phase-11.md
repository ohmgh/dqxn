# Phase 11: Theme UI + Diagnostics + Onboarding

**What:** Three independent feature clusters built on the settings row system from Phase 10: theme editing/selection UI, diagnostics module, and onboarding flow. These clusters share no dependencies and can be developed in any order or concurrently.

**Depends on:** Phase 10 (settings row system, overlay scaffold, OverlayNavHost infrastructure)

**Composable count:** ~26. **Port volume:** ~2,200 lines from old codebase + ~2,000 lines new.

## Theme editing UI (`:feature:settings`)

**`ThemeSelector`** — 2-page horizontal pager (built-in, custom). Free themes first, then custom, then premium (gated). Theme preview + apply/revert lifecycle (preview times out after 60s with toast). Long-press built-in → clone to custom. Long-press custom → open in Theme Studio. Old `ThemeSelectorContent.kt` (399 lines, 5 internal composables).

**`ThemeStudio`** — custom theme CRUD (max 12). Old `ThemeStudioContent.kt` (672 lines, 5+ composables). 8 mutable color/gradient state vars, `isDirty` via `derivedStateOf`, auto-save `LaunchedEffect`. Key risk: tightly coupled state — decompose into `ThemeStudioStateHolder` class.

**`InlineColorPicker`** — HSL sliders + hex editor. Old 413 lines, 2 internal composables. 4 mutable float states (H/S/L/A), bidirectional HSL-to-RGB sync, keyboard focus management. Extract `colorToHsl`/`colorToHex`/`parseHexToColor` to testable utility — these are the most valuable test targets.

**`GradientTypeSelector`** — 5 gradient types via `FilterChip`. Old 80 lines. Trivial.

**`GradientStopRow`** — 2-5 stop gradient editor. Old 145 lines. Stop add/remove boundary logic.

**`ThemeSwatchRow`** — 7-value `SwatchType` enum selector. Old 204 lines.

**`AutoSwitchModeContent`** — 5 auto-switch modes with premium gating. Old 205 lines.

**`IlluminanceThresholdControl`** — custom Canvas logarithmic lux meter (~283 lines) with tap+drag gesture. Port the drawing math directly — do not rebuild. Extract `luxToPosition`/`positionToLux` logarithmic mapping to testable utility.

**Entitlement stub UI:** `StubEntitlementManager` from Phase 6 remains. Premium themes in `:pack:themes` render but `Gated.requiredEntitlements` respected by `ThemeSelector` UI (lock icon, purchase button disabled with "Coming soon"). Play Billing integration deferred post-launch.

## `:feature:diagnostics` (new module)

Entirely new code — no old codebase equivalent. Debug builds only.

- **Provider Health dashboard (F3.13):** list all registered providers with connection state, last emission time, error count, staleness indicator. Data from `DataProviderRegistry.getAll()` + `ProviderStatusProvider` interface (defined in `:sdk:observability`, implemented by `WidgetBindingCoordinator` in `:feature:dashboard` — avoids `:feature:diagnostics` → `:feature:dashboard` dependency). Tap provider → detail view with connection log (F7.6, rolling 50 events from `ConnectionEventStore`), retry button
- **Diagnostic snapshot viewer:** browse snapshots from `DiagnosticSnapshotCapture`, sorted by timestamp, filterable by type (crash, ANR, anomaly, chaos)
- **Session recording capture (F13.3):** tap, move, resize, navigation events logged with timestamps. `SessionRecorder` service class captures events to a ring buffer (max 10,000 events, ~500KB). Replay viewer shows scrollable timeline with event markers. Recording toggle in diagnostics — not always-on. **Highest-risk novel component** — no old code to reference. Keep scope minimal for V1: event capture + text-based timeline. Graphical replay viewer is stretch
- **Observability data display:** metrics dashboard (frame times from `MetricsCollector`, recomposition counts, memory), log viewer with level filtering from `DqxnLogger` JSON-lines file

## `:feature:onboarding` (new module)

New code — old codebase had no onboarding flow.

- **Analytics consent dialog (F12.5):** Shown before first-run flow if analytics not yet opted-in. Explains data collected, purpose, right to revoke. Opt-IN required per PDPA/GDPR. Consent persisted in `UserPreferencesRepository`. Must gate ALL analytics collection — no Firebase events before consent
- **First-launch disclaimer (NF-D3):** Brief, dismissable notice that the app supplements — does not replace — the vehicle dashboard. Shown once. Applies only when app is being used in-vehicle context
- **First-run flow (F11.1):** Theme selection (free themes only — reuses simplified `ThemeSelector` subset), permission requests (lazy — only if user adds GPS-dependent widget), quick tour of edit mode (tap-and-hold explainer)
- **Progressive tips (F11.1):** Contextual hints on first encounter: (a) first launch → "Tap Edit to customize", (b) first edit mode → "Tap a widget to select it. Drag to move.", (c) first widget focus → "Use corners to resize. Tap the gear for settings.", (d) first widget settings → dot indicators with page labels. Tracked via `UserPreferencesRepository` flags — shown once per tip
- **Permission rationale (F11.6/F11.7):** Explain why each permission is needed before requesting. Location → GPS speed. Bluetooth → ERP device pairing. Denied permission → widget shows "Setup Required" overlay with tap-to-setup. Permanently denied → link to system settings
- **Default preset validation (F11.5):** First launch layout is clock + battery + date only — no GPS-dependent widgets until location permission granted

## OverlayNavHost completion

Remaining routes added to `OverlayNavHost`:

| Route | Destination | Coordinator interaction |
|---|---|---|
| `ThemeSelector` | Theme browser + preview | `ThemeCoordinator.preview()/apply()/revert()` |
| `Diagnostics` | Provider health + snapshots | Read-only from observability |
| `Onboarding` | First-run flow | Theme selection → `ThemeCoordinator` |

All 7 routes now populated. Integration test: navigate to each route, verify content renders, back navigation returns to dashboard.

## Analytics event call sites (F12.2, F12.3, F12.6, F12.7)

Wire `AnalyticsTracker` (contract from Phase 3, Firebase impl from Phase 5) at overlay interaction points:
- F12.2: `widget_add`, `theme_change`, `upsell_impression`, `purchase_start` (no-op with stub)
- F12.3: `session_start`, `session_end` (with widget count, edit frequency)
- F12.6: upsell events include `trigger_source` parameter (`theme_preview`, `widget_picker`, `settings`)
- F12.7: session end includes `jank_percent`, `peak_thermal_level`, `widget_render_failures`, `provider_errors`

All events gated on analytics consent (F12.5). No events fire if user has not opted in.

**Tests:**
- `InlineColorPicker` color conversion tests — `colorToHsl`/`colorToHex`/`parseHexToColor` with known values (black, white, pure RGB, achromatic grays, hue boundary 0/120/240/360). Round-trip accuracy: `color → hsl → color` within ±1/255 per channel
- `IlluminanceThresholdControl`: `luxToPosition`/`positionToLux` logarithmic mapping inverses, boundary values (0 lux, 10000 lux), dashed line geometry
- `GradientStopRow`: min 2 stops (remove button disabled at 2), max 5 stops (add button disabled at 5), stop position clamped to 0.0-1.0
- `ThemeSelector`: free-first ordering, preview timeout (60s → auto-revert), clone-to-custom, entitlement lock icons
- `ThemeStudio`: `isDirty` derivation, auto-save trigger, `buildCustomTheme()` output validation, max-12 custom theme limit
- Provider Health dashboard: renders provider list, staleness indicator updates, retry triggers provider reconnect
- Session recording: event capture ring buffer overflow, timeline rendering with event markers
- Onboarding: analytics consent dialog blocks analytics, first-run tip sequence, permission rationale display
- Default preset validation (F11.5): first-launch layout contains only clock + battery + date widgets — no GPS-dependent widgets present. Verified via `PresetLoader` unit test returning the default preset and asserting widget typeIds
- Analytics event call sites: events fire after opt-in, suppressed before consent. Parameter completeness: `upsell_impression` includes `trigger_source` (F12.6), `session_end` includes `jank_percent` + `peak_thermal_level` + `widget_render_failures` + `provider_errors` (F12.7), `widget_add` includes `typeId` + `packId`
- Overlay navigation completion: all 7 routes render, back navigation correct, theme preview overlay correctly blocks other navigation during active preview
- On-device: full overlay interaction flow via manual testing and semantics queries where gesture simulation isn't feasible
