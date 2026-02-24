---
phase: 04-ksp-codegen
plan: 03
subsystem: codegen
tags: [ksp, kotlinpoet, hilt, agentic, compile-testing, kctfork]

# Dependency graph
requires:
  - phase: 01-build-system-foundation
    provides: "Convention plugins, KSP version catalog entries, module stubs"
provides:
  - "AgenticProcessor KSP processor for @AgenticCommand auto-registration"
  - "CommandRouterGenerator producing Hilt @Binds @IntoSet modules"
  - "SchemaGenerator producing self-describing list-commands registry"
  - "Compile-testing infrastructure pattern for KSP processors with kctfork"
affects: [06-deployable-app-agentic]

# Tech tracking
tech-stack:
  added: [kctfork-0.8.0, junit-platform-launcher]
  patterns: [ksp-ksp2-compile-testing, dagger-stubs-for-test, configureKsp-api]

key-files:
  created:
    - android/codegen/agentic/src/main/kotlin/app/dqxn/android/codegen/agentic/AgenticProcessor.kt
    - android/codegen/agentic/src/main/kotlin/app/dqxn/android/codegen/agentic/AgenticProcessorProvider.kt
    - android/codegen/agentic/src/main/kotlin/app/dqxn/android/codegen/agentic/model/CommandInfo.kt
    - android/codegen/agentic/src/main/kotlin/app/dqxn/android/codegen/agentic/generation/CommandRouterGenerator.kt
    - android/codegen/agentic/src/main/kotlin/app/dqxn/android/codegen/agentic/generation/SchemaGenerator.kt
    - android/codegen/agentic/src/main/resources/META-INF/services/com.google.devtools.ksp.processing.SymbolProcessorProvider
    - android/codegen/agentic/src/test/kotlin/app/dqxn/android/codegen/agentic/AgenticProcessorTest.kt
    - android/codegen/agentic/src/test/kotlin/app/dqxn/android/codegen/agentic/AgenticStubs.kt
  modified:
    - android/codegen/agentic/build.gradle.kts

key-decisions:
  - "Dagger annotation stubs in compile-testing instead of withCompilation=false -- KSP2 mode compiles generated sources regardless of setting"
  - "kctfork configureKsp(useKsp2=true) API over direct symbolProcessorProviders assignment -- KSP2 mode required for kctfork 0.8.0 with Kotlin 2.3.10"
  - "useJUnitPlatform + junit-platform-launcher for JVM module test discovery -- dqxn.kotlin.jvm convention plugin does not configure JUnit5"
  - "Duplicate command name detection added to processor -- compile error on two @AgenticCommand classes with same name"

patterns-established:
  - "kctfork KSP2 compile-testing: configureKsp(useKsp2=true) { symbolProcessorProviders += Provider() }"
  - "Dagger/Hilt stub pattern: minimal annotation-shaped stubs for compile-testing without real Dagger dependency"
  - "JVM module test setup: tasks.withType<Test> { useJUnitPlatform() } + testRuntimeOnly(junit-platform-launcher)"

requirements-completed: [F2.12, F3.8]

# Metrics
duration: 12min
completed: 2026-02-24
---

# Phase 4 Plan 3: Agentic KSP Processor Summary

**KSP processor generating Hilt-compatible @Binds @IntoSet command router + self-describing list-commands schema, validated with 6 kctfork compile-tests**

## Performance

- **Duration:** 12 min
- **Started:** 2026-02-24T01:33:48Z
- **Completed:** 2026-02-24T01:46:46Z
- **Tasks:** 2
- **Files modified:** 9

## Accomplishments
- AgenticProcessor scans @AgenticCommand, validates CommandHandler implementation, extracts annotation args, detects duplicate names
- CommandRouterGenerator produces Hilt @Module @InstallIn interface with @Binds @IntoSet per handler (fixes old codebase's no-arg constructor anti-pattern)
- SchemaGenerator produces GeneratedCommandSchema object with CommandSchemaEntry data class for list-commands self-describing registry
- 6 compile-testing tests: Hilt module generation, schema generation, multi-command combined output, empty input, missing interface error, duplicate name error

## Task Commits

Each task was committed atomically:

1. **Task 1: Implement :codegen:agentic processor** - `a3f1d39` (feat)
2. **Task 2: Compile-testing for agentic processor** - `d6fd308` (test)

## Files Created/Modified
- `android/codegen/agentic/build.gradle.kts` - Dependencies: ksp-api, kotlinpoet, kotlinpoet-ksp, kctfork, JUnit5
- `android/codegen/agentic/src/main/kotlin/.../AgenticProcessor.kt` - Single-pass KSP processor with validation
- `android/codegen/agentic/src/main/kotlin/.../AgenticProcessorProvider.kt` - KSP service entry point
- `android/codegen/agentic/src/main/kotlin/.../model/CommandInfo.kt` - Internal data class for extracted command metadata
- `android/codegen/agentic/src/main/kotlin/.../generation/CommandRouterGenerator.kt` - Hilt @Module interface generation with KotlinPoet
- `android/codegen/agentic/src/main/kotlin/.../generation/SchemaGenerator.kt` - GeneratedCommandSchema object generation
- `android/codegen/agentic/src/main/resources/META-INF/services/...SymbolProcessorProvider` - Service loader registration
- `android/codegen/agentic/src/test/kotlin/.../AgenticProcessorTest.kt` - 6 compile-testing tests
- `android/codegen/agentic/src/test/kotlin/.../AgenticStubs.kt` - Synthetic annotation, interface, and Dagger stubs

## Decisions Made
- **Dagger stubs over withCompilation=false**: KSP2 mode in kctfork compiles generated sources regardless of the `withCompilation` flag. Providing minimal Dagger annotation stubs (`@Module`, `@Binds`, `@IntoSet`, `@InstallIn`, `SingletonComponent`) is cleaner than fighting the framework.
- **kctfork KSP2 mode required**: kctfork 0.8.0 bundles kotlin-compiler-embeddable 2.2.0. KSP1 mode doesn't properly invoke the processor with this setup. KSP2 mode via `configureKsp(useKsp2 = true)` works correctly.
- **JVM module JUnit5 setup**: The `dqxn.kotlin.jvm` convention plugin doesn't configure `useJUnitPlatform()` or the JUnit Platform launcher. Both were added directly to the module's build.gradle.kts.
- **Duplicate name detection**: Added to the processor as a compile-time error. Not in the original plan spec but is good practice for preventing runtime confusion.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] kctfork API differences from documented patterns**
- **Found during:** Task 2
- **Issue:** kctfork 0.8.0 uses `JvmCompilationResult` (not `KotlinCompilation.Result`), `configureKsp()` API instead of direct `symbolProcessorProviders` assignment, and `@ExperimentalCompilerApi` opt-in required
- **Fix:** Used correct kctfork 0.8.0 API: `configureKsp(useKsp2=true)`, `@file:OptIn(ExperimentalCompilerApi::class)`, `JvmCompilationResult` return type
- **Files modified:** AgenticProcessorTest.kt
- **Committed in:** d6fd308

**2. [Rule 3 - Blocking] Generated Hilt module fails compilation in test**
- **Found during:** Task 2
- **Issue:** KSP2 mode compiles generated sources. Generated AgenticHiltModule references Dagger classes not on test classpath.
- **Fix:** Added minimal Dagger/Hilt annotation stubs (daggerStubs()) to compile-testing sources
- **Files modified:** AgenticStubs.kt
- **Committed in:** d6fd308

**3. [Rule 3 - Blocking] JUnit5 test discovery fails on JVM module**
- **Found during:** Task 2
- **Issue:** `dqxn.kotlin.jvm` convention plugin doesn't configure `useJUnitPlatform()` or include junit-platform-launcher
- **Fix:** Added `tasks.withType<Test> { useJUnitPlatform() }` and `testRuntimeOnly("org.junit.platform:junit-platform-launcher")` to build.gradle.kts
- **Files modified:** build.gradle.kts
- **Committed in:** d6fd308

---

**Total deviations:** 3 auto-fixed (all Rule 3 - blocking)
**Impact on plan:** All fixes necessary for test infrastructure to function. No scope creep.

## Issues Encountered
- kctfork 0.8.0 bundles kotlin-compiler-embeddable 2.2.0 while project uses Kotlin 2.3.10 -- KSP2 mode handles this gracefully, KSP1 mode does not invoke the processor
- sun.misc.Unsafe deprecation warning from KSP's kotlin-compiler-embeddable -- cosmetic, does not affect correctness

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Agentic processor ready to become functional when Phase 6 provides real `@AgenticCommand` annotation in `:core:agentic`
- Compile-testing pattern established for future KSP processor tests (Plan 01/02 can reuse kctfork setup)
- kctfork KSP2 mode + Dagger stubs pattern documented for Plan 02 tests

## Self-Check: PASSED

All 9 files verified present. Both task commits (a3f1d39, d6fd308) verified in git log.

---
*Phase: 04-ksp-codegen*
*Completed: 2026-02-24*
