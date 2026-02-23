import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType

class AndroidTestConventionPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    with(target) {
      pluginManager.apply("de.mannodermaus.android-junit5")

      val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

      dependencies {
        val junitBom = platform(libs.findLibrary("junit-bom").get())
        add("testImplementation", junitBom)
        add("testRuntimeOnly", junitBom)
        add("testImplementation", libs.findLibrary("junit-jupiter-api").get())
        add("testImplementation", libs.findLibrary("junit-jupiter-params").get())
        add("testRuntimeOnly", libs.findLibrary("junit-jupiter-engine").get())
        add("testRuntimeOnly", libs.findLibrary("junit-vintage-engine").get())
        add("testImplementation", libs.findLibrary("mockk").get())
        add("testImplementation", libs.findLibrary("truth").get())
        add("testImplementation", libs.findLibrary("turbine").get())
        add("testImplementation", libs.findLibrary("kotlinx-coroutines-test").get())
        add("testImplementation", libs.findLibrary("robolectric").get())
        add("testImplementation", libs.findLibrary("jqwik").get())
      }

      // Configure all Test tasks with JUnit Platform and structured output
      tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        reports.junitXml.outputLocation.set(layout.buildDirectory.dir("test-results/${name}"))
      }

      // Register fastTest and composeTest as independent Test tasks.
      // These are registered when the library or application plugin is applied,
      // because testDebugUnitTest only exists for Android modules.
      registerTagFilteredTestTasks("com.android.library")
      registerTagFilteredTestTasks("com.android.application")
    }
  }

  private fun Project.registerTagFilteredTestTasks(pluginId: String) {
    plugins.withId(pluginId) {
      // Defer task registration until afterEvaluate to ensure testDebugUnitTest exists.
      // AGP registers test tasks during variant creation which happens after plugin apply.
      afterEvaluate {
        val debugTestTask = tasks.named("testDebugUnitTest")

        tasks.register("fastTest", Test::class.java) {
          description = "Run only @Tag(\"fast\") tests"
          group = "verification"
          classpath = (debugTestTask.get() as Test).classpath
          testClassesDirs = (debugTestTask.get() as Test).testClassesDirs
          useJUnitPlatform { includeTags("fast") }
          reports.junitXml.outputLocation.set(layout.buildDirectory.dir("test-results/fastTest"))
        }

        tasks.register("composeTest", Test::class.java) {
          description = "Run only @Tag(\"compose\") tests"
          group = "verification"
          classpath = (debugTestTask.get() as Test).classpath
          testClassesDirs = (debugTestTask.get() as Test).testClassesDirs
          useJUnitPlatform { includeTags("compose") }
          reports.junitXml.outputLocation.set(layout.buildDirectory.dir("test-results/composeTest"))
        }
      }
    }
  }
}
