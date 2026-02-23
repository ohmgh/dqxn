import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class AndroidHiltPluginTest {

  @TempDir lateinit var testProjectDir: File
  private lateinit var libDir: File

  @BeforeEach
  fun setup() {
    libDir = TestProjectSetup.setupSingleModule(testProjectDir)
  }

  @Test
  fun `dqxn_android_hilt applies ksp and hilt plugins`() {
    writeBuildFile(
      """
      plugins {
        id("dqxn.android.library")
        id("dqxn.android.hilt")
      }
      android { namespace = "com.test.hilt" }
      tasks.register("printPlugins") {
        doLast {
          val hasKsp = project.plugins.hasPlugin("com.google.devtools.ksp")
          val hasHilt = project.plugins.hasPlugin("com.google.dagger.hilt.android")
          println("hasKsp=${'$'}hasKsp")
          println("hasHilt=${'$'}hasHilt")
        }
      }
      """
    )

    val result = TestProjectSetup.runner(testProjectDir, ":lib:printPlugins").build()
    assertThat(result.output).contains("hasKsp=true")
    assertThat(result.output).contains("hasHilt=true")
  }

  @Test
  fun `dqxn_android_hilt adds hilt dependencies`() {
    writeBuildFile(
      """
      plugins {
        id("dqxn.android.library")
        id("dqxn.android.hilt")
      }
      android { namespace = "com.test.hilt" }
      tasks.register("printDeps") {
        doLast {
          val implDeps = configurations.getByName("implementation").allDependencies
            .map { "${'$'}{it.group}:${'$'}{it.name}" }
          val kspDeps = configurations.getByName("ksp").allDependencies
            .map { "${'$'}{it.group}:${'$'}{it.name}" }
          println("impl=${'$'}implDeps")
          println("ksp=${'$'}kspDeps")
        }
      }
      """
    )

    val result = TestProjectSetup.runner(testProjectDir, ":lib:printDeps").build()
    assertThat(result.output).contains("com.google.dagger:hilt-android")
    assertThat(result.output).contains("com.google.dagger:hilt-compiler")
  }

  private fun writeBuildFile(content: String) {
    File(libDir, "build.gradle.kts").writeText(content.trimIndent())
  }
}
