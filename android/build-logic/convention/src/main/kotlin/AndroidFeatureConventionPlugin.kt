import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

class AndroidFeatureConventionPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    with(target) {
      with(pluginManager) {
        apply("dqxn.android.library")
        apply("dqxn.android.compose")
        apply("dqxn.android.hilt")
        apply("dqxn.android.test")
      }

      val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

      dependencies {
        add("implementation", project(":sdk:contracts"))
        add("implementation", project(":sdk:common"))
        add("implementation", project(":sdk:ui"))
        add("implementation", project(":sdk:observability"))

        add("implementation", libs.findLibrary("lifecycle-runtime-compose").get())
        add("implementation", libs.findLibrary("lifecycle-viewmodel-compose").get())
        add("implementation", libs.findLibrary("hilt-navigation-compose").get())
        add("implementation", libs.findLibrary("navigation-compose").get())
      }
    }
  }
}
