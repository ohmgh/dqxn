plugins { id("dqxn.android.feature") }

android { namespace = "app.dqxn.android.feature.settings" }

dependencies {
  implementation(project(":core:design"))
  implementation(project(":data"))
  implementation(libs.compose.material.icons.extended)

  testImplementation(libs.compose.ui.test.junit4)
  debugImplementation(libs.compose.ui.test.manifest)
}
