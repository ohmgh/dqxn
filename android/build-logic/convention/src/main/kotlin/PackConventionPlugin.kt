import com.google.devtools.ksp.gradle.KspExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension

class PackConventionPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    with(target) {
      with(pluginManager) {
        apply("dqxn.android.library")
        apply("dqxn.android.compose")
        apply("dqxn.android.hilt")
        apply("dqxn.android.test")
        apply("com.google.devtools.ksp")
        apply("org.jetbrains.kotlin.plugin.serialization")
      }

      val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

      dependencies {
        // Auto-wire all :sdk:* modules
        add("implementation", project(":sdk:contracts"))
        add("implementation", project(":sdk:common"))
        add("implementation", project(":sdk:ui"))
        add("implementation", project(":sdk:observability"))
        add("implementation", project(":sdk:analytics"))

        // KSP processor for @DashboardWidget / @DashboardDataProvider
        add("ksp", project(":codegen:plugin"))

        // Required libraries
        add("implementation", libs.findLibrary("kotlinx-collections-immutable").get())
        add("implementation", libs.findLibrary("kotlinx-coroutines-core").get())
        add("implementation", libs.findLibrary("kotlinx-serialization-json").get())
      }

      // KSP args (convention-based paths, no afterEvaluate)
      extensions.configure<KspExtension> {
        arg("themesDir", "${projectDir}/src/main/resources/themes/")
        arg("packId", project.name)
      }

      // Wire KSP-generated stability config into Compose compiler
      extensions.configure<ComposeCompilerGradlePluginExtension> {
        stabilityConfigurationFiles.add(
          layout.buildDirectory.file(
            "generated/ksp/debugKotlin/resources/compose_stability_config.txt"
          )
        )
      }
    }
  }
}
