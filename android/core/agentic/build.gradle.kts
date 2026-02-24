plugins {
  id("dqxn.android.library")
  id("dqxn.android.hilt")
  id("dqxn.android.test")
  alias(libs.plugins.kotlin.serialization)
}

android { namespace = "app.dqxn.android.core.agentic" }

dependencies {
  implementation(project(":sdk:observability"))
  implementation(project(":sdk:common"))
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.kotlinx.collections.immutable)

  compileOnly(platform(libs.compose.bom))
  compileOnly(libs.compose.ui) // SemanticsOwner / SemanticsNode type references
}
