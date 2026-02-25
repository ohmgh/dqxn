plugins {
  id("dqxn.android.library")
  id("dqxn.android.hilt")
  id("dqxn.android.test")
  // Pitest mutation testing -- INCOMPATIBLE with current stack:
  //   info.solidsoft.pitest 1.19.0-rc.3: loads on Android library but does NOT register
  //   pitest extension (only works on pure JVM projects, not Android library modules)
  //   pl.droidsonroids.pitest 0.2.25: fails on Gradle 9.3 with "Cannot mutate
  //   configuration container for buildscript"
  // Deferred until pitest gains AGP 9 / Gradle 9.3 support.
  // id("info.solidsoft.pitest") version "1.19.0-rc.3"
}

android { namespace = "app.dqxn.android.sdk.common" }

// pitest {
//   targetClasses.set(listOf("app.dqxn.android.sdk.common.*"))
//   pitestVersion.set("1.22.0")
//   threads.set(4)
//   outputFormats.set(listOf("XML", "HTML"))
//   timestampedReports.set(false)
//   junit5PluginVersion.set("1.2.1")
//   mutators.set(listOf("DEFAULTS"))
// }

dependencies {
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.coroutines.android)
  // testImplementation("org.pitest:pitest-junit5-plugin:1.2.1") // DEFERRED with pitest
}
