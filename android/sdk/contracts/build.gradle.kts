plugins {
  id("dqxn.android.library")
  id("dqxn.android.test")
  alias(libs.plugins.kotlin.serialization)
}

android {
  namespace = "app.dqxn.android.sdk.contracts"
  testFixtures { enable = true }
}

dependencies {
  api(project(":sdk:common"))

  compileOnly(platform(libs.compose.bom))
  compileOnly(
    libs.compose.runtime
  ) // @Composable, @Immutable annotations only — no Compose compiler
  compileOnly(libs.compose.ui) // Modifier type reference in WidgetRenderer.Render()
  implementation(libs.kotlinx.collections.immutable)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.serialization.json)

  // testFixtures dependencies (separate scope — not inherited from main)
  testFixturesImplementation(project(":sdk:common"))
  testFixturesImplementation(platform(libs.compose.bom))
  testFixturesImplementation(libs.compose.runtime)
  testFixturesImplementation(libs.kotlinx.collections.immutable)
  testFixturesImplementation(libs.kotlinx.coroutines.core)
  testFixturesImplementation(libs.kotlinx.coroutines.test)
  testFixturesImplementation(platform(libs.junit.bom))
  testFixturesImplementation(libs.junit.jupiter.api)
  testFixturesImplementation(libs.junit.vintage.engine)
  testFixturesImplementation(libs.truth)
  testFixturesImplementation(libs.jqwik)
  testFixturesImplementation(libs.turbine)
  testFixturesImplementation(libs.robolectric)
}
