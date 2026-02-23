# Old Codebase: Build System & Configuration

**Source:** `/Users/ohm/Workspace/dqxn.old/android/`
**Analysis Date:** 2026-02-23

---

## 1. Module Graph

All modules from `settings.gradle.kts`:

```
:app                        — Single-activity entry, DI assembly
:core:common                — Shared utilities, coroutine infrastructure
:core:plugin-api            — Plugin/widget API surface (Compose, serialization, KSP entitlements)
:core:widget-primitives     — Reusable widget UI building blocks
:core:plugin-processor      — KSP processor (pure JVM) — theme/manifest/entitlement codegen
:core:agentic               — Debug agentic framework types (Android library)
:core:agentic-processor     — KSP processor (pure JVM) — agentic command codegen
:data:persistence           — DataStore + Room persistence layer
:feature:dashboard          — Dashboard screen, grid, layout, ViewModel
:feature:driving            — Driving mode feature (empty — just namespace + Hilt)
:feature:packs:free         — Free/essentials widget pack
:feature:packs:themes       — Theme pack (custom themes)
:feature:packs:sg-erp2      — Singapore ERP2 OBU integration pack
:feature:packs:demo         — Demo/mock data pack
```

**Total: 13 modules** (plus root project)

**Not in settings.gradle.kts but has build.gradle.kts:**
- `feature/diagnostics/build.gradle.kts` — exists on disk but NOT included in the build. Namespace `app.dqxn.android.feature.diagnostics`. Would depend on `:core:plugin-api` and `:feature:packs:sg-erp2`.

### Module Dependency Graph

```
:app
├── :core:common
├── :core:plugin-api
├── :core:widget-primitives
├── :core:agentic
├── :core:agentic-processor (ksp)
├── :data:persistence
├── :feature:dashboard
├── :feature:driving
├── :feature:packs:sg-erp2
├── :feature:packs:demo
└── :feature:packs:themes

:feature:dashboard
├── :core:plugin-api
├── :core:widget-primitives
├── :core:agentic
├── :data:persistence
├── :feature:packs:free        ← VIOLATION: dashboard depends on packs
└── :feature:packs:sg-erp2     ← VIOLATION: dashboard depends on packs

:feature:packs:free
├── :core:plugin-api
├── :core:widget-primitives
└── :core:plugin-processor (ksp)

:feature:packs:themes
├── :core:plugin-api
├── :core:widget-primitives
├── :core:common
├── :data:persistence
├── :feature:packs:free        ← pack depends on another pack
└── :core:plugin-processor (ksp)

:feature:packs:sg-erp2
├── :core:plugin-api
├── :core:common
├── :core:widget-primitives
├── :data:persistence
├── :feature:packs:free        ← pack depends on another pack
└── :core:plugin-processor (ksp)

:feature:packs:demo
├── :core:plugin-api
├── :core:common
└── :core:plugin-processor (ksp)

:core:plugin-api
└── :core:plugin-processor (ksp)

:core:widget-primitives
├── :core:plugin-api
└── :data:persistence

:core:common
└── (standalone — hilt only)

:core:agentic
└── :core:agentic-processor (ksp)

:data:persistence
└── :core:common
```

---

## 2. Convention Plugins

Build-logic lives at `build-logic/convention/`.

**Registration** (`build-logic/convention/build.gradle.kts`):

| Plugin ID | Implementation Class |
|---|---|
| `dqxn.android.application` | `AndroidApplicationConventionPlugin` |
| `dqxn.android.library` | `AndroidLibraryConventionPlugin` |
| `dqxn.android.feature` | `AndroidFeatureConventionPlugin` |
| `dqxn.android.hilt` | `AndroidHiltConventionPlugin` |

**Build-logic toolchain:** JDK 21. Depends on AGP, Kotlin, Compose, and KSP gradle plugins as `compileOnly`.

### 2.1 `dqxn.android.application`

**File:** `build-logic/convention/src/main/kotlin/AndroidApplicationConventionPlugin.kt`

**Applies:**
- `com.android.application`
- `org.jetbrains.kotlin.plugin.compose`

**Configures:**
- `compileSdk = 36`
- `minSdk = 31`, `targetSdk = 36`
- `testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"`
- Java source/target compatibility: `VERSION_21`
- `buildFeatures.compose = true`
- `buildFeatures.buildConfig = true`
- Release: `isMinifyEnabled = true`, proguard files (`proguard-android-optimize.txt` + `proguard-rules.pro`)

**Does NOT apply:** Hilt, KSP, Kotlin serialization (these are added per-module)

### 2.2 `dqxn.android.library`

**File:** `build-logic/convention/src/main/kotlin/AndroidLibraryConventionPlugin.kt`

**Applies:**
- `com.android.library`

**Configures:**
- `compileSdk = 36`
- `minSdk = 31`
- `testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"`
- Java source/target compatibility: `VERSION_21`

**Does NOT apply:** Kotlin Compose plugin, Hilt, KSP. Compose is opt-in per-module.

**Note:** Does NOT apply the Kotlin plugin explicitly — AGP 9 manages Kotlin directly (no `org.jetbrains.kotlin.android` needed).

### 2.3 `dqxn.android.feature`

**File:** `build-logic/convention/src/main/kotlin/AndroidFeatureConventionPlugin.kt`

**Applies (chained):**
- `dqxn.android.library` (which applies `com.android.library`)
- `dqxn.android.hilt` (which applies `com.google.devtools.ksp` + `com.google.dagger.hilt.android`)
- `org.jetbrains.kotlin.plugin.compose`

**Configures:**
- `buildFeatures.compose = true`

**Auto-wired dependencies:**
- `implementation(project(":core:common"))`
- Compose BOM (platform): `compose-bom`
- `compose-ui`, `compose-ui-graphics`, `compose-material3`, `compose-material-icons-extended`, `compose-ui-tooling-preview`
- `androidx-lifecycle-runtime-ktx`, `androidx-lifecycle-viewmodel-compose`
- `hilt-navigation-compose`
- `debugImplementation`: `compose-ui-tooling`

**Special:** Registers theme JSON files as KSP task inputs (`src/main/resources/themes/`) if KSP is applied and directory exists. Uses `afterEvaluate` + `PathSensitivity.RELATIVE`.

### 2.4 `dqxn.android.hilt`

**File:** `build-logic/convention/src/main/kotlin/AndroidHiltConventionPlugin.kt`

**Applies:**
- `com.google.devtools.ksp`
- `com.google.dagger.hilt.android`

**Auto-wired dependencies:**
- `implementation`: `hilt-android`
- `ksp`: `hilt-compiler`

---

## 3. Version Catalog

**File:** `gradle/libs.versions.toml`

### Versions

| Key | Value | Notes |
|---|---|---|
| `agp` | `9.0.0` | New target is 9.0.1 |
| `kotlin` | `2.2.0` | New target is 2.3+ (AGP 9 managed) |
| `ksp` | `2.2.0-2.0.2` | Must match Kotlin version |
| `hilt` | `2.59.1` | |
| `room` | `2.8.4` | Dropped in new arch (Proto DataStore replaces Room) |
| `coroutines` | `1.10.2` | |
| `compose-bom` | `2026.01.01` | |
| `navigation` | `2.9.7` | |
| `kotlinxSerialization` | `1.9.0` | |
| `roborazzi` | `1.58.0` | Dropped in new arch (no screenshot tests) |
| `robolectric` | `4.16.1` | |
| `showkase` | `1.0.5` | Not mentioned in new arch |
| `accompanist` | `0.34.0` | |
| `extol` | `2.1.0` | Singapore OBU SDK |
| `minSdk` | `31` | Same |
| `targetSdk` | `36` | Same |
| `compileSdk` | `36` | Same |

### Critical Libraries

**Compose (BOM-managed, no explicit versions):**
- `compose-ui`, `compose-ui-graphics`, `compose-ui-tooling-preview`, `compose-material3`, `compose-material-icons-extended`, `compose-ui-tooling`, `compose-ui-test-manifest`, `compose-ui-test-junit4`

**AndroidX:**
- `androidx-core-ktx` 1.15.0
- `androidx-lifecycle-runtime-ktx` 2.8.7
- `androidx-lifecycle-viewmodel-compose` 2.8.7
- `androidx-activity-compose` 1.9.3
- `androidx-core-splashscreen` 1.0.1
- `androidx-car-app` 1.4.0 (Android Auto — not used in new arch)

**Hilt:**
- `hilt-android` 2.59.1
- `hilt-compiler` 2.59.1 (KSP)
- `hilt-navigation-compose` 1.2.0

**Data:**
- `datastore-preferences` 1.2.0
- `room-runtime` / `room-ktx` / `room-compiler` 2.8.4

**External:**
- `play-services-location` 21.3.0
- `sqlcipher` 4.6.1 (forced for 16KB page compat over OBU SDK's transitive 4.5.7)
- `extol-sdk` 2.1.0 (from `https://extol.mycloudrepo.io/public/repositories/extol-android`)

**KSP / Codegen:**
- `ksp-api` (matches ksp version)
- `kotlinpoet` 1.18.1
- `kotlinpoet-ksp` 1.18.1

**Testing:**
- `junit` 4.13.2 (old uses JUnit 4; new uses JUnit 5)
- `truth` 1.4.2
- `kotlinx-coroutines-test` (matches coroutines version)
- `androidx-junit` 1.2.1
- `androidx-test-core` 1.6.1
- `mockk` 1.13.14
- `turbine` 1.2.0
- `roborazzi` / `roborazzi-compose` 1.58.0
- `robolectric` 4.16.1
- `showkase` / `showkase-processor` 1.0.5

### Plugins

| Alias | Plugin ID | Version |
|---|---|---|
| `android-application` | `com.android.application` | 9.0.0 |
| `android-library` | `com.android.library` | 9.0.0 |
| `kotlin-compose` | `org.jetbrains.kotlin.plugin.compose` | 2.2.0 |
| `hilt` | `com.google.dagger.hilt.android` | 2.59.1 |
| `ksp` | `com.google.devtools.ksp` | 2.2.0-2.0.2 |
| `kotlin-serialization` | `org.jetbrains.kotlin.plugin.serialization` | 2.2.0 |
| `roborazzi` | `io.github.takahirom.roborazzi` | 1.58.0 |

---

## 4. Gradle Configuration

### `gradle.properties`

```properties
kotlin.code.style=official
org.gradle.java.home=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
org.gradle.caching=true
org.gradle.daemon=true
org.gradle.parallel=true
org.gradle.jvmargs=-Xmx3g -Dkotlin.daemon.jvm.options="-Xmx3g" -Dfile.encoding=UTF-8 -XX:+UseParallelGC
org.gradle.configuration-cache=true
android.useAndroidX=true
android.disallowKotlinSourceSets=false
#android.nonTransitiveRClass=true    (commented out)
#android.nonFinalResIds=true         (commented out)
```

**Migration notes for gradle.properties:**
- JDK changes from 21 to 25 in new codebase
- `android.nonTransitiveRClass` and `android.nonFinalResIds` are commented out. New codebase should enable both for build performance.
- `org.gradle.configuration-cache=true` already present (required for AGP 9)

### Gradle Wrapper

- **Version:** 9.3.1 (same as new target)
- **Distribution:** `gradle-9.3.1-bin.zip`

### SDK Versions (hardcoded in convention plugins)

| Setting | Value |
|---|---|
| `compileSdk` | 36 |
| `minSdk` | 31 |
| `targetSdk` | 36 |
| `Java source/target` | VERSION_21 |
| `JVM toolchain` | 21 |
| `buildToolsVersion` | 36.1.0 (app only) |

### Repositories

**Root** (`settings.gradle.kts`):
- `google()`
- `mavenCentral()`
- `https://extol.mycloudrepo.io/public/repositories/extol-android` (OBU SDK)

**Build-logic** (`build-logic/settings.gradle.kts`):
- `google()`
- `mavenCentral()`

**Plugin management:**
- `google()`
- `mavenCentral()`
- `gradlePluginPortal()`

`repositoriesMode = FAIL_ON_PROJECT_REPOS` (no per-module repositories)

---

## 5. Build Variants

### App Module (`:app`)

**Build types:**

| Type | `isDebuggable` | `applicationIdSuffix` | `isMinifyEnabled` | `isShrinkResources` | `DEMO_MODE_AVAILABLE` |
|---|---|---|---|---|---|
| `debug` | `true` | `.debug` | `false` | `false` | `true` |
| `release` | `false` | — | `true` | `true` | `false` |

**No product flavors defined.** Single dimension only.

**compileSdk override in app:**
```kotlin
compileSdk {
    version = release(36) {
        minorApiLevel = 1
    }
}
```
Uses AGP 9's new typed SDK version API with `minorApiLevel = 1` for preview API access.

### Data Persistence Module (`:data:persistence`)

Has its own build type config:
- `debug`: `DEMO_MODE_DEFAULT_ENABLED = true`
- `release`: `DEMO_MODE_DEFAULT_ENABLED = false`

### SG-ERP2 Pack

- `buildConfig = true` (for `OBU_SDK_KEY` build config field)
- Lint disable: `Aligned16KB` (OBU SDK compatibility)

### Managed Devices (`:app`)

```kotlin
localDevices {
    create("pixel6api33") {
        device = "Pixel 6"
        apiLevel = 33
        systemImageSource = "aosp-atd"
    }
}
groups {
    create("phones") {
        targetDevices.add(allDevices["pixel6api33"])
    }
}
```

---

## 6. Per-Module Plugin Usage

| Module | Convention Plugin | Additional Plugins | KSP Processors |
|---|---|---|---|
| `:app` | `dqxn.android.application` + `dqxn.android.hilt` | `kotlin-serialization`, `ksp` | `:core:agentic-processor` |
| `:core:common` | `dqxn.android.library` + `dqxn.android.hilt` | — | (hilt only via convention) |
| `:core:plugin-api` | `dqxn.android.library` | `kotlin-serialization`, `kotlin-compose`, `ksp` | `:core:plugin-processor` |
| `:core:widget-primitives` | `dqxn.android.library` | `kotlin-compose`, `kotlin-serialization` | — |
| `:core:plugin-processor` | `kotlin("jvm")` | `kotlin-serialization` | — (IS a processor) |
| `:core:agentic` | `dqxn.android.library` | `kotlin-serialization`, `ksp` | `:core:agentic-processor` |
| `:core:agentic-processor` | `kotlin("jvm")` | — | — (IS a processor) |
| `:data:persistence` | `dqxn.android.library` + `dqxn.android.hilt` | `kotlin-serialization` | `room-compiler` (via hilt + explicit ksp) |
| `:feature:dashboard` | `dqxn.android.feature` | `kotlin-serialization`, `roborazzi` | (hilt only via convention) |
| `:feature:driving` | `dqxn.android.feature` | — | (hilt only via convention) |
| `:feature:packs:free` | `dqxn.android.feature` | `kotlin-serialization`, `ksp` | `:core:plugin-processor`, `showkase-processor` |
| `:feature:packs:themes` | `dqxn.android.feature` | `kotlin-serialization`, `ksp` | `:core:plugin-processor` |
| `:feature:packs:sg-erp2` | `dqxn.android.feature` | `kotlin-serialization`, `ksp` | `:core:plugin-processor` |
| `:feature:packs:demo` | `dqxn.android.feature` | `ksp` | `:core:plugin-processor` |

---

## 7. KSP Arguments

| Module | KSP Arg Key | Value |
|---|---|---|
| `:app` | `routePackage` | `app.dqxn.android.navigation` |
| `:core:plugin-api` | `entitlementsPath` | `${projectDir}/src/main/resources/entitlements.json` |
| `:feature:packs:free` | `themesDir` | `${projectDir}/src/main/resources/themes` |
| `:feature:packs:free` | `manifestPath:free` | `${projectDir}/src/main/resources/pack/manifest.json` |
| `:feature:packs:themes` | `themesDir` | `${projectDir}/src/main/resources/themes` |
| `:feature:packs:sg-erp2` | `manifestPath:sg-erp2` | `${projectDir}/src/main/resources/pack/manifest.json` |
| `:feature:packs:demo` | `manifestPath:demo` | `${projectDir}/src/main/resources/pack/manifest.json` |

---

## 8. Custom Gradle Tasks

### `updateIanaTimezones` (`:feature:packs:free`)

Registered in `feature/packs/free/build.gradle.kts` as `UpdateIanaTimezonesTask`:
- Fetches `https://data.iana.org/time-zones/data/zone1970.tab`
- Parses ISO 6709 coordinates for each timezone
- Generates `IanaTimezoneCoordinates.kt` at `src/main/java/.../providers/IanaTimezoneCoordinates.kt`
- Run: `./gradlew :feature:packs:free:updateIanaTimezones --no-configuration-cache`

---

## 9. Packaging

`:app` excludes `pack/manifest.json` from packaging:
```kotlin
packaging {
    resources {
        excludes += "pack/manifest.json"
    }
}
```

---

## 10. Migration Notes: Old → New Architecture

### Module Restructuring

| Old Module | New Module(s) | Notes |
|---|---|---|
| `:core:common` | `:sdk:common` | Moves from `core` to `sdk` namespace |
| `:core:plugin-api` | `:sdk:contracts` | API surface for packs; pure Kotlin + `compileOnly(compose.runtime)` |
| `:core:widget-primitives` | `:sdk:ui` | Reusable widget UI; gets Compose compiler |
| `:core:plugin-processor` | `:codegen:plugin` | KSP processor stays pure JVM |
| `:core:agentic` | `:core:agentic` | Stays in core |
| `:core:agentic-processor` | `:codegen:agentic` | KSP processor moves to codegen |
| `:data:persistence` | `:data` | Proto DataStore replaces Room entirely |
| `:feature:dashboard` | `:feature:dashboard` | Must NOT depend on any `:pack:*` |
| `:feature:driving` | REMOVED | Deferred post-launch |
| `:feature:packs:free` | `:pack:essentials` | Renamed; uses `dqxn.pack` convention plugin |
| `:feature:packs:themes` | `:pack:themes` | Uses `dqxn.pack` convention plugin |
| `:feature:packs:sg-erp2` | `:pack:plus` (or separate) | Renamed; uses `dqxn.pack` convention plugin |
| `:feature:packs:demo` | `:pack:demo` | Uses `dqxn.pack` convention plugin |
| (new) | `:sdk:observability` | Split from core |
| (new) | `:sdk:analytics` | Split from core |
| (new) | `:core:design` | Design system module |
| (new) | `:core:thermal` | Thermal management |
| (new) | `:core:firebase` | Firebase isolation |
| (new) | `:feature:settings` | New feature |
| (new) | `:feature:diagnostics` | New feature |
| (new) | `:feature:onboarding` | New feature |
| (new) | `:pack:essentials:snapshots` | Snapshot sub-module |

### Convention Plugin Restructuring

| Old Plugin | New Plugin | Key Differences |
|---|---|---|
| `dqxn.android.application` | `dqxn.android.application` | AGP 9.0.1, Kotlin 2.3+ (AGP-managed, no explicit Kotlin plugin) |
| `dqxn.android.library` | `dqxn.android.library` | Same concept; no explicit Kotlin plugin |
| `dqxn.android.feature` | `dqxn.android.feature` (implied) | Must NOT auto-wire `:core:common`; wires `:sdk:*` instead |
| `dqxn.android.hilt` | `dqxn.android.hilt` | Same concept |
| (new) | `dqxn.android.compose` | Explicit Compose compiler opt-in (was scattered across feature/app/per-module) |
| (new) | `dqxn.pack` | Pack-specific: applies `dqxn.android.compose`, auto-wires `:sdk:*` deps |
| (new) | `dqxn.snapshot` | Snapshot sub-modules: pure Kotlin, no Compose compiler, `:sdk:contracts` only |
| (new) | `dqxn.kotlin.jvm` | Pure JVM modules (codegen processors) |

### Dependency Rule Violations to Fix

The old codebase has these violations that the new architecture explicitly forbids:

1. **`:feature:dashboard` → `:feature:packs:free`** — Dashboard depends directly on the free pack. In new arch, dashboard CANNOT depend on any pack. Pack-specific types needed by dashboard must move to `:sdk:contracts`.

2. **`:feature:dashboard` → `:feature:packs:sg-erp2`** — Same violation. OBU-specific types must be abstracted behind `:sdk:contracts` interfaces.

3. **`:feature:packs:themes` → `:feature:packs:free`** — Pack depends on another pack. In new arch, packs can only depend on `:sdk:*` and `:pack:*:snapshots`. Shared types must move to `:sdk:contracts` or `:sdk:ui`.

4. **`:feature:packs:sg-erp2` → `:feature:packs:free`** — Same cross-pack violation.

5. **`:feature:packs:*` → `:data:persistence`** — Packs depend on persistence layer. In new arch, packs cannot depend on `:data`. Data access must go through `:sdk:contracts` interfaces.

6. **`:core:widget-primitives` → `:data:persistence`** — Core UI module depends on data layer. In new arch, `:sdk:ui` cannot depend on `:data`.

### Version Bumps Required

| Dependency | Old Version | New Target | Notes |
|---|---|---|---|
| AGP | 9.0.0 | 9.0.1 | Minor bump |
| Kotlin | 2.2.0 | 2.3+ | AGP 9 manages Kotlin directly |
| KSP | 2.2.0-2.0.2 | Must match Kotlin 2.3+ | |
| JDK | 21 | 25 | Both toolchain and JAVA_HOME |
| Room | 2.8.4 | REMOVED | Replaced by Proto DataStore |
| JUnit | 4.13.2 | JUnit 5 | Complete migration |
| Roborazzi | 1.58.0 | REMOVED | No screenshot tests in new arch |
| Showkase | 1.0.5 | TBD | Not mentioned in new requirements |
| Android Auto (`car-app`) | 1.4.0 | REMOVED | Not in new arch scope |

### Compose Compiler Scope Changes

**Old:** Compose compiler applied inconsistently — `kotlin-compose` plugin added manually on modules that need it (`plugin-api`, `widget-primitives`, `app`, all features via `dqxn.android.feature`).

**New:** Strict scoping via convention plugins:
- `dqxn.android.compose` — explicit for `:app`, `:feature:*`, `:sdk:ui`, `:core:design`
- `dqxn.pack` — applies `dqxn.android.compose` internally for all packs
- `dqxn.snapshot` — NO Compose compiler (only `@Immutable` via transitive `compose.runtime`)
- `:sdk:contracts` — `compileOnly(compose.runtime)` for annotations only, NO Compose compiler

### Key Architectural Differences in Build

1. **No `org.jetbrains.kotlin.android` plugin** — AGP 9 manages Kotlin directly. The old code uses `org.jetbrains.kotlin.plugin.compose` explicitly; the new arch relies on AGP 9's integration.

2. **Proto DataStore replaces Room** — `room-runtime`, `room-ktx`, `room-compiler` are all removed. `.proto` schemas in `:data` module.

3. **`kotlinx-collections-immutable`** — Not present in old codebase at all. Required everywhere in new arch for `ImmutableList`/`ImmutableMap`.

4. **Pack isolation enforced at build level** — `dqxn.pack` convention plugin restricts dependency scope. Old codebase used `dqxn.android.feature` for packs with no isolation.

5. **Snapshot sub-modules** — New concept entirely. `:pack:*:snapshots` uses `dqxn.snapshot` plugin. Pure Kotlin, no Compose compiler. Only `:sdk:contracts` dependency.

6. **No KAPT anywhere** — Old codebase already uses KSP exclusively (no `kapt()` calls found). This is consistent with new requirements.

7. **Configuration cache** — Already enabled in old codebase. AGP 9 requires it.

### Custom Maven Repository

The EXTOL OBU SDK repository (`https://extol.mycloudrepo.io/public/repositories/extol-android`) is needed only for `:pack:plus` (formerly `:feature:packs:sg-erp2`). Should be conditional or scoped to that pack module in the new architecture rather than declared at root settings level.

---

## 11. Namespace Map

| Module | Namespace |
|---|---|
| `:app` | `app.dqxn.android` |
| `:core:common` | `app.dqxn.android.core.common` |
| `:core:plugin-api` | `app.dqxn.android.core.pluginapi` |
| `:core:widget-primitives` | `app.dqxn.android.core.widgetprimitives` |
| `:core:agentic` | `app.dqxn.android.core.agentic` |
| `:data:persistence` | `app.dqxn.android.data.persistence` |
| `:feature:dashboard` | `app.dqxn.android.feature.dashboard` |
| `:feature:driving` | `app.dqxn.android.feature.driving` |
| `:feature:packs:free` | `app.dqxn.android.feature.packs.free` |
| `:feature:packs:themes` | `app.dqxn.android.feature.packs.themes` |
| `:feature:packs:sg-erp2` | `app.dqxn.android.feature.packs.sgerp2` |
| `:feature:packs:demo` | `app.dqxn.android.feature.packs.demo` |

**New namespace pattern:** `app.dqxn.android.{layer}.{module}` (e.g., `app.dqxn.android.sdk.contracts`, `app.dqxn.android.pack.essentials`)
