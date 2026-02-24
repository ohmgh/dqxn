# Project State

## Current Position

- **Phase:** 4 — KSP Codegen
- **Current Plan:** 2 of 3 complete (01, 03)
- **Milestone:** V1 Launch
- **Next action:** Phase 4 Plan 02 (if not already running in parallel)
- **Last session:** 2026-02-24T01:49:36.055Z
- **Stopped at:** Completed 04-03-PLAN.md

## Progress

| Phase | Status | Notes |
|---|---|---|
| 1. Build System Foundation | Complete (4/4 plans) | All plans complete |
| 2. SDK Contracts + Common | Complete (5/5 plans) | All plans complete — types, unit tests, contract test infrastructure |
| 3. SDK Observability + Analytics + UI | Complete (3/3 plans) | All plans complete — observability, metrics/health/diagnostics/analytics, SDK UI |
| 4. KSP Codegen | In Progress (2/3 plans) | Plans 01, 03 complete — plugin + agentic processors |
| 5. Core Infrastructure | Pending | |
| 6. Deployable App + Agentic | Pending | First on-device deployment |
| 7. Dashboard Shell | Pending | Highest risk phase |
| 8. Essentials Pack | Pending | Architecture validation gate |
| 9. Themes, Demo + Chaos | Pending | Depends on Phases 8, 10 (SetupSheet UI required for sg-erp2 BLE device pairing) |
| 10. Settings Foundation + Setup UI | Pending | Unblocks sg-erp2 pairing |
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
| 04-03 | 12min | 2 | 9 |

## Context

- All architecture docs finalized under `.planning/`: ARCHITECTURE.md, REQUIREMENTS.md, DECISIONS.md, ROADMAP.md, MIGRATION.md (split into per-phase files)
- Old codebase mapped in `.planning/oldcodebase/` (8 docs, ~6000 lines)
- Replication advisory at `.planning/migration/replication-advisory.md` — 7 hard-to-replicate UX areas cross-referenced in phase files and risk-flags.md
- Build infrastructure established: Gradle 9.3.1, AGP 9.0.1, Kotlin 2.3.10, all convention plugins compiling
- All 26 module stubs created with correct convention plugins, settings.gradle.kts stable (26 = original 25 + `:data:proto`)
- Spotless/ktfmt formatting enforced, pre-commit hook with boundary checks active
- Custom lint rules: 5 detectors with 30 tests enforcing KAPT ban, secrets detection, module boundaries, Compose scope, agentic threading
- TestKit tests: 18 tests validating convention plugin behavior (SDK versions, Compose, Hilt, Pack wiring, tag filtering, version catalog completeness)
- Toolchain compatibility validated: all 7 areas PASS (Compose, testFixtures, KSP, tag filtering, EXTOL SDK, Proto DataStore via JVM split, clean build).
- EXTOL SDK: `sg.gov.lta:extol:2.1.0` from `https://extol.mycloudrepo.io/public/repositories/extol-android`
