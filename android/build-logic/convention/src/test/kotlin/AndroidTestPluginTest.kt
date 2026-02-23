import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class AndroidTestPluginTest {

  @TempDir lateinit var testProjectDir: File
  private lateinit var libDir: File

  @BeforeEach
  fun setup() {
    libDir = TestProjectSetup.setupSingleModule(testProjectDir)
  }

  @Test
  fun `dqxn_android_test registers fastTest task`() {
    writeBuildFile(
      """
      plugins {
        id("dqxn.android.library")
        id("dqxn.android.test")
      }
      android { namespace = "com.test.androidtest" }
      """
    )

    val result = TestProjectSetup.runner(testProjectDir, ":lib:tasks", "--all").build()
    assertThat(result.output).contains("fastTest")
  }

  @Test
  fun `dqxn_android_test registers composeTest task`() {
    writeBuildFile(
      """
      plugins {
        id("dqxn.android.library")
        id("dqxn.android.test")
      }
      android { namespace = "com.test.androidtest" }
      """
    )

    val result = TestProjectSetup.runner(testProjectDir, ":lib:tasks", "--all").build()
    assertThat(result.output).contains("composeTest")
  }

  @Test
  fun `dqxn_android_test configures junit platform`() {
    writeBuildFile(
      """
      plugins {
        id("dqxn.android.library")
        id("dqxn.android.test")
      }
      android { namespace = "com.test.androidtest" }
      tasks.register("printPlugins") {
        doLast {
          val hasJunit5 = project.plugins.hasPlugin("de.mannodermaus.android-junit5")
          println("hasJunit5=${'$'}hasJunit5")
        }
      }
      """
    )

    val result = TestProjectSetup.runner(testProjectDir, ":lib:printPlugins").build()
    assertThat(result.output).contains("hasJunit5=true")
  }

  private fun writeBuildFile(content: String) {
    File(libDir, "build.gradle.kts").writeText(content.trimIndent())
  }
}
