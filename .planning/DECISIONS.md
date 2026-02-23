# Architecture Decisions

Why-table for the project. Each entry records a rejected alternative and the reason.

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
| Why `collectAsState()` on Layer 0, not `collectAsStateWithLifecycle()`? | Lifecycle-aware collection pauses on background, causing a jank spike when all 12+ widgets resume simultaneously. Layer 0 is always-present; CPU reduction uses explicit `pauseAll()`/`resumeAll()` on `WidgetBindingCoordinator`. |
| Why `Channel<DashboardCommand>` for discrete commands, not `StateFlow`? | `StateFlow` conflates, losing intermediate commands. Discrete commands are transactional and ordered — every mutation must be processed. |
| Why `MutableStateFlow<DragUpdate>` for drag, not `Channel`? | Drag is continuous and latest-value-wins. A channel queues every pixel delta, consuming memory and processing stale positions. Conflation is the correct semantic for gesture coordinates. |
| Why 1:1 provider-to-snapshot, not composite snapshot types? | Composite types force a single provider to own independent sources with different availability/frequency/failure. When one sub-source is unavailable, the provider fabricates a zero (`0` = "not accelerating", not "unknown"). 1:1 makes each slot independently degradable. |
| Why `isEditMode` NOT saved to `SavedStateHandle`? | Restoring edit mode on process death risks stale edit state. 500ms debounced layout save means recent moves are persisted. Restoring into view mode is always safe. |
| Why theme preview state lost on process death? | Reverts to the committed theme — correct behavior. Persisting would commit an uncommitted selection. |
| Why `SharingStarted.Eagerly` for `ThemeAutoSwitchEngine`? | Eager ensures active theme is computed at cold start before first composition. `WhileSubscribed` delays until first subscriber, causing a flash. |
| Why crash counts in `SharedPreferences`, not DataStore? | Must be readable synchronously before DataStore initializes. DataStore requires coroutines — unavailable in `UncaughtExceptionHandler` or before `Application.onCreate()` scope exists. |
| Why `CrashEvidenceWriter` uses `commit()`, not `apply()`? | `apply()` is async — may not complete before process dies after uncaught exception. `commit()` guarantees the write survives process death. |
| Why separate diagnostic snapshot rotation pools (crash, thermal, performance)? | A single pool lets frequent thermal oscillation evict crash snapshots before the agent retrieves them. Separate pools ensure crash evidence is never displaced by thermal churn. |
| Why `AnrWatchdog` requires 2 consecutive missed pings, not 1? | A single miss can be caused by GC pauses, not a true ANR. Two consecutive misses (~5s block) indicates a genuine main-thread deadlock. |
| Why `RenderEffect.createBlurEffect()` for glow, not `BlurMaskFilter`? | `BlurMaskFilter` is CPU-based with offscreen buffer allocation. `RenderEffect` is a GPU shader, zero offscreen buffers. minSdk 31 guarantees availability. |
| Why `graphicsLayer` offset for drag, not `Modifier.offset`? | `Modifier.offset` triggers layout pass, invalidating measure/layout of surrounding widgets on every drag pixel. `graphicsLayer` is a pure GPU transform on the RenderNode. |
| Why pixel-shift (OLED burn-in) on `DashboardLayer` as a whole, not per-widget? | Per-widget shift invalidates every RenderNode every 5 minutes, causing full redraw. A single `graphicsLayer` translation on the outermost layer shifts all content with one GPU transform. |
| Why App Startup only for WorkManager + Firebase Crashlytics? | App Startup runs before `Application.onCreate()` — before Hilt's DI graph. All other components depend on Hilt injection and must init after `super.onCreate()`. |
| Why shader pre-warm during splash screen? | First-launch shader compilation (50–200ms per unique shader) causes jank on first `RenderEffect` use. Pre-warming moves this cost off the critical render path. |
| Why `sealed interface Route` with `@Serializable`, not string route keys? | String routes have no compile-time parameter validation. Type-safe routes make missing/wrong-type arguments a compilation error. |
| Why `PluginApiVersion` annotation is documentation-only? | All packs ship in the same APK — no runtime compatibility gap. Enforcement overhead unjustified without third-party packs. |
| Why data types are string identifiers, not a closed enum? | A closed enum would require packs to modify `:sdk:contracts` to add new data types. Strings let packs define types (`"sg-erp:toll-rate"`) without touching the SDK. |
| Why `DataProviderInterceptor` via Hilt multibinding, not `@VisibleForTesting`? | Production code modification for test injection creates permanent test coupling. Multibinding registers `ChaosProviderInterceptor` only in debug builds — zero release overhead. |
| Why `ProviderFault` in `:sdk:contracts` testFixtures? | Both `ChaosProviderInterceptor` (E2E chaos) and `TestDataProvider` (unit tests) need the same fault primitives. Sharing via testFixtures ensures identical flow transformation logic. |
| Why JUnit5 for unit tests, JUnit4 for Hilt integration? | `HiltAndroidRule` is JUnit4-only. JUnit5 used everywhere else for `@Tag` filtering, `@Nested`, and jqwik property-based testing. |
| Why `StandardTestDispatcher`, never `UnconfinedTestDispatcher`? | `UnconfinedTestDispatcher` executes eagerly and non-deterministically, hiding timing bugs. `StandardTestDispatcher` requires explicit `advanceTimeBy`/`advanceUntilIdle` for deterministic tests. |
| Why banner actions via `Channel<NotificationActionEvent>`, not callback lambdas? | Lambdas capture mutable state, making `Banner` non-serializable and coupling action logic to the composable. Channel dispatch keeps the type serializable. |
| Why `NotificationCoordinator` observes singleton flows, not receives emissions? | `@Singleton` subsystems cannot call `@ViewModelScoped` coordinator — Hilt scope violation. Coordinator observes flows it already needs for banner re-derivation on ViewModel recreation. |
| Why two-slot banner stacking (CRITICAL + non-critical)? | A persistent `dismissible = false` CRITICAL banner would permanently block all other banners. Two slots prevent starvation. |
| Why toasts use `collectAsStateWithLifecycle()` but banners use `collectAsState()`? | Toasts consumed while backgrounded would be silently auto-dismissed unseen. Lifecycle-aware pauses consumption until user returns. Banners are re-derived from persistent state on resume regardless. |
| Why setup confirmations use local `SnackbarHostState`, not `NotificationCoordinator`? | User-caused events (permission toggled, calibration done) belong to their screen. System-caused events (crash recovery, storage pressure) go through the coordinator. |
| Why `resizeableActivity="false"`? | Dashboard is fullscreen-only. Multi-window creates fractional viewports incompatible with grid layout and widget touch targets. |
| Why free-sizing window changes are NOT config changes? | Would trigger widget relocation, defeating no-automatic-relocation. Only declared display configs (fold × orientation) define viewport boundaries. |
| Why R8 rules per-module `consumer-proguard-rules.pro`, not centralized? | Centralized rules require all modules to know each other's retention needs. Per-module rules are co-located and auto-merged by AGP. |
| Why mutation testing deferred post-launch? | During greenfield development, code and tests are co-authored — high kill rate doesn't indicate test quality. Pitest adds build overhead without meaningful signal until codebase stabilizes. |
| Why `editingWidgetId` derived from full NavHost back-stack scan? | Child routes of `WidgetSettings` must preserve preview peek. Checking only `currentEntry` collapses preview on every child navigation. |
| Why `SetPreviewTheme` fired by caller before navigation, not by destination? | Firing on destination entry means navigation starts before preview theme applies, producing a flash of non-previewed theme during animation. |
| Why two independent preview cleanup mechanisms (DisposableEffect + LaunchedEffect)? | `LaunchedEffect(Unit)` runs once — NavHost keeps Settings in composition during child pushes. Quick dismissal before animation completes can leave `previewTheme` set. `DisposableEffect` in ThemeSelector covers this race. |
| Why `hasRequestedPermissions` local state in permission card? | Without it, pre-request state (`shouldShowRationale = false`, not granted) is indistinguishable from permanent denial. Prevents false "permanently denied" before user has ever been asked. |
| Why provider settings use immediate write-through, not debounced? | Setup flow writes one key at a time — no burst pattern. Layout saves are debounced because drag/resize produces rapid batches. |
| Why `BackHandler` uses two exclusive instances (page > 0 and page == 0)? | Two simultaneously-active handlers have non-deterministic precedence. Separate handlers with mutually exclusive conditions guarantee exactly one fires per back press. |
| Why no NDK at V1? | No first-party native code required. NDK adds build complexity, ABI split overhead, and a separate crash reporter. Add `firebase-crashlytics-ndk` post-launch if silent process deaths appear. |
| Why `FramePacer` uses `Window.setFrameRate()` (API 34+), not Choreographer frame skipping? | Choreographer-based skipping fights Compose's rendering pipeline. `Window.setFrameRate()` communicates intent to the system display pipeline, allowing OS to optimize holistically. |
| Why accumulation providers aggregate on `Dispatchers.Default`, not via UI collection? | A dropped UI collection causes distance drift — missed GPS samples subtract from accuracy. The accumulator integrates every sample at full fidelity; UI StateFlow is a conflated projection of already-accurate aggregates. |
| Why `DeduplicatingErrorReporter` uses 60-second window? | Without deduplication, a flapping provider generates hundreds of identical reports per minute, flooding crash reporting quotas. 60s window batches while preserving suppression count. |
| Why `JankDetector` fires at exponential thresholds (5, 20, 100 frames)? | Single threshold at 5 only captures onset. Escalating thresholds capture evolving state during extended jank, distinguishing brief spikes from systemic degradation. |
| Why `list-diagnostics` structured metadata, not raw `adb shell ls`? | Filename parsing is fragile with no metadata. Structured response includes trigger type, widget ID, and `recommendedCommand` for immediate follow-up. |
| Why agentic responses written to temp files, not returned in Bundle? | `Bundle.toString()` has quoting ambiguity. Binder transaction limit (~1MB) exceeded by full diagnostics dumps. File-based responses avoid both. |
| Why `AgenticContentProvider` uses `@EntryPoint` in `call()`, not `onCreate()`? | `ContentProvider.onCreate()` runs before `Application.onCreate()` — before Hilt's `SingletonComponent` exists. Deferring to `call()` ensures DI graph is ready. |
| Why `query()` paths in `AgenticContentProvider` are lock-free direct reads? | If a `CommandHandler` deadlocks or accidentally calls `Dispatchers.Main`, `call()` becomes unavailable. Lock-free `query()` reads from concurrent data structures, bypassing all coroutine machinery. |
| Why KSP processor for `@AgenticCommand` runs as `debugKsp` only? | Agentic framework is debug-only. Running in release generates dead code that `src/debug/` gating excludes — wasted compile time and potential R8 confusion. |
