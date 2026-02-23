---
phase: 01-build-system-foundation
plan: 02
subsystem: infra
tags: [gradle, module-stubs, settings, spotless, ktfmt, editorconfig, pre-commit, convention-plugins]

# Dependency graph
requires:
  - phase: 01-build-system-foundation (plan 01)
    provides: Gradle infrastructure, 9 convention plugins, version catalog
provides:
  - settings.gradle.kts with all 25 module includes (stable from Phase 1 onward)
  - 25 module stubs with correct convention plugins and namespaces
  - Pack auto-wiring of all :sdk:* dependencies via dqxn.pack plugin
  - Spotless + ktfmt Google style formatting enforcement
  - .editorconfig aligned with ktfmt conventions
  - Pre-commit hook with formatting, kapt, and boundary checks
affects: [all-phases, sdk-modules, feature-modules, pack-modules, codegen-modules, core-modules, data-module]

# Tech tracking
tech-stack:
  added: [spotless-6.25.0, ktfmt-google-style]
  patterns: [module-stub-convention-plugin-only, spotless-root-only, pre-commit-boundary-checks, afterEvaluate-deferred-task-registration]

key-files:
  created:
    - android/settings.gradle.kts (updated with 25 module includes)
    - android/sdk/contracts/build.gradle.kts
    - android/sdk/common/build.gradle.kts
    - android/sdk/ui/build.gradle.kts
    - android/sdk/observability/build.gradle.kts
    - android/sdk/analytics/build.gradle.kts
    - android/core/design/build.gradle.kts
    - android/core/thermal/build.gradle.kts
    - android/core/firebase/build.gradle.kts
    - android/core/agentic/build.gradle.kts
    - android/codegen/plugin/build.gradle.kts
    - android/codegen/agentic/build.gradle.kts
    - android/data/build.gradle.kts
    - android/feature/dashboard/build.gradle.kts
    - android/feature/settings/build.gradle.kts
    - android/feature/diagnostics/build.gradle.kts
    - android/feature/onboarding/build.gradle.kts
    - android/pack/essentials/build.gradle.kts
    - android/pack/essentials/snapshots/build.gradle.kts
    - android/pack/plus/build.gradle.kts
    - android/pack/themes/build.gradle.kts
    - android/pack/demo/build.gradle.kts
    - android/app/build.gradle.kts
    - android/lint-rules/build.gradle.kts
    - android/baselineprofile/build.gradle.kts
    - android/benchmark/build.gradle.kts
    - android/.editorconfig
    - android/.githooks/pre-commit
  modified:
    - android/build.gradle.kts (added Spotless plugin + installGitHooks task)
    - android/build-logic/convention/src/main/kotlin/AndroidTestConventionPlugin.kt (afterEvaluate fix)

key-decisions:
  - "All 25 modules created together: Gradle 9.3.1 requires all included project directories to exist during configuration"
  - "afterEvaluate for tag-filtered test tasks: AGP registers testDebugUnitTest during variant creation, not plugin apply"
  - "baselineprofile uses com.android.test with targetProjectPath = :app"
  - "ktfmt Google style (2-space indent): consistent with Google Kotlin conventions"

patterns-established:
  - "Module stubs: convention plugin + namespace only, no source files until implementation phase"
  - "Spotless applied at root only, reformats all .kt and .kts via Google ktfmt style"
  - "Pre-commit hook: spotlessCheck + kapt detection + pack boundary violation"

requirements-completed: [NF27, NF28]

# Metrics
duration: 7min
completed: 2026-02-24
---

# Phase 01 Plan 02: Module Stubs Summary

**25 module stubs with convention plugin wiring, settings.gradle.kts stable graph, Spotless/ktfmt formatting, and pre-commit boundary checks**

## Performance

- **Duration:** 7 min
- **Started:** 2026-02-23T16:57:08Z
- **Completed:** 2026-02-23T17:04:51Z
- **Tasks:** 3
- **Files modified:** 62

## Accomplishments
- Complete settings.gradle.kts with all 25 modules included: 5 sdk, 4 core, 2 codegen, 1 data, 4 feature, 5 pack (including essentials:snapshots), 1 app, 3 support (lint-rules, baselineprofile, benchmark)
- Every module stub applies its correct convention plugin: library, feature, pack, snapshot, kotlin-jvm, application, com.android.test
- Pack dependency auto-wiring verified: `:pack:essentials` classpath shows all 5 `:sdk:*` modules
- Spotless with ktfmt Google style enforced across all .kt and .kts files
- Pre-commit hook checks formatting, kapt usage, and pack-to-feature boundary violations

## Task Commits

Each task was committed atomically:

1. **Task 1: Create settings.gradle.kts, sdk/core/codegen/data stubs** - `d2e7656` (feat)
2. **Task 2: Create feature, pack, app, and support module stubs** - `5f8d6d2` (feat)
3. **Task 3: Configure Spotless, .editorconfig, and pre-commit hook** - `b7050dc` (chore)

## Files Created/Modified
- `android/settings.gradle.kts` - Complete 25-module include graph
- `android/sdk/*/build.gradle.kts` - 5 SDK module stubs (contracts, common, ui, observability, analytics)
- `android/core/*/build.gradle.kts` - 4 core module stubs (design, thermal, firebase, agentic)
- `android/codegen/*/build.gradle.kts` - 2 codegen JVM stubs (plugin, agentic)
- `android/data/build.gradle.kts` - Data layer stub with Hilt
- `android/feature/*/build.gradle.kts` - 4 feature module stubs (dashboard, settings, diagnostics, onboarding)
- `android/pack/*/build.gradle.kts` - 5 pack module stubs (essentials, essentials:snapshots, plus, themes, demo)
- `android/app/build.gradle.kts` - Application module with all feature/pack/core/data/sdk dependencies
- `android/lint-rules/build.gradle.kts` - JVM lint rules stub
- `android/baselineprofile/build.gradle.kts` - com.android.test targeting :app
- `android/benchmark/build.gradle.kts` - Library stub for macrobenchmark
- `android/.editorconfig` - Editor config aligned with ktfmt Google style
- `android/.githooks/pre-commit` - Formatting + boundary check hook
- `android/build.gradle.kts` - Added Spotless plugin + installGitHooks task
- `android/build-logic/convention/src/main/kotlin/AndroidTestConventionPlugin.kt` - afterEvaluate fix for test task registration
- 25x `src/main/AndroidManifest.xml` - Empty manifests for Android modules

## Decisions Made
- **All modules created together:** Gradle 9.3.1 requires every `include()`'d project directory to exist at configuration time. Tasks 1 and 2 were structurally linked — couldn't verify sdk stubs without feature/pack/app directories existing.
- **afterEvaluate for test task registration:** `tasks.named("testDebugUnitTest")` fails if called during `plugins.withId` callback because AGP registers test tasks during variant creation (after plugin apply). Wrapping in `afterEvaluate` defers until the task exists.
- **baselineprofile targetProjectPath:** The `com.android.test` plugin requires `targetProjectPath` or it fails with "targetProjectPath cannot be null". Set to `:app`.
- **ktfmt Google style (2-space indent):** Applied to all existing and new files. Convention plugins from Plan 01 were also reformatted.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Created all module directories for Gradle configuration**
- **Found during:** Task 1 (settings.gradle.kts verification)
- **Issue:** Gradle 9.3.1 requires all `include()`'d project directories to exist during configuration. Running `:sdk:contracts:tasks` failed because `:feature:diagnostics` directory didn't exist yet.
- **Fix:** Created all 25 module directories and their build.gradle.kts stubs upfront instead of splitting across Tasks 1 and 2
- **Files modified:** All module directories and build files
- **Verification:** `./gradlew tasks` succeeds
- **Committed in:** d2e7656 (Task 1), 5f8d6d2 (Task 2)

**2. [Rule 1 - Bug] Fixed AndroidTestConventionPlugin task registration timing**
- **Found during:** Task 1 (Gradle configuration)
- **Issue:** `tasks.named("testDebugUnitTest")` threw "Task not found" when called inside `plugins.withId("com.android.application")` callback for `:app` module. AGP registers variant test tasks after plugin apply.
- **Fix:** Wrapped tag-filtered test task registration in `afterEvaluate` to defer until `testDebugUnitTest` exists
- **Files modified:** AndroidTestConventionPlugin.kt
- **Verification:** `:app` module configures successfully, fastTest and composeTest tasks registered
- **Committed in:** d2e7656 (Task 1)

**3. [Rule 1 - Bug] Fixed baselineprofile missing targetProjectPath**
- **Found during:** Task 1 (Gradle configuration)
- **Issue:** `com.android.test` plugin requires `targetProjectPath` to be set. Without it, configuration fails with "targetProjectPath cannot be null in test project baselineprofile".
- **Fix:** Added `targetProjectPath = ":app"` to baselineprofile build.gradle.kts
- **Files modified:** baselineprofile/build.gradle.kts
- **Verification:** `./gradlew tasks` succeeds
- **Committed in:** 5f8d6d2 (Task 2)

**4. [Rule 1 - Bug] Fixed Exec task in root build.gradle.kts**
- **Found during:** Task 3 (Spotless configuration)
- **Issue:** `exec { commandLine(...) }` inside `doLast` block failed with "Unresolved reference 'exec'" — wrong API for Gradle task DSL
- **Fix:** Changed to `tasks.register<Exec>("installGitHooks")` with direct `commandLine()` property
- **Files modified:** build.gradle.kts
- **Verification:** Build script compiles
- **Committed in:** b7050dc (Task 3)

---

**Total deviations:** 4 auto-fixed (3 bugs, 1 blocking)
**Impact on plan:** All fixes necessary for correct Gradle configuration. No scope creep.

## Issues Encountered
- Spotless configuration cache staleness: After reformatting files, Spotless's JVM-local cache becomes stale with Gradle config cache enabled. Required `rm -rf .gradle/configuration-cache` to resolve. Known upstream issue (diffplug/spotless#987). Non-blocking — subsequent runs work correctly after cache regeneration.
- JDK 25 must be explicitly set via JAVA_HOME for Gradle daemon: The system default `java` is JDK 21 but convention plugins require JDK 25. Resolved by setting `JAVA_HOME` to the Homebrew-installed JDK 25 path.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- All 25 module stubs exist with correct convention plugins — ready for Plan 03 (lint rules) and Plan 04 (TestKit tests)
- settings.gradle.kts is stable and complete — no subsequent phase needs to modify it
- Convention plugins validated against real Android modules (not just build-logic compilation)
- Spotless formatting baseline established — all future code will be auto-formatted

## Self-Check: PASSED

- All 28 key files verified present on disk
- All 3 task commits (d2e7656, 5f8d6d2, b7050dc) verified in git log
- `./gradlew tasks` succeeds (BUILD SUCCESSFUL)
- `./gradlew spotlessCheck` passes
- Pack essentials dependency graph shows all 5 :sdk:* modules
- 25 modules in settings.gradle.kts confirmed

---
*Phase: 01-build-system-foundation*
*Completed: 2026-02-24*
