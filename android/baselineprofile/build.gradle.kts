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

baselineProfile {
  useConnectedDevices = true
}

dependencies {
  implementation(libs.benchmark.macro.junit4)
}
