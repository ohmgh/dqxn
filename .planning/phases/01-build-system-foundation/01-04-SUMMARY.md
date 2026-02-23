---
phase: 01-build-system-foundation
plan: 04
subsystem: testing, infra
tags: [gradle-testkit, junit5, agp9, convention-plugins, toolchain-validation]

# Dependency graph
requires:
  - phase: 01-01
    provides: "Convention plugins (AndroidLibrary, Compose, Hilt, Pack, Test, Snapshot)"
  - phase: 01-02
    provides: "Version catalog (libs.versions.toml) and module stubs"
provides:
  - "18 TestKit tests validating convention plugin behavior"
  - "Toolchain compatibility matrix (Compose, testFixtures, KSP, Proto DataStore, EXTOL)"
  - "JUnit BOM scoping fix (testImplementation + testRuntimeOnly)"
  - "junit-vintage-engine artifact name fix"
  - "TestProjectSetup shared test infrastructure for TestKit"
affects: [02-sdk-contracts-common, 05-core-infrastructure]

# Tech tracking
tech-stack:
  added: [gradle-testkit, junit-platform-launcher]
  patterns: [root-plus-submodule-testkit, printConfig-task-extraction, assertWithMessage-truth-pattern]

key-files:
  created:
    - android/build-logic/convention/src/test/kotlin/TestProjectSetup.kt
    - android/build-logic/convention/src/test/kotlin/AndroidLibraryPluginTest.kt
    - android/build-logic/convention/src/test/kotlin/AndroidComposePluginTest.kt
    - android/build-logic/convention/src/test/kotlin/AndroidHiltPluginTest.kt
    - android/build-logic/convention/src/test/kotlin/PackPluginTest.kt
    - android/build-logic/convention/src/test/kotlin/AndroidTestPluginTest.kt
    - android/build-logic/convention/src/test/kotlin/VersionCatalogCompletenessTest.kt
  modified:
    - android/build-logic/convention/build.gradle.kts
    - android/build-logic/convention/src/main/kotlin/AndroidTestConventionPlugin.kt
    - android/gradle/libs.versions.toml
    - android/.gitignore
    - .planning/STATE.md

key-decisions:
  - "Root+submodule TestKit pattern: root declares AGP/KSP/Hilt apply-false to resolve buildscript classpath, submodule applies convention plugins"
  - "Proto DataStore plugin incompatible with AGP 9 (BaseExtension removal) — Phase 5 needs Wire migration or custom protoc task"
  - "JUnit BOM must be applied to both testImplementation and testRuntimeOnly to constrain vintage-engine"
  - "EXTOL SDK not available in public repositories — sg-erp2 pack deferred until SDK access obtained"

patterns-established:
  - "TestKit root+submodule: TestProjectSetup.setupSingleModule() / setupMultiModuleForPack() for Android convention plugin testing"
  - "printConfig task pattern: custom task in test project that prints Android DSL values for assertion"
  - "assertWithMessage().that().containsMatch() for Truth regex assertions (not .named() which breaks chain)"

requirements-completed: [F13.10, NF27, NF28, NF35]

# Metrics
duration: 28min
completed: 2026-02-23
---

# Phase 1 Plan 04: TestKit + Toolchain Validation Summary

**18 Gradle TestKit tests validating convention plugin wiring (SDK versions, Compose, Hilt, Pack deps, tag filtering, catalog completeness) plus 7 throwaway toolchain experiments resolving AGP 9 / JDK 25 compatibility**

## Performance

- **Duration:** ~28 min
- **Started:** 2026-02-23T17:14:12Z
- **Completed:** 2026-02-23T17:42:01Z
- **Tasks:** 2
- **Files modified:** 11 (7 created, 4 modified)

## Accomplishments
- 18 TestKit tests across 6 test classes validating all convention plugins: AndroidLibrary (4), AndroidCompose (2), AndroidHilt (2), Pack (3), AndroidTest (3), VersionCatalogCompleteness (4)
- Shared TestProjectSetup infrastructure solving the fundamental Android TestKit classpath problem (convention plugins have compileOnly AGP, TestKit needs it on buildscript classpath)
- Toolchain compatibility matrix: Compose PASS, testFixtures PASS, KSP PASS, tag filtering PASS, full build PASS (38s/25 modules), Proto DataStore FAIL (AGP 9 BaseExtension removal), EXTOL NOT_AVAILABLE
- Fixed JUnit BOM scoping bug that would have broken all testRuntimeOnly dependency resolution in future phases
- Fixed junit-vintage-engine artifact name in version catalog

## Task Commits

Each task was committed atomically:

1. **Task 1: Write Gradle TestKit tests for convention plugins and version catalog** - `220262d` (test)
2. **Task 2: Run throwaway toolchain validations and update STATE.md** - `4749c2a` (fix)

**Plan metadata:** (pending)

_Note: Task 1 commit (220262d) was staged by a concurrent agent with an incorrect commit message ("fix(02): revise plans based on checker feedback") but contains the correct TestKit test files._

## Files Created/Modified

### Created
- `android/build-logic/convention/src/test/kotlin/TestProjectSetup.kt` - Shared TestKit infrastructure: creates temp Gradle projects with root+submodule pattern, parses version catalog for plugin versions
- `android/build-logic/convention/src/test/kotlin/AndroidLibraryPluginTest.kt` - 4 tests: compileSdk=36, minSdk=31, compose disabled, unitTests.isIncludeAndroidResources
- `android/build-logic/convention/src/test/kotlin/AndroidComposePluginTest.kt` - 2 tests: compose=true, compose compiler plugin present
- `android/build-logic/convention/src/test/kotlin/AndroidHiltPluginTest.kt` - 2 tests: KSP+Hilt plugins applied, hilt-android + hilt-compiler in deps
- `android/build-logic/convention/src/test/kotlin/PackPluginTest.kt` - 3 tests: all :sdk:* deps wired, compose+hilt+ksp applied, serialization applied
- `android/build-logic/convention/src/test/kotlin/AndroidTestPluginTest.kt` - 3 tests: fastTest exists, composeTest exists, JUnit5 applied
- `android/build-logic/convention/src/test/kotlin/VersionCatalogCompletenessTest.kt` - 4 tests: required versions (23), libraries (25), plugins (9), gradle-plugin libraries (8)

### Modified
- `android/build-logic/convention/build.gradle.kts` - Added junit-platform-launcher + useJUnitPlatform() for TestKit test execution
- `android/build-logic/convention/src/main/kotlin/AndroidTestConventionPlugin.kt` - JUnit BOM applied to both testImplementation and testRuntimeOnly
- `android/gradle/libs.versions.toml` - Fixed artifact: `vintage-engine` to `junit-vintage-engine`
- `android/.gitignore` - Added `.jqwik-database`

## Decisions Made

1. **Root+submodule TestKit pattern** — Convention plugins have `compileOnly` on AGP, so TestKit can't resolve `LibraryExtension` in flat projects. Solution: root `build.gradle.kts` declares AGP/KSP/Hilt with `apply false` (putting classes on buildscript classpath), submodule `:lib` applies convention plugins. This is now the standard pattern in `TestProjectSetup`.

2. **Proto DataStore plugin incompatible with AGP 9** — `protobuf-gradle-plugin` 0.9.6 casts to `BaseExtension` which was removed in AGP 9. Error: `Cannot cast LibraryExtensionImpl to BaseExtension`. No newer plugin version exists. Phase 5 must use either Wire migration or custom `Exec` task invoking `protoc` directly.

3. **JUnit BOM dual-configuration scoping** — `mannodermaus-junit` 2.0.1 upgrades JUnit BOM from 5.11.4 to 5.14.1. The 5.14.1 BOM no longer constrains `junit-vintage-engine`. Fix: apply BOM to both `testImplementation` and `testRuntimeOnly` so constraints reach all configurations.

4. **EXTOL SDK unavailable** — Not in any public Maven repository. sg-erp2 pack deferred until SDK access is obtained via LTA.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] JUnit BOM scoping only applied to testImplementation**
- **Found during:** Task 2 (fastTest/composeTest tag filtering validation)
- **Issue:** JUnit BOM was only applied to `testImplementation` in AndroidTestConventionPlugin, so `testRuntimeOnly` dependencies (like `junit-vintage-engine`) didn't get version constraints from the BOM. This caused resolution failure when mannodermaus 2.0.1 upgraded to JUnit 5.14.1 BOM which dropped vintage-engine constraints.
- **Fix:** Applied BOM to both configurations via shared `val junitBom = platform(libs.findLibrary("junit-bom").get())` then `add("testImplementation", junitBom)` and `add("testRuntimeOnly", junitBom)`.
- **Files modified:** `android/build-logic/convention/src/main/kotlin/AndroidTestConventionPlugin.kt`
- **Verification:** `./gradlew :sdk:common:testDebugUnitTest` passes, resolves vintage-engine correctly
- **Committed in:** 4749c2a (Task 2 commit)

**2. [Rule 1 - Bug] junit-vintage-engine artifact name wrong in version catalog**
- **Found during:** Task 2 (fastTest/composeTest tag filtering validation)
- **Issue:** `libs.versions.toml` declared artifact name as `vintage-engine` but the correct Maven artifact is `junit-vintage-engine`. Dependency resolution failed.
- **Fix:** Changed `name = "vintage-engine"` to `name = "junit-vintage-engine"` in libs.versions.toml.
- **Files modified:** `android/gradle/libs.versions.toml`
- **Verification:** `./gradlew :sdk:common:testDebugUnitTest` resolves and runs correctly
- **Committed in:** 4749c2a (Task 2 commit)

---

**Total deviations:** 2 auto-fixed (2x Rule 1 - Bug)
**Impact on plan:** Both fixes critical — without them, all JUnit5 test execution with vintage-engine would fail in every module. Caught exactly as intended by throwaway validations.

## Issues Encountered

1. **TestKit classpath resolution for Android convention plugins** — `GradleRunner` with `includeBuild("build-logic")` fails because convention plugins have `compileOnly` AGP. Tried `pluginManagement { plugins { } }` with versions (only sets defaults, doesn't resolve classpath). Solved with root+submodule pattern documented in Decisions.

2. **Truth `.named()` breaks type chain** — `Truth.assertThat(string).named("label").containsMatch(pattern)` fails because `.named()` returns `Subject` base class, losing `StringSubject` methods. Switched to `assertWithMessage("label").that(string).containsMatch(pattern)`.

3. **Concurrent agent commit collision** — A parallel agent committed Task 1's staged files under commit 220262d with message "fix(02): revise plans based on checker feedback". Files are correct but the commit message references phase 02. Accepted as-is since git history is otherwise clean.

## Toolchain Compatibility Results

| Area | Result | Details |
|---|---|---|
| Pack stub + empty KSP | PASS | `:pack:essentials:compileDebugKotlin` succeeds. Empty `:codegen:plugin` (JVM stub) works as no-op KSP processor. |
| fastTest/composeTest tag isolation | PASS | `fastTest` runs 1 of 2 tests (only `@Tag("fast")`). `testDebugUnitTest` runs 2 of 2. Both work in same Gradle invocation. |
| Compose compiler + AGP 9 | PASS | `@Composable` function compiles in `:sdk:ui` with `dqxn.android.compose` plugin. |
| Proto DataStore + JDK 25 | FAIL | `protobuf-gradle-plugin` 0.9.6 casts to `BaseExtension` removed in AGP 9. **Blocker for Phase 5.** |
| testFixtures + AGP 9 | PASS | `android { testFixtures { enable = true } }` works. `android.experimental.enableTestFixturesKotlinSupport=true` still required. |
| EXTOL SDK | NOT_AVAILABLE | Not in public Maven repositories. sg-erp2 pack deferred. |
| Clean build time (stubs) | 38s | `assembleDebug` across 25 modules (589 tasks, 425 executed). Well under NF35 120s target. |

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- **Phase 1 complete.** All 4 plans executed. Convention plugins tested, lint rules validated, toolchain compatibility resolved.
- **Phase 2 (SDK Contracts + Common) unblocked.** All `:sdk:*` stubs exist, convention plugins validated, test infrastructure ready.
- **Phase 5 blocker identified:** Proto DataStore plugin needs Wire migration or custom protoc task. Decision deferred to Phase 5 planning.
- **sg-erp2 blocker identified:** EXTOL SDK not available. Pack deferred until SDK access obtained.

## Self-Check: PASSED

- All 8 files verified present on disk
- Both task commits (220262d, 4749c2a) verified in git log

---
*Phase: 01-build-system-foundation*
*Completed: 2026-02-23*
