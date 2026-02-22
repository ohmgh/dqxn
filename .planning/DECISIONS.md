# Architecture Decisions

Why-table extracted from CLAUDE.md. Each entry records a rejected alternative and the reason.

| Question | Answer |
|---|---|
| Why not a single DashboardState? | 60+ `.copy()` allocations/sec, universal recomposition. |
| Why not KAPT? | Breaks Gradle configuration cache. KSP is a hard requirement. |
| Why ImmutableList everywhere? | Compose treats regular `List` as unstable — forces recomposition. |
| Why no sliders in settings? | Conflict with `HorizontalPager` swipe gestures. Use discrete button groups. |
| Why Proto DataStore, not Room? | No queries needed. Proto is faster for document-style persistence with schema evolution. |
| Why no Timber? | Varargs allocation, no structured data, no trace correlation. `DqxnLogger` is zero-allocation when disabled. |
| Why `SupervisorJob` for bindings? | Without it, one provider crash propagates and kills all widget bindings. |
| Why generic `DataProvider<T>`? | Untyped `provideState(): Flow<DataSnapshot>` lets a provider declare `snapshotType = SpeedSnapshot::class` but emit `BatterySnapshot` — silent `null` from `as? T`. Generic bound makes the compiler enforce consistency. |
| Why `merge() + scan()`, not `combine()`? | `combine()` requires all upstreams to emit before producing any value. A stuck provider blocks all slots — contradicts multi-slot independent availability. `merge() + scan()` surfaces partial data immediately. |
| Why typed DataSnapshot, not Map? | `Map<String, Any?>` boxes primitives. 60 emissions/sec x 12 widgets = 720 garbage objects/sec. |
| Why non-sealed DataSnapshot? | Sealed forces all subtypes into `:sdk:contracts` — packs can't define snapshot types without modifying SDK. KSP `@DashboardSnapshot` gives compile-time validation without same-module restriction. |
| Why snapshot sub-modules, not promote to `:sdk:contracts`? | Promotion divorces types from producers, grows `:sdk:contracts` into a domain dumping ground, and recompiles all modules on every change. Sub-modules preserve producer ownership and limit blast radius. |
| Why `KClass` keys in `WidgetData`? | String keys allow typos, no compiler enforcement. `snapshot<SpeedSnapshot>()` can't reference a nonexistent type. |
| Why multi-slot `WidgetData`? | Speedometer consumes 3 independent providers. Single-slot loses independent availability and graceful degradation. |
| Why plain `Layout`, not `LazyLayout`? | Absolute-position grid. `LazyLayout` adds `SubcomposeLayout` overhead without benefit. |
| Why `callbackFlow` for sensors? | Ensures cleanup via `awaitClose`. Direct `SensorEventListener` leaks registrations. |
| Why `:core:firebase`, not in `:sdk:observability`? | Would make Firebase a transitive dependency of every module. |
| Why `ContentProvider`, not `BroadcastReceiver` for agentic? | BR runs on main thread. CP runs on binder thread — `runBlocking` is safe, no ANR risk. |
| Why not unify widget status + notifications? | Widget status is continuous state (`StateFlow`), notifications are discrete events. Folding both creates a god-object violating decomposed-state principle. |
| Why toasts through `NotificationCoordinator`, not `DashboardEffect`? | `DashboardEffect` is a raw `Channel` — no priority ordering, no rate limiting. Toasts need both. |
| Why `AlertSoundManager` separate from `NotificationCoordinator`? | Scope mismatch (`@Singleton` vs `@ViewModelScoped`), independent triggers, audio focus requires application-lifetime resources. |
| Why no pack `NotificationEmitter` at V1? | Every V1 notification is already widget state or shell-originated. Premature API commitment with no consumer. |
| Why no notification rules engine? | Only ~5 rules at launch. Coordinator observes state flows directly. |
| Why `AlertResult` return type? | Fire-and-forget gives no feedback on denial/unavailability. Changing contract post-V1 is painful — return type costs nothing now. |
| Why condition-keyed banner IDs? | Generated UUIDs cause dismiss+recreate flicker on state oscillation. Condition keys enable stable animation and targeted dismissal. |
| Why split CRITICAL banner to Layer 1.5? | `NotificationBannerHost` at Layer 0.5 is occluded by `OverlayNavHost` at Layer 1. CRITICAL banners need separate host after overlay. |
| Why `Channel.BUFFERED` for toasts? | Rendezvous channel suspends producer when consumer isn't collecting. Buffered prevents silent suspension. |
| Why defer driving mode? | DQXN is general-purpose. Driving mode is a pack-provided feature, not a shell concern. |
| Why unbounded canvas? | Viewport-bounded means widgets placed on tablet silently disappear on phone with no recovery. Unbounded preserves all positions — smaller viewports render a subset. |
| Why no-straddle snap? | Partially visible widgets are visual corruption. Binary visibility: fully rendered or not at all. |
| Why no automatic relocation? | Proportional anchoring degrades ungracefully. Accepting hidden widgets + edit mode rearrangement is simpler and predictable. |
| Why per-profile dashboards, not per-widget visibility? | Visibility filter creates sparse layouts, requires duplicate widgets, cross-fade feels like nothing happened. Per-profile = independent pages, correct swipe metaphor, launcher-ready. |
| Why horizontal swipe for profiles? | Muscle memory — Android home screen page swiping. Per-profile dashboards are actual pages. |
| Why bottom bar with profile icons, not page dots? | Icons give one-tap direct access. Page dots require sequential swiping. |
| Why bottom bar, not FABs? | Bottom bar composes cleanly: settings + profiles + add-widget. FABs scatter and conflict with widget tap targets. |
| Why profiles from packs? | Shell can't know all contexts. Same discovery pattern as widgets/providers. |
| Why no screenshot-matching tests? | Custom Canvas drawing unreliable under Robolectric's Canvas shadow. Replaced by semantics-based verification + draw-math unit tests. |
| Why semantics tree, not UiAutomator? | UiAutomator is slow (~500ms), lossy for Compose. `SemanticsOwner` is in-process, <5ms. |
| Why `SemanticsOwnerHolder` singleton? | `AgenticContentProvider` runs on binder threads, needs cross-thread reference to `SemanticsOwner`. Debug-only. |
| Why always set test tags? | ~50 nodes, negligible cost. Enables Espresso + accessibility tooling in release instrumented tests. |
