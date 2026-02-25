plugins {
  id("dqxn.android.feature")
  alias(libs.plugins.kotlin.serialization)
}

android { namespace = "app.dqxn.android.feature.settings" }

dependencies {
  implementation(project(":core:design"))
  implementation(project(":data"))
  implementation(project(":sdk:analytics"))
  implementation(libs.compose.material.icons.extended)
  implementation(libs.kotlinx.serialization.json)

  testImplementation(libs.compose.ui.test.junit4)
  debugImplementation(libs.compose.ui.test.manifest)
}
