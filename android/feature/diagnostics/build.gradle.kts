plugins { id("dqxn.android.feature") }

android { namespace = "app.dqxn.android.feature.diagnostics" }

dependencies {
  implementation(project(":core:design"))
  implementation(project(":data"))
  implementation(project(":sdk:analytics"))

  testImplementation(libs.compose.ui.test.junit4)
  debugImplementation(libs.compose.ui.test.manifest)
}
