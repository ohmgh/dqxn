import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

class SnapshotConventionPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    with(target) {
      pluginManager.apply("dqxn.android.library")

      val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

      dependencies {
        add("api", project(":sdk:contracts"))
        // @Immutable annotation for snapshot data classes — compileOnly mirrors :sdk:contracts
        add("compileOnly", platform(libs.findLibrary("compose-bom").get()))
        add("compileOnly", libs.findLibrary("compose-runtime").get())
      }
    }
  }
}
