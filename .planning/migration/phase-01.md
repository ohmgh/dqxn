# Phase 1: Build System Foundation

**What:** Gradle infrastructure that all modules depend on. Nothing compiles without this.

**Pre-requisites:**

- JDK 25 installed (or Gradle toolchain auto-download configured via `org.gradle.java.installations.auto-download=true`)
- Any recent Gradle installation for bootstrapping (`gradle wrapper --gradle-version 9.3.1`)
- Android SDK platform 36 installed

**Deliverables:**

- `android/settings.gradle.kts` — module includes for ALL modules (stub `build.gradle.kts` for modules not yet implemented — see Module Include Strategy), version catalog, `build-logic` includeBuild
- `android/build.gradle.kts` — root project
- `android/gradle/libs.versions.toml` — complete version catalog (see Version Catalog)
- `android/gradle.properties` — configuration cache, KSP incremental, Compose compiler flags, `jvmToolchain(25)` in convention plugins (no hardcoded `org.gradle.java.home`)
- `android/build-logic/convention/` — all convention plugins with Gradle TestKit tests (see Convention Plugin Specs)
- `android/lint-rules/` — `:lint-rules` module with custom lint checks and unit tests (see Lint Rules)
- JUnit5 test tags (`fast`, `compose`, `integration`, `benchmark`) and convention plugin `fastTest`/`composeTest` Gradle tasks (F13.10) — available from Phase 2 onward so every module benefits
- Code formatting: ktfmt (Google style) via Spotless Gradle plugin, `.editorconfig`, enforced in pre-commit hook
- Gradle wrapper (9.3.1)

## Module Include Strategy

`settings.gradle.kts` includes ALL modules from Phase 1 onward. Modules not yet implemented get a stub `build.gradle.kts` containing only the plugin application:

```kotlin
// sdk/contracts/build.gradle.kts (stub — source added in Phase 2)
plugins {
    id("dqxn.android.library")
}
```

Benefits:

- `./gradlew tasks` validates all plugin resolution against actual module targets
- `./gradlew :pack:essentials:dependencies` validates `dqxn.pack` auto-wiring from Phase 1 (closes 7-phase gap)
- `settings.gradle.kts` is stable — no modifications per phase
- Phases 3+4 (concurrent) don't create merge conflicts on settings

Subsequent phases fill in source files; they do not modify `settings.gradle.kts` or stub build files (only `:app/build.gradle.kts` gains `implementation(project(...))` entries as packs land).

## Convention Plugin Specs

**`dqxn.android.application`**

- Applies `com.android.application`
- Sets compileSdk 36, minSdk 31, targetSdk 36, `jvmToolchain(25)`
- Configures release build type (minify, R8)
- Enables `buildConfig = true`
- Applies `dqxn.android.compose`

**`dqxn.android.library`**

- Applies `com.android.library`
- Sets compileSdk 36, minSdk 31, `jvmToolchain(25)`
- Configures `unitTests.isIncludeAndroidResources = true`, `unitTests.isReturnDefaultValues = true`
- Does NOT enable Compose — Compose is opt-in via `dqxn.android.compose`

**`dqxn.android.compose`**

- Enables `buildFeatures { compose = true }` (verify AGP 9.0.1 mechanism — see AGP 9 Compose Investigation)
- Adds Compose BOM + core dependencies (`compose-ui`, `compose-ui-graphics`, `compose-material3`, `compose-ui-tooling-preview`)
- Adds `debugImplementation` compose-ui-tooling
- Wires base stability config file (`sdk/common/compose_compiler_config.txt`)
- Accepts additional stability config paths via extension property (for KSP-generated files in `dqxn.pack`)

**`dqxn.android.hilt`**

- Applies `com.google.devtools.ksp` and `com.google.dagger.hilt.android`
- Adds `hilt-android` (implementation) and `hilt-compiler` (ksp)

**`dqxn.android.test`**

- Applies `de.mannodermaus.android-junit5`
- Adds: `junit-jupiter-api`, `junit-jupiter-engine`, `junit-jupiter-params` (testImplementation / testRuntimeOnly)
- Adds: `junit-vintage-engine` (testRuntimeOnly) — required because `WidgetRendererContractTest` uses JUnit4 `ComposeContentTestRule`. Without this, JUnit4-based tests are silently skipped by the JUnit5 platform
- Adds: jqwik (testImplementation) — available from Phase 2 for property-based tests
- Adds: MockK, Truth, Turbine, kotlinx-coroutines-test, Robolectric (testImplementation)
- Configures `useJUnitPlatform()` in test tasks
- Registers `fastTest` task (`--include-tags fast`) and `composeTest` task (`--include-tags compose`)
- Configures structured test output to `{module}/build/test-results/{variant}/` (F13.8)

**`dqxn.pack`** (most complex convention plugin — all responsibilities enumerated)

- Applies `dqxn.android.library` (base Android library config)
- Applies `dqxn.android.compose` (packs contain `@Composable Render()` — Compose compiler required)
- Applies `dqxn.android.hilt` (packs use `@Module @InstallIn(SingletonComponent::class)` for multibinding)
- Applies `dqxn.android.test`
- Applies `com.google.devtools.ksp` and adds `ksp(project(":codegen:plugin"))` for `@DashboardWidget` / `@DashboardDataProvider` processing
- Applies `org.jetbrains.kotlin.plugin.serialization` (packs use kotlinx.serialization for settings)
- Wires KSP-generated Compose stability config file into Compose compiler options (via `dqxn.android.compose` extension)
- Auto-wires all `:sdk:*` dependencies with `implementation` scope: `:sdk:contracts`, `:sdk:common`, `:sdk:ui`, `:sdk:observability`, `:sdk:analytics`
- Adds `implementation(libs.kotlinx.collections.immutable)` and `implementation(libs.kotlinx.coroutines.core)`
- Configures common KSP args: `themesDir` pointing to `src/main/resources/themes/`, `manifestPath` (convention-based, no `afterEvaluate` — configuration-cache safe)
- Note: `:codegen:plugin` doesn't exist until Phase 4 adds processor source. Stub module has an empty `build.gradle.kts`. KSP processing is a no-op until then. The convention plugin still wires the dependency — Gradle resolves it against the empty stub

**`dqxn.snapshot`** (pure-data sub-modules for cross-boundary snapshot types)

- Applies `dqxn.android.library` (must be Android library because `:sdk:contracts` is an Android library — `api()` scope requires compatible module types. Architecture docs saying "no Android" are inaccurate; this is an Android library with no Compose compiler, no Hilt, no KSP — effectively pure Kotlin data classes within an Android library container)
- Adds `api(project(":sdk:contracts"))` as only project dependency
- Does NOT apply the Compose compiler plugin — no `@Composable` function processing
- `@Immutable` annotation (from `androidx.compose.runtime`) is available transitively because `:sdk:contracts` depends on `compose.runtime` (for `@Composable` in `WidgetRenderer.Render()` signature)
- No kotlinx.serialization, no Hilt, no KSP

**`dqxn.android.feature`**

- Applies `dqxn.android.library`, `dqxn.android.compose`, `dqxn.android.hilt`, `dqxn.android.test`
- Auto-wires: `:sdk:contracts`, `:sdk:common`, `:sdk:ui`, `:sdk:observability` (implementation scope)
- Adds: lifecycle, hilt-navigation-compose, navigation-compose
- Does NOT wire `:core:*` or `:data` — features declare these manually per module

**`dqxn.kotlin.jvm`** (lightweight, for `:codegen:*` modules)

- Applies `kotlin("jvm")`
- Sets `jvmToolchain(25)`
- No Android, no Compose, no Hilt

## Version Catalog

`android/gradle/libs.versions.toml` — complete catalog covering all phases. Dependencies not consumed until later phases are still present — changing the catalog later is trivial but missing entries cause confusion about intentional omissions.

Required entries (non-exhaustive — full enumeration at implementation time):

| Category | Entries |
|---|---|
| Build plugins | AGP 9.0.1, KSP (Kotlin 2.3-compatible), Hilt, `com.google.protobuf` (plugin + `protoc` + `protobuf-kotlin-lite` runtime), `de.mannodermaus.android-junit5`, kotlinx.serialization, Spotless |
| Compose | Compose BOM, `compose-runtime` (standalone — needed by `:sdk:contracts` without compiler), compose-ui, compose-material3, compose-ui-tooling, compose-ui-test-junit4 |
| AndroidX | lifecycle, navigation-compose, activity-compose, core-ktx, core-splashscreen, datastore-proto, datastore-preferences, window (foldable APIs) |
| Kotlin | kotlinx-coroutines (core + test), kotlinx-collections-immutable, kotlinx-serialization-json |
| DI | hilt-android, hilt-compiler, hilt-navigation-compose |
| KSP authoring | ksp-api, KotlinPoet, KotlinPoet-KSP |
| Firebase | crashlytics, analytics, performance (behind interfaces in `:core:firebase`) |
| Debug | LeakCanary |
| Testing | JUnit5 (jupiter-api, engine, params), junit-vintage-engine (for JUnit4 `ComposeContentTestRule` in `WidgetRendererContractTest`), jqwik, MockK, Truth, Turbine, Robolectric, compose-ui-test-junit4, kotlinx-coroutines-test |
| Location | play-services-location |
| Conditional | EXTOL SDK (contingent on Phase 1 compat check) |

## Lint Rules

`:lint-rules` module with the following detectors (all delivered in Phase 1, enforcement starts when consumers exist):

| Rule | Severity | Enforcement starts | What it catches |
|---|---|---|---|
| `KaptDetection` | Error | Phase 1 | Any module applying `kapt` plugin — breaks configuration cache |
| `NoHardcodedSecrets` | Error | Phase 1 | SDK keys, API tokens, credentials in source |
| `ModuleBoundaryViolation` | Error | Phase 2 | Pack modules importing outside `:sdk:*` / `*:snapshots` boundary |
| `ComposeInNonUiModule` | Error | Phase 2 | Compose imports in non-UI modules |
| `AgenticMainThreadBan` | Error | Phase 6 | `Dispatchers.Main` usage in agentic command handlers |

Each rule has unit tests (positive case: fires on violating code; negative case: does not fire on clean code) using `LintDetectorTest`. Additional rules (`WidgetScopeBypass`) added when the first widget renderer is implemented (Phase 8).

## AGP 9 Compose Investigation (must resolve before implementing `dqxn.android.compose`)

AGP 9.0.1 manages Kotlin directly (no `org.jetbrains.kotlin.android` plugin). The old codebase applies `org.jetbrains.kotlin.plugin.compose` as a separate JetBrains plugin. Phase 1 must determine:

1. Does AGP 9.0.1 subsume the Compose compiler, or is the separate plugin still required?
2. If the separate plugin is needed, does it conflict with AGP 9's built-in Kotlin management?
3. What is the correct `buildFeatures` / plugin DSL for enabling Compose in AGP 9.0.1?

**This is the single most consequential build decision in Phase 1.** Every Compose-using module (`:sdk:ui`, all packs, all features, `:core:design`, `:app`) depends on it. Resolve BEFORE writing the convention plugin — do not leave as a runtime investigation. Create a throwaway module, apply AGP 9.0.1, add a `@Composable` function, and verify it compiles. Document the finding inline in `dqxn.android.compose` plugin source.

**Additionally verify:** `org.jetbrains.kotlin.plugin.serialization` compatibility with AGP 9's built-in Kotlin management. `dqxn.pack` applies this plugin (line 150), as does `:sdk:contracts`. If AGP 9 subsumes serialization configuration, the separate plugin may conflict. Test in the same throwaway module.

## Toolchain Compatibility Checks

Binary go/no-go before Phase 2 starts:

- **Proto DataStore**: Add a stub `.proto` file to a throwaway module applying `com.google.protobuf` plugin. Verify `compileDebugKotlin` passes AND generated Kotlin files are present in build output. If protoc binary is incompatible with JDK 25 or generated code fails to compile with Kotlin 2.3+, investigate before Phase 5 designs around it.
- **EXTOL SDK**: Add `implementation("sg.gov.lta:extol:X.Y.Z")` to a throwaway module, run `assembleDebug` (not just `compileDebugKotlin` — catches linking/packaging failures from JNI or native library incompatibilities). Record result in `STATE.md` Decisions section. If incompatible, remove `:pack:sg-erp2` from Phase 9 scope immediately — don't waste design effort on connection state machine and 8 provider contracts for a pack that can't ship.

Delete throwaway modules after checks. These are 10-minute verifications that prevent Phase 5/9 scope surprises.

## `java-test-fixtures` Strategy

Modules that need testFixtures (`:sdk:contracts` in Phase 2, others later) apply `java-test-fixtures` manually in their `build.gradle.kts`. This is not baked into any convention plugin — testFixtures are opt-in per module.

**Phase 1 smoke check required:** Apply `java-test-fixtures` to an Android library stub, add a class in `src/testFixtures/kotlin/`, and verify `./gradlew :stub:testFixturesClasses` passes. The `java-test-fixtures` plugin has historically had compatibility issues with Android library modules and AGP versions. Phase 2 is blocked if this fails under AGP 9.0.1.

## `:sdk:contracts` Compose Runtime Dependency

`:sdk:contracts` is "pure Kotlin + coroutines" but has `@Composable` in the `WidgetRenderer.Render()` signature and `@Immutable` annotation usage. It needs `compileOnly(libs.compose.runtime)` — the Compose runtime for annotations, NOT the Compose compiler. This is added manually in Phase 2's `:sdk:contracts/build.gradle.kts`, not via a convention plugin.

## Deferred Validation Registry

Several convention plugins can't be fully validated until their first real consumer. Deferred validation is assigned to the consuming phase's success criteria:

| Plugin | Deferred validation | Owner phase |
|---|---|---|
| `dqxn.android.library` | First module compiles | Phase 2 (`:sdk:common` compiles) |
| `dqxn.android.compose` | Compose code compiles | Phase 3 (`:sdk:ui` compiles) |
| `dqxn.android.hilt` | Hilt DI graph resolves | Phase 6 (`:app:installDebug`) |
| `dqxn.android.test` | First test runs with JUnit5 | Phase 2 (`ConnectionStateMachineTest` passes) |
| `dqxn.android.feature` | Feature module compiles | Phase 7 (`:feature:dashboard` compiles) |
| `dqxn.pack` | Dependency graph + KSP wiring | Phase 1 stub check + Phase 8 real validation |
| `dqxn.snapshot` | Snapshot sub-module compiles | Phase 8 (`:pack:essentials:snapshots` compiles) |
| `dqxn.android.application` | APK assembles | Phase 6 (`:app:installDebug`) |
| `dqxn.kotlin.jvm` | Codegen module compiles | Phase 4 (`:codegen:plugin` compiles) |

Phase 1 partially validates `dqxn.pack` via stub module dependency resolution (`./gradlew :pack:essentials:dependencies`). Full validation (KSP processing, Compose compilation, runtime behavior) waits for Phase 8.

**Ported from old:** Convention plugin structure (4 plugins → 9, significantly expanded). Version catalog (updated versions, added missing deps). Convention plugins must be rewritten for AGP 9.0.1's new DSL interfaces (old `BaseExtension` types are gone). Old `afterEvaluate` pattern in `AndroidFeatureConventionPlugin` must NOT be replicated — configuration-cache hazard. Old `VersionCatalogsExtension.named("libs")` API may be deprecated in Gradle 9.3.1 — verify and use type-safe catalog accessor if needed. No existing R8 rules to port — old codebase has zero custom ProGuard/R8 rules. Phase 6 writes rules from scratch.

**Validation:**

1. `./gradlew tasks --console=plain` succeeds — all convention plugins resolve, all stub modules parse
2. `./gradlew :build-logic:convention:test` passes — Gradle TestKit configuration assertions (compileSdk/minSdk/targetSdk, Compose enabled where expected, Hilt wired, `dqxn.pack` dependency graph correct)
3. `./gradlew :pack:essentials:dependencies --configuration debugCompileClasspath` shows `:sdk:contracts`, `:sdk:common`, `:sdk:ui`, `:sdk:observability`, `:sdk:analytics` with `implementation` scope
4. `./gradlew :lint-rules:test` passes — all lint rules have positive/negative test cases
5. Version catalog contains all required dependency aliases (verified by assertion test in `:build-logic:convention:test`)
6. Proto DataStore throwaway: `compileDebugKotlin` passes, generated Kotlin files present in build output
7. EXTOL SDK throwaway: `assembleDebug` passes (or incompatibility recorded in `STATE.md`)
8. `./gradlew :pack:essentials:compileDebugKotlin` succeeds with empty `:codegen:plugin` JVM stub — verifies KSP doesn't fail when processor produces no output (Android consumer + JVM processor cross-platform stub wiring)
9. `java-test-fixtures` throwaway: `./gradlew :stub:testFixturesClasses` passes for an Android library module — catches AGP 9.0.1 testFixtures incompatibility before Phase 2
10. AGP 9 Compose throwaway: composable function compiles via `dqxn.android.compose` — confirms Compose compiler integration mechanism
11. `./gradlew spotlessCheck` passes on stub modules — confirms formatting toolchain works
