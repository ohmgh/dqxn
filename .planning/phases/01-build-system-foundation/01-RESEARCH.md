# Phase 1: Build System Foundation - Research

**Researched:** 2026-02-23
**Domain:** Gradle build infrastructure, convention plugins, AGP 9.0.1, toolchain compatibility
**Confidence:** MEDIUM-HIGH (AGP 9 is new; some areas require throwaway-module validation)

## Summary

Phase 1 establishes the entire Gradle infrastructure: convention plugins, version catalog, lint rules, module stubs, and formatting. Every subsequent phase compiles against this foundation. The critical technical risk is AGP 9.0.1's new built-in Kotlin management and how it interacts with the Compose compiler plugin, serialization plugin, KSP, and Hilt within convention plugins. AGP 9 is a major breaking change from AGP 8 -- old `BaseExtension` types are gone, `kotlinOptions` is replaced by `compilerOptions`, and `org.jetbrains.kotlin.android` must not be applied.

The old codebase already runs AGP 9.0.0 with Gradle 9.3.1, which de-risks the base toolchain. However, the old codebase targets Kotlin 2.2.0 / KSP 2.2.0-2.0.2 / JDK 21, while the new architecture targets Kotlin 2.3+ / JDK 25 -- version bumps that need validation. The Hilt 2.59.x line has a known `ComponentTreeDeps` bug with AGP 9 (fixed in 2.59.1+), and the android-junit5 plugin has been renamed to `android-junit-framework` with plugin ID `de.mannodermaus.android-junit` at version 2.0.1.

**Primary recommendation:** Build convention plugins as Kotlin class-based plugins (not precompiled scripts) in `build-logic/convention/`. Use AGP 9.0.1's built-in Kotlin (do NOT apply `org.jetbrains.kotlin.android`). Still apply `org.jetbrains.kotlin.plugin.compose` and `org.jetbrains.kotlin.plugin.serialization` explicitly where needed -- AGP 9 does NOT subsume these. Resolve all throwaway-module compatibility checks before writing real convention plugin logic.

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| F13.10 | Test categorization via JUnit5 tags: `fast`, `compose`, `integration`, `benchmark`. Convention plugin provides `fastTest`/`composeTest` tasks | JUnit5 tag filtering via `de.mannodermaus.android-junit` 2.0.1. Register custom tasks with `--include-tags`. See Convention Plugin Specs and JUnit5 Integration sections. |
| NF27 | minSdk 31 | Hardcoded in `dqxn.android.library` / `dqxn.android.application` convention plugins. Validated by Gradle TestKit assertion. |
| NF28 | targetSdk 36 | Hardcoded in `dqxn.android.application` convention plugin. Validated by Gradle TestKit assertion. |
| NF35 | Incremental build time < 15s, clean build < 120s | Achieved via: configuration cache, KSP incremental, parallel builds, no KAPT, Gradle 9.3.1 build cache. Measured post-Phase 1 with stub modules. |
</phase_requirements>

## Standard Stack

### Core

| Library / Tool | Version | Purpose | Confidence |
|---|---|---|---|
| AGP | 9.0.1 | Android build plugin with built-in Kotlin | HIGH -- old codebase runs 9.0.0, 9.0.1 is patch release |
| Gradle | 9.3.1 | Build system | HIGH -- old codebase already uses this |
| JDK | 25 | Build toolchain | MEDIUM -- untested with protoc and jqwik; throwaway validation needed |
| Kotlin | 2.3.x (AGP-managed) | Language. AGP 9 has runtime dep on KGP 2.2.10 minimum, auto-upgrades. To use 2.3.x, declare `org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.x` in buildscript deps | MEDIUM -- version bump from old codebase's 2.2.0 |
| KSP | 2.3.x-matching | Annotation processing. Must match Kotlin version exactly (e.g., `2.3.0-2.0.2`) | MEDIUM -- version must be discovered at implementation time |
| Hilt / Dagger | 2.59.2 | DI framework. 2.59+ required for AGP 9. 2.59.1+ fixes `ComponentTreeDeps` bug | HIGH -- verified fixed in 2.59.1/2.59.2 |
| Compose BOM | 2026.01.01+ | Compose dependency management | HIGH -- old codebase already uses this |
| `org.jetbrains.kotlin.plugin.compose` | Must match Kotlin version | Compose compiler plugin. Still required with AGP 9 -- NOT subsumed | HIGH -- confirmed by official docs |
| `org.jetbrains.kotlin.plugin.serialization` | Must match Kotlin version | kotlinx.serialization compiler plugin. Still required with AGP 9 | HIGH -- confirmed still separate |
| `de.mannodermaus.android-junit` | 2.0.1 | JUnit5 on Android. Renamed from `android-junit5`. Jacoco broken on AGP 9 | HIGH -- verified AGP 9 compatible |
| Spotless | Latest (6.x+) | Code formatting with ktfmt | MEDIUM -- ktfmt 0.57+ has breaking API change, must verify Spotless version supports it |
| ktfmt | 0.54-0.56 or latest with compatible Spotless | Google-style Kotlin formatter | MEDIUM -- Spotless+ktfmt 0.57 incompatibility known (Issue #2602) |

### Supporting

| Library | Version | Purpose | When to Use |
|---|---|---|---|
| Protobuf Gradle Plugin | 0.9.6 | Proto DataStore code generation | Phase 1 throwaway validation only; real use in Phase 5 |
| `protobuf-kotlin-lite` | 4.x | Proto runtime for Android (lite variant) | Phase 5+ |
| KotlinPoet + KotlinPoet-KSP | 1.18.1+ | Code generation for KSP processors | Phase 4, but version catalog entry needed now |
| kotlinx-collections-immutable | 0.3.8+ | Compose-stable collections | Every module via convention plugins |
| LeakCanary | Latest | Memory leak detection (debug only) | Phase 6+ |
| Firebase (Crashlytics, Analytics, Perf) | Latest | Observability (behind interfaces) | Phase 5+ |
| `com.android.tools.lint:lint-api` | 32.0.1 | Custom lint rules (AGP major + 23 = lint major) | Phase 1 lint-rules module |
| `com.android.tools.lint:lint-tests` | 32.0.1 | Lint rule unit testing | Phase 1 lint-rules module |
| jqwik | 1.9.3 | Property-based testing (JUnit5 engine) | Phase 2+, version catalog entry now |
| MockK | 1.13.14+ | Kotlin mocking | Phase 2+, version catalog entry now |
| Truth | 1.4.2+ | Assertion library | Phase 2+, version catalog entry now |
| Turbine | 1.2.0+ | Flow testing | Phase 2+, version catalog entry now |
| Robolectric | 4.16.1+ | JVM Android testing | Phase 2+, version catalog entry now |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|---|---|---|
| ktfmt via Spotless | ktlint via Spotless | ktfmt (Google style) is the project decision per phase-01.md. ktlint is more configurable but ktfmt is opinionated-by-design |
| Precompiled script plugins (.kts) | Class-based plugins (.kt) | Class-based gives full IDE support, type-safe extensions, easier testing. Precompiled scripts are simpler but can't access version catalog `libs` accessor -- deal-breaker for convention plugins |
| Gradle TestKit | Manual `./gradlew` assertions | TestKit runs in-process, faster, catches plugin application errors at test time |
| `de.mannodermaus.android-junit` 2.0.1 | Raw JUnit5 Platform + manual config | Plugin handles Android-specific JUnit5 wiring (test runner, instrumented tests). Without it, significant manual configuration |

## Architecture Patterns

### Recommended Project Structure

```
android/
├── build-logic/
│   ├── convention/
│   │   ├── build.gradle.kts          # Dependencies on AGP, KGP, KSP, Hilt, Compose, Spotless plugin APIs
│   │   └── src/main/kotlin/
│   │       ├── AndroidApplicationConventionPlugin.kt
│   │       ├── AndroidLibraryConventionPlugin.kt
│   │       ├── AndroidComposeConventionPlugin.kt
│   │       ├── AndroidHiltConventionPlugin.kt
│   │       ├── AndroidTestConventionPlugin.kt
│   │       ├── AndroidFeatureConventionPlugin.kt
│   │       ├── PackConventionPlugin.kt
│   │       ├── SnapshotConventionPlugin.kt
│   │       ├── KotlinJvmConventionPlugin.kt
│   │       └── src/test/kotlin/       # Gradle TestKit tests
│   └── settings.gradle.kts
├── gradle/
│   └── libs.versions.toml
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
└── [all module stubs with build.gradle.kts]
```

### Pattern 1: Convention Plugin Structure (AGP 9 Class-Based)

**What:** Each convention plugin is a Kotlin class implementing `Plugin<Project>`. Registered via `gradlePlugin` block in `build-logic/convention/build.gradle.kts`.

**Key constraint:** Convention plugins in `build-logic` cannot access the main project's version catalog via type-safe `libs` accessor. Must use `project.extensions.getByType<VersionCatalogsExtension>().named("libs")` or pass plugin coordinates as string constants.

**Example (AGP 9 Android Library plugin):**
```kotlin
// Source: AGP 9 release notes + old codebase analysis
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")
            // AGP 9: no org.jetbrains.kotlin.android needed

            extensions.configure<LibraryExtension> {
                compileSdk = 36
                defaultConfig {
                    minSdk = 31
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                }
                testOptions {
                    unitTests.isIncludeAndroidResources = true
                    unitTests.isReturnDefaultValues = true
                }
                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_25
                    targetCompatibility = JavaVersion.VERSION_25
                }
            }

            // AGP 9 new DSL: kotlin { compilerOptions {} } replaces kotlinOptions
            extensions.configure<KotlinAndroidProjectExtension> {
                compilerOptions {
                    // jvmTarget inherits from compileOptions.targetCompatibility
                    // Only set if override needed
                }
            }
        }
    }
}
```

**NOTE on CommonExtension:** AGP 9 removes parameterized `CommonExtension<*,*,*,*,*,*>`. Use plain `CommonExtension` (non-parameterized). The `defaultConfig` block requires `.apply {}` instead of direct lambda in some contexts.

### Pattern 2: Version Catalog Access in Convention Plugins

**What:** Convention plugins need library/plugin coordinates but cannot use type-safe `libs` accessor.

**Two approaches:**

1. **Programmatic catalog access (recommended for convention plugins):**
```kotlin
val libs = project.extensions.getByType<VersionCatalogsExtension>().named("libs")
val hiltVersion = libs.findVersion("hilt").get().toString()
dependencies.add("implementation", libs.findLibrary("hilt-android").get())
dependencies.add("ksp", libs.findLibrary("hilt-compiler").get())
```

2. **Plugin dependency in build-logic (for applying plugins):**
```kotlin
// build-logic/convention/build.gradle.kts
dependencies {
    compileOnly(libs.android.gradlePlugin)       // com.android.tools.build:gradle
    compileOnly(libs.kotlin.gradlePlugin)         // org.jetbrains.kotlin:kotlin-gradle-plugin
    compileOnly(libs.compose.gradlePlugin)        // org.jetbrains.kotlin.plugin.compose
    compileOnly(libs.ksp.gradlePlugin)            // com.google.devtools.ksp
    compileOnly(libs.hilt.gradlePlugin)           // com.google.dagger:hilt-android-gradle-plugin
}
```
Then apply by class: `pluginManager.apply(SomePluginClass::class.java)` or by ID string.

**NOTE:** `build-logic` has its OWN `libs.versions.toml` or re-declares the root catalog. The standard pattern in the Android ecosystem is to declare plugin libraries in the root `libs.versions.toml` as `[libraries]` entries for use in `build-logic/convention/build.gradle.kts`.

### Pattern 3: Compose Compiler Plugin Application (AGP 9)

**What:** AGP 9 does NOT subsume the Compose compiler. `org.jetbrains.kotlin.plugin.compose` must still be explicitly applied.

**How it works with AGP 9's built-in Kotlin:**
- AGP 9 manages Kotlin compilation (no `org.jetbrains.kotlin.android`)
- The Compose compiler plugin (`org.jetbrains.kotlin.plugin.compose`) is a Kotlin compiler plugin, applied on top of whatever Kotlin version AGP provides
- The plugin version MUST match the Kotlin version
- `buildFeatures { compose = true }` still needed in the `android` block
- `composeCompiler {}` block (top-level, alongside `android {}`) configures stability files and reports

```kotlin
// dqxn.android.compose convention plugin
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

            extensions.configure<CommonExtension> {
                buildFeatures {
                    compose = true
                }
            }

            extensions.configure<ComposeCompilerGradlePluginExtension> {
                // Base stability config
                stabilityConfigurationFile.set(
                    rootProject.layout.projectDirectory.file("sdk/common/compose_compiler_config.txt")
                )
                // Optional: metrics
                reportsDestination.set(layout.buildDirectory.dir("compose_compiler"))
            }
        }
    }
}
```

### Pattern 4: Test Fixtures for Android Library Modules (AGP 9)

**What:** AGP 9 brings improved test fixtures IDE support. Android modules use `android.testFixtures.enable = true`, NOT the `java-test-fixtures` plugin.

**Key details:**
- Kotlin sources in test fixtures require: `android.experimental.enableTestFixturesKotlinSupport=true` in `gradle.properties` (may be stable in AGP 9 -- verify with throwaway)
- Consumer: `testImplementation(testFixtures(project(":sdk:contracts")))`
- Cannot use `java-test-fixtures` plugin on Android library modules

**Phase 1 validation:** Create a throwaway Android library module, enable test fixtures, add a Kotlin class in `src/testFixtures/kotlin/`, verify compilation succeeds.

### Anti-Patterns to Avoid

- **Applying `org.jetbrains.kotlin.android`:** Build failure with AGP 9. The error message is explicit: "The 'org.jetbrains.kotlin.android' plugin is no longer required."
- **Using `afterEvaluate` in convention plugins:** Breaks Gradle configuration cache. The old codebase does this in `AndroidFeatureConventionPlugin` for KSP theme directory wiring. Use `configureEach` or `named` on task collections instead.
- **Using `android.kotlinOptions {}`:** Deprecated. Migrate to `kotlin { compilerOptions {} }`.
- **Using parameterized `CommonExtension<*,*,*,*,*,*>`:** Type parameters removed in AGP 9. Use unparameterized `CommonExtension`.
- **Applying `kapt()` anywhere:** Breaks configuration cache. KSP only. Lint rule `KaptDetection` enforces this.
- **Using `java-test-fixtures` on Android modules:** Wrong plugin. Use `android { testFixtures.enable = true }` instead.
- **Hardcoding `org.gradle.java.home` in `gradle.properties`:** Not portable. Use `jvmToolchain(25)` in convention plugins or `kotlin { jvmToolchain(25) }`.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---|---|---|---|
| JUnit5 on Android | Manual JUnit Platform setup | `de.mannodermaus.android-junit` 2.0.1 | Handles Android test runner integration, instrumented test bridging |
| Code formatting | Custom ktlint wrapper | Spotless + ktfmt | Spotless handles Git hook integration, incremental formatting, Gradle caching |
| Lint rule testing | Manual lint registry setup | `com.android.tools.lint:lint-tests` | Provides `LintDetectorTest` base class with positive/negative assertion DSL |
| Compose stability config | Manual `-P` flag passing | `composeCompiler { stabilityConfigurationFile }` DSL | Type-safe, cached, per-module override support |
| Convention plugin testing | Manual `./gradlew` shell calls | Gradle TestKit (`GradleRunner`) | In-process, fast, captures build output for assertions |
| Version management | Hardcoded strings in build files | `libs.versions.toml` version catalog | Single source of truth, IDE autocomplete, Dependabot/Renovate compatible |

**Key insight:** The build system is pure infrastructure. Every component has a standard solution. The complexity is in correctly wiring AGP 9's new DSL, not in building custom tooling.

## Common Pitfalls

### Pitfall 1: Hilt `ComponentTreeDeps` Missing Annotation

**What goes wrong:** Hilt 2.59.0 Gradle plugin generates code referencing `dagger.hilt.internal.componenttreedeps.ComponentTreeDeps`, but the runtime artifact doesn't include this class. Compilation fails.
**Why it happens:** Hilt 2.59.0 shipped AGP 9 Gradle plugin support without the corresponding runtime annotation.
**How to avoid:** Use Hilt 2.59.1+ (verified fixed). The version catalog MUST specify `2.59.1` or `2.59.2` minimum.
**Warning signs:** `error: package dagger.hilt.internal.componenttreedeps does not exist`

### Pitfall 2: Spotless + ktfmt Version Incompatibility

**What goes wrong:** ktfmt 0.57+ replaced `FormattingOptions.manageTrailingCommas` with `FormattingOptions.trailingCommaManagementStrategy` (enum). Spotless versions that call the old method get `NoSuchMethodError`.
**Why it happens:** Breaking API change in ktfmt without Spotless update.
**How to avoid:** Pin ktfmt version to one compatible with the Spotless version. Check Spotless changelog for ktfmt compatibility. Alternatively, use the `ktfmt()` call in Spotless without an explicit version to let Spotless choose its bundled compatible version.
**Warning signs:** `java.lang.NoSuchMethodError: 'boolean com.facebook.ktfmt.format.FormattingOptions.getManageTrailingCommas()'`

### Pitfall 3: Version Catalog Not Accessible in Convention Plugins

**What goes wrong:** `libs.xxx` is unresolved in convention plugin source files.
**Why it happens:** Precompiled script plugins and class-based plugins in `build-logic` (an included build) don't have automatic access to the root project's type-safe catalog accessors.
**How to avoid:** Use `VersionCatalogsExtension` programmatic API: `project.extensions.getByType<VersionCatalogsExtension>().named("libs")`. For build-logic's own dependencies, declare plugin libraries as `[libraries]` entries in the root catalog and reference them in `build-logic/convention/build.gradle.kts`.
**Warning signs:** `Unresolved reference: libs` in convention plugin .kt files

### Pitfall 4: AGP 9 `CommonExtension` Type Parameters Removed

**What goes wrong:** Code referencing `CommonExtension<*, *, *, *, *, *>` fails to compile.
**Why it happens:** AGP 9 removes the generic type parameters from `CommonExtension`.
**How to avoid:** Use unparameterized `CommonExtension` or the specific extension type (`LibraryExtension`, `ApplicationExtension`).
**Warning signs:** Generic type mismatch errors when configuring Android extensions in convention plugins

### Pitfall 5: KSP Version Mismatch with Kotlin

**What goes wrong:** KSP fails with "incompatible Kotlin version" errors.
**Why it happens:** KSP version must exactly match the Kotlin version prefix. AGP 9 pins KGP >= 2.2.10; if you want Kotlin 2.3.x, KSP must be 2.3.x-matching.
**How to avoid:** Always synchronize KSP version with Kotlin version in the version catalog. AGP 9 auto-upgrades KSP to 2.2.10-2.0.2 minimum if lower.
**Warning signs:** KSP compilation errors mentioning version incompatibility

### Pitfall 6: Jacoco Broken on AGP 9

**What goes wrong:** Code coverage reports fail or produce no output.
**Why it happens:** The `de.mannodermaus.android-junit` plugin warns that Jacoco integration is deprecated for AGP 9.0.0-alpha04+. Google removed APIs needed for the old Jacoco DSL.
**How to avoid:** Do not depend on Jacoco for coverage in Phase 1. If coverage is needed later, use Kover (JetBrains) instead.
**Warning signs:** Jacoco deprecation warnings during build

### Pitfall 7: JDK 25 Compatibility with protoc

**What goes wrong:** `protoc` binary or generated code may not work with JDK 25.
**Why it happens:** JDK 25 is very new; protoc native binaries may not have been tested against it.
**How to avoid:** Phase 1 throwaway-module validation: apply protobuf plugin, add a `.proto` file, run `compileDebugKotlin`, verify generated files. If it fails, document the issue and investigate protoc toolchain configuration (can pin protoc to use a specific JDK).
**Warning signs:** protoc execution errors or generated code compilation failures

### Pitfall 8: Test Fixtures Kotlin Support Flag

**What goes wrong:** Kotlin sources in `testFixtures` source set are not compiled.
**Why it happens:** Prior to AGP 8.9/9.0, Kotlin in test fixtures required `android.experimental.enableTestFixturesKotlinSupport=true`. This may be stable in AGP 9 -- but if not set, Kotlin files are silently ignored.
**How to avoid:** Include the flag in `gradle.properties` as a safety measure. The throwaway validation will confirm whether it's still needed.
**Warning signs:** Test fixtures classes not found by consumer modules; no compilation errors but missing symbols

## Code Examples

### Convention Plugin Registration (build-logic/convention/build.gradle.kts)

```kotlin
// Source: AGP 9 migration guides + old codebase analysis
plugins {
    `kotlin-dsl`
}

dependencies {
    compileOnly(libs.android.gradlePlugin)           // com.android.tools.build:gradle:9.0.1
    compileOnly(libs.kotlin.gradlePlugin)             // org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.x
    compileOnly(libs.compose.compiler.gradlePlugin)   // org.jetbrains.kotlin.plugin.compose gradle plugin
    compileOnly(libs.ksp.gradlePlugin)                // com.google.devtools.ksp:symbol-processing-gradle-plugin
    compileOnly(libs.hilt.gradlePlugin)               // com.google.dagger:hilt-android-gradle-plugin
    compileOnly(libs.android.junit.gradlePlugin)      // de.mannodermaus.android-junit gradle plugin
    compileOnly(libs.spotless.gradlePlugin)            // com.diffplug.spotless:spotless-plugin-gradle
    // For serialization plugin application
    compileOnly(libs.kotlin.serialization.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "dqxn.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "dqxn.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "dqxn.android.compose"
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("androidHilt") {
            id = "dqxn.android.hilt"
            implementationClass = "AndroidHiltConventionPlugin"
        }
        register("androidTest") {
            id = "dqxn.android.test"
            implementationClass = "AndroidTestConventionPlugin"
        }
        register("androidFeature") {
            id = "dqxn.android.feature"
            implementationClass = "AndroidFeatureConventionPlugin"
        }
        register("pack") {
            id = "dqxn.pack"
            implementationClass = "PackConventionPlugin"
        }
        register("snapshot") {
            id = "dqxn.snapshot"
            implementationClass = "SnapshotConventionPlugin"
        }
        register("kotlinJvm") {
            id = "dqxn.kotlin.jvm"
            implementationClass = "KotlinJvmConventionPlugin"
        }
    }
}
```

### dqxn.android.test Convention Plugin (JUnit5 + Tag Filtering)

```kotlin
// Source: phase-01.md spec + de.mannodermaus.android-junit docs
class AndroidTestConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("de.mannodermaus.android-junit")

            val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

            dependencies {
                // JUnit 5
                add("testImplementation", platform(libs.findLibrary("junit-bom").get()))
                add("testImplementation", libs.findLibrary("junit-jupiter-api").get())
                add("testImplementation", libs.findLibrary("junit-jupiter-params").get())
                add("testRuntimeOnly", libs.findLibrary("junit-jupiter-engine").get())
                // JUnit 4 bridge (for ComposeTestRule, HiltAndroidRule)
                add("testRuntimeOnly", libs.findLibrary("junit-vintage-engine").get())
                // Supporting
                add("testImplementation", libs.findLibrary("mockk").get())
                add("testImplementation", libs.findLibrary("truth").get())
                add("testImplementation", libs.findLibrary("turbine").get())
                add("testImplementation", libs.findLibrary("coroutines-test").get())
                add("testImplementation", libs.findLibrary("robolectric").get())
                add("testImplementation", libs.findLibrary("jqwik").get())
            }

            // Configure JUnit Platform + tag-based tasks
            tasks.withType<Test>().configureEach {
                useJUnitPlatform()
                // Structured output path
                reports.junitXml.outputLocation.set(
                    layout.buildDirectory.dir("test-results/${name}")
                )
            }

            // Register fastTest task
            tasks.register("fastTest") {
                description = "Run only @Tag(\"fast\") tests"
                group = "verification"
                dependsOn(tasks.named("testDebugUnitTest").map {
                    it.apply {
                        (this as Test).useJUnitPlatform {
                            includeTags("fast")
                        }
                    }
                })
            }

            // Register composeTest task
            tasks.register("composeTest") {
                description = "Run only @Tag(\"compose\") tests"
                group = "verification"
                dependsOn(tasks.named("testDebugUnitTest").map {
                    it.apply {
                        (this as Test).useJUnitPlatform {
                            includeTags("compose")
                        }
                    }
                })
            }
        }
    }
}
```

**NOTE:** The `fastTest`/`composeTest` task registration pattern above is a sketch. The actual implementation needs to create separate test tasks (not modify the default), because `includeTags` on the default task would filter ALL test runs. The correct approach is to register new test tasks that copy the configuration of `testDebugUnitTest` but add tag filtering. This is a known complexity with JUnit5 tag-based task creation on Android -- the planner should allocate explicit effort for this.

### dqxn.pack Convention Plugin (Pack Isolation)

```kotlin
// Source: phase-01.md spec + CLAUDE.md dependency rules
class PackConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("dqxn.android.library")
            pluginManager.apply("dqxn.android.compose")
            pluginManager.apply("dqxn.android.hilt")
            pluginManager.apply("dqxn.android.test")
            pluginManager.apply("com.google.devtools.ksp")
            pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")

            val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

            dependencies {
                // Auto-wire all :sdk:* modules
                add("implementation", project(":sdk:contracts"))
                add("implementation", project(":sdk:common"))
                add("implementation", project(":sdk:ui"))
                add("implementation", project(":sdk:observability"))
                add("implementation", project(":sdk:analytics"))

                // KSP processor for @DashboardWidget / @DashboardDataProvider
                add("ksp", project(":codegen:plugin"))

                // Required libraries
                add("implementation", libs.findLibrary("kotlinx-collections-immutable").get())
                add("implementation", libs.findLibrary("kotlinx-coroutines-core").get())
            }

            // KSP args (convention-based paths, no afterEvaluate)
            extensions.configure<com.google.devtools.ksp.gradle.KspExtension> {
                arg("themesDir", "${projectDir}/src/main/resources/themes/")
            }
        }
    }
}
```

### Gradle TestKit Test (Convention Plugin Validation)

```kotlin
// Source: Gradle TestKit docs
class AndroidLibraryPluginTest {
    @TempDir
    lateinit var testProjectDir: File

    @Test
    fun `dqxn_android_library sets correct SDK versions`() {
        // Setup minimal project
        File(testProjectDir, "settings.gradle.kts").writeText("""
            rootProject.name = "test"
        """.trimIndent())

        File(testProjectDir, "build.gradle.kts").writeText("""
            plugins {
                id("dqxn.android.library")
            }
            android {
                namespace = "com.test"
            }
        """.trimIndent())

        File(testProjectDir, "src/main/AndroidManifest.xml").apply {
            parentFile.mkdirs()
            writeText("<manifest />")
        }

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("tasks", "--console=plain")
            .withPluginClasspath()
            .build()

        assertThat(result.output).contains("BUILD SUCCESSFUL")
        // Additional assertions on compileSdk, minSdk via extracting config
    }
}
```

### Lint Rule Example (KaptDetection)

```kotlin
// Source: Android lint-api docs + phase-01.md spec
class KaptDetectionDetector : Detector(), GradleScanner {
    override fun checkDslPropertyAssignment(
        context: GradleContext,
        property: String,
        value: String,
        parent: String,
        parentParent: String?,
        propertyCookie: Any,
        valueCookie: Any
    ) {
        // Detect kapt plugin application
    }

    companion object {
        val ISSUE = Issue.create(
            id = "KaptDetection",
            briefDescription = "KAPT is not allowed -- use KSP",
            explanation = "KAPT breaks Gradle configuration cache. All annotation processing must use KSP.",
            category = Category.CORRECTNESS,
            severity = Severity.ERROR,
            implementation = Implementation(
                KaptDetectionDetector::class.java,
                Scope.GRADLE_SCOPE
            )
        )
    }
}
```

## State of the Art

| Old Approach | Current Approach (AGP 9) | When Changed | Impact |
|---|---|---|---|
| `org.jetbrains.kotlin.android` plugin | Built-in Kotlin (AGP manages Kotlin) | AGP 9.0 (Jan 2026) | Remove kotlin-android plugin entirely. Build fails if applied. |
| `android.kotlinOptions {}` | `kotlin { compilerOptions {} }` | AGP 9.0 | Must migrate all convention plugins |
| `CommonExtension<*,*,*,*,*,*>` | `CommonExtension` (unparameterized) | AGP 9.0 | Convention plugin code must update type references |
| `applicationVariants` / `libraryVariants` | `androidComponents.onVariants {}` | AGP 9.0 | Old variant API disabled by default |
| `kapt()` for annotation processing | `ksp()` exclusively | Pre-AGP 9 best practice | KaptDetection lint rule enforces |
| `android-junit5` plugin (1.x) | `android-junit` (2.0.1) | Jan 2026 | Plugin ID change, supports JUnit 5+6, AGP 9 compatible |
| Jacoco coverage on Android | Kover (JetBrains) | AGP 9 | Jacoco integration broken on AGP 9 |
| Java 8 source/target default | Java 11 default | AGP 9.0 | Convention plugins should set Java 25 explicitly |
| `proguard-android.txt` | `proguard-android-optimize.txt` only | AGP 9.0 | Old file disallowed by default |

**Deprecated/outdated:**
- `BaseExtension`: Removed in AGP 9. Use `ApplicationExtension`, `LibraryExtension`, or `CommonExtension`.
- `org.jetbrains.kotlin.android`: Build error if applied. AGP 9 manages Kotlin.
- `variantFilter`: Replaced by `androidComponents.beforeVariants` + selector API.
- `dexOptions`: Had no effect since AGP 7.0, now removed.
- Jacoco on AGP 9: Broken, use Kover.

## Open Questions

1. **JDK 25 + protoc binary compatibility**
   - What we know: protobuf-gradle-plugin 0.9.6 requires Gradle 5.6+ and Java 8+. The `protoc` native binary is OS-specific.
   - What's unclear: Whether the `protoc` binary (downloaded by the plugin) runs correctly under JDK 25, and whether Kotlin 2.3+ code generation produces valid code for JDK 25 targets.
   - Recommendation: Phase 1 throwaway module validation. If protoc fails, pin its JDK via Gradle toolchain isolation (protoc runs as an external process, not JVM-hosted, so this is likely fine).

2. **Kotlin 2.3.x exact version and matching KSP version**
   - What we know: AGP 9.0.1 ships with runtime dependency on KGP 2.2.10. To use 2.3.x, must declare higher version in buildscript dependencies.
   - What's unclear: The exact latest stable Kotlin 2.3.x release and its matching KSP version as of implementation date.
   - Recommendation: At implementation time, check the Kotlin release page and KSP releases for the latest stable pair. If Kotlin 2.3.x is not yet stable, use the latest 2.2.x that AGP 9 supports and bump later.

3. **`android.experimental.enableTestFixturesKotlinSupport` still needed in AGP 9?**
   - What we know: Required since AGP 8.5. AGP 9 release notes mention improved test fixture IDE support but don't explicitly state this flag is no longer needed.
   - What's unclear: Whether AGP 9 made Kotlin test fixtures non-experimental.
   - Recommendation: Include the flag in `gradle.properties`. Throwaway validation will confirm. Harmless if no longer needed.

4. **EXTOL SDK compatibility with Kotlin 2.3+ / JDK 25**
   - What we know: Old codebase uses EXTOL 2.1.0 with Kotlin 2.2.0 / JDK 21. The SDK is pre-compiled (AAR).
   - What's unclear: Whether the pre-compiled AAR works under the new toolchain (JNI, native libs, desugaring).
   - Recommendation: Phase 1 throwaway: `assembleDebug` (not just compile) to catch linking/packaging failures. Record result in STATE.md.

5. **Spotless + ktfmt version pairing**
   - What we know: ktfmt 0.57+ has a breaking API change. Spotless must be new enough to handle the new `trailingCommaManagementStrategy` enum.
   - What's unclear: Exact Spotless version that supports ktfmt 0.57+.
   - Recommendation: At implementation time, check Spotless changelog. If uncertain, use `ktfmt()` without explicit version to let Spotless use its bundled version.

6. **`de.mannodermaus.android-junit` 2.0.1 + JDK 25**
   - What we know: Plugin 2.0.1 is AGP 9 compatible. JUnit 6 requires JDK 17+ / API 35+.
   - What's unclear: Whether JDK 25 causes issues with the test runner infrastructure.
   - Recommendation: Should work -- JUnit5 is well-tested on modern JDKs. Validate with first real test in Phase 2.

## Sources

### Primary (HIGH confidence)

- [AGP 9.0.1 Release Notes](https://developer.android.com/build/releases/agp-9-0-0-release-notes) -- built-in Kotlin, new DSL, KSP auto-upgrade, R8 changes, test fixtures
- [Migrate to Built-in Kotlin](https://developer.android.com/build/migrate-to-built-in-kotlin) -- kotlinOptions migration, kapt to legacy-kapt, source sets
- [Compose Compiler Gradle Plugin](https://developer.android.com/develop/ui/compose/compiler) -- still required with AGP 9, composeCompiler block
- [Gradle 9.3.1 Release Notes](https://docs.gradle.org/9.3.1/release-notes.html) -- test reporting improvements, security fixes
- [Dagger/Hilt Releases](https://github.com/google/dagger/releases) -- 2.59.2 latest, AGP 9 support timeline
- [android-junit-framework releases](https://github.com/mannodermaus/android-junit-framework/releases) -- 2.0.1 with AGP 9 support

### Secondary (MEDIUM confidence)

- [JetBrains: Update your Kotlin projects for AGP 9.0](https://blog.jetbrains.com/kotlin/2026/01/update-your-projects-for-agp9/) -- migration overview
- [ITNEXT: Migrating to AGP 9 in KMP and Convention Plugins](https://itnext.io/migrating-to-agp-9-in-kotlin-multiplatform-and-convention-plugins-my-story-cefff6b915f3) -- convention plugin gotchas
- [Hilt ComponentTreeDeps Issue #5099](https://github.com/google/dagger/issues/5099) -- confirmed fixed in 2.59.1+
- [Spotless ktfmt Issue #2602](https://github.com/diffplug/spotless/issues/2602) -- ktfmt 0.57 incompatibility
- [Gradle Version Catalogs docs](https://docs.gradle.org/current/userguide/version_catalogs.html) -- catalog accessor generation rules
- [jqwik User Guide 1.9.3](https://jqwik.net/docs/current/user-guide.html) -- maintenance mode, JUnit5 engine

### Tertiary (LOW confidence)

- JDK 25 + protoc compatibility -- no sources found, needs throwaway validation
- EXTOL SDK + Kotlin 2.3+ -- no sources found, needs throwaway validation
- `android.experimental.enableTestFixturesKotlinSupport` status in AGP 9 -- conflicting information, needs throwaway validation

### Project Sources (HIGH confidence)

- `/Users/ohm/Workspace/dqxn/.planning/migration/phase-01.md` -- detailed phase spec with all convention plugin requirements
- `/Users/ohm/Workspace/dqxn/.planning/arch/build-system.md` -- build architecture and agentic framework spec
- `/Users/ohm/Workspace/dqxn/.planning/arch/testing.md` -- test infrastructure and framework choices
- `/Users/ohm/Workspace/dqxn/.planning/oldcodebase/build-system.md` -- old codebase build analysis (convention plugins, version catalog, module graph)
- `/Users/ohm/Workspace/dqxn/.planning/REQUIREMENTS.md` -- F13.10, NF27, NF28, NF35
- `/Users/ohm/Workspace/dqxn/.planning/DECISIONS.md` -- 89 architecture decisions
- `/Users/ohm/Workspace/dqxn/CLAUDE.md` -- project instructions and constraints

## Metadata

**Confidence breakdown:**
- Standard stack: MEDIUM-HIGH -- AGP 9.0.1, Gradle 9.3.1, Hilt 2.59.2 all verified. JDK 25 and Kotlin 2.3.x version pairing need runtime validation.
- Architecture: HIGH -- Convention plugin patterns well-documented. Old codebase provides working reference for 4 of 9 plugins.
- Pitfalls: HIGH -- All major pitfalls (ComponentTreeDeps, ktfmt compat, catalog accessors, CommonExtension types) documented with mitigations.
- Toolchain compatibility: MEDIUM -- JDK 25 + protoc, EXTOL SDK, and test fixtures Kotlin flag all need throwaway validation.

**Research date:** 2026-02-23
**Valid until:** 2026-03-23 (stable domain -- AGP 9 is released, not preview)
