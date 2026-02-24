plugins {
  id("dqxn.android.library")
  id("dqxn.android.hilt")
  id("dqxn.android.test")
}

android { namespace = "app.dqxn.android.core.thermal" }

dependencies {
  implementation(project(":sdk:observability"))
  implementation(project(":sdk:common"))

  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.coroutines.android)
}
