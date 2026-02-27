# Plan: Extract Agentic Library to ~/Workspace/agentic/

## Context

The agentic system (ADB debug console, semantics introspection, chaos fault injection) is project-agnostic infrastructure trapped inside DQXN-specific modules (`:core:agentic`, `:codegen:agentic`, `app/src/agentic/`). Extract the reusable framework into a standalone library at `~/Workspace/agentic/` with namespace `dev.agentic.android`, consumed by DQXN via Gradle composite build.

## What moves vs. stays

**Library (`~/Workspace/agentic/`):**
- Command dispatch: `@AgenticCommand`, `CommandHandler`, `CommandParams`, `CommandResult`, `AgenticCommandRouter`
- ADB transport: `BaseAgenticContentProvider` (extracted base class)
- Semantics: `SemanticsOwnerHolder`, `SemanticsSnapshot`, `SemanticsFilter`
- Chaos engine: `ChaosEngine`, `FaultInjector` (new interface), `Fault` (new type hierarchy), `ChaosProfile` (open interface), built-in profiles, session tracking
- Codegen: KSP processor (updated FQNs)
- Built-in handlers: `ping`, `list-commands`, `dump-semantics`, `query-semantics`
- Test utilities: `FakeCommandHandler`, `fakeRouter`

**DQXN (stays):**
- All 17 project-specific handlers (dump-health, add-widget, list-widgets, etc.)
- `ChaosProviderInterceptor` (implements both `FaultInjector` and `DataProviderInterceptor`)
- `AgenticContentProvider` subclass (Hilt `@EntryPoint` wiring)
- `AgenticModule` (DQXN-specific Hilt bindings)
- 3 debug overlays (FrameStats, ThermalTrending, WidgetHealth)
- All handler tests for DQXN-specific handlers

## Library module structure

```
~/Workspace/agentic/
  settings.gradle.kts
  build.gradle.kts              ← group = "dev.agentic.android" on all subprojects
  gradle.properties             ← same JDK/AGP/Gradle as DQXN
  gradle/libs.versions.toml    ← subset of DQXN's catalog
  gradle/wrapper/               ← copy from DQXN (Gradle 9.3.1)

  runtime/                      ← dev.agentic.android.runtime (Android library + Hilt)
    AgenticCommand.kt              @Retention(SOURCE) annotation
    CommandHandler.kt              interface
    CommandParams.kt               data class + extensions
    CommandResult.kt               sealed interface + toJson()
    AgenticCommandRouter.kt        @Singleton dispatch
    BaseAgenticContentProvider.kt  ADB transport base class (NEW)
    handlers/
      PingHandler.kt               built-in
      ListCommandsHandler.kt       built-in
    di/
      AgenticRuntimeModule.kt      @Multibinds + @Binds built-in handlers

  semantics/                    ← dev.agentic.android.semantics (Android library + Hilt)
    SemanticsOwnerHolder.kt        @Singleton, WeakReference<SemanticsOwner>
    SemanticsSnapshot.kt           @Serializable data class
    SemanticsFilter.kt             data class + matches()
    handlers/
      DumpSemanticsHandler.kt      built-in
      QuerySemanticsHandler.kt     built-in
    di/
      AgenticSemanticsModule.kt    @Binds built-in handlers

  chaos/                        ← dev.agentic.android.chaos (Android library + Hilt)
    Fault.kt                       sealed interface (Kill, Stall, Error, Delay, ErrorOnNext, Corrupt, Flap)
    FaultInjector.kt               interface (inject, clear, clearAll, activeFaults)
    ChaosEngine.kt                 @Singleton, takes FaultInjector (not ChaosProviderInterceptor)
    ChaosProfile.kt                open interface (not sealed — consumers can extend)
    ChaosProfileRegistry.kt       mutable registry with fromName/all/register
    ScheduledFault.kt              data class (targetId, not providerId)
    ChaosSession.kt                session + InjectedFault + ChaosSessionSummary
    profiles/                      5 built-in profiles (no ThermalRamp/EntitlementChurn placeholders)
      StressProfile.kt
      FlapProfile.kt
      StormProfile.kt
      KillAllProfile.kt
      CombinedProfile.kt

  codegen/                      ← dev.agentic.android.codegen (JVM library, KSP)
    AgenticProcessor.kt            FQN constants → dev.agentic.android.runtime.*
    AgenticProcessorProvider.kt
    generation/
      CommandRouterGenerator.kt    generated package → dev.agentic.android.runtime.generated
      SchemaGenerator.kt           generated package → dev.agentic.android.runtime.generated
    model/CommandInfo.kt
    resources/META-INF/services/   → dev.agentic.android.codegen.AgenticProcessorProvider

  testing/                      ← dev.agentic.android.testing (Android library, no Hilt)
    FakeCommandHandler.kt          configurable stub
    FakeCommandRouter.kt           factory function
```

## Execution steps

### Step 1: Library project scaffold

Create `~/Workspace/agentic/` with:
- `settings.gradle.kts` — includes `:runtime`, `:semantics`, `:chaos`, `:codegen`, `:testing`
- `build.gradle.kts` — plugin declarations (apply false), `subprojects { group = "dev.agentic.android" }`
- `gradle.properties` — match DQXN (JDK 25, config cache, ZGC, etc.)
- `gradle/libs.versions.toml` — subset: AGP 9.0.1, Kotlin 2.3.10, KSP 2.3.6, Hilt 2.59.2, coroutines 1.10.2, compose-bom 2026.02.00, kotlinx-serialization 1.10.0, kotlinx-collections-immutable 0.3.8, kotlinpoet 1.18.1, kctfork 0.12.1, JUnit5, Truth, Turbine, MockK, mannodermaus-junit 2.0.1
- Copy `gradlew`, `gradlew.bat`, `gradle/wrapper/` from DQXN

**Verify:** `cd ~/Workspace/agentic && ./gradlew projects --console=plain`

### Step 2: Runtime module

Create `runtime/build.gradle.kts` — Android library, Hilt, KSP, serialization, JUnit5.

Port from `core/agentic/src/main/` (package rename `app.dqxn.android.core.agentic` → `dev.agentic.android.runtime`):
- `AgenticCommand.kt`, `CommandHandler.kt`, `CommandParams.kt`, `CommandResult.kt`, `AgenticCommandRouter.kt`

Create new `BaseAgenticContentProvider.kt` — extract from DQXN's `AgenticContentProvider.kt`:
- Abstract: `provideRouter(): AgenticCommandRouter?`, `routerUnavailableError(): String`, `timeoutMs: Long`
- Concrete: ADB transport (temp file protocol, param parsing, cleanup, timeout, error wrapping)
- Open: `onQueryPath(pathSegment): Cursor?` for project-specific escape hatches

Port built-in handlers from `app/src/agentic/kotlin/.../handlers/`:
- `PingHandler.kt` → `runtime/src/main/.../dev/agentic/runtime/handlers/PingHandler.kt`
- `ListCommandsHandler.kt` → `runtime/src/main/.../dev/agentic/runtime/handlers/ListCommandsHandler.kt`

Create `AgenticRuntimeModule.kt` — `@Multibinds Set<CommandHandler>` + `@Binds @IntoSet` for both built-in handlers.

Port tests:
- `AgenticCommandRouterTest.kt` (from `core/agentic/src/test/`)
- `PingHandlerTest.kt` (from `app/src/test/`)
- New `BaseAgenticContentProviderTest.kt` (adapt from `app/src/test/.../AgenticContentProviderTest.kt`)

**Verify:** `./gradlew :runtime:testDebugUnitTest --console=plain`

### Step 3: Semantics module

Create `semantics/build.gradle.kts` — Android library, Hilt, KSP, serialization, `compileOnly(compose.ui)`.

Port from `core/agentic/src/main/` (package rename → `dev.agentic.android.semantics`):
- `SemanticsOwnerHolder.kt`, `SemanticsSnapshot.kt`, `SemanticsFilter.kt`

Port built-in handlers from `app/src/agentic/kotlin/.../handlers/`:
- `DumpSemanticsHandler.kt`, `QuerySemanticsHandler.kt` → `dev.agentic.android.semantics.handlers`

Create `AgenticSemanticsModule.kt` — `@Binds @IntoSet` for both handlers.

Port test: `SemanticsOwnerHolderTest.kt`

**Verify:** `./gradlew :semantics:testDebugUnitTest --console=plain`

### Step 4: Chaos module

Create `chaos/build.gradle.kts` — Android library, Hilt, KSP, coroutines. **No dependency on `:runtime`** — chaos is standalone. No built-in handlers (chaos handlers need domain-specific target discovery).

Create new types:
- `Fault.kt` — `sealed interface` with 7 variants (Kill, Stall, Error, Delay, ErrorOnNext, Corrupt, Flap), replacing DQXN's `ProviderFault`
- `FaultInjector.kt` — `interface` with inject/clear/clearAll/activeFaults
- `ChaosProfileRegistry.kt` — mutable registry, replaces sealed companion `fromName`/`all`

Port + refactor from `core/agentic/src/main/.../chaos/`:
- `ChaosEngine.kt` — constructor takes `FaultInjector` (not `ChaosProviderInterceptor`). `interceptor.injectFault()` → `injector.inject()`, field names `providerId` → `targetId`
- `ChaosProfile.kt` — change from `sealed interface` to open `interface`. Extract 5 built-in profiles (ProviderStress→StressProfile, ProviderFlap→FlapProfile, WidgetStorm→StormProfile, ProcessDeath→KillAllProfile, Combined→CombinedProfile) into `profiles/` package. Drop ThermalRamp and EntitlementChurn (empty placeholders, DQXN-specific).
- `ScheduledFault.kt` — `providerId` → `targetId`, `ProviderFault` → `Fault`
- `ChaosSession.kt`, `InjectedFault.kt`, `ChaosSessionSummary.kt` — `providerId` → `targetId`

Port test: `ChaosEngineTest.kt` — use `FakeFaultInjector` (inline in test), remove ThermalRamp/EntitlementChurn assertions, update Fault type references.

**Verify:** `./gradlew :chaos:testDebugUnitTest --console=plain`

### Step 5: Codegen module

Create `codegen/build.gradle.kts` — JVM library (Kotlin JVM plugin), KSP API, KotlinPoet, kctfork.

Port from `codegen/agentic/src/main/` (package rename → `dev.agentic.android.codegen`):
- `AgenticProcessor.kt` — update FQN constants:
  - `AGENTIC_COMMAND_FQN` → `"dev.agentic.android.runtime.AgenticCommand"`
  - `COMMAND_HANDLER_FQN` → `"dev.agentic.android.runtime.CommandHandler"`
- `AgenticProcessorProvider.kt` — package rename
- `generation/CommandRouterGenerator.kt` — `GENERATED_PACKAGE` → `"dev.agentic.android.runtime.generated"`, `COMMAND_HANDLER` ClassName → `("dev.agentic.android.runtime", "CommandHandler")`
- `generation/SchemaGenerator.kt` — `GENERATED_PACKAGE` → `"dev.agentic.android.runtime.generated"`
- `model/CommandInfo.kt` — package rename
- `META-INF/services` file → `dev.agentic.android.codegen.AgenticProcessorProvider`

Port tests:
- `AgenticStubs.kt` — update stub packages to `dev.agentic.android.runtime`
- `AgenticProcessorTest.kt` — update expected generated package/import assertions

**Verify:** `./gradlew :codegen:test --console=plain`

### Step 6: Testing module

Create `testing/build.gradle.kts` — Android library (no Hilt), depends on `:runtime`.

Create:
- `FakeCommandHandler.kt` — configurable stub implementing `CommandHandler`
- `FakeCommandRouter.kt` — factory creating `AgenticCommandRouter` from fake handlers

**Verify:** `./gradlew :testing:assembleDebug --console=plain`

### Step 7: Wire DQXN composite build + migrate imports

**DQXN settings.gradle.kts:**
- Add `includeBuild("../agentic")` after `pluginManagement` block
- Remove `include(":core:agentic")` and `include(":codegen:agentic")`

**DQXN app/build.gradle.kts — replace deps:**
```diff
- debugImplementation(project(":core:agentic"))
- add("benchmarkImplementation", project(":core:agentic"))
+ debugImplementation("dev.agentic.android:runtime")
+ debugImplementation("dev.agentic.android:semantics")
+ debugImplementation("dev.agentic.android:chaos")
+ add("benchmarkImplementation", "dev.agentic.android:runtime")
+ add("benchmarkImplementation", "dev.agentic.android:semantics")
+ add("benchmarkImplementation", "dev.agentic.android:chaos")
+ testImplementation("dev.agentic.android:testing")

- add("kspDebug", project(":codegen:agentic"))
- add("kspBenchmark", project(":codegen:agentic"))
+ add("kspDebug", "dev.agentic.android:codegen")
+ add("kspBenchmark", "dev.agentic.android:codegen")
```

**DQXN feature/dashboard/build.gradle.kts:**
```diff
- implementation(project(":core:agentic"))
+ implementation("dev.agentic.android:semantics")
```

**Migrate imports across 66 files** (all files matching `app.dqxn.android.core.agentic`):
- `app.dqxn.android.core.agentic.AgenticCommand` → `dev.agentic.android.runtime.AgenticCommand`
- `app.dqxn.android.core.agentic.CommandHandler` → `dev.agentic.android.runtime.CommandHandler`
- `app.dqxn.android.core.agentic.CommandParams` → `dev.agentic.android.runtime.CommandParams`
- `app.dqxn.android.core.agentic.CommandResult` → `dev.agentic.android.runtime.CommandResult`
- `app.dqxn.android.core.agentic.AgenticCommandRouter` → `dev.agentic.android.runtime.AgenticCommandRouter`
- `app.dqxn.android.core.agentic.getString` → `dev.agentic.android.runtime.getString`
- `app.dqxn.android.core.agentic.requireString` → `dev.agentic.android.runtime.requireString`
- `app.dqxn.android.core.agentic.SemanticsOwnerHolder` → `dev.agentic.android.semantics.SemanticsOwnerHolder`
- `app.dqxn.android.core.agentic.SemanticsSnapshot` → `dev.agentic.android.semantics.SemanticsSnapshot`
- `app.dqxn.android.core.agentic.SemanticsFilter` → `dev.agentic.android.semantics.SemanticsFilter`
- `app.dqxn.android.core.agentic.chaos.ChaosEngine` → `dev.agentic.android.chaos.ChaosEngine`
- `app.dqxn.android.core.agentic.chaos.ChaosProfile` → `dev.agentic.android.chaos.ChaosProfile`
- `app.dqxn.android.core.agentic.chaos.ChaosProviderInterceptor` → `app.dqxn.android.agentic.chaos.ChaosProviderInterceptor` (stays in DQXN, new package)

### Step 8: DQXN-side refactoring

**Refactor `AgenticContentProvider.kt`** — thin subclass of `BaseAgenticContentProvider`:
```kotlin
internal class AgenticContentProvider : BaseAgenticContentProvider() {
    @EntryPoint @InstallIn(SingletonComponent::class)
    interface AgenticEntryPoint { fun commandRouter(): AgenticCommandRouter }

    override fun provideRouter(): AgenticCommandRouter? = try {
        EntryPointAccessors.fromApplication(context!!.applicationContext, AgenticEntryPoint::class.java)
            .commandRouter()
    } catch (_: IllegalStateException) { null }

    override fun onQueryPath(pathSegment: String): Cursor? = when (pathSegment) {
        "health", "anr" -> null  // placeholders
        else -> null
    }
}
```

**Move `ChaosProviderInterceptor`** from `core/agentic/` to `app/src/agentic/kotlin/.../agentic/chaos/`:
- Implement both `FaultInjector` (from library) and `DataProviderInterceptor` (from DQXN SDK)
- Store `Fault` types (from library), apply directly to flows — no `ProviderFault` bridge needed

**Update `AgenticModule`:**
```kotlin
@Module @InstallIn(SingletonComponent::class)
internal abstract class AgenticModule {
    @Multibinds abstract fun commandHandlers(): Set<CommandHandler>
    @Binds @IntoSet abstract fun bindChaosInterceptor(impl: ChaosProviderInterceptor): DataProviderInterceptor
    @Binds abstract fun bindFaultInjector(impl: ChaosProviderInterceptor): FaultInjector
}
```

**Update `ChaosInjectHandler`** — construct `Fault` types instead of `ProviderFault`, inject `FaultInjector` instead of `ChaosProviderInterceptor`.

**Delete handlers that moved to library:**
- `PingHandler.kt`, `ListCommandsHandler.kt`, `DumpSemanticsHandler.kt`, `QuerySemanticsHandler.kt` from `app/src/agentic/kotlin/.../handlers/`
- `PingHandlerTest.kt` from `app/src/test/`

**Update lint rule** (`AgenticMainThreadBanDetector.kt`):
```kotlin
private fun isAgenticModule(packageName: String): Boolean =
    packageName.startsWith("dev.agentic.android") ||
    packageName.startsWith("app.dqxn.android.agentic")
```

### Step 9: Cleanup

**Delete from DQXN:**
- Entire `android/core/agentic/` directory
- Entire `android/codegen/agentic/` directory

**Move `ChaosProviderInterceptorTest.kt`** from `core/agentic/src/test/` to `app/src/test/.../agentic/chaos/` — update imports to use `Fault` types from library + `DataProviderInterceptor` from SDK.

### Step 10: Final verification

```bash
# Library: all tests pass
cd ~/Workspace/agentic && ./gradlew test --console=plain

# DQXN: all tests pass
cd ~/Workspace/dqxn/android && ./gradlew test --console=plain

# DQXN: debug APK assembles (Hilt + KSP across composite build boundary)
cd ~/Workspace/dqxn/android && ./gradlew assembleDebug --console=plain
```

## Key design decisions

1. **Chaos module has no `:runtime` dependency** — it's a standalone fault injection engine. Consumers combine it with `:runtime` at the app level if they want chaos *commands*.
2. **No built-in chaos handlers in library** — `chaos-start`/`chaos-stop`/`chaos-inject` need domain-specific target discovery (DQXN uses `Set<DataProvider<*>>`), so they stay project-side.
3. **`ChaosProfile` is an open interface, not sealed** — consumers can define custom profiles and register them via `ChaosProfileRegistry`.
4. **`BaseAgenticContentProvider` uses abstract methods, not interfaces** — ContentProvider lifecycle requires a concrete class. Template method pattern fits naturally.
5. **Library Hilt modules coexist with KSP-generated modules** — duplicate `@Multibinds` declarations are merged by Hilt. Library binds built-in handlers; KSP binds project-specific handlers.
6. **No `ProviderFault` bridge** — DQXN's `ChaosProviderInterceptor` stores library `Fault` types directly and applies them to flows without converting to `ProviderFault`. The `ProviderFault` type in `:sdk:contracts` becomes unused by the interceptor (may be used elsewhere — don't delete).

## Risks

| Risk | Mitigation |
|---|---|
| Hilt module discovery across composite build | Hilt scans transitive deps for @Module — standard pattern for Hilt libraries. Verified. |
| KSP processor JAR resolution via composite build | KSP deps are JVM JARs, not AARs. Composite build resolves identically to project deps. |
| AGP/Kotlin version mismatch across boundary | Both projects use identical toolchain. Version catalog values copied. |
| Duplicate command names (library + leftover DQXN handlers) | KSP processor enforces unique names at compile time — fails fast. |
| `ChaosEngine` constructor change breaks DI | `AgenticModule` must bind `ChaosProviderInterceptor as FaultInjector`. Missing binding = compile-time Hilt error. |
