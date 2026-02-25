plugins {
  id("dqxn.android.library")
  id("dqxn.android.test")
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.kotlin.compose)
  // Compose compiler required for @Composable interface method bytecode.
  // Without it, WidgetRenderer.Render() gets a 4-param signature in bytecode while callers
  // (compiled with Compose compiler) emit invokeinterface with 6-param (Composer, int) signature,
  // causing NoSuchMethodError at runtime (JVM invokeinterface requires method on interface type).
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
  ) // @Composable, @Immutable annotations only — Compose compiler transforms interface signature
  compileOnly(libs.compose.ui) // Modifier type reference in WidgetRenderer.Render()

  // Compose compiler plugin needs runtime on test classpath
  testCompileOnly(platform(libs.compose.bom))
  testCompileOnly(libs.compose.runtime)
  api(libs.kotlinx.collections.immutable)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.serialization.json)

  // testFixtures dependencies (separate scope — not inherited from main)
  testFixturesImplementation(project(":sdk:common"))
  testFixturesImplementation(platform(libs.compose.bom))
  testFixturesImplementation(libs.compose.runtime)
  testFixturesImplementation(libs.compose.ui)
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
