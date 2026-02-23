import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class SnapshotConventionPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    with(target) {
      pluginManager.apply("dqxn.android.library")

      dependencies { add("api", project(":sdk:contracts")) }
    }
  }
}
