plugins {
  id("dqxn.android.library")
  id("dqxn.android.hilt")
  id("dqxn.android.test")
}

android { namespace = "app.dqxn.android.sdk.common" }

dependencies {
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.coroutines.android)
}
