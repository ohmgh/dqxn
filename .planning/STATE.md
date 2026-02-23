# Project State

## Current Position

- **Phase:** 2 — SDK Contracts + Common
- **Current Plan:** 1 of 5
- **Milestone:** V1 Launch
- **Next action:** Phase 2, Plan 01 — SDK contracts foundation
- **Last session:** 2026-02-23T17:44:33.369Z
- **Stopped at:** Completed 01-04-PLAN.md (Phase 1 complete)

## Progress

| Phase | Status | Notes |
|---|---|---|
| 1. Build System Foundation | Complete (4/4 plans) | All plans complete |
| 2. SDK Contracts + Common | Pending | |
| 3. SDK Observability + Analytics + UI | Pending | Concurrent with Phase 4 |
| 4. KSP Codegen | Pending | Concurrent with Phase 3 |
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
- **Proto DataStore plugin incompatible with AGP 9** — Phase 5 needs Wire migration or custom protoc Exec task
- **EXTOL SDK not available** in public repositories — sg-erp2 pack deferred

### Phase 1 Toolchain Compatibility (Plan 04)

| Area | Result | Details |
|---|---|---|
| Pack stub + empty KSP | PASS | `:pack:essentials:compileDebugKotlin` succeeds. Empty `:codegen:plugin` (JVM stub) works as no-op KSP processor. |
| fastTest/composeTest tag isolation | PASS | `fastTest` runs 1 of 2 tests (only `@Tag("fast")`). `testDebugUnitTest` runs 2 of 2. Both work in same Gradle invocation. Independent `Test` task registration via `afterEvaluate` confirmed working. |
| Compose compiler + AGP 9 | PASS | `@Composable` function compiles in `:sdk:ui` with `dqxn.android.compose` plugin. `org.jetbrains.kotlin.plugin.compose` correctly applied alongside AGP 9's built-in Kotlin. |
| Proto DataStore + JDK 25 | FAIL | `protobuf-gradle-plugin` 0.9.6 casts to `BaseExtension` which was removed in AGP 9. Error: `Cannot cast LibraryExtensionImpl to BaseExtension`. No newer plugin version available (0.9.6 is latest). **Blocker for Phase 5**: need Wire migration, manual protoc task, or upstream fix. |
| testFixtures + AGP 9 | PASS | `android { testFixtures { enable = true } }` works. Kotlin sources in `src/testFixtures/kotlin/` compile. `android.experimental.enableTestFixturesKotlinSupport=true` still required (prints warning). |
| EXTOL SDK | NOT_AVAILABLE | Not in public Maven repositories. sg-erp2 pack deferred until SDK access is obtained. |
| Clean build time (stubs) | 38s | `assembleDebug` across all 25 modules. 589 tasks, 425 executed. Well under NF35 120s target. |

**Proto DataStore resolution path**: The most viable workaround for Phase 5 is registering a custom `Exec` task that invokes `protoc` directly (protoc is a native binary, not JVM-hosted, so AGP version doesn't matter). Alternatively, Square's Wire protobuf library generates Kotlin directly without the Gradle plugin. Decision deferred to Phase 5 planning.

**JUnit BOM version conflict**: `mannodermaus-junit` 2.0.1 upgrades JUnit BOM from 5.11.4 to 5.14.1. The 5.14.1 BOM no longer constrains `org.junit.vintage:junit-vintage-engine`. Fixed by applying BOM to both `testImplementation` and `testRuntimeOnly` configurations, plus correcting the artifact name from `vintage-engine` to `junit-vintage-engine`.

## Performance Metrics

| Phase-Plan | Duration | Tasks | Files |
|---|---|---|---|
| 01-01 | 12min | 2 | 20 |
| 01-02 | 7min | 3 | 62 |
| 01-03 | 16min | 2 | 14 |
| 01-04 | 28min | 2 | 11 |

## Context

- All architecture docs finalized under `.planning/`: ARCHITECTURE.md, REQUIREMENTS.md, DECISIONS.md, ROADMAP.md, MIGRATION.md (split into per-phase files)
- Old codebase mapped in `.planning/oldcodebase/` (8 docs, ~6000 lines)
- Replication advisory at `.planning/migration/replication-advisory.md` — 7 hard-to-replicate UX areas cross-referenced in phase files and risk-flags.md
- Build infrastructure established: Gradle 9.3.1, AGP 9.0.1, Kotlin 2.3.10, all convention plugins compiling
- All 25 module stubs created with correct convention plugins, settings.gradle.kts stable
- Spotless/ktfmt formatting enforced, pre-commit hook with boundary checks active
- Custom lint rules: 5 detectors with 30 tests enforcing KAPT ban, secrets detection, module boundaries, Compose scope, agentic threading
- TestKit tests: 18 tests validating convention plugin behavior (SDK versions, Compose, Hilt, Pack wiring, tag filtering, version catalog completeness)
- Toolchain compatibility validated: Compose, testFixtures, KSP, tag filtering all pass. Proto DataStore plugin incompatible with AGP 9 (workaround identified). EXTOL SDK not available.
