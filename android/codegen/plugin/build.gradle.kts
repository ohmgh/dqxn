plugins { id("dqxn.kotlin.jvm") }

dependencies {
  implementation(libs.ksp.api)
  implementation(libs.kotlinpoet)
  implementation(libs.kotlinpoet.ksp)

  testImplementation(platform(libs.junit.bom))
  testImplementation(libs.junit.jupiter.api)
  testRuntimeOnly(libs.junit.jupiter.engine)
  testImplementation(libs.truth)
  testImplementation(libs.kctfork.core)
  testImplementation(libs.kctfork.ksp)
}
