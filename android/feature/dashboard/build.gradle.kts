plugins { id("dqxn.android.feature") }

android {
  namespace = "app.dqxn.android.feature.dashboard"
  testFixtures { enable = true }
}

dependencies {
  implementation(project(":core:design"))
  implementation(project(":core:thermal"))
  implementation(project(":data"))
  implementation(project(":sdk:analytics"))

  implementation(libs.window)

  testImplementation(testFixtures(project(":sdk:contracts")))
  testImplementation(libs.turbine)
  testImplementation(libs.truth)
  testImplementation(libs.mockk)
  testImplementation(libs.kotlinx.coroutines.test)

  testFixturesImplementation(platform(libs.compose.bom))
  testFixturesImplementation(libs.compose.runtime)
  testFixturesImplementation(platform(libs.junit.bom))
  testFixturesImplementation(libs.junit.jupiter.api)
  testFixturesImplementation(project(":sdk:contracts"))
  testFixturesImplementation(project(":sdk:observability"))
  testFixturesImplementation(project(":data"))
  testFixturesImplementation(libs.kotlinx.coroutines.test)
  testFixturesImplementation(libs.truth)
  testFixturesImplementation(libs.kotlinx.collections.immutable)
  testFixturesImplementation(libs.mockk)
  testFixturesImplementation(libs.window)
}
