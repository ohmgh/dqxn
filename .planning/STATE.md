# Project State

## Current Position

- **Phase:** 10 — Settings Foundation + Setup UI (6/10 plans)
- **Current Plan:** Plan 07 of 10
- **Milestone:** V1 Launch
- **Next action:** Execute remaining Phase 10 plans
- **Last session:** 2026-02-25T04:09:23Z
- **Stopped at:** Completed 10-06-PLAN.md

## Progress

| Phase | Status | Notes |
|---|---|---|
| 1. Build System Foundation | Complete (4/4 plans) | All plans complete |
| 2. SDK Contracts + Common | Complete (5/5 plans) | All plans complete — types, unit tests, contract test infrastructure |
| 3. SDK Observability + Analytics + UI | Complete (3/3 plans) | All plans complete — observability, metrics/health/diagnostics/analytics, SDK UI |
| 4. KSP Codegen | Complete (3/3 plans) | All plans complete — plugin processor, compile-testing, agentic processor |
| 5. Core Infrastructure | Complete (5/5 plans) | All plans complete -- proto schemas, thermal, firebase, data repos, stores, presets |
| 6. Deployable App + Agentic | Complete (4/4 plans) | Core agentic types + app shell + 15 handlers + debug overlays + release validated |
| 7. Dashboard Shell | Complete (16/16 plans) | All coordinators + UI composables + ViewModel + DashboardScreen + profile switching + gap closure tests complete. All quality gaps closed (Q1-Q5). |
| 8. Essentials Pack | Complete (11/11 plans) | All plans complete -- snapshots, lint, providers, widgets, themes, integration tests, on-device verification |
| 9. Themes, Demo + Chaos | Complete (7/7 plans) | All plans complete -- themes pack, snapshot relocation, demo pack, chaos engine, entitlements, app integration + regression gate + SC3 gap closure |
| 10. Settings Foundation + Setup UI | In Progress (6/10 plans) | Data layer clearAll + analyticsConsent + SemanticColors + DeviceScanStateMachine + OverlayScaffold + SettingRowDispatcher + all 12 row types + SetupEvaluator + 7 setup cards |
| 11. Theme UI + Diagnostics + Onboarding | Pending | Concurrent with Phase 9 |
| 12. CI Gates + Benchmarking | Pending | Concurrent with Phases 9-11 |
| 13. E2E Integration + Launch Polish | Pending | Convergence point |

## Decisions

Key decisions accumulated during architecture phase — full table in `DECISIONS.md` (89 entries). Highlights:

- **Per-profile dashboards** over per-widget visibility (sparse layouts, gesture mismatch)
- **Unbounded canvas** with no-straddle snap and no automatic relocation
- **Driving mode deferred** post-launch — DQXN is general-purpose, not vehicle-first
- **Essentials pack** renamed from "free" (packId=`essentials`, entitlement tier stays `free`)
- **Phase 10 decomposed** from original "Features + Polish" into 4 phases (10-13) for settings, theme UI, CI, and E2E
- [Phase 05]: Pure-Kotlin hex color parser (parseHexColor) instead of android.graphics.Color.parseColor -- enables unit testing without Robolectric
- [Phase 05]: ComponentCallbacks2 for reactive system dark mode detection in ThemeAutoSwitchEngine -- fires on uiMode changes without Activity restart
- [Phase 05]: @param:IoDispatcher for Kotlin 2.3 constructor parameter annotation targeting -- avoids KT-73255 future behavior change
- [Phase 05]: Custom qualifier annotations (@UserPreferences, @ProviderSettings, @WidgetStyles) over @Named strings for type-safe Preferences DataStore disambiguation
- [Phase 07]: internal var storageChecker over spyk for StorageMonitor testability -- direct property injection works correctly with virtual time advancement
- [Phase 07]: Mock WidgetDataBinder (not FakeWidgetDataBinder) for harness -- FakeWidgetDataBinder is standalone, not a subclass
- [Phase 07]: SnapSpec type assertion over durationMillis property -- SnapSpec does not expose durationMillis as public property; type check + delay assertion for verification
- [Phase 07]: testScheduler.runCurrent() over advanceUntilIdle() for coordinator tests -- staleness watchdog infinite delay loop hangs advanceUntilIdle
- [Phase 07]: coordinator.destroy() required in every coordinator test -- standalone SupervisorJob not child of test Job; runTest cleanup advanceUntilIdle hangs without explicit cancellation
- [Phase 07]: :core:agentic added to :feature:dashboard allowed deps with parenthetical justification (debug semantics registration)
- [Phase 07]: No destroy() needed for coordinators without standalone SupervisorJob -- child Job cancellation sufficient for LayoutCoordinator, NotificationCoordinator, ProfileCoordinator, ConfigurationBoundaryDetector
- [Phase 07]: mockkStatic(Settings.Global::class) for ReducedMotionHelper tests -- avoids Robolectric while testing real production code
- [Phase 07]: coordinatorScope stored from initialize() for alertEmitter.fire() launch -- fire() is suspend, showBanner() is not; isInitialized guard for calls before initialize()
- [Phase 08]: Public visibility required for KSP-annotated pack classes -- KotlinPoet interfaceBuilder prohibits INTERNAL on abstract members in generated Hilt modules
- [Phase 08]: Timestamp-based accessibility differentiation for action-only widgets -- data.timestamp > 0L distinguishes empty from bound state for contract test #5
- [Phase 08]: Canvas-drawn icons (BatteryIcon, LightBulbIcon) for zero additional icon library dependency with data-state-based color tinting
- [Phase 08]: AgenticContentProvider exported=true in debug manifest for ADB content call access -- debug-only, no security concern
- [Phase 08]: Injectable FusedLocationProviderClient over lazy LocationServices.getFusedLocationProviderClient -- enables mock injection for contract tests without Android runtime
- [Phase 08]: SolarLocationModule in pack providers package -- pack-local Hilt module for FusedLocationProviderClient @Provides, keeps pack isolation clean
- [Phase 09]: Same package preserved during snapshot relocation -- no import changes needed in essentials pack consumers
- [Phase 09]: Pack-local ThemeFileParser over inline DashboardThemeDefinition construction -- JSON files as source of truth reduces error surface of manually transcribing 22 themes
- [Phase 09]: assertWithMessage() over .named() for Truth assertions -- .named() deprecated in current Truth version
- [Phase 09]: dqxn.pack convention plugin already provides kotlinx.serialization plugin and dependency -- no explicit additions needed in build.gradle.kts
- [Phase 09]: Added :sdk:contracts dependency to :core:agentic for DataProviderInterceptor/ProviderFault access
- [Phase 09]: backgroundScope flow collection pattern for virtual-time-dependent tests -- avoids Turbine timeout conflicts with StandardTestDispatcher
- [Phase 09]: AtomicBoolean time-window gating for Flap fault -- coroutineScope launch toggles passing flag alongside upstream collect
- [Phase 09]: Delta-computed delays from absolute ScheduledFault.delayMs timestamps -- profile's delayMs is cumulative offset from session start, engine computes inter-fault deltas
- [Phase 09]: AccelerationSnapshot uses acceleration/lateralAcceleration fields per actual snapshot schema -- plan referenced longitudinalG/lateralG which don't exist
- [Phase 09]: Empty stub modules (diagnostics, onboarding, settings, plus) excluded from regression gate -- pre-existing failOnNoDiscoveredTests, not caused by Phase 9
- [Phase 09]: MockK any() matches null for nullable String? parameter -- no anyOrNull() needed
- [Phase 10]: PairedDeviceStore.clearAll() uses Proto DataStore getDefaultInstance() (not Preferences clear()) -- Proto DataStore has no clear() method
- [Phase 10]: LayoutRepository.clearAll() resets to FallbackLayout.createFallbackStore() -- maintains invariant of never having zero profiles
- [Phase 10]: FakeLayoutRepository updated alongside real LayoutRepository impl -- interface contract change requires all implementations to add clearAll()
- [Phase 10]: [Phase 10-03]: ScanDevice(name, macAddress, associationId) data class over BluetoothDevice -- enables pure JVM testing without Android runtime
- [Phase 10]: [Phase 10-03]: Constructor-injectable timing params (retryDelayMs, autoReturnDelayMs, maxAttempts) for deterministic virtual-time testing with StandardTestDispatcher
- [Phase 10]: [Phase 10-03]: Single verificationJob field tracks both retry and auto-return delayed jobs, cancelled atomically on cancel/reset
- [Phase 10]: [Phase 10-03]: CDM cancel detection via string contains ('user_rejected', 'canceled') matching Android CDM error patterns
- [Phase 10]: [Phase 10-04]: EnumSetting dispatched as EnumSetting<Nothing> with @Suppress(UNCHECKED_CAST) for generic type erasure in when branch
- [Phase 10]: [Phase 10-04]: Pre-existing SetupDefinitionRenderer.kt compilation errors (Plan 10-03 forward references) documented as out-of-scope, not caused by this plan
- [Phase 10]: onSoundPickerRequested callback param on SettingRowDispatcher -- SoundPickerSettingRow needs parent-level ActivityResultLauncher, not direct navigation
- [Phase 10]: [Phase 10-05]: Role.Switch semantics matcher for toggle test -- hasToggleableState not available in compose-ui-test API
- [Phase 10]: [Phase 10-06]: ImmutableList<PairedDevice> snapshot param over PairedDeviceStore Flow for evaluateWithPersistence() -- non-suspend evaluation against pre-collected state
- [Phase 10]: [Phase 10-06]: EntitlementManager passed through SetupDefinitionRenderer to SettingRowDispatcher -- consistent API, no lambda adapter
- [Phase 10]: [Phase 10-06]: hasRequestedPermissions local state guards false permanent-denial detection before first permission request (Pitfall 2)

### Phase 1 Decisions

- **Kotlin 2.3.10** over 2.3.0 (latest stable patch)
- **KSP 2.3.6** with new simplified versioning scheme (no longer Kotlin-version-prefixed)
- **Single shared libs.versions.toml** between root and build-logic (via versionCatalogs create/from)
- **JDK 25 toolchain** with Kotlin JVM_24 fallback (Kotlin 2.3.10 does not support JVM 25 target yet)
- **afterEvaluate for tag-filtered test tasks** — AGP registers testDebugUnitTest during variant creation, not at plugin apply time
- **ktfmt Google style** (2-space indent) enforced via Spotless from day one
- **Standalone TestLintTask.lint() with JUnit5** over extending LintDetectorTest (JUnit3/4)
- **Package-based module classification** for lint boundary enforcement (app.dqxn.android.pack.* = pack module)
- **UElementHandler from com.android.tools.lint.client.api** (not Detector inner class) for lint API 32
- **Root+submodule TestKit pattern** for Android convention plugin testing (AGP on buildscript classpath via apply-false)
- **JUnit BOM dual-configuration scoping** — must apply to both testImplementation and testRuntimeOnly for vintage-engine resolution
- **Proto DataStore resolved via JVM module split** — `:data:proto` (JVM + protobuf plugin) generates proto classes, `:data` (Android) consumes them. Same pattern as NIA (android/nowinandroid#2054)
- **EXTOL SDK available** at `sg.gov.lta:extol:2.1.0` from `https://extol.mycloudrepo.io/public/repositories/extol-android` — sg-erp2 pack unblocked

### Phase 1 Toolchain Compatibility (Plan 04)

| Area | Result | Details |
|---|---|---|
| Pack stub + empty KSP | PASS | `:pack:essentials:compileDebugKotlin` succeeds. Empty `:codegen:plugin` (JVM stub) works as no-op KSP processor. |
| fastTest/composeTest tag isolation | PASS | `fastTest` runs 1 of 2 tests (only `@Tag("fast")`). `testDebugUnitTest` runs 2 of 2. Both work in same Gradle invocation. Independent `Test` task registration via `afterEvaluate` confirmed working. |
| Compose compiler + AGP 9 | PASS | `@Composable` function compiles in `:sdk:ui` with `dqxn.android.compose` plugin. `org.jetbrains.kotlin.plugin.compose` correctly applied alongside AGP 9's built-in Kotlin. |
| Proto DataStore + JDK 25 | PASS | `protobuf-gradle-plugin` 0.9.6 incompatible with Android modules under AGP 9 (`BaseExtension` removed). **Resolved:** `:data:proto` JVM module sidesteps the issue — protobuf plugin works on JVM targets. Same approach as NIA (android/nowinandroid#2054). |
| testFixtures + AGP 9 | PASS | `android { testFixtures { enable = true } }` works. Kotlin sources in `src/testFixtures/kotlin/` compile. `android.experimental.enableTestFixturesKotlinSupport=true` still required (prints warning). |
| EXTOL SDK | PASS | Available at `sg.gov.lta:extol:2.1.0` from `https://extol.mycloudrepo.io/public/repositories/extol-android`. 7 versions published (1.0.0-beta.1 through 2.1.0, latest July 2025). |
| Clean build time (stubs) | 38s | `assembleDebug` across all 25 modules. 589 tasks, 425 executed. Well under NF35 120s target. |

**Proto DataStore resolution**: Resolved by splitting proto generation into `:data:proto` JVM module. The `protobuf-gradle-plugin` only breaks on Android modules (casts to removed `BaseExtension`). JVM modules don't touch Android APIs. Proto schemas are pure Kotlin anyway — the Android-specific DataStore code (serializers, repositories) stays in `:data`. Same approach as Now in Android (PR #2054, merged Jan 2026).

**JUnit BOM version conflict**: `mannodermaus-junit` 2.0.1 upgrades JUnit BOM from 5.11.4 to 5.14.1. The 5.14.1 BOM no longer constrains `org.junit.vintage:junit-vintage-engine`. Fixed by applying BOM to both `testImplementation` and `testRuntimeOnly` configurations, plus correcting the artifact name from `vintage-engine` to `junit-vintage-engine`.

### Phase 2 Decisions

- **retryCount as public read-only property** for test observability (private set)
- **Disconnect resets retryCount from all states** — clean disconnect = fresh retry budget
- **SearchTimeout produces AppError.Device** (not Network) — device-discovery timeout, not network failure
- **No DQXNDispatchers interface** — qualifier annotations only, per phase-02.md
- **compileOnly(platform(compose-bom))** resolves compose-runtime/ui versions without Compose compiler in :sdk:contracts
- **AGP 9 testFixtures via android block** — `java-test-fixtures` plugin conflicts; use `android { testFixtures { enable = true } }` instead
- **WidgetData.withSlot PersistentMap handling** — runtime type check for ImmutableMap→PersistentMap.put()
- **DataSnapshot created in Task 1** — forward-dependency from WidgetSpec.compatibleSnapshots required early creation
- **InfoStyle and InstructionAction in setup package** — shared by SetupDefinition and SettingDefinition; settings imports from setup
- **Setting wrapper defaults delegated from inner SettingDefinition** — all fields overridable per-instance
- **DashboardPackManifest @Serializable + @Immutable** — KSP generates at build time, runtime deserializable
- **Test DataSnapshot subtypes without @Immutable** — compose.runtime is compileOnly, test sources use plain data classes
- **compose.ui added to testFixtures deps** — Modifier reference in TestWidgetRenderer required compose.ui in testFixtures scope
- **Non-Compose fallback for contract render tests** — :sdk:contracts has no Compose compiler, so contract tests #2/#3 verify accessibilityDescription instead of Render(). Real Compose render tests in pack modules (Phase 8+)
- **testScheduler.advanceTimeBy for cancellation test** — advanceUntilIdle causes infinite loop with continuous-emitting providers; bounded time advance is correct pattern

### Phase 3 Decisions

- **FakeSharedPreferences over Robolectric** for CrashEvidenceWriter tests — JUnit5 @Test incompatible with @RunWith(RobolectricTestRunner)
- **NEVER_REPORTED sentinel (Long.MIN_VALUE)** with explicit identity check for DeduplicatingErrorReporter first-report handling — arithmetic comparison overflows
- **TraceContext created in Task 1** — DqxnLogger suspend extensions require it at compile time (forward dependency from Task 2)
- **Adapted InfoCardLayout to Phase 2 SizeOption/InfoCardLayoutMode** — used SMALL/MEDIUM/LARGE/EXTRA_LARGE (0.75-1.5) and STANDARD/COMPACT/WIDE, not old-codebase NONE/SMALL/MEDIUM/LARGE/XL (0.0-1.0) and STACK/GRID/COMPACT
- **ConcurrentHashMap null-value workaround** — CacheEntry wrapper in IconResolver for null ImageVector caching
- **hilt-android annotations only** — :sdk:ui gets @Inject/@MapKey without full Hilt KSP plugin
- **Open classes for testability** — DiagnosticSnapshotCapture/DiagnosticFileWriter made open; MockK cannot mock final classes with constructor-initialized AtomicBoolean on JDK 25
- **compileOnly(compose.runtime) for @Immutable in :sdk:observability** — same pattern as :sdk:contracts
- **EnrichedEvent for PackAnalytics** — anonymous object cannot extend sealed interface; concrete data class subtype instead
- **WidgetHealthMonitor uses scope dispatcher** — hardcoded Dispatchers.Default prevented backgroundScope in tests

### Phase 4 Decisions

- **Hub-and-spoke KSP processor** — single PluginProcessor delegates to WidgetHandler, DataProviderHandler, SnapshotHandler (coordinated manifest output requires all three)
- **Single-pass execution** via invoked flag — no multi-round KSP processing needed
- **DataProvider dataType from snapshot type argument** — resolves @DashboardSnapshot.dataType on DataProvider<T> type arg, empty string fallback for cross-module snapshots
- **HiltModuleGenerator produces interface** — @Binds on interface (no impl code needed) vs abstract class
- **Aggregating vs isolating KSP** — ManifestGenerator aggregating=true (all symbols), HiltModuleGenerator aggregating=false (per-class isolation)
- **stabilityConfigurationFiles (plural, additive)** — generated config adds alongside base config from AndroidComposeConventionPlugin
- **Dagger stubs in compile-testing** — KSP2 mode compiles generated sources; providing minimal Dagger annotation stubs is cleaner than fighting withCompilation flag
- **kctfork KSP2 mode required** — kctfork 0.8.0 bundles kotlin-compiler-embeddable 2.2.0; KSP1 mode fails to invoke processor, KSP2 works correctly
- **JVM module JUnit5 setup** — dqxn.kotlin.jvm convention plugin doesn't configure useJUnitPlatform; added per-module with junit-platform-launcher dependency
- **Duplicate @AgenticCommand name detection** — compile error on two handlers with same command name
- **kctfork KSP2 processorOptions** — use `processorOptions` map inside `configureKsp(useKsp2=true)` block, NOT top-level `kspProcessorOptions` extension
- **ManifestGenerator always runs** — manifest generated even with no annotations (pack always needs manifest descriptor); HiltModule skips correctly

### Phase 5 Decisions

- **ThermalMonitor interface** over open class — cleaner DI, allows FakeThermalManager without mocking
- **FakeThermalManager dual-mode** — optional CoroutineScope; null scope gives synchronous updates, provided scope enables flow derivation
- **Proto suffix on store messages** — DashboardStoreProto not DashboardStore, avoids clashes with domain types in :data
- **preferredRefreshRate** over Surface.setFrameRate() — simpler cross-API approach via WindowManager.LayoutParams
- **Constructor-inject Firebase SDK instances** via @Provides instead of static Firebase.* accessors — enables clean mock-based testing without mockkStatic on JDK 25
- **AtomicBoolean for AnalyticsTracker consent state** — thread-safe toggling without synchronization overhead
- **No Bundle content assertions in firebase tests** — Android stub Bundle (isReturnDefaultValues=true) does not store values; delegation verified via MockK verify
- **ConnectionEventStore shares @ProviderSettings DataStore** — dedicated `__connection_events__` key avoids 7th DataStore file anti-pattern
- **@param:ApplicationContext for PresetLoader** — same KT-73255 pattern as Plan 03

### Phase 6 Decisions

- **compose-ui as testImplementation for core:agentic** -- compileOnly for main sources (no Compose compiler), but testImplementation needed because SemanticsOwner must be loadable for type check in register()
- **CommandResult.toJson() parses data as raw JSON** -- embeds structured JSON objects in response, falls back to string primitive for non-JSON data
- **AgenticCommandRouter indexes aliases** -- lazy handlerMap includes both primary names and aliases from CommandHandler.aliases
- **@ApplicationContext (not @param:ApplicationContext) on @Provides function params** -- @param: target only applies to constructor val/var (KT-73255)
- **FakeSharedPreferences inline in test** -- JUnit5 compatible, no Robolectric Android runtime dependency for CrashRecovery tests
- **AgenticCommandRouter public visibility** -- internal was inaccessible from :app debug source set via @EntryPoint; cross-module access requires public
- **Provider<Set<CommandHandler>> in ListCommandsHandler** -- breaks Dagger circular dependency (handler is itself in the set it injects)
- **Handler classes public (not internal)** -- KSP-generated AgenticHiltModule has public bind functions that cannot expose internal parameter types
- **isReturnDefaultValues=true on application convention** -- Android stub Bundle doesn't store values; unit tests verify file I/O directly
- **Configurable timeoutMs on handleCall** -- production 8s, tests 200ms for fast test runs
- **Exclude protolite-well-known-types from firebase-perf** -- firebase-perf:21.0.4 transitively pulls protolite-well-known-types:18.0.0 which duplicates classes in protobuf-javalite:4.30.2 from :data:proto

### Phase 7 Decisions

- **mockkStatic(VibrationEffect::class)** for DashboardHaptics tests -- VibrationEffect.createPredefined returns null in Android unit test stubs
- **testFixtures deps: compose-bom/runtime, junit-bom/jupiter-api, mockk, window** -- required by parallel plan 07-01 testFixtures sources for Compose compiler, JUnit5 TestWatcher, mocking
- **Temporary file staging for parallel wave execution** -- moved 07-01 uncommitted files to /tmp during 07-02 test compilation, restored after
- **SafeModeManager clock as constructor parameter** -- init block reads clock via checkSafeMode(), must be available before first use (not mutable var)
- **handleResetLayout captures existing widgets before state update** -- prevents repo/StateFlow race with active flow collection
- **android.graphics.Rect apply{} workaround** -- AGP returnDefaultValues=true stubs constructors; set public fields directly
- **runTest(UnconfinedTestDispatcher()) for coordinator tests** -- backgroundScope does not advance with StandardTestDispatcher advanceUntilIdle()
- **Non-suspend endDrag/endResize with scope.launch** -- AwaitPointerEventScope is restricted suspension; persistence launched async on ViewModel scope
- **Elapsed-time long-press detection** -- restricted suspension forbids coroutineScope+delay; track System.currentTimeMillis() on each pointer event instead
- **ConfigurationBoundaryDetector mock needs explicit boundaries stub** -- relaxed MockK returns Object for ImmutableList, causing ClassCastException
- **bind/startBinding split for error count preservation** -- bind() resets error count (public API), startBinding() preserves it (retry internal); prevents infinite retry loops in handleBindingError
- **destroy() method on WidgetBindingCoordinator** -- standalone SupervisorJob() needs explicit cancellation on ViewModel.onCleared() to prevent coroutine leaks
- **Mocked WidgetDataBinder for retry tests** -- real merge+scan+flatMapLatest pipeline's channelFlow dispatch layers hang with StandardTestDispatcher + CEH + SupervisorJob; mock isolates coordinator retry logic
- **awaitCancellation() over delay(Long.MAX_VALUE) in test flows** -- delay schedules TestCoroutineScheduler event at virtual time MAX_VALUE; runTest cleanup advanceUntilIdle hangs trying to reach it
- **StateFlow.distinctUntilChanged() removed** -- deprecated in Kotlin coroutines because StateFlow already guarantees structural equality dedup; treated as compile error
- **Layout save failure via explicit reportLayoutSaveFailure() method** -- LayoutRepository has no save failure flow; explicit method callable from LayoutCoordinator or ViewModel
- **State-based error boundary for WidgetSlot** -- Compose forbids try-catch around @Composable calls; render errors tracked via WidgetStatusCache and binding coordinator crash reporting, not Compose-level catch
- **Banner priority tier separation** -- NotificationBannerHost (Layer 0.5) skips CRITICAL, CriticalBannerHost (Layer 1.5) only renders CRITICAL; safe mode banner visible above all overlays
- **Channel(capacity=64) for DashboardViewModel command routing** -- sequential consumption, try/catch per command, CancellationException rethrown
- **collectAsState() for all Layer 0 state** -- per CLAUDE.md Layer 0 rule, NOT collectAsStateWithLifecycle
- **Job-based scope cancellation in DashboardTestHarness** -- child Job of testScope for forever-collecting coordinator flows, close() cancels before runTest exits
- **Versioned<T> wrapper in FakeLayoutRepository** -- AtomicLong version counter forces MutableStateFlow re-emission on profile switch despite structurally-equal content; combine() approach rejected (broke 11 existing tests via scheduler mismatch)
- **HorizontalPager userScrollEnabled = !isEditMode** -- profile swipe disabled during edit mode (F1.29), horizontal gestures reserved for widget drag

### Phase 8 Decisions

- **Import-based lint detection** over UCallExpression for WidgetScopeBypass -- lint test infrastructure with allowCompilationErrors() cannot resolve method PSI for visitMethodCall
- **Package-based widget detection** (app.dqxn.android.pack.*.widgets.*) over file-path-based -- lint test infra uses temp dirs making file paths unreliable
- **SnapshotConventionPlugin compileOnly compose.runtime** -- compileOnly in sdk:contracts does not propagate transitively via api()
- [Phase 08-03]: Moved ProviderSettingsStore interface to :sdk:contracts (typealias in :data) -- packs CANNOT depend on :data per CLAUDE.md module rules; pure interface belongs in :sdk:contracts
- [Phase 08-03]: AccelerometerProvider TYPE_LINEAR_ACCELERATION first, TYPE_ACCELEROMETER + low-pass filter fallback (alpha=0.8)
- [Phase 08-03]: GpsSpeedProvider fallback speed from consecutive location deltas when hasSpeed()=false, accuracy=null signals computed vs hardware
- [Phase 08-03]: Pack classes use default (public) visibility for KSP-generated Hilt module compatibility
- [Phase 08-04]: SolarCalculator API takes (lat, lon, LocalDate, ZoneId) returning SolarResult with epoch millis -- more composable than old ZonedDateTime API
- [Phase 08-04]: Long.MIN_VALUE sentinel for polar edge cases (no sunrise/sunset) rather than nullable types or exceptions
- [Phase 08-04]: SolarTimezoneDataProvider recalculates at midnight boundary only -- simpler than sunrise/sunset transition timers
- [Phase 08-04]: IanaTimezoneCoordinates returns Pair<Double, Double> instead of dedicated data class -- internal utility only
- [Phase 08-07]: Canvas-drawn warning triangle instead of material-icons-extended dependency -- packs should not pull heavyweight icon libraries (~30MB)
- [Phase 08-07]: Inline DashboardThemeDefinition construction instead of JSON file loading -- ThemeJsonParser is in :core:design which packs cannot depend on per module rules
- [Phase 08-07]: Manual EssentialsThemeModule for ThemeProvider @Binds @IntoSet -- KSP codegen only handles @DashboardWidget and @DashboardDataProvider annotations
- [Phase 08-06a]: Pack-local RegionDetector created for timezone-based speed unit detection -- packs cannot depend on :data or :core; 50+ timezone entries for MPH/KPH and Japan detection
- [Phase 08-06a]: Public visibility on SpeedLimitCircleRenderer and SpeedLimitRectRenderer -- KSP-generated HiltModule requires public parameter types on @Binds functions
- [Phase 08-05a]: Shared companion utilities for clock/date renderers -- ClockDigitalRenderer.resolveZone() reused by analog, DateSimpleRenderer.formatDateFromSnapshot() reused by Stack/Grid
- [Phase 08-05a]: Hand angle pure functions exposed as internal Companion for direct unit testing at cardinal positions
- [Phase 08-02]: System.nanoTime() over SystemClock.elapsedRealtimeNanos() for DataSnapshot.timestamp -- SystemClock returns 0 in JVM unit tests, breaking contract test assertion #3
- [Phase 08-02]: Removed @DashboardDataProvider from CallActionProvider -- KSP processor only checks direct supertypes, cannot resolve indirect DataProvider inheritance via ActionableProvider
- [Phase 08-08]: System.nanoTime() over SystemClock.elapsedRealtimeNanos() for all provider timestamps -- SystemClock returns 0 in JVM tests
- [Phase 08-08]: Direct renderer instantiation in PackCompileVerificationTest -- KSP debugUnitTest generates empty manifest that shadows main
- [Phase 08-08]: DqxnLogger/MetricsCollector in ReleaseModule (not AppModule) -- avoids DuplicateBindings with DebugModule in debug builds
- [Phase 08-08]: SolarCalculator DST fix: date.atStartOfDay(zoneId) for offset derivation -- ZonedDateTime.now() produces wrong offset for dates in different DST period
- [Phase 08-08]: Eager mock init with .also{} over lateinit @BeforeEach -- JUnit5 parent @BeforeEach runs before child, causing uninitialized mock access
- [Phase 08-08]: AddWidgetHandler validates typeId against Set<WidgetRenderer> only -- no command channel bridge outside DashboardViewModel
- [Phase 08-11]: Singleton DashboardCommandBus with MutableSharedFlow (capacity 64, DROP_OLDEST) bridges SingletonComponent handlers to ViewModelRetainedComponent DashboardViewModel
- [Phase 08-11]: advanceUntilIdle() before bus.dispatch() in ViewModel test -- StandardTestDispatcher requires explicit advancement for init coroutines before SharedFlow emission

## Performance Metrics

| Phase-Plan | Duration | Tasks | Files |
|---|---|---|---|
| 01-01 | 12min | 2 | 20 |
| 01-02 | 7min | 3 | 62 |
| 01-03 | 16min | 2 | 14 |
| 01-04 | 28min | 2 | 11 |
| 02-01 | 10min | 2 | 12 |
| 02-02 | 5min | 2 | 28 |
| 02-03 | 6min | 2 | 23 |
| 02-04 | 7min | 2 | 8 |
| 02-05 | 9min | 2 | 11 |
| 03-01 | 10min | 3 | 28 |
| 03-02 | 23min | 2 | 21 |
| 03-03 | 10min | 2 | 17 |
| 04-01 | 4min | 2 | 17 |
| 04-02 | 6min | 2 | 5 |
| 04-03 | 12min | 2 | 9 |
| 05-01 | 5min | 2 | 13 |
| 05-02 | 7min | 2 | 7 |
| 05-05 | 9min | 2 | 14 |
| Phase 05 P03 | 9min | 3 tasks | 22 files |
| Phase 05 P04 | 6min | 3 tasks | 19 files |
| 06-01 | 6min | 2 | 11 |
| 06-02 | 7min | 2 | 12 |
| 06-03 | 12min | 3 | 24 |
| 06-04 | 6min | 2 | 6 |
| 07-02 | 9min | 2 | 7 |
| 07-01 | 25min | 2 | 14 |
| Phase 07 P02 | 9min | 2 tasks | 7 files |
| 07-03 | 10min | 2 | 8 |
| 07-04 | 25min | 2 | 11 |
| 07-05 | 5min | 2 | 5 |
| 07-06 | 10min | 2 | 12 |
| 07-07 | 45min | 2 | 9 |
| Phase 07 P13 | 9min | 2 tasks | 4 files |
| Phase 07-08 P08 | 45min | 1 tasks | 3 files |
| Phase 07 P09 | 15min | 2 tasks | 4 files |
| Phase 07 P10 | 62min | 2 tasks | 2 files |
| Phase 07 P11 | 5min | 1 task | 1 file |
| Phase 07 P12 | 5min | 1 task | 2 files |
| Phase 07 P16 | 1min | 1 tasks | 1 files |
| Phase 07 P14 | 4min | 2 tasks | 5 files |
| Phase 07 P15 | 4min | 2 tasks | 5 files |
| 08-01 | 8min | 2 | 13 |
| 08-03 | 25min | 2 | 10 |
| 08-04 | 30min | 2 | 7 |
| Phase 08 P06b | 28min | 2 tasks | 4 files |
| 08-05b | 27min | 2 | 8 |
| 08-07 | 35min | 2 | 5 |
| 08-06a | 24min | 2 | 9 |
| 08-02 | 15min | 2 | 17 |
| 08-05a | 35min | 2 | 10 |
| 08-08 | 40min | 3 | 17 |
| Phase 08 P09 | 7min | 2 tasks | 1 files |
| 08-11 | 5min | 2 | 6 |
| Phase 08 P10 | 3min | 2 tasks | 4 files |
| Phase 09 P02 | 4min | 3 tasks | 11 files |
| Phase 09 P01 | 7min | 2 tasks | 28 files |
| Phase 09 P04 | 7min | 2 tasks | 7 files |
| Phase 09 P05 | 5min | 2 tasks | 9 files |
| Phase 09 P03 | 4min | 3 tasks | 9 files |
| Phase 09 P06 | 2min | 2 tasks | 1 files |
| Phase 09 P07 | 2min | 1 tasks | 1 files |
| 10-01 | 4min | 2 | 19 |
| Phase 10 P03 | 5min | 2 tasks | 3 files |
| 10-02 | 5min | 2 | 5 |
| 10-04 | 5min | 2 | 8 |
| Phase 10-05 P05 | 8min | 2 tasks | 8 files |
| 10-06 | 8min | 2 | 10 |

## Context

- All architecture docs finalized under `.planning/`: ARCHITECTURE.md, REQUIREMENTS.md, DECISIONS.md, ROADMAP.md, MIGRATION.md (split into per-phase files)
- Old codebase mapped in `.planning/oldcodebase/` (8 docs, ~6000 lines)
- Replication advisory at `.planning/migration/replication-advisory.md` — 7 hard-to-replicate UX areas cross-referenced in phase files and risk-flags.md
- Build infrastructure established: Gradle 9.3.1, AGP 9.0.1, Kotlin 2.3.10, all convention plugins compiling
- All 26 module stubs created with correct convention plugins, settings.gradle.kts stable (26 = original 25 + `:data:proto`)
- Spotless/ktfmt formatting enforced, pre-commit hook with boundary checks active
- Custom lint rules: 6 detectors with 35 tests enforcing KAPT ban, secrets detection, module boundaries, Compose scope, agentic threading, widget scope bypass
- TestKit tests: 18 tests validating convention plugin behavior (SDK versions, Compose, Hilt, Pack wiring, tag filtering, version catalog completeness)
- Toolchain compatibility validated: all 7 areas PASS (Compose, testFixtures, KSP, tag filtering, EXTOL SDK, Proto DataStore via JVM split, clean build).
- EXTOL SDK: `sg.gov.lta:extol:2.1.0` from `https://extol.mycloudrepo.io/public/repositories/extol-android`
