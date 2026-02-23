import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class AndroidComposePluginTest {

  @TempDir lateinit var testProjectDir: File
  private lateinit var libDir: File

  @BeforeEach
  fun setup() {
    libDir = TestProjectSetup.setupSingleModule(testProjectDir, includeComposeConfig = true)
  }

  @Test
  fun `dqxn_android_compose enables compose build feature`() {
    writeBuildFile(
      """
      plugins {
        id("dqxn.android.library")
        id("dqxn.android.compose")
      }
      android { namespace = "com.test.compose" }
      tasks.register("printConfig") {
        doLast { println("compose=${'$'}{android.buildFeatures.compose}") }
      }
      """
    )

    val result = TestProjectSetup.runner(testProjectDir, ":lib:printConfig").build()
    assertThat(result.output).contains("compose=true")
  }

  @Test
  fun `dqxn_android_compose applies compose compiler plugin`() {
    writeBuildFile(
      """
      plugins {
        id("dqxn.android.library")
        id("dqxn.android.compose")
      }
      android { namespace = "com.test.compose" }
      tasks.register("printPlugins") {
        doLast {
          val hasCompose = project.plugins.hasPlugin("org.jetbrains.kotlin.plugin.compose")
          println("hasComposePlugin=${'$'}hasCompose")
        }
      }
      """
    )

    val result = TestProjectSetup.runner(testProjectDir, ":lib:printPlugins").build()
    assertThat(result.output).contains("hasComposePlugin=true")
  }

  private fun writeBuildFile(content: String) {
    File(libDir, "build.gradle.kts").writeText(content.trimIndent())
  }
}
