plugins {
  id("dqxn.android.application")
  id("dqxn.android.hilt")
  id("dqxn.android.test")
  alias(libs.plugins.kotlin.serialization)
}

android {
  namespace = "app.dqxn.android"

  defaultConfig {
    applicationId = "app.dqxn.android"
    versionCode = 1
    versionName = "0.1.0"
  }
}

dependencies {
  // Feature modules
  implementation(project(":feature:dashboard"))
  implementation(project(":feature:settings"))
  implementation(project(":feature:diagnostics"))
  implementation(project(":feature:onboarding"))

  // Pack modules
  implementation(project(":pack:essentials"))
  implementation(project(":pack:themes"))
  implementation(project(":pack:demo"))

  // Core modules
  implementation(project(":core:firebase"))
  implementation(project(":core:thermal"))
  implementation(project(":core:design"))
  debugImplementation(project(":core:agentic"))

  // Data layer
  implementation(project(":data"))

  // SDK modules
  implementation(project(":sdk:contracts"))
  implementation(project(":sdk:observability"))
  implementation(project(":sdk:analytics"))
  implementation(project(":sdk:ui"))

  // AndroidX
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.core.splashscreen)

  // Serialization (used by AgenticContentProvider for JSON param parsing)
  implementation(libs.kotlinx.serialization.json)

  // KSP: agentic command processor (debug only -- generates AgenticHiltModule)
  add("kspDebug", project(":codegen:agentic"))

  // Debug tools
  debugImplementation(libs.leakcanary)
}
