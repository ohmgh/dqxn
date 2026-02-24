# Phase 6: Deployable App + Agentic Framework - Research

**Researched:** 2026-02-24
**Domain:** Android app assembly, Hilt DI wiring, ContentProvider-based debug framework, R8/ProGuard, CI pipeline
**Confidence:** HIGH

## Summary

Phase 6 bridges the gap from library modules (Phases 2-5) to a running Android application. It assembles `:app` (single-activity shell with Hilt, edge-to-edge rendering, empty multibinding sets), `:core:agentic` (annotation + handler infrastructure + `AgenticContentProvider` transport), and the full set of Phase 6 diagnostic command handlers in `:app/src/debug/`. The phase also introduces `AlertSoundManager`, `CrashRecovery`, `StubEntitlementManager`, ProGuard/R8 rules for release builds, and CI pipeline configuration.

The core technical challenge is threefold: (1) assembling a Hilt DI graph that resolves all dependencies across 10+ modules including empty multibinding sets for not-yet-migrated packs, (2) implementing the `AgenticContentProvider` transport with correct cold-start race handling and the response-file protocol, and (3) validating that R8 doesn't strip KSP-generated or proto-generated classes in release builds. None of these are architecturally novel -- the patterns are well-documented in ARCHITECTURE.md and the old codebase analysis -- but the wiring requires precision.

The `@AgenticCommand` annotation and `CommandHandler` interface must be created in `:core:agentic` (currently they exist only as compile-testing stubs in `:codegen:agentic`). The KSP processor in `:codegen:agentic` is already built and tested -- Phase 6 provides its real input classes and consumes its generated output (`AgenticHiltModule`, `GeneratedCommandSchema`).

**Primary recommendation:** Build incrementally -- `:core:agentic` foundation first (annotation, handler interface, engine, ContentProvider), then `:app` minimal shell (Hilt application, activity, blank canvas, empty multibinding sets), then diagnostic handlers, then release build validation and CI. Each step is independently testable.

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| F1.1 | Full-screen edge-to-edge rendering, no system chrome by default | `enableEdgeToEdge()` + `WindowInsetsControllerCompat` on `MainActivity`. Old codebase pattern verified. Convention plugin already applies Compose. |
| F1.13 | Dashboard-as-shell pattern (canvas persists beneath all overlays) | Blank canvas placeholder in Phase 6 (real dashboard in Phase 7). `Box` with Layer 0 + Layer 1 overlay structure. |
| F1.23 | Multi-window disabled: `resizeableActivity="false"` | Manifest attribute on `<activity>`. Straightforward. |
| F13.2 | Agentic framework for ADB-driven automation | `AgenticContentProvider` with `call()` + `query()` paths, `@EntryPoint` for Hilt access, response-file protocol. Architecture fully documented in `arch/build-system.md`. |
| F13.4 | Demo mode flag (debug builds only) | `BuildConfig.DEMO_MODE_AVAILABLE` -- `true` in debug, `false` in release via `buildConfigField`. |
| F13.5 | Structured state dumps: ADB-queryable JSON state dumps | `dump-health`, `dump-layout`, `get-metrics`, `list-commands`, `list-diagnostics` handlers against Phase 3/5 observability and data infrastructure. |
| F13.8 | Structured test output: JUnit XML to predictable paths | Already configured by `AndroidTestConventionPlugin` -- `reports.junitXml.outputLocation` set per task. Phase 6 just documents the tiered pipeline. |
| F13.9 | Tiered validation pipeline for agentic development | Documentation deliverable: compile check -> fast tests -> full module tests -> dependent tests -> on-device smoke -> full suite. |
| F13.11 | Semantics tree inspection: ADB-queryable Compose semantics tree | `SemanticsOwnerHolder` singleton in `:core:agentic`, registered by `DashboardLayer` via `RootForTest.semanticsOwner`. `dump-semantics` / `query-semantics` handlers. |
| NF20 | No hardcoded secrets in source | `NoHardcodedSecrets` lint rule already exists (Phase 1). Phase 6 ensures no secrets in `:app` or `:core:agentic` source. SDK keys via `local.properties` pattern. |
| NF21 | Agentic receiver restricted to debug builds | `AgenticContentProvider` registered in `src/debug/AndroidManifest.xml` only. All handlers in `src/debug/`. |
| NF22 | Demo providers gated to debug builds only | `BuildConfig.DEMO_MODE_AVAILABLE` = `false` in release. Demo pack (`pack/demo`) compiled into all builds but gated at runtime by this flag. |
| NF23 | `neverForLocation="true"` on BT scan permission | `<uses-permission android:name="android.permission.BLUETOOTH_SCAN" android:usesPermissionFlags="neverForLocation"/>` in main manifest. |
</phase_requirements>

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Hilt | 2.59.2 | Dependency injection, multibinding sets, `@EntryPoint` for ContentProvider | Already in version catalog, convention plugin wired |
| KSP | 2.3.6 | Agentic command annotation processing | `:codegen:agentic` already built and tested |
| kotlinx.serialization.json | 1.10.0 | Command params parsing, response serialization | Already in catalog, no additional dependency |
| AndroidX Activity Compose | 1.9.3 | `ComponentActivity` + `setContent` | Already in catalog |
| AndroidX Core Splashscreen | 1.0.1 | Splash screen compat | Already in catalog |
| Compose BOM | 2026.02.00 | UI framework for blank canvas | Already in catalog, Compose convention applied by application plugin |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| AndroidX Core KTX | 1.15.0 | `bundleOf`, `WindowInsetsControllerCompat` | ContentProvider response bundles, edge-to-edge |
| LeakCanary | 2.14 | Memory leak detection | `debugImplementation` only in `:app` |
| kotlinx.collections.immutable | 0.3.8 | Immutable collections in handler responses | Already in catalog |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| ContentProvider transport | BroadcastReceiver (old codebase) | ContentProvider runs on binder thread (no ANR timeout), supports concurrent commands, `query()` deadlock-safe path. BroadcastReceiver has 10s ANR timeout and runs on main thread. ContentProvider is strictly better. |
| Response-file protocol | Inline Bundle response | Binder ~1MB transaction limit, `Bundle.toString()` quoting issues with nested JSON. File protocol has no size limit and clean JSON. |
| `@EntryPoint` for ContentProvider Hilt access | `@AndroidEntryPoint` | ContentProvider `onCreate()` runs before `Application.onCreate()`. `@EntryPoint` in `call()` accesses Hilt graph lazily after app initialization. |

## Architecture Patterns

### Recommended File Structure

```
app/
  src/main/kotlin/app/dqxn/android/
    DqxnApplication.kt              # @HiltAndroidApp
    MainActivity.kt                  # Single activity, edge-to-edge, splash
    di/
      AppModule.kt                   # Empty multibinding sets, AlertSoundManager, CrashRecovery, StubEntitlementManager
  src/main/AndroidManifest.xml       # Permissions, resizeableActivity=false
  src/main/res/                      # Launcher icons, themes, strings
  src/debug/kotlin/app/dqxn/android/debug/
    AgenticContentProvider.kt        # ContentProvider transport
    DebugModule.kt                   # Debug-only Hilt bindings
    handlers/                        # All @AgenticCommand handlers
      PingHandler.kt
      DumpHealthHandler.kt
      DumpLayoutHandler.kt
      DiagnoseCrashHandler.kt
      DiagnosePerformanceHandler.kt
      ListDiagnosticsHandler.kt
      GetMetricsHandler.kt
      ListWidgetsHandler.kt
      ListProvidersHandler.kt
      ListThemesHandler.kt
      ListCommandsHandler.kt
      DumpSemanticsHandler.kt
      QuerySemanticsHandler.kt
      TriggerAnomalyHandler.kt
      CaptureSnapshotHandler.kt
    overlays/
      FrameStatsOverlay.kt
      WidgetHealthOverlay.kt
      ThermalTrendingOverlay.kt
  src/debug/AndroidManifest.xml      # AgenticContentProvider registration
  src/release/kotlin/app/dqxn/android/release/
    ReleaseModule.kt                 # No-op implementations for debug interfaces
  proguard-rules.pro                 # R8 keep rules

core/agentic/
  src/main/kotlin/app/dqxn/android/core/agentic/
    AgenticCommand.kt                # @Retention(SOURCE) annotation
    CommandHandler.kt                # Interface for all handlers
    AgenticCommandRouter.kt          # Routes command name -> handler
    CommandResult.kt                 # Standardized response envelope
    SemanticsOwnerHolder.kt          # @Singleton, debug-only semantics access
    SemanticsSnapshot.kt             # Serializable semantics tree model
    SemanticsFilter.kt               # Query filter criteria
```

### Pattern 1: Empty Multibinding Sets for Zero-Pack Startup

**What:** Hilt multibinding requires at least one `@IntoSet` or `@Multibinds` declaration. With no packs compiled yet, `:app` provides empty sets.
**When to use:** When the DI consumer requires `Set<T>` but no providers exist yet.
**Example:**

```kotlin
// Source: Dagger multibindings documentation + old codebase DebugModule pattern
@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {
  @Multibinds abstract fun widgetRenderers(): Set<WidgetRenderer>
  @Multibinds abstract fun dataProviders(): Set<DataProvider<*>>
  @Multibinds abstract fun themeProviders(): Set<ThemeProvider>
  @Multibinds abstract fun dataProviderInterceptors(): Set<DataProviderInterceptor>
  @Multibinds abstract fun packManifests(): Set<DashboardPackManifest>

  companion object {
    @Provides @Singleton
    fun alertSoundManager(
      /* AudioManager, Vibrator, SoundPool via Hilt */
    ): AlertEmitter = AlertSoundManager(...)

    @Provides @Singleton
    fun crashRecovery(
      @ApplicationContext context: Context,
    ): CrashRecovery = CrashRecovery(
      context.getSharedPreferences("crash_recovery", Context.MODE_PRIVATE)
    )

    @Provides @Singleton
    fun stubEntitlementManager(): EntitlementManager =
      StubEntitlementManager()
  }
}
```

**Critical:** Use `@Multibinds` abstract function (not `@Provides` returning `emptySet()`) because `@Multibinds` allows packs to contribute via `@IntoSet` without conflicting with a concrete `@Provides`. A `@Provides Set<T>` and `@IntoSet` on the same type causes a Hilt compile error.

### Pattern 2: AgenticContentProvider with @EntryPoint

**What:** ContentProvider transport that accesses Hilt graph via `@EntryPoint` in `call()`, not `onCreate()`.
**When to use:** Debug-only ContentProvider that needs Hilt dependencies but can't use `@AndroidEntryPoint` due to initialization ordering.
**Example:**

```kotlin
// Source: arch/build-system.md lines 256-331
class AgenticContentProvider : ContentProvider() {
  @EntryPoint
  @InstallIn(SingletonComponent::class)
  interface AgenticEntryPoint {
    fun commandRouter(): AgenticCommandRouter
    fun logger(): DqxnLogger
    fun healthMonitor(): WidgetHealthMonitor
    fun anrWatchdog(): AnrWatchdog
    fun semanticsHolder(): SemanticsOwnerHolder
  }

  override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
    val entryPoint = try {
      EntryPointAccessors.fromApplication(
        context!!.applicationContext, AgenticEntryPoint::class.java
      )
    } catch (e: IllegalStateException) {
      return bundleOf("filePath" to writeResponse(
        """{"status":"error","message":"App initializing, retry after ping"}"""))
    }

    val traceId = "agentic-${SystemClock.elapsedRealtimeNanos()}"
    val resultJson = runBlocking(Dispatchers.Default) {
      withTimeout(8_000) {
        entryPoint.commandRouter().route(method, parseParams(arg, traceId))
      }
    }
    return bundleOf("filePath" to writeResponse(resultJson))
  }

  override fun query(uri: Uri, ...): Cursor? {
    // Lock-free direct reads for deadlock scenarios
    return when (uri.pathSegments.firstOrNull()) {
      "health" -> cachedHealthMonitor?.let { buildHealthCursor(it) }
      "anr" -> cachedAnrWatchdog?.let { buildAnrCursor(it) }
      else -> null
    }
  }

  override fun onCreate(): Boolean {
    context?.cacheDir?.listFiles { f -> f.name.startsWith("agentic_") }
      ?.forEach { it.delete() }
    return true
  }
  // ... other ContentProvider stubs return null/0
}
```

### Pattern 3: Debug/Release Module Split

**What:** Debug build provides full agentic infrastructure. Release build provides no-op stubs for any interfaces that cross the debug/main boundary.
**When to use:** Always for debug-only features that have main-sourceset consumers.
**Example:**

```kotlin
// src/release/kotlin/.../ReleaseModule.kt
@Module
@InstallIn(SingletonComponent::class)
object ReleaseModule {
  // No-op implementations for interfaces consumed by main source set
  // that have real implementations only in debug
}

// src/debug/AndroidManifest.xml
<provider
  android:name=".debug.AgenticContentProvider"
  android:authorities="${applicationId}.debug.agentic"
  android:exported="false" />
```

**Important:** `android:exported="false"` because `adb shell content call` bypasses the exported flag -- it always works for the shell user. Setting `exported="false"` prevents other apps from calling the provider.

### Pattern 4: CrashRecovery via SharedPreferences (Pre-DataStore)

**What:** Synchronous crash timestamp tracking in `Application.onCreate()` before DataStore is available.
**When to use:** Safe mode detection requires reading crash history before any async initialization.
**Example:**

```kotlin
class CrashRecovery(private val prefs: SharedPreferences) {
  fun recordCrash() {
    val now = System.currentTimeMillis()
    val timestamps = getRecentCrashTimestamps()
      .filter { now - it < WINDOW_MS }
      .plus(now)
    prefs.edit().putString(KEY, timestamps.joinToString(",")).commit()
  }

  fun isInSafeMode(): Boolean {
    val timestamps = getRecentCrashTimestamps()
    val now = System.currentTimeMillis()
    return timestamps.count { now - it < WINDOW_MS } >= THRESHOLD
  }

  companion object {
    const val WINDOW_MS = 60_000L
    const val THRESHOLD = 4  // >= 4 crashes in 60s
  }
}
```

### Anti-Patterns to Avoid

- **`@Provides Set<T>` instead of `@Multibinds`:** Providing a concrete empty set prevents packs from contributing via `@IntoSet`. Always use `@Multibinds` for sets that will grow.
- **`runBlocking(Dispatchers.Main)` in CommandHandler:** Causes handler deadlock when main thread is busy with Compose. Enforced by `AgenticMainThreadBan` lint rule.
- **Accessing Hilt in `ContentProvider.onCreate()`:** Hilt graph isn't ready yet. Always use `@EntryPoint` in `call()`/`query()` methods.
- **Inline JSON in Bundle response:** Binder transaction limit (~1MB) and `Bundle.toString()` quoting issues. Always use response-file protocol.
- **`implementation` for `:core:agentic` in `:app`:** Should be `debugImplementation` so the module is completely absent from release APK. R8 tree-shaking is NOT a substitute -- lint rules and manifest merging can still pull in references.
- **`@Singleton` on `SemanticsOwnerHolder` without debug-only scoping:** Must be provided only in `DebugModule`, not main `AppModule`, to prevent release builds from referencing Compose test APIs.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Semantics tree access | Custom accessibility service | `RootForTest.semanticsOwner` from Compose testing API | Stable API, same data source as `ComposeTestRule`, no accessibility service setup |
| JSON serialization for command responses | Manual `StringBuilder` | `kotlinx.serialization.json` `JsonObject` builder | Type-safe, handles escaping, already in dependency graph |
| ContentProvider authority | Hardcoded string | `${applicationId}.debug.agentic` with manifest placeholder | Handles `.debug` suffix in debug builds automatically |
| Edge-to-edge rendering | Manual WindowInsets manipulation | `enableEdgeToEdge()` from AndroidX Activity | Handles status bar, navigation bar, and gesture navigation correctly |
| Empty multibinding sets | `@Provides fun provideWidgets(): Set<WidgetRenderer> = emptySet()` | `@Multibinds abstract fun widgetRenderers(): Set<WidgetRenderer>` | `@Multibinds` is additive; `@Provides` conflicts with `@IntoSet` from packs |

**Key insight:** The agentic framework's value is in the wiring, not novel algorithms. Every component (ContentProvider, Hilt EntryPoint, multibinding, response-file protocol) uses standard Android patterns. The complexity is in getting them to work together across debug/release splits with correct initialization ordering.

## Common Pitfalls

### Pitfall 1: ContentProvider onCreate() vs Application.onCreate() Race

**What goes wrong:** `AgenticContentProvider.onCreate()` runs before `DqxnApplication.onCreate()`. Attempting to access Hilt graph in `ContentProvider.onCreate()` throws `IllegalStateException`.
**Why it happens:** Android initializes ContentProviders before Application. Hilt's `SingletonComponent` is created in `Application.onCreate()`.
**How to avoid:** Access Hilt via `@EntryPoint` + `EntryPointAccessors.fromApplication()` only in `call()` and `query()`, never in `onCreate()`. Wrap in try-catch for the rare cold-start race.
**Warning signs:** `IllegalStateException: The component was not created` in logcat during app launch with immediate adb command.

### Pitfall 2: R8 Stripping KSP-Generated Classes

**What goes wrong:** `assembleRelease` succeeds but APK crashes with `ClassNotFoundException` for KSP-generated `PackManifest` or `AgenticHiltModule` classes.
**Why it happens:** R8's tree-shaking removes classes it deems unreachable. KSP-generated classes referenced only via Hilt multibinding reflection can appear unreachable.
**How to avoid:** Add `consumer-proguard-rules.pro` to modules that produce KSP-generated code. `:codegen:plugin` output, `:data:proto` generated classes, and `kotlinx.serialization` `@Serializable` classes all need keep rules. Validate with `assembleRelease` + device install.
**Warning signs:** Release build installs but crashes on startup, or `Set<WidgetRenderer>` is empty when it shouldn't be. In Phase 6 specifically, the empty sets mean no runtime impact, but the R8 rules must be correct for Phase 8+.

### Pitfall 3: Hilt Multibinding with @Provides vs @Multibinds

**What goes wrong:** Adding `@Provides fun emptyWidgets(): Set<WidgetRenderer> = emptySet()` in `AppModule`. When `:pack:essentials` later adds `@IntoSet` contributions, Hilt fails with "Set<WidgetRenderer> is bound multiple times."
**Why it happens:** `@Provides` creates a concrete binding. `@IntoSet` creates a contribution. Hilt can't merge both.
**How to avoid:** Always use `@Multibinds abstract fun widgetRenderers(): Set<WidgetRenderer>` for sets that packs will contribute to.
**Warning signs:** Hilt compile error mentioning "bound multiple times" when adding pack dependencies to `:app`.

### Pitfall 4: debugImplementation vs implementation for :core:agentic

**What goes wrong:** Using `implementation(project(":core:agentic"))` includes agentic code in release APK. Even if R8 tree-shakes the classes, lint rules, manifest entries, and transitive dependencies leak into release.
**Why it happens:** The old codebase used `implementation` with a "R8 will tree-shake" comment. This is fragile.
**How to avoid:** Use `debugImplementation(project(":core:agentic"))` in `:app`. Ensure all agentic handler code is in `src/debug/` source set. For types that cross the debug/main boundary (if any), define interfaces in main and implementations in debug.
**Warning signs:** Release APK size unexpectedly large, or release lint checks triggering on agentic code.

### Pitfall 5: AgenticContentProvider exported=true Security

**What goes wrong:** Setting `android:exported="true"` on the ContentProvider allows any app on the device to call agentic commands.
**Why it happens:** The old codebase's `AgenticReceiver` was `exported="true"` because broadcasts require it. ContentProvider doesn't.
**How to avoid:** Use `android:exported="false"`. ADB shell always has access regardless of the `exported` flag (shell runs as `u0_a*` with `INTERACT_ACROSS_USERS` or as `shell` user which bypasses the check). Additionally, verify calling UID via `Binder.getCallingUid()` in `call()`.
**Warning signs:** Third-party app successfully calling agentic commands.

### Pitfall 6: SemanticsOwnerHolder Memory Leak

**What goes wrong:** `SemanticsOwnerHolder` holds a strong reference to `SemanticsOwner`, which holds references to the entire Compose tree.
**Why it happens:** The holder is `@Singleton` but the `SemanticsOwner` is tied to the `ComposeView` lifecycle.
**How to avoid:** Clear the reference in `DashboardLayer`'s `DisposableEffect` cleanup. Use `WeakReference` or explicit `register()`/`unregister()` pattern.
**Warning signs:** LeakCanary reports `SemanticsOwner` retained after activity recreation.

### Pitfall 7: Proto Class Stripping in Release Build

**What goes wrong:** Proto-generated classes in `:data:proto` (JVM module) get stripped by R8 because they're only accessed via DataStore serializers at runtime.
**Why it happens:** Proto uses reflection for message parsing. R8 can't trace through `MessageLite.parseFrom()` calls statically.
**How to avoid:** `:data:proto` or `:data` must ship `consumer-proguard-rules.pro` with `-keep class app.dqxn.android.data.proto.** { *; }`. The `consumer-proguard-rules.pro` is automatically merged by AGP when `:app` depends on the library.
**Warning signs:** `ClassNotFoundException` or `NoSuchMethodError` for proto-generated classes at runtime in release builds only.

## Code Examples

### Empty Multibinding Module

```kotlin
// Source: Dagger multibindings docs + phase-06.md AppModule spec
@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {
  @Multibinds abstract fun widgetRenderers(): Set<WidgetRenderer>
  @Multibinds abstract fun dataProviders(): Set<@JvmWildcard DataProvider<*>>
  @Multibinds abstract fun themeProviders(): Set<ThemeProvider>
  @Multibinds abstract fun interceptors(): Set<DataProviderInterceptor>
  @Multibinds abstract fun packManifests(): Set<DashboardPackManifest>
}
```

Note: `Set<DataProvider<*>>` may require `@JvmWildcard` annotation for Hilt to handle the star projection correctly. Verify during implementation.

### AgenticCommandRouter

```kotlin
// Source: arch/build-system.md, codegen:agentic CommandRouterGenerator output
class AgenticCommandRouter @Inject constructor(
  private val handlers: Set<@JvmSuppressWildcards CommandHandler>,
) {
  private val handlerMap: Map<String, CommandHandler> by lazy {
    handlers.associateBy { it.name }
  }

  suspend fun route(method: String, params: CommandParams): String {
    val handler = handlerMap[method]
      ?: return """{"status":"error","message":"Unknown command: $method"}"""
    return try {
      val result = handler.execute(params)
      """{"status":"ok","data":$result}"""
    } catch (e: Exception) {
      """{"status":"error","message":"${e.message?.replace("\"", "\\\"")}"}"""
    }
  }
}
```

### Debug Manifest for ContentProvider

```xml
<!-- src/debug/AndroidManifest.xml -->
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
  <application>
    <provider
      android:name=".debug.AgenticContentProvider"
      android:authorities="${applicationId}.debug.agentic"
      android:exported="false" />
  </application>
</manifest>
```

### MainActivity Minimal Shell

```kotlin
// Source: old codebase MainActivity + CLAUDE.md edge-to-edge pattern
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    installSplashScreen()
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    setContent {
      // Blank canvas placeholder -- real DashboardShell lands in Phase 7
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(Color(0xFF0F172A)) // Dark navy background
          .testTag("dashboard_grid"),
      )
    }
  }
}
```

### CommandHandler Interface (for :core:agentic)

```kotlin
// Source: codegen:agentic AgenticStubs.kt (test stub shape) + arch/build-system.md
interface CommandHandler {
  val name: String
  val description: String
  val category: String
  suspend fun execute(params: CommandParams): String
}

// Minimal params container
data class CommandParams(
  val raw: Map<String, String> = emptyMap(),
  val traceId: String,
)
```

### CrashRecovery Pattern

```kotlin
// Source: phase-06.md CrashRecovery spec
@Singleton
class CrashRecovery @Inject constructor(
  @Named("crash_recovery") private val prefs: SharedPreferences,
) {
  fun recordCrash() {
    val now = System.currentTimeMillis()
    val timestamps = readTimestamps()
      .filter { now - it < WINDOW_MS }
      .plus(now)
    prefs.edit()
      .putString(KEY_TIMESTAMPS, timestamps.joinToString(","))
      .commit() // commit(), not apply() -- must survive process death
  }

  fun isInSafeMode(): Boolean {
    val now = System.currentTimeMillis()
    return readTimestamps().count { now - it < WINDOW_MS } >= THRESHOLD
  }

  fun clearCrashHistory() {
    prefs.edit().remove(KEY_TIMESTAMPS).apply()
  }

  private fun readTimestamps(): List<Long> =
    prefs.getString(KEY_TIMESTAMPS, null)
      ?.split(",")
      ?.mapNotNull { it.toLongOrNull() }
      ?: emptyList()

  companion object {
    private const val KEY_TIMESTAMPS = "crash_timestamps"
    private const val WINDOW_MS = 60_000L
    private const val THRESHOLD = 4
  }
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `BroadcastReceiver` transport (old codebase `AgenticReceiver`) | `ContentProvider` transport (`AgenticContentProvider`) | Phase 6 architecture decision | No ANR timeout, concurrent commands, `query()` deadlock-safe path |
| JSONL logcat output (old `LogcatSink`) | Response-file protocol (temp file in cacheDir) | Phase 6 architecture decision | No Binder size limit, clean JSON parsing, no logcat parsing |
| Manual handler registration via Builder | KSP-generated `@IntoSet` Hilt module + `@AgenticCommand` annotation | Phase 4 + Phase 6 | Type-safe DI, compile-time duplicate detection |
| `implementation` for agentic in release | `debugImplementation` | Phase 6 architecture decision | Zero agentic code in release APK |

**Deprecated/outdated:**
- `BroadcastTransport` from old codebase: Replaced by `AgenticContentProvider`. Don't port.
- `LogcatSink` for command output: Replaced by response-file protocol. Keep `LogcatSink` only for debug log viewing.
- `AgenticEngine.Builder` pattern: Replaced by Hilt `@Inject` constructor + `Set<CommandHandler>` multibinding. Don't port builder.

## Open Questions

1. **`Set<DataProvider<*>>` Star Projection in Hilt**
   - What we know: Hilt multibinding with generic types requires care. `@JvmWildcard` or `@JvmSuppressWildcards` may be needed.
   - What's unclear: Whether Hilt 2.59.2 handles `Set<DataProvider<*>>` directly or needs annotation help. The old codebase didn't use generics in multibinding sets.
   - Recommendation: Test with a minimal Hilt module during implementation. If it fails, use `Set<@JvmSuppressWildcards DataProvider<*>>` (Dagger convention) or introduce a non-generic `DataProviderEntry` wrapper.

2. **:core:agentic as debugImplementation Only**
   - What we know: Phase 6 spec says "debugImplementation only." ARCHITECTURE.md says `:core:agentic` is `debugImplementation`.
   - What's unclear: `@AgenticCommand` annotation has `@Retention(SOURCE)` -- it's consumed by KSP at compile time only. But the `CommandHandler` interface is needed at runtime for the generated Hilt module. If `:core:agentic` is `debugImplementation`, the generated `AgenticHiltModule` (which references `CommandHandler`) only works in debug. This is correct for Phase 6, but the KSP processor runs as `debugKsp` -- need to verify that KSP only processes debug source sets.
   - Recommendation: Wire `:core:agentic` as `debugImplementation` in `:app`. Configure `:codegen:agentic` KSP as `kspDebug` (not `ksp`). Handlers in `src/debug/` naturally have access. Verify the generated module only appears in debug build artifacts.

3. **Response File Cleanup Race Condition**
   - What we know: `AgenticContentProvider.onCreate()` cleans up old response files. New files are created per command.
   - What's unclear: If the agent reads the file path from Bundle but then the ContentProvider processes another command that triggers `onCreate()` (e.g., after process death), could the file be deleted before the agent reads it?
   - Recommendation: `onCreate()` runs once per process lifecycle, not per command. The race is between process restarts only. Accept this -- if the process dies, the response is lost anyway. Agent retries.

4. **Debug Overlays: Compose in src/debug/**
   - What we know: Phase 6 spec lists 3 debug overlays (Frame Stats, Widget Health, Thermal Trending). Phase-06.md lists 6. ARCHITECTURE.md says "V1 ships three overlays."
   - What's unclear: Whether to build all 6 or just 3 in Phase 6.
   - Recommendation: Build the 3 that ARCHITECTURE.md specifies (Frame Stats, Widget Health, Thermal Trending). The other 3 (Recomposition Visualization, Provider Flow DAG, State Machine Viewer) are deferred per ARCHITECTURE.md.

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit5 (via `dqxn.android.test` convention plugin) + MockK 1.14.9 + Truth 1.4.4 |
| Config file | Convention plugin auto-configures `useJUnitPlatform()` |
| Quick run command | `./gradlew :app:fastTest :core:agentic:fastTest --console=plain` |
| Full suite command | `./gradlew :app:testDebugUnitTest :core:agentic:testDebugUnitTest --console=plain` |
| Estimated runtime | ~20-30 seconds for Phase 6 unit tests |

### Phase Requirements -> Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| F1.1 | Edge-to-edge rendering | smoke (on-device) | `adb shell content call --uri content://app.dqxn.android.debug.agentic --method query-semantics --arg '{"testTag":"dashboard_grid"}'` | No -- Wave 0 |
| F1.13 | Dashboard-as-shell pattern | unit (composition) | `./gradlew :app:testDebugUnitTest --tests "*.MainActivityTest"` | No -- Wave 0 |
| F1.23 | Multi-window disabled | manifest verification | Lint or manual manifest inspection | No -- Wave 0 |
| F13.2 | Agentic framework | unit | `./gradlew :core:agentic:testDebugUnitTest --console=plain` | No -- Wave 0 |
| F13.4 | Demo mode flag | unit | `./gradlew :app:testDebugUnitTest --tests "*.BuildConfigTest"` | No -- Wave 0 |
| F13.5 | Structured state dumps | unit | `./gradlew :app:testDebugUnitTest --tests "*.handlers.*"` | No -- Wave 0 |
| F13.8 | Structured test output | convention verification | Already configured by AndroidTestConventionPlugin | Existing |
| F13.9 | Tiered validation pipeline | documentation | N/A (documentation deliverable) | N/A |
| F13.11 | Semantics tree inspection | unit | `./gradlew :core:agentic:testDebugUnitTest --tests "*.SemanticsOwnerHolderTest"` | No -- Wave 0 |
| NF20 | No hardcoded secrets | lint | `./gradlew :app:lintDebug --console=plain` | Existing |
| NF21 | Agentic debug-only | manifest/build verification | Verify AgenticContentProvider only in debug manifest | No -- Wave 0 |
| NF22 | Demo providers debug-only | unit | `./gradlew :app:testDebugUnitTest --tests "*.BuildConfigTest"` | No -- Wave 0 |
| NF23 | BT neverForLocation | manifest verification | Lint or manual manifest inspection | No -- Wave 0 |

### Nyquist Sampling Rate

- **Minimum sample interval:** After every committed task -> run: `./gradlew :app:fastTest :core:agentic:fastTest --console=plain`
- **Full suite trigger:** Before merging final task of any plan wave
- **Phase-complete gate:** `./gradlew :app:testDebugUnitTest :core:agentic:testDebugUnitTest :app:assembleRelease --console=plain` all green before verification
- **Estimated feedback latency per task:** ~15-25 seconds

### Wave 0 Gaps (must be created before implementation)

- [ ] `core/agentic/src/test/kotlin/.../AgenticCommandRouterTest.kt` -- covers F13.2 command routing
- [ ] `core/agentic/src/test/kotlin/.../SemanticsOwnerHolderTest.kt` -- covers F13.11 semantics tree access
- [ ] `app/src/test/kotlin/.../handlers/PingHandlerTest.kt` -- covers F13.2 ping probe
- [ ] `app/src/test/kotlin/.../handlers/DumpHealthHandlerTest.kt` -- covers F13.5 state dumps
- [ ] `app/src/test/kotlin/.../AlertSoundManagerTest.kt` -- covers AlertEmitter implementation
- [ ] `app/src/test/kotlin/.../CrashRecoveryTest.kt` -- covers safe mode detection
- [ ] `app/src/test/kotlin/.../AgenticContentProviderTest.kt` -- covers F13.2 transport (cold-start race, timeout, concurrent calls)

## Sources

### Primary (HIGH confidence)

- `/Users/ohm/Workspace/dqxn/.planning/arch/build-system.md` -- Full `AgenticContentProvider` pseudocode, threading model, response protocol, semantics tree API, command registry, compound diagnostics. Lines 110-870.
- `/Users/ohm/Workspace/dqxn/.planning/migration/phase-06.md` -- Phase 6 spec: `:app` module assembly, `:core:agentic` infrastructure, handler inventory, test requirements, validation criteria.
- `/Users/ohm/Workspace/dqxn/.planning/oldcodebase/app-module.md` -- Old codebase `:app` module: DI assembly, debug/release split, manifest, entry points, agentic receiver pattern.
- `/Users/ohm/Workspace/dqxn/.planning/oldcodebase/core-libraries.md` -- Old codebase `core/agentic`: `CommandHandler`, `AgenticEngine`, `CommandDispatcher`, handler inventory, builder pattern.
- `/Users/ohm/Workspace/dqxn/android/codegen/agentic/src/` -- KSP processor already built: `AgenticProcessor`, `CommandRouterGenerator`, `SchemaGenerator`. Generates `AgenticHiltModule` and `GeneratedCommandSchema`.
- `/Users/ohm/Workspace/dqxn/android/sdk/observability/src/main/kotlin/` -- Phase 3 observability: `WidgetHealthMonitor`, `MetricsCollector`, `DiagnosticSnapshotCapture`, `CrashEvidenceWriter` (all handler data sources).
- `/Users/ohm/Workspace/dqxn/android/sdk/contracts/src/main/kotlin/` -- Phase 2 contracts: `AlertEmitter`, `EntitlementManager`, `WidgetRenderer`, `DataProvider`, `ThemeProvider`, `DataProviderInterceptor`, `DashboardPackManifest`.

### Secondary (MEDIUM confidence)

- Hilt multibindings documentation (Dagger/Hilt official) -- `@Multibinds` vs `@Provides` for empty sets
- AndroidX Activity `enableEdgeToEdge()` documentation -- edge-to-edge rendering pattern
- Android ContentProvider documentation -- `onCreate()` lifecycle ordering, binder thread pool

### Tertiary (LOW confidence)

- `Set<DataProvider<*>>` Hilt multibinding behavior with star projections -- needs runtime validation

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH -- all dependencies already in version catalog, convention plugins tested
- Architecture: HIGH -- `AgenticContentProvider` pseudocode in ARCHITECTURE.md, old codebase patterns verified, KSP processor already built
- Pitfalls: HIGH -- cold-start race, R8 stripping, multibinding patterns are well-documented in project docs and old codebase analysis
- Testing: HIGH -- JUnit5 + MockK infrastructure established in Phase 1, test convention plugin verified

**Research date:** 2026-02-24
**Valid until:** 2026-03-24 (stable domain -- Android ContentProvider, Hilt patterns are mature)
