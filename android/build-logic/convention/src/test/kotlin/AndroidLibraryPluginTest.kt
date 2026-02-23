import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class AndroidLibraryPluginTest {

  @TempDir lateinit var testProjectDir: File
  private lateinit var libDir: File

  @BeforeEach
  fun setup() {
    libDir = TestProjectSetup.setupSingleModule(testProjectDir)
  }

  @Test
  fun `dqxn_android_library sets compileSdk 36`() {
    writeBuildFile(
      """
      plugins { id("dqxn.android.library") }
      android { namespace = "com.test.lib" }
      tasks.register("printConfig") {
        doLast { println("compileSdk=${'$'}{android.compileSdk}") }
      }
      """
    )

    val result = TestProjectSetup.runner(testProjectDir, ":lib:printConfig").build()
    assertThat(result.output).contains("compileSdk=36")
  }

  @Test
  fun `dqxn_android_library sets minSdk 31`() {
    writeBuildFile(
      """
      plugins { id("dqxn.android.library") }
      android { namespace = "com.test.lib" }
      tasks.register("printConfig") {
        doLast { println("minSdk=${'$'}{android.defaultConfig.minSdk}") }
      }
      """
    )

    val result = TestProjectSetup.runner(testProjectDir, ":lib:printConfig").build()
    assertThat(result.output).contains("minSdk=31")
  }

  @Test
  fun `dqxn_android_library does not enable compose`() {
    writeBuildFile(
      """
      plugins { id("dqxn.android.library") }
      android { namespace = "com.test.lib" }
      tasks.register("printConfig") {
        doLast { println("compose=${'$'}{android.buildFeatures.compose}") }
      }
      """
    )

    val result = TestProjectSetup.runner(testProjectDir, ":lib:printConfig").build()
    assertThat(result.output).containsMatch("compose=(null|false)")
  }

  @Test
  fun `dqxn_android_library configures unit test options`() {
    writeBuildFile(
      """
      plugins { id("dqxn.android.library") }
      android { namespace = "com.test.lib" }
      tasks.register("printConfig") {
        doLast {
          println("includeAndroidResources=${'$'}{android.testOptions.unitTests.isIncludeAndroidResources}")
        }
      }
      """
    )

    val result = TestProjectSetup.runner(testProjectDir, ":lib:printConfig").build()
    assertThat(result.output).contains("includeAndroidResources=true")
  }

  private fun writeBuildFile(content: String) {
    File(libDir, "build.gradle.kts").writeText(content.trimIndent())
  }
}
