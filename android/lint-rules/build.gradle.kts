plugins { id("dqxn.kotlin.jvm") }

dependencies {
  compileOnly(libs.lint.api)
  compileOnly(libs.lint.checks)

  testImplementation(libs.lint.api)
  testImplementation(libs.lint.checks)
  testImplementation(libs.lint.tests)
  testImplementation(platform(libs.junit.bom))
  testImplementation(libs.junit.jupiter.api)
  testRuntimeOnly(libs.junit.jupiter.engine)
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> { useJUnitPlatform() }

tasks.named<Jar>("jar") {
  manifest { attributes("Lint-Registry-v2" to "app.dqxn.android.lint.DqxnIssueRegistry") }
}
