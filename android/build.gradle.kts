plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.android.library) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.kotlin.serialization) apply false
  alias(libs.plugins.kotlin.jvm) apply false
  alias(libs.plugins.hilt) apply false
  alias(libs.plugins.ksp) apply false
  alias(libs.plugins.protobuf) apply false
  alias(libs.plugins.spotless)
  alias(libs.plugins.android.junit) apply false
}

spotless {
  kotlin {
    target("**/*.kt")
    targetExclude("**/build/**")
    ktfmt().googleStyle()
  }
  kotlinGradle {
    target("**/*.kts")
    targetExclude("**/build/**")
    ktfmt().googleStyle()
  }
}

tasks.register<Exec>("installGitHooks") {
  description = "Installs git hooks from android/.githooks"
  group = "setup"
  commandLine("git", "config", "core.hooksPath", "android/.githooks")
}
