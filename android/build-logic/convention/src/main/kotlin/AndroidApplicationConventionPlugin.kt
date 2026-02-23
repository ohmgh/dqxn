import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidApplicationConventionPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    with(target) {
      with(pluginManager) {
        apply("com.android.application")
        apply("dqxn.android.compose")
      }

      extensions.configure<ApplicationExtension> {
        compileSdk = 36

        defaultConfig {
          minSdk = 31
          targetSdk = 36
          testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }

        buildTypes {
          release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
              getDefaultProguardFile("proguard-android-optimize.txt"),
              "proguard-rules.pro",
            )
          }
        }

        buildFeatures { buildConfig = true }
      }

      extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension> {
        jvmToolchain(25)
      }
    }
  }
}
