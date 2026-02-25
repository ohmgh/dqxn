plugins {
  id("com.android.test")
  // baselineprofile plugin deferred -- 1.4.1 incompatible with AGP 9 (TestExtension type mismatch)
  // BaselineProfileRule from library dep still works for generating profiles on-device
}

android {
  namespace = "app.dqxn.android.baselineprofile"
  compileSdk = 36
  targetProjectPath = ":app"

  defaultConfig {
    minSdk = 31
    targetSdk = 36
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }
}

dependencies {
  implementation(libs.benchmark.macro.junit4)
  implementation(libs.uiautomator)
  implementation(libs.androidx.test.ext.junit)
}
