plugins { id("dqxn.kotlin.jvm") }

tasks.withType<Test> { useJUnitPlatform() }

dependencies {
  implementation(libs.ksp.api)
  implementation(libs.kotlinpoet)
  implementation(libs.kotlinpoet.ksp)

  testImplementation(platform(libs.junit.bom))
  testImplementation(libs.junit.jupiter.api)
  testRuntimeOnly(libs.junit.jupiter.engine)
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
  testImplementation(libs.truth)
  testImplementation(libs.kctfork.core)
  testImplementation(libs.kctfork.ksp)
}
