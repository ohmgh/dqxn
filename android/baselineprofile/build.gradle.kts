plugins {
  id("com.android.test")
  alias(libs.plugins.baselineprofile)
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
