import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidLibraryConventionPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    with(target) {
      pluginManager.apply("com.android.library")

      extensions.configure<LibraryExtension> {
        compileSdk = 36

        defaultConfig {
          minSdk = 31
          testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }

        testOptions {
          unitTests.isIncludeAndroidResources = true
          unitTests.isReturnDefaultValues = true
        }

        lint {
          error.add("HardcodedText")
        }
      }

      extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension> {
        jvmToolchain(25)
      }
    }
  }
}
