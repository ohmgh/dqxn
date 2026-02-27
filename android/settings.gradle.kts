pluginManagement {
  includeBuild("build-logic")
  repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
  }
}

includeBuild("../../agentic")

dependencyResolutionManagement {
  repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
  repositories {
    google()
    mavenCentral()
    // EXTOL OBU SDK (LTA Singapore)
    maven { url = uri("https://extol.mycloudrepo.io/public/repositories/extol-android") }
  }
}

rootProject.name = "dqxn"

// SDK modules (pack API surface)
include(":sdk:contracts")

include(":sdk:common")

include(":sdk:ui")

include(":sdk:observability")

include(":sdk:analytics")

// Core modules (shell internals)
include(":core:design")

include(":core:thermal")

include(":core:firebase")

// Code generation (KSP, build-time only)
include(":codegen:plugin")

// Data layer
include(":data")

include(":data:proto")

// Feature modules
include(":feature:dashboard")

include(":feature:settings")

include(":feature:diagnostics")

include(":feature:onboarding")

// Pack modules
include(":pack:essentials")

include(":pack:essentials:snapshots")

include(":pack:plus")

include(":pack:themes")

include(":pack:demo")

// Application
include(":app")

// CI/Quality (not app runtime)
include(":lint-rules")

include(":baselineprofile")

include(":benchmark")
