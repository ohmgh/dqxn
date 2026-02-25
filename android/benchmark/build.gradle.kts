plugins {
  id("com.android.test")
}

android {
  namespace = "app.dqxn.android.benchmark"
  compileSdk = 36
  targetProjectPath = ":app"

  defaultConfig {
    minSdk = 31
    targetSdk = 36
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  buildTypes {
    create("benchmark") {
      isDebuggable = true
      signingConfig = signingConfigs.getByName("debug")
      // Fallback to debug until release Hilt DI bindings are complete (DiagnosticSnapshotCapture missing in release)
      matchingFallbacks += listOf("debug")
    }
  }
}

dependencies {
  implementation(libs.benchmark.macro.junit4)
  implementation(libs.uiautomator)
  implementation(libs.androidx.test.ext.junit)
}
