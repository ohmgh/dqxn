plugins {
  id("dqxn.android.library")
  id("dqxn.android.compose")
  id("dqxn.android.test")
  id("dqxn.android.hilt")
  alias(libs.plugins.kotlin.serialization)
}

android { namespace = "app.dqxn.android.core.design" }

dependencies {
  implementation(project(":sdk:ui"))
  implementation(project(":sdk:contracts"))
  implementation(project(":sdk:observability"))
  implementation(project(":sdk:common"))
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.kotlinx.collections.immutable)
}
