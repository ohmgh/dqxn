---
phase: 01-build-system-foundation
plan: 01
subsystem: infra
tags: [gradle, agp9, convention-plugins, version-catalog, kotlin-2.3, ksp, hilt, compose, junit5]

# Dependency graph
requires:
  - phase: none
    provides: greenfield — no prior phases
provides:
  - Gradle 9.3.1 wrapper and root build infrastructure
  - Complete version catalog (libs.versions.toml) covering all 13 phases
  - 9 convention plugins enforcing SDK versions, Compose, Hilt, test, pack isolation
  - gradle.properties with config cache, parallel, KSP incremental
  - Build-logic convention module compiling against AGP 9.0.1 + Kotlin 2.3.10
affects: [all-phases, sdk-modules, feature-modules, pack-modules, codegen-modules, lint-rules]

# Tech tracking
tech-stack:
  added: [AGP 9.0.1, Kotlin 2.3.10, KSP 2.3.6, Hilt 2.59.2, Compose BOM 2026.02.00, JUnit5 5.11.4, mannodermaus-junit 2.0.1, Gradle 9.3.1, JDK 25]
  patterns: [class-based-convention-plugins, programmatic-version-catalog-access, plugin-composition-via-apply, tag-filtered-test-tasks]

key-files:
  created:
    - android/gradle/libs.versions.toml
    - android/build-logic/convention/build.gradle.kts
    - android/build-logic/convention/src/main/kotlin/AndroidApplicationConventionPlugin.kt
    - android/build-logic/convention/src/main/kotlin/AndroidLibraryConventionPlugin.kt
    - android/build-logic/convention/src/main/kotlin/AndroidComposeConventionPlugin.kt
    - android/build-logic/convention/src/main/kotlin/AndroidHiltConventionPlugin.kt
    - android/build-logic/convention/src/main/kotlin/AndroidTestConventionPlugin.kt
    - android/build-logic/convention/src/main/kotlin/AndroidFeatureConventionPlugin.kt
    - android/build-logic/convention/src/main/kotlin/PackConventionPlugin.kt
    - android/build-logic/convention/src/main/kotlin/SnapshotConventionPlugin.kt
    - android/build-logic/convention/src/main/kotlin/KotlinJvmConventionPlugin.kt
    - android/gradle.properties
    - android/build.gradle.kts
    - android/settings.gradle.kts
    - android/build-logic/settings.gradle.kts
    - android/sdk/common/compose_compiler_config.txt
  modified: []

key-decisions:
  - "Kotlin 2.3.10 over 2.3.0: latest stable patch with AGP 9.0.1 compatibility"
  - "KSP 2.3.6 with new versioning scheme (no longer Kotlin-version-prefixed)"
  - "kotlinx-serialization 1.10.0: latest stable matching Kotlin 2.3.x"
  - "JDK 25 toolchain with Kotlin fallback to JVM_24 target (Kotlin 2.3.10 does not yet support JVM 25 target)"
  - "stabilityConfigurationFiles (plural) API over deprecated singular stabilityConfigurationFile"
  - "Single shared libs.versions.toml between root and build-logic (via versionCatalogs create/from)"
  - "Compose buildFeatures enabled via plugins.withId for both library and application extensions (AGP 9 CommonExtension lacks direct buildFeatures access)"

patterns-established:
  - "Convention plugin composition: higher-level plugins apply lower-level ones (feature applies library+compose+hilt+test)"
  - "Version catalog programmatic access via VersionCatalogsExtension.named('libs')"
  - "Tag-filtered Test tasks (fastTest, composeTest) as independent task instances cloning classpath from testDebugUnitTest"
  - "No org.jetbrains.kotlin.android anywhere — AGP 9 manages Kotlin directly"

requirements-completed: [F13.10, NF27, NF28, NF35]

# Metrics
duration: 12min
completed: 2026-02-24
---

# Phase 01 Plan 01: Build System Foundation Summary

**Gradle 9.3.1 infrastructure with AGP 9.0.1, Kotlin 2.3.10, 9 convention plugins, and complete 13-phase version catalog**

## Performance

- **Duration:** 12 min
- **Started:** 2026-02-23T16:40:54Z
- **Completed:** 2026-02-23T16:52:42Z
- **Tasks:** 2
- **Files modified:** 20

## Accomplishments
- Complete Gradle infrastructure: wrapper (9.3.1), root build files, settings with FAIL_ON_PROJECT_REPOS
- Full version catalog covering all 13 phases: AGP 9.0.1, Kotlin 2.3.10, KSP 2.3.6, Hilt 2.59.2, Compose BOM 2026.02.00, Firebase, Protobuf, Lint, JUnit5, plus all testing and supporting libraries
- All 9 convention plugins compiling: application, library, compose, hilt, test, feature, pack, snapshot, kotlin-jvm
- AndroidTestConventionPlugin with independent fastTest/composeTest tasks using JUnit5 tag filtering (F13.10)
- SDK version enforcement: compileSdk=36, minSdk=31 (NF27), targetSdk=36 (NF28)
- Build performance: config cache, parallel execution, KSP incremental, ZGC (NF35)

## Task Commits

Each task was committed atomically:

1. **Task 1: Create Gradle wrapper, root build files, version catalog, and gradle.properties** - `63310af` (feat)
2. **Task 2: Implement all 9 convention plugins** - `740dc44` (feat)

## Files Created/Modified
- `android/gradlew` + `android/gradlew.bat` + `android/gradle/wrapper/*` - Gradle 9.3.1 wrapper
- `android/gradle.properties` - Build performance config (config cache, parallel, KSP incremental, ZGC)
- `android/gradle/libs.versions.toml` - Complete version catalog for all 13 phases
- `android/build.gradle.kts` - Root project, all plugins apply-false
- `android/settings.gradle.kts` - Plugin management + FAIL_ON_PROJECT_REPOS
- `android/build-logic/settings.gradle.kts` - Shared catalog from root
- `android/build-logic/convention/build.gradle.kts` - All 9 plugins registered, compileOnly deps for AGP/KGP/KSP/Hilt/Compose/Spotless/JUnit/Serialization
- `android/build-logic/convention/src/main/kotlin/*.kt` - 9 convention plugin implementations
- `android/sdk/common/compose_compiler_config.txt` - Empty placeholder for Compose stability config

## Decisions Made
- **Kotlin 2.3.10** selected as latest stable (2.3.0 also available but 2.3.10 has more patches). KSP 2.3.6 uses a new simplified versioning scheme (not the old `2.3.0-2.0.2` format).
- **kotlinx-serialization 1.10.0** instead of 1.9.0 (old codebase) — latest stable for Kotlin 2.3.x.
- **JDK 25 via toolchain** — Kotlin 2.3.10 falls back to JVM_24 target (JVM 25 not yet supported by Kotlin compiler). This is expected and non-blocking; JDK 25 runtime handles JVM 24 bytecode.
- **`stabilityConfigurationFiles` (plural)** — the singular `stabilityConfigurationFile` was deprecated in Compose compiler 2.3.x. Using the new list-based API.
- **Compose buildFeatures via `plugins.withId`** — AGP 9's unparameterized `CommonExtension` doesn't directly expose `buildFeatures`. The Compose plugin configures both `LibraryExtension` and `ApplicationExtension` via `plugins.withId` checks.
- **Single shared version catalog** — build-logic uses `versionCatalogs { create("libs") { from(files("../gradle/libs.versions.toml")) } }` instead of a separate build-logic catalog. Single source of truth.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Installed JDK 25 via Homebrew**
- **Found during:** Task 1 (Gradle wrapper setup)
- **Issue:** JDK 25 not installed on machine. Gradle toolchain detection failed with "Cannot find a Java installation matching: languageVersion=25"
- **Fix:** Installed `openjdk` (25.0.2) via Homebrew and configured `~/.gradle/gradle.properties` with `org.gradle.java.installations.paths` for Gradle auto-detection
- **Files modified:** None in project (user-level Gradle config)
- **Verification:** `./gradlew :build-logic:convention:assemble` succeeds

**2. [Rule 1 - Bug] Fixed CommonExtension.buildFeatures access in AGP 9**
- **Found during:** Task 2 (AndroidComposeConventionPlugin)
- **Issue:** `extensions.configure<CommonExtension> { buildFeatures { compose = true } }` failed with "Unresolved reference 'buildFeatures'" — AGP 9's unparameterized CommonExtension doesn't expose buildFeatures directly
- **Fix:** Used `plugins.withId("com.android.library")` and `plugins.withId("com.android.application")` to configure the specific extension types that do have buildFeatures
- **Files modified:** AndroidComposeConventionPlugin.kt
- **Verification:** Build compiles cleanly

**3. [Rule 1 - Bug] Fixed deprecated stabilityConfigurationFile API**
- **Found during:** Task 2 (AndroidComposeConventionPlugin)
- **Issue:** `stabilityConfigurationFile` (singular) deprecated in Compose compiler 2.3.x, producing a deprecation warning
- **Fix:** Migrated to `stabilityConfigurationFiles.add()` (plural, new list-based API)
- **Files modified:** AndroidComposeConventionPlugin.kt
- **Verification:** Build compiles with no deprecation warnings

**4. [Rule 3 - Blocking] Fixed KSP version scheme change**
- **Found during:** Task 1 (version catalog creation)
- **Issue:** KSP changed from `{kotlin-version}-{ksp-patch}` format (e.g., `2.2.0-2.0.2`) to simplified `2.3.x` versioning. Initial `2.3.0-2.0.2` reference was invalid
- **Fix:** Used KSP 2.3.6 (latest in new versioning scheme) verified against Maven Central
- **Files modified:** libs.versions.toml
- **Verification:** Build resolves all dependencies

---

**Total deviations:** 4 auto-fixed (2 bugs, 2 blocking)
**Impact on plan:** All fixes necessary for correctness. No scope creep.

## Issues Encountered
- Kotlin 2.3.10 does not yet support JVM 25 as a target. Falls back to JVM_24 with a warning: "Kotlin does not yet support 25 JDK target, falling back to Kotlin JVM_24 JVM target". This is non-blocking — the build works and JDK 25 runtime handles JVM 24 bytecode.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Build infrastructure complete, ready for module stubs (Plan 02), lint rules (Plan 03), and TestKit tests (Plan 04)
- Convention plugins compile but haven't been applied to actual Android modules yet — that validation comes with module stubs in Plan 02
- The fastTest/composeTest task registration pattern uses lazy task references; actual runtime validation requires an Android module to exist

## Self-Check: PASSED

- All 16 key files verified present on disk
- Both task commits (63310af, 740dc44) verified in git log
- `./gradlew :build-logic:convention:assemble` succeeds (BUILD SUCCESSFUL)

---
*Phase: 01-build-system-foundation*
*Completed: 2026-02-24*
