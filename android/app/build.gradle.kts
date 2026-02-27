plugins {
  id("dqxn.android.application")
  id("dqxn.android.hilt")
  id("dqxn.android.test")
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.baselineprofile)
}

android {
  namespace = "app.dqxn.android"

  defaultConfig {
    applicationId = "app.dqxn.android"
    versionCode = 1
    versionName = "0.1.0"
    testInstrumentationRunner = "app.dqxn.android.e2e.HiltTestRunner"
  }

  buildTypes {
    create("benchmark") {
      initWith(buildTypes.getByName("release"))
      signingConfig = signingConfigs.getByName("debug")
      isDebuggable = true
      matchingFallbacks += listOf("release")
    }
  }

  sourceSets {
    getByName("debug").kotlin.srcDir("src/agentic/kotlin")
    getByName("benchmark").kotlin.srcDir("src/agentic/kotlin")
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
  debugImplementation("dev.agentic.android:runtime")
  debugImplementation("dev.agentic.android:semantics")
  debugImplementation("dev.agentic.android:chaos")
  add("benchmarkImplementation", "dev.agentic.android:runtime")
  add("benchmarkImplementation", "dev.agentic.android:semantics")
  add("benchmarkImplementation", "dev.agentic.android:chaos")

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

  // Coroutines Play Services (Task.await() for Play Core APIs)
  implementation(libs.kotlinx.coroutines.play.services)

  // Google Play In-App Update + Review
  implementation(libs.play.app.update.ktx)
  implementation(libs.play.review.ktx)

  // KSP: agentic command processor (generates AgenticHiltModule for debug + benchmark)
  add("kspDebug", "dev.agentic.android:codegen")
  add("kspBenchmark", "dev.agentic.android:codegen")

  // Baseline Profile
  implementation(libs.profileinstaller)
  baselineProfile(project(":baselineprofile"))

  // Hilt testing (Robolectric + @HiltAndroidTest)
  testImplementation(libs.hilt.testing)
  testImplementation("dev.agentic.android:testing")
  kspTest(libs.hilt.compiler)

  // AndroidX Test (instrumented tests)
  androidTestImplementation(libs.hilt.testing)
  add("kspAndroidTest", libs.hilt.compiler)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.uiautomator)
  androidTestImplementation(libs.truth)
  androidTestImplementation(libs.kotlinx.serialization.json)

  // Debug tools
  debugImplementation(libs.leakcanary)
}
