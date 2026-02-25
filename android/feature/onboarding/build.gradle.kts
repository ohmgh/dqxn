plugins { id("dqxn.android.feature") }

android { namespace = "app.dqxn.android.feature.onboarding" }

dependencies {
  implementation(project(":data"))
  implementation(project(":core:design"))
  implementation(project(":sdk:analytics"))

  testImplementation(libs.compose.ui.test.junit4)
  debugImplementation(libs.compose.ui.test.manifest)
}
