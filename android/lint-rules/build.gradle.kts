plugins { id("dqxn.kotlin.jvm") }

dependencies {
  compileOnly(libs.lint.api)
  compileOnly(libs.lint.checks)

  testImplementation(libs.lint.tests)
}
