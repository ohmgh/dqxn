plugins {
  id("dqxn.android.library")
  id("dqxn.android.test")
  alias(libs.plugins.kotlin.serialization)
}

android { namespace = "app.dqxn.android.sdk.observability" }

dependencies {
  implementation(project(":sdk:contracts"))
  implementation(project(":sdk:common"))

  implementation(libs.kotlinx.collections.immutable)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.serialization.json)
}
