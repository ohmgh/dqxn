plugins {
  id("dqxn.android.library")
  id("dqxn.android.hilt")
  id("dqxn.android.test")
  alias(libs.plugins.kotlin.serialization)
}

android { namespace = "app.dqxn.android.data" }

dependencies {
  api(project(":data:proto"))
  implementation(project(":sdk:contracts"))
  implementation(project(":sdk:observability"))
  implementation(project(":sdk:common"))
  implementation(libs.datastore.proto)
  implementation(libs.datastore.preferences)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.collections.immutable)
  implementation(libs.kotlinx.serialization.json)
}
