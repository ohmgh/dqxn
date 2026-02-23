import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class PackPluginTest {

  @TempDir lateinit var testProjectDir: File

  @BeforeEach
  fun setup() {
    TestProjectSetup.setupMultiModuleForPack(testProjectDir)
  }

  @Test
  fun `dqxn_pack wires all sdk modules`() {
    val result =
      TestProjectSetup.runner(
          testProjectDir,
          ":testpack:dependencies",
          "--configuration",
          "debugCompileClasspath",
        )
        .build()

    assertThat(result.output).contains(":sdk:contracts")
    assertThat(result.output).contains(":sdk:common")
    assertThat(result.output).contains(":sdk:ui")
    assertThat(result.output).contains(":sdk:observability")
    assertThat(result.output).contains(":sdk:analytics")
  }

  @Test
  fun `dqxn_pack applies compose and hilt and ksp`() {
    File(testProjectDir, "testpack/build.gradle.kts")
      .writeText(
        """
      plugins { id("dqxn.pack") }
      android { namespace = "com.test.pack" }
      tasks.register("printPlugins") {
        doLast {
          val hasCompose = project.plugins.hasPlugin("org.jetbrains.kotlin.plugin.compose")
          val hasHilt = project.plugins.hasPlugin("com.google.dagger.hilt.android")
          val hasKsp = project.plugins.hasPlugin("com.google.devtools.ksp")
          println("hasCompose=${'$'}hasCompose")
          println("hasHilt=${'$'}hasHilt")
          println("hasKsp=${'$'}hasKsp")
        }
      }
      """
          .trimIndent()
      )

    val result = TestProjectSetup.runner(testProjectDir, ":testpack:printPlugins").build()
    assertThat(result.output).contains("hasCompose=true")
    assertThat(result.output).contains("hasHilt=true")
    assertThat(result.output).contains("hasKsp=true")
  }

  @Test
  fun `dqxn_pack applies serialization`() {
    File(testProjectDir, "testpack/build.gradle.kts")
      .writeText(
        """
      plugins { id("dqxn.pack") }
      android { namespace = "com.test.pack" }
      tasks.register("printPlugins") {
        doLast {
          val hasSerialization = project.plugins.hasPlugin("org.jetbrains.kotlin.plugin.serialization")
          println("hasSerialization=${'$'}hasSerialization")
        }
      }
      """
          .trimIndent()
      )

    val result = TestProjectSetup.runner(testProjectDir, ":testpack:printPlugins").build()
    assertThat(result.output).contains("hasSerialization=true")
  }
}
