plugins { `kotlin-dsl` }

java { toolchain { languageVersion = JavaLanguageVersion.of(25) } }

dependencies {
  compileOnly(libs.android.gradlePlugin)
  compileOnly(libs.kotlin.gradlePlugin)
  compileOnly(libs.compose.compiler.gradlePlugin)
  compileOnly(libs.ksp.gradlePlugin)
  compileOnly(libs.hilt.gradlePlugin)
  compileOnly(libs.android.junit.gradlePlugin)
  compileOnly(libs.spotless.gradlePlugin)
  compileOnly(libs.kotlin.serialization.gradlePlugin)

  testImplementation(gradleTestKit())
  testImplementation(platform(libs.junit.bom))
  testImplementation(libs.junit.jupiter.api)
  testRuntimeOnly(libs.junit.jupiter.engine)
  testImplementation(libs.truth)
}

gradlePlugin {
  plugins {
    register("androidApplication") {
      id = "dqxn.android.application"
      implementationClass = "AndroidApplicationConventionPlugin"
    }
    register("androidLibrary") {
      id = "dqxn.android.library"
      implementationClass = "AndroidLibraryConventionPlugin"
    }
    register("androidCompose") {
      id = "dqxn.android.compose"
      implementationClass = "AndroidComposeConventionPlugin"
    }
    register("androidHilt") {
      id = "dqxn.android.hilt"
      implementationClass = "AndroidHiltConventionPlugin"
    }
    register("androidTest") {
      id = "dqxn.android.test"
      implementationClass = "AndroidTestConventionPlugin"
    }
    register("androidFeature") {
      id = "dqxn.android.feature"
      implementationClass = "AndroidFeatureConventionPlugin"
    }
    register("pack") {
      id = "dqxn.pack"
      implementationClass = "PackConventionPlugin"
    }
    register("snapshot") {
      id = "dqxn.snapshot"
      implementationClass = "SnapshotConventionPlugin"
    }
    register("kotlinJvm") {
      id = "dqxn.kotlin.jvm"
      implementationClass = "KotlinJvmConventionPlugin"
    }
  }
}
