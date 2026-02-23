plugins {
  id("dqxn.android.library")
  id("dqxn.android.compose")
  id("dqxn.android.test")
}

android { namespace = "app.dqxn.android.sdk.ui" }

dependencies {
  implementation(project(":sdk:contracts"))
  implementation(project(":sdk:common"))
  implementation(libs.kotlinx.collections.immutable)
  implementation(libs.compose.material.icons.extended)

  testImplementation(libs.compose.ui.test.junit4)
  debugImplementation(libs.compose.ui.test.manifest)
}
