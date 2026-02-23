# Phase 4: KSP Codegen - Research

**Researched:** 2026-02-24
**Domain:** KSP annotation processing, KotlinPoet code generation, Hilt multibinding generation
**Confidence:** HIGH

## Summary

Phase 4 builds two KSP processors (`:codegen:plugin` and `:codegen:agentic`) that run at compile time in pack modules to auto-generate Hilt multibinding modules, pack manifests, Compose stability config files, and agentic command routing. Both processors are pure JVM (`dqxn.kotlin.jvm` convention plugin) and depend on `ksp-api` + `kotlinpoet` + `kotlinpoet-ksp`. They never have a Gradle module dependency on the Android modules whose annotations they process -- they read annotation metadata from `KSAnnotation` in the consumer's compilation environment.

The project already has the infrastructure: KSP 2.3.6 in the version catalog, KotlinPoet 1.18.1, both codegen module stubs (`plugins { id("dqxn.kotlin.jvm") }`), and the `PackConventionPlugin` already wires `ksp(project(":codegen:plugin"))`. The three annotations (`@DashboardWidget`, `@DashboardDataProvider`, `@DashboardSnapshot`) exist in `:sdk:contracts` from Phase 2. What's missing: processor source code, compile-testing library in the catalog, and the agentic convention plugin wiring for `:codegen:agentic`.

**Primary recommendation:** Build `:codegen:plugin` first (it serves every pack module), validate with `kctfork` compile-testing, then build `:codegen:agentic` as a structurally similar but simpler processor. Keep processors single-pass with clear error messages. Use KotlinPoet's KSP interop (`writeTo(codeGenerator)` + `addOriginatingKSFile`) for all code generation.

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| F2.12 | `@DashboardWidget` KSP annotation -> auto-registration | `:codegen:plugin` processor scans `@DashboardWidget`, generates Hilt `@Module` with `@Binds @IntoSet` for `WidgetRenderer`, generates `PackManifest` with `PackWidgetRef` entries. KotlinPoet KSP interop provides the code generation API. |
| F3.8 | `@DashboardDataProvider` KSP annotation -> auto-registration | Same processor scans `@DashboardDataProvider`, generates Hilt `@Module` with `@Binds @IntoSet` for `DataProvider<*>`, adds `PackDataProviderRef` entries to manifest. |
</phase_requirements>

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `com.google.devtools.ksp:symbol-processing-api` | 2.3.6 | KSP processor API (Resolver, KSAnnotated, CodeGenerator) | Already in catalog as `libs.ksp-api`. Google's official API for Kotlin annotation processing. |
| `com.squareup:kotlinpoet` | 1.18.1 | Kotlin source code generation (TypeSpec, FunSpec, FileSpec) | Already in catalog as `libs.kotlinpoet`. Industry standard for Kotlin codegen. |
| `com.squareup:kotlinpoet-ksp` | 1.18.1 | KSP interop: `toTypeName()`, `writeTo(CodeGenerator)`, `addOriginatingKSFile()` | Already in catalog as `libs.kotlinpoet-ksp`. Handles incremental processing, type resolution, originating file tracking. |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `dev.zacsweers.kctfork:core` | 0.12.1 (verify) | Compile-testing infrastructure for KSP processors | All processor tests. NOT in catalog yet -- must add. |
| `dev.zacsweers.kctfork:ksp` | 0.8.0 (verify) | KSP-specific compile-testing (`symbolProcessorProviders`) | All processor tests. NOT in catalog yet -- must add. |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `kctfork` (ZacSweers fork) | `tschuchortdev/kotlin-compile-testing` | `kctfork` tracks latest Kotlin versions faster. ZacSweers actively maintains it. Preferred for Kotlin 2.3.x compatibility. |
| KotlinPoet | Raw `OutputStream.write()` via `codeGenerator.createNewFile()` | KotlinPoet handles import management, formatting, incremental file tracking. Raw strings are error-prone for anything beyond trivial generation. |
| Single hub processor | Separate `SymbolProcessorProvider` per concern | Single hub is simpler, matches old codebase pattern. KSP calls `process()` once per round per provider -- multiple providers add overhead. Single processor with handler delegation is the right call. |

**Dependencies to add to `:codegen:plugin/build.gradle.kts`:**
```kotlin
plugins { id("dqxn.kotlin.jvm") }

dependencies {
    implementation(libs.ksp.api)
    implementation(libs.kotlinpoet)
    implementation(libs.kotlinpoet.ksp)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.truth)
    testImplementation(libs.kctfork.core)
    testImplementation(libs.kctfork.ksp)
}
```

**Entries to add to `libs.versions.toml`:**
```toml
[versions]
kctfork = "0.8.0"  # verify latest compatible with Kotlin 2.3.x

[libraries]
kctfork-core = { group = "dev.zacsweers.kctfork", name = "core", version.ref = "kctfork" }
kctfork-ksp = { group = "dev.zacsweers.kctfork", name = "ksp", version.ref = "kctfork" }
```

**Important version concern:** `kctfork` bundles `kotlin-compiler-embeddable` and requires it match the project's Kotlin version. The project uses Kotlin 2.3.10. `kctfork` 0.8.0 was released July 2025 -- it may not support Kotlin 2.3.10. This needs validation at implementation time. If incompatible, check for a newer `kctfork` release or use the `core` artifact at 0.12.1. **Confidence: MEDIUM** -- version compatibility must be verified.

## Architecture Patterns

### Recommended Project Structure

```
codegen/
├── plugin/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── kotlin/app/dqxn/android/codegen/plugin/
│       │   │   ├── PluginProcessorProvider.kt       # SymbolProcessorProvider entry point
│       │   │   ├── PluginProcessor.kt               # Hub processor, delegates to handlers
│       │   │   ├── handlers/
│       │   │   │   ├── WidgetHandler.kt             # @DashboardWidget processing
│       │   │   │   ├── DataProviderHandler.kt       # @DashboardDataProvider processing
│       │   │   │   ├── SnapshotHandler.kt           # @DashboardSnapshot validation
│       │   │   │   └── ThemeHandler.kt              # @ThemePackMarker JSON processing (if in scope)
│       │   │   ├── generation/
│       │   │   │   ├── HiltModuleGenerator.kt       # Generates @Module @InstallIn classes
│       │   │   │   ├── ManifestGenerator.kt         # Generates PackManifest implementations
│       │   │   │   └── StabilityConfigGenerator.kt  # Generates compose_compiler_config.txt
│       │   │   └── validation/
│       │   │       ├── TypeIdValidator.kt            # {packId}:{widget-name} format
│       │   │       └── SnapshotValidator.kt          # @Immutable, val-only, implements DataSnapshot
│       │   └── resources/META-INF/services/
│       │       └── com.google.devtools.ksp.processing.SymbolProcessorProvider
│       └── test/
│           └── kotlin/app/dqxn/android/codegen/plugin/
│               ├── WidgetProcessorTest.kt
│               ├── DataProviderProcessorTest.kt
│               ├── SnapshotValidationTest.kt
│               ├── TypeIdValidationTest.kt
│               └── HiltModuleGenerationTest.kt
├── agentic/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── kotlin/app/dqxn/android/codegen/agentic/
│       │   │   ├── AgenticProcessorProvider.kt
│       │   │   ├── AgenticProcessor.kt
│       │   │   └── generation/
│       │   │       ├── CommandRouterGenerator.kt    # Generates AgenticCommandRouter
│       │   │       └── SchemaGenerator.kt           # Generates list-commands schema
│       │   └── resources/META-INF/services/
│       │       └── com.google.devtools.ksp.processing.SymbolProcessorProvider
│       └── test/
│           └── kotlin/app/dqxn/android/codegen/agentic/
│               ├── AgenticProcessorTest.kt
│               └── SchemaGenerationTest.kt
```

### Pattern 1: Hub-and-Spoke Processor

**What:** A single `SymbolProcessorProvider` creates one `SymbolProcessor` that delegates to handler objects per annotation type. Single-pass execution (no multi-round).

**When to use:** Multiple related annotations that generate coordinated output (e.g., widget + provider + snapshot all contribute to one manifest).

**Example:**
```kotlin
// Source: KSP quickstart + old codebase pattern
class PluginProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val options: Map<String, String>,
) : SymbolProcessor {
    private var invoked = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (invoked) return emptyList()
        invoked = true

        val widgetResults = WidgetHandler(resolver, logger).process()
        val providerResults = DataProviderHandler(resolver, logger).process()
        val snapshotResults = SnapshotHandler(resolver, logger).process()

        // Generate coordinated outputs
        HiltModuleGenerator(codeGenerator).generate(widgetResults, providerResults)
        ManifestGenerator(codeGenerator).generate(widgetResults, providerResults)
        StabilityConfigGenerator(codeGenerator).generate(snapshotResults)

        return emptyList() // No deferred symbols -- single-pass
    }
}
```

### Pattern 2: Hilt Module Generation via KotlinPoet

**What:** Generate `@Module @InstallIn(SingletonComponent::class)` interfaces with `@Binds @IntoSet` methods for each annotated class.

**When to use:** Every `@DashboardWidget` class needs Hilt multibinding into `Set<WidgetRenderer>`. Every `@DashboardDataProvider` class needs multibinding into `Set<DataProvider<*>>`.

**Example:**
```kotlin
// Source: KotlinPoet docs + Hilt multibinding pattern
fun generateHiltModule(
    packId: String,
    widgets: List<WidgetInfo>,
    providers: List<ProviderInfo>,
): FileSpec {
    val moduleType = TypeSpec.interfaceBuilder("${packId.capitalize()}HiltModule")
        .addAnnotation(ClassName("dagger", "Module"))
        .addAnnotation(
            AnnotationSpec.builder(ClassName("dagger.hilt", "InstallIn"))
                .addMember("%T::class", ClassName("dagger.hilt.components", "SingletonComponent"))
                .build()
        )

    widgets.forEach { widget ->
        moduleType.addFunction(
            FunSpec.builder("bind${widget.className}")
                .addModifiers(KModifier.ABSTRACT)
                .addAnnotation(ClassName("dagger", "Binds"))
                .addAnnotation(ClassName("dagger.multibindings", "IntoSet"))
                .addParameter("impl", widget.typeName)
                .returns(ClassName("app.dqxn.android.sdk.contracts.widget", "WidgetRenderer"))
                .build()
        )
    }

    providers.forEach { provider ->
        moduleType.addFunction(
            FunSpec.builder("bind${provider.className}")
                .addModifiers(KModifier.ABSTRACT)
                .addAnnotation(ClassName("dagger", "Binds"))
                .addAnnotation(ClassName("dagger.multibindings", "IntoSet"))
                .addParameter("impl", provider.typeName)
                .returns(
                    ClassName("app.dqxn.android.sdk.contracts.provider", "DataProvider")
                        .parameterizedBy(STAR)
                )
                .build()
        )
    }

    return FileSpec.builder("app.dqxn.android.pack.${packId}.generated", "${packId.capitalize()}HiltModule")
        .addType(moduleType.build())
        .build()
}
```

### Pattern 3: Compose Stability Config Generation

**What:** KSP processor generates a `compose_compiler_config.txt` file listing all `@DashboardSnapshot`-annotated classes. The `dqxn.pack` convention plugin wires this file into `composeCompiler.stabilityConfigurationFile`.

**When to use:** Every snapshot sub-module produces types that cross module boundaries. Without stability config, Compose treats them as unstable.

**Important:** The generated file is NOT a Kotlin source file -- it's a plain text file. Use `codeGenerator.createNewFile()` with `extensionName = "txt"` or write directly to a build directory location that the convention plugin references.

**Convention plugin wiring needed:**
```kotlin
// In PackConventionPlugin, add:
composeCompiler {
    stabilityConfigurationFile.set(
        layout.buildDirectory.file("generated/ksp/main/kotlin/compose_stability_config.txt")
    )
}
```

### Pattern 4: Compile-Testing with kctfork

**What:** In-process compilation testing that feeds source code to the KSP processor and asserts on outputs (generated files, compilation errors/warnings).

**When to use:** All processor tests. No need for Gradle integration tests -- compile-testing is faster and more precise.

**Example:**
```kotlin
// Source: kctfork README
@Test
fun `valid DashboardWidget generates Hilt module`() {
    val source = SourceFile.kotlin("SpeedometerRenderer.kt", """
        package app.dqxn.android.pack.essentials.widgets.speedometer

        import app.dqxn.android.sdk.contracts.annotation.DashboardWidget
        import app.dqxn.android.sdk.contracts.widget.WidgetRenderer

        @DashboardWidget(
            typeId = "essentials:speedometer",
            displayName = "Speedometer",
        )
        class SpeedometerRenderer : WidgetRenderer { /* ... */ }
    """.trimIndent())

    val compilation = KotlinCompilation().apply {
        sources = listOf(source)
        symbolProcessorProviders = listOf(PluginProcessorProvider())
        // Must add SDK contracts stubs to classpath
    }
    val result = compilation.compile()

    assertThat(result.exitCode).isEqualTo(ExitCode.OK)
    // Assert generated Hilt module exists
    val generatedFiles = compilation.kspSourcesDir.walkTopDown().filter { it.extension == "kt" }.toList()
    assertThat(generatedFiles.map { it.name }).contains("EssentialsHiltModule.kt")
}
```

### Anti-Patterns to Avoid

- **Source-file regex parsing:** The old codebase's `SettingsHandler` and `ConstraintHandler` read raw source text with regex. Never do this -- use KSP's `Resolver` API to inspect types, properties, annotations, and supertypes via the symbol tree.
- **No-arg constructor instantiation:** The old `AgenticProcessor` generates `FooHandler()` directly. The new architecture uses Hilt -- generated code must produce `@Module` + `@Binds @IntoSet`, not direct construction.
- **Hard-coded type mappings:** The old codebase has hard-coded enum-to-FQN maps and icon-to-localId maps. The new processor should derive everything from annotation arguments and KSP type resolution.
- **Warnings instead of errors:** The old `DataContractHandler` uses `logger.warn()` for missing providers. Use `logger.error()` for validation failures that should block compilation (malformed `typeId`, missing `@Immutable`, etc.).
- **Runtime dependency from processor to consumer modules:** The `:codegen:plugin` module MUST NOT have a Gradle `implementation`/`api` dependency on `:sdk:contracts`. The processor reads annotation metadata from `KSAnnotation` objects in the consumer's compilation classpath. Processor only needs `ksp-api`, `kotlinpoet`, `kotlinpoet-ksp`.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Kotlin source generation | StringBuilder + manual imports | KotlinPoet `FileSpec`/`TypeSpec`/`FunSpec` | Import resolution, formatting, escaping, indent management are solved problems |
| KSP type-to-KotlinPoet conversion | Manual type name string construction | `kotlinpoet-ksp` `toTypeName()`, `toClassName()` | Handles generics, nullability, type aliases, type parameters correctly |
| Incremental processing tracking | Manual file dependency tracking | `addOriginatingKSFile()` on KotlinPoet builders | KSP needs to know which input files generated which outputs for cache invalidation |
| Compile-time test compilation | Gradle TestKit integration tests | `kctfork` in-process compilation | Faster (no Gradle overhead), more precise assertions, direct access to generated source |

**Key insight:** KSP + KotlinPoet + kctfork is the standard toolkit. The old codebase already used the first two (minus kctfork). The main work is writing the handlers and generators correctly, not assembling the framework.

## Common Pitfalls

### Pitfall 1: Processor Runs in Consumer's Compilation, Not Its Own

**What goes wrong:** Developers add `implementation(project(":sdk:contracts"))` to `:codegen:plugin` thinking the processor needs the annotation classes. This creates a circular dependency or unnecessary coupling.

**Why it happens:** Confusion between "the processor's own classpath" and "the classpath where the processor runs." KSP processors read annotation metadata via `KSAnnotation.annotationType` and `arguments` -- they don't need the annotation class on their own classpath.

**How to avoid:** `:codegen:plugin` depends only on `ksp-api`, `kotlinpoet`, `kotlinpoet-ksp`. It reads annotation FQNs as strings: `resolver.getSymbolsWithAnnotation("app.dqxn.android.sdk.contracts.annotation.DashboardWidget")`.

**Warning signs:** Build error about missing `@DashboardWidget` class in `:codegen:plugin` compilation.

### Pitfall 2: kctfork Version vs. Kotlin Version Mismatch

**What goes wrong:** Tests fail with `IncompatibleClassChangeError` or `NoSuchMethodError` because `kctfork` bundles a different `kotlin-compiler-embeddable` version than the project's Kotlin 2.3.10.

**Why it happens:** `kctfork` only supports one Kotlin compiler version at a time. If the latest release predates Kotlin 2.3.10, there's a mismatch.

**How to avoid:** Check `kctfork` release notes for Kotlin version compatibility before adding to catalog. If no compatible version exists, options: (a) pin to a nightly/snapshot build, (b) use Google's built-in KSP testing utilities if available, (c) write Gradle integration tests with TestKit as fallback.

**Warning signs:** Cryptic `AbstractMethodError` or `ClassCastException` in compile-testing test setup.

### Pitfall 3: Missing Service File Registration

**What goes wrong:** Processor builds fine but is never invoked. Pack modules compile without error but no code is generated.

**Why it happens:** `META-INF/services/com.google.devtools.ksp.processing.SymbolProcessorProvider` file is missing, misnamed, or contains the wrong FQN.

**How to avoid:** Template the service file at project creation. Test that the processor actually runs (not just compiles) via kctfork tests that assert generated output exists.

**Warning signs:** Clean build succeeds but no files appear in `build/generated/ksp/`.

### Pitfall 4: Aggregating vs. Isolating Dependencies

**What goes wrong:** Incremental compilation regenerates all output files on any source change, or worse, misses changes and produces stale output.

**Why it happens:** Incorrect `aggregating` flag in `Dependencies()`. Aggregating processors rerun on any change. Isolating processors only rerun when their tracked input files change.

**How to avoid:** Use `Dependencies(aggregating = false, *originatingFiles)` for per-class generation (Hilt modules). Use `Dependencies(aggregating = true)` only for outputs that depend on ALL annotated symbols (manifest summary, stability config). The `kotlinpoet-ksp` `writeTo(codeGenerator, aggregating)` parameter handles this.

**Warning signs:** Unnecessary rebuilds, or stale generated code after adding new annotated classes.

### Pitfall 5: Compose Stability Config File Location

**What goes wrong:** The KSP-generated stability config file is written to a location that the `composeCompiler` DSL can't find, or the convention plugin wires a path that doesn't exist at configuration time.

**Why it happens:** KSP generates files under `build/generated/ksp/`. The Compose compiler needs the file path at Gradle configuration time, but the file only exists after KSP runs during compilation.

**How to avoid:** Use a fixed, predictable path in both the processor and the convention plugin. The processor writes to `build/generated/ksp/{variant}/resources/compose_stability_config.txt`. The convention plugin sets `stabilityConfigurationFile` to that path. Alternatively, the processor can write the file into the KSP source output directory and the plugin reads it from there.

**Warning signs:** Compose compiler warns about "stability configuration file not found" or snapshot types still show as unstable in Compose compiler reports.

### Pitfall 6: Single-Pass vs. Multi-Round Processing

**What goes wrong:** Processor tries to resolve symbols generated by other processors in the same round (e.g., Hilt-generated components).

**Why it happens:** KSP processes in rounds. Symbols generated in round N are available in round N+1. The plugin processor generates Hilt modules, but Hilt's own KSP processor hasn't run yet.

**How to avoid:** The plugin processor generates standalone Hilt modules (interfaces with `@Binds` methods). These don't depend on anything Hilt generates. Keep processor single-pass (`invoked` flag) and return `emptyList()` from `process()`.

**Warning signs:** `KSAnnotated.validate()` returns false for generated types.

## Code Examples

### Reading Annotation Arguments from KSAnnotation

```kotlin
// Source: KSP quickstart docs
fun extractWidgetInfo(classDecl: KSClassDeclaration, logger: KSPLogger): WidgetInfo? {
    val annotation = classDecl.annotations.firstOrNull {
        it.annotationType.resolve().declaration.qualifiedName?.asString() ==
            "app.dqxn.android.sdk.contracts.annotation.DashboardWidget"
    } ?: return null

    val typeId = annotation.arguments
        .first { it.name?.asString() == "typeId" }
        .value as String

    val displayName = annotation.arguments
        .first { it.name?.asString() == "displayName" }
        .value as String

    // Validate typeId format: {packId}:{widget-name}
    val typeIdRegex = Regex("^[a-z][a-z0-9]*:[a-z][a-z0-9-]*$")
    if (!typeIdRegex.matches(typeId)) {
        logger.error(
            "@DashboardWidget typeId must match '{packId}:{widget-name}' format, got: $typeId",
            classDecl
        )
        return null
    }

    return WidgetInfo(
        className = classDecl.simpleName.asString(),
        packageName = classDecl.packageName.asString(),
        typeId = typeId,
        displayName = displayName,
        typeName = classDecl.toClassName(),
        originatingFile = classDecl.containingFile!!,
    )
}
```

### Validating @DashboardSnapshot

```kotlin
// Source: Phase 4 spec + KSP API
fun validateSnapshot(classDecl: KSClassDeclaration, logger: KSPLogger): Boolean {
    var valid = true

    // Must be a data class
    if (classDecl.classKind != ClassKind.CLASS || Modifier.DATA !in classDecl.modifiers) {
        logger.error("@DashboardSnapshot must be applied to a data class", classDecl)
        valid = false
    }

    // Must implement DataSnapshot
    val implementsDataSnapshot = classDecl.superTypes.any {
        it.resolve().declaration.qualifiedName?.asString() ==
            "app.dqxn.android.sdk.contracts.provider.DataSnapshot"
    }
    if (!implementsDataSnapshot) {
        logger.error("@DashboardSnapshot class must implement DataSnapshot", classDecl)
        valid = false
    }

    // Must have @Immutable annotation
    val hasImmutable = classDecl.annotations.any {
        it.annotationType.resolve().declaration.qualifiedName?.asString() ==
            "androidx.compose.runtime.Immutable"
    }
    if (!hasImmutable) {
        logger.error("@DashboardSnapshot class must be annotated with @Immutable", classDecl)
        valid = false
    }

    // All properties must be val (no var)
    classDecl.getAllProperties().forEach { prop ->
        if (prop.isMutable) {
            logger.error("@DashboardSnapshot properties must be val, not var: ${prop.simpleName.asString()}", prop)
            valid = false
        }
    }

    return valid
}
```

### Writing Generated File with KotlinPoet KSP Interop

```kotlin
// Source: KotlinPoet KSP interop docs
fun writeHiltModule(
    codeGenerator: CodeGenerator,
    fileSpec: FileSpec,
    aggregating: Boolean = false,
) {
    // writeTo automatically collects originating KSFiles from all TypeSpec/FunSpec builders
    // that had addOriginatingKSFile() called on them
    fileSpec.writeTo(codeGenerator, aggregating = aggregating)
}
```

### Compile-Testing a Validation Error

```kotlin
// Source: kctfork README + KSP testing pattern
@Test
fun `invalid typeId produces compilation error`() {
    val source = SourceFile.kotlin("BadWidget.kt", """
        package test

        import app.dqxn.android.sdk.contracts.annotation.DashboardWidget

        @DashboardWidget(
            typeId = "INVALID",
            displayName = "Bad Widget",
        )
        class BadWidget
    """.trimIndent())

    val compilation = KotlinCompilation().apply {
        sources = listOf(source, *contractStubs())
        symbolProcessorProviders = listOf(PluginProcessorProvider())
    }
    val result = compilation.compile()

    assertThat(result.exitCode).isEqualTo(ExitCode.COMPILATION_ERROR)
    assertThat(result.messages).contains("typeId must match '{packId}:{widget-name}'")
}
```

### Providing Contract Stubs for Compile-Testing

Since `:codegen:plugin` doesn't depend on `:sdk:contracts` at compile time, tests must provide stub versions of the annotation classes:

```kotlin
// Source: kctfork pattern for providing annotation/interface stubs
fun contractStubs(): Array<SourceFile> = arrayOf(
    SourceFile.kotlin("DashboardWidget.kt", """
        package app.dqxn.android.sdk.contracts.annotation
        @Retention(AnnotationRetention.SOURCE)
        @Target(AnnotationTarget.CLASS)
        annotation class DashboardWidget(
            val typeId: String,
            val displayName: String,
            val icon: String = "",
        )
    """.trimIndent()),
    SourceFile.kotlin("DashboardDataProvider.kt", """
        package app.dqxn.android.sdk.contracts.annotation
        @Retention(AnnotationRetention.SOURCE)
        @Target(AnnotationTarget.CLASS)
        annotation class DashboardDataProvider(
            val localId: String,
            val displayName: String,
            val description: String = "",
        )
    """.trimIndent()),
    SourceFile.kotlin("DashboardSnapshot.kt", """
        package app.dqxn.android.sdk.contracts.annotation
        @Retention(AnnotationRetention.SOURCE)
        @Target(AnnotationTarget.CLASS)
        annotation class DashboardSnapshot(val dataType: String)
    """.trimIndent()),
    SourceFile.kotlin("DataSnapshot.kt", """
        package app.dqxn.android.sdk.contracts.provider
        interface DataSnapshot { val timestamp: Long }
    """.trimIndent()),
    SourceFile.kotlin("Immutable.kt", """
        package androidx.compose.runtime
        @Retention(AnnotationRetention.BINARY)
        @Target(AnnotationTarget.CLASS)
        annotation class Immutable
    """.trimIndent()),
    SourceFile.kotlin("WidgetRenderer.kt", """
        package app.dqxn.android.sdk.contracts.widget
        interface WidgetRenderer
    """.trimIndent()),
    SourceFile.kotlin("DataProvider.kt", """
        package app.dqxn.android.sdk.contracts.provider
        interface DataProvider<T : DataSnapshot>
    """.trimIndent()),
)
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| KAPT annotation processing | KSP 2.x (default since early 2025) | KSP2 became default 2025 | 2x faster, configuration cache compatible, Kotlin-native |
| `tschuchortdev/kotlin-compile-testing` | `ZacSweers/kotlin-compile-testing` (kctfork) | Fork actively maintained since 2023 | Better Kotlin version tracking, maintained by prolific OSS contributor |
| KSP1 (Kotlin-version-prefixed versioning) | KSP2 simplified versioning (e.g., 2.3.6) | 2025 | Version catalog cleaner, decoupled from Kotlin patch versions |
| Manual `@Module` + `@Binds @IntoSet` per pack | KSP-generated Hilt modules | Phase 4 (new) | Zero boilerplate in pack modules, no manual registration |

**Deprecated/outdated:**
- `tschuchortdev/kotlin-compile-testing`: Original library, development stalled. Use `kctfork` instead.
- KSP1 versioning: e.g., `1.9.22-1.0.17`. KSP2 uses simplified version numbers.
- KAPT: Still works but breaks Gradle configuration cache and is 2x slower. Project explicitly bans KAPT (lint rule `KaptDetection`).

## Open Questions

1. **kctfork Kotlin 2.3.10 compatibility**
   - What we know: `kctfork` 0.8.0 released July 2025. Project uses Kotlin 2.3.10.
   - What's unclear: Whether 0.8.0 supports Kotlin 2.3.10 or if a newer version is needed. The `core` artifact is at 0.12.1 which may be more recent.
   - Recommendation: Try 0.8.0 first. If incompatible, check 0.12.1 or search for newer releases. Version mismatch will surface immediately as test compilation errors.

2. **Compose stability config file path coordination**
   - What we know: The processor generates the file, the convention plugin must reference it. Both need to agree on the exact path.
   - What's unclear: Best practice for KSP-generated non-source files (configs, resources) and how to wire them into Compose compiler DSL.
   - Recommendation: Use a hardcoded relative path within the build directory. Both processor and convention plugin reference the same constant. Write the config file as a resource via `codeGenerator.createNewFile(Dependencies.ALL_FILES, "", "compose_stability_config", "txt")`.

3. **@DashboardDataProvider annotation shape: `localId` vs `typeId`**
   - What we know: `@DashboardWidget` uses `typeId` (format: `{packId}:{widget-name}`). `@DashboardDataProvider` currently uses `localId` (from Phase 2).
   - What's unclear: Whether the provider annotation should be updated to use `typeId` format for consistency, or keep `localId` with the `packId:` prefix added at generation time.
   - Recommendation: The processor should combine packId from the module's KSP options with `localId` to form the full `sourceId` for the manifest. The `DataProviderSpec` interface already has `sourceId: String`. The annotation uses `localId` to keep pack-scope identity clear. The convention plugin already passes `packId` via KSP arg.

4. **Agentic processor phase ordering**
   - What we know: `@AgenticCommand` lives in `:core:agentic` (Phase 6). `:codegen:agentic` is built in Phase 4.
   - What's unclear: Whether agentic processor tests can be fully validated in Phase 4 without the real annotation.
   - Recommendation: Per phase-04.md, "Phase 4 builds the processor structure against expected annotation shapes; it becomes functional when Phase 6 provides the annotation types." Tests use compile-testing with synthetic annotation declarations (same pattern as contract stubs above).

5. **Dagger dependency in generated code**
   - What we know: Generated Hilt modules reference `dagger.Module`, `dagger.Binds`, `dagger.multibindings.IntoSet`, `dagger.hilt.InstallIn`, `dagger.hilt.components.SingletonComponent`.
   - What's unclear: Whether the generated code compiles correctly when tested via kctfork (need Dagger/Hilt on the test classpath).
   - Recommendation: For compile-testing, assert that the generated source CODE is correct (string/AST comparison). Don't try to compile the generated Hilt modules within the compile-test -- they need Dagger's KSP processor to fully compile. Test generation correctness, not Dagger integration. Full integration verified in Phase 6 when `:app` assembles.

## Existing Codebase Assets

### Already Present

| Asset | Location | Relevance |
|-------|----------|-----------|
| `@DashboardWidget` annotation | `android/sdk/contracts/.../annotation/DashboardWidget.kt` | Processor's primary input annotation. `typeId`, `displayName`, `icon` params. |
| `@DashboardDataProvider` annotation | `android/sdk/contracts/.../annotation/DashboardDataProvider.kt` | Processor's secondary input. `localId`, `displayName`, `description` params. |
| `@DashboardSnapshot` annotation | `android/sdk/contracts/.../annotation/DashboardSnapshot.kt` | Validation-only processing. `dataType` param. |
| `DashboardPackManifest` | `android/sdk/contracts/.../pack/DashboardPackManifest.kt` | Target type for manifest generation. Has `PackWidgetRef`, `PackDataProviderRef`, `PackThemeRef`, `PackCategory`. |
| `WidgetRenderer` interface | `android/sdk/contracts/.../widget/WidgetRenderer.kt` | Return type for `@Binds @IntoSet` in generated Hilt modules. |
| `DataProvider` interface | `android/sdk/contracts/.../provider/DataProvider.kt` | Return type for `@Binds @IntoSet` in generated Hilt modules. |
| `DataSnapshot` interface | `android/sdk/contracts/.../provider/DataSnapshot.kt` | Supertype that `@DashboardSnapshot` classes must implement. |
| `PackConventionPlugin` | `android/build-logic/.../PackConventionPlugin.kt` | Already wires `ksp(project(":codegen:plugin"))` and passes `themesDir` KSP arg. Needs `packId` arg and stability config wiring. |
| `KotlinJvmConventionPlugin` | `android/build-logic/.../KotlinJvmConventionPlugin.kt` | Applied to `:codegen:*` modules. Sets JVM toolchain 25. |
| Empty codegen stubs | `android/codegen/plugin/build.gradle.kts`, `android/codegen/agentic/build.gradle.kts` | Both have `plugins { id("dqxn.kotlin.jvm") }` only. Ready for dependencies and source. |
| `compose_compiler_config.txt` | `android/sdk/common/compose_compiler_config.txt` | Base stability config (currently empty). Pack-level generated configs supplement this. |
| Old codebase KSP mapping | `.planning/oldcodebase/ksp-processors.md` | Full reference for old processor patterns, annotation shapes, generation output. 7 handlers documented. |

### Needs to be Added

| Asset | Where | Notes |
|-------|-------|-------|
| `kctfork` entries in version catalog | `android/gradle/libs.versions.toml` | `kctfork-core` and `kctfork-ksp` libraries |
| `packId` KSP arg in `PackConventionPlugin` | `android/build-logic/.../PackConventionPlugin.kt` | Processor needs to know which pack module it's running in |
| Stability config file path wiring | `PackConventionPlugin` | `composeCompiler { stabilityConfigurationFile = ... }` |
| Processor dependencies | `android/codegen/plugin/build.gradle.kts` | `ksp-api`, `kotlinpoet`, `kotlinpoet-ksp`, test deps |
| Service provider file | `android/codegen/plugin/src/main/resources/META-INF/services/...` | Registers `PluginProcessorProvider` |
| All processor source code | `android/codegen/plugin/src/main/kotlin/...` | The primary deliverable of this phase |
| All processor tests | `android/codegen/plugin/src/test/kotlin/...` | Compile-testing validation |

## Sources

### Primary (HIGH confidence)
- KSP quickstart: https://kotlinlang.org/docs/ksp-quickstart.html -- Processor API, Resolver, service registration
- KotlinPoet KSP interop: https://square.github.io/kotlinpoet/interop-ksp/ -- `writeTo(CodeGenerator)`, `addOriginatingKSFile()`, type resolution
- Compose stability config: https://developer.android.com/develop/ui/compose/performance/stability/fix -- `stabilityConfigurationFile` DSL
- Project codebase: `android/sdk/contracts/src/main/kotlin/` -- actual annotation shapes, interface contracts (Phase 2 deliverables)
- Old codebase mapping: `.planning/oldcodebase/ksp-processors.md` -- 7 handler patterns, migration notes, weaknesses documented

### Secondary (MEDIUM confidence)
- kctfork: https://github.com/ZacSweers/kotlin-compile-testing -- Compile-testing API, KSP testing support
- kctfork Maven: https://central.sonatype.com/artifact/dev.zacsweers.kctfork/ksp -- Version 0.8.0 (verify Kotlin 2.3.10 compat)
- Hilt multibinding KotlinPoet pattern: https://dagger.dev/dev-guide/ksp.html -- Dagger KSP configuration

### Tertiary (LOW confidence)
- kctfork 0.8.0 Kotlin version compatibility: Not verified against Kotlin 2.3.10. Needs implementation-time validation.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH -- all core libraries (`ksp-api`, `kotlinpoet`, `kotlinpoet-ksp`) already in project catalog with pinned versions. Well-documented APIs.
- Architecture: HIGH -- hub-and-spoke processor pattern proven in old codebase. KotlinPoet Hilt generation is straightforward. Annotation shapes exist from Phase 2.
- Pitfalls: HIGH -- old codebase documents specific failure modes. Compile-testing catches most issues pre-integration.
- Testing: MEDIUM -- `kctfork` version compatibility with Kotlin 2.3.10 is unverified. Fallback options exist but add friction.

**Research date:** 2026-02-24
**Valid until:** 2026-03-24 (stable domain, KSP/KotlinPoet APIs rarely break)
