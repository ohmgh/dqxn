@file:OptIn(ExperimentalCompilerApi::class)

package app.dqxn.android.codegen.plugin

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.configureKsp
import com.tschuchort.compiletesting.symbolProcessorProviders
import java.io.File
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class TypeIdValidationTest {

  @TempDir lateinit var tempDir: File

  private fun compile(vararg sources: SourceFile): JvmCompilationResult {
    val compilation =
      KotlinCompilation().apply {
        this.sources =
          contractStubs().toList() +
            daggerStubs().toList() +
            manifestStubs().toList() +
            sources.toList()
        workingDir = tempDir
        inheritClassPath = true
        configureKsp {
          symbolProcessorProviders += PluginProcessorProvider()
          processorOptions["packId"] = "test"
        }
      }
    return compilation.compile()
  }

  private fun widgetSource(typeId: String): SourceFile =
    SourceFile.kotlin(
      "TestWidget.kt",
      """
          package test

          import app.dqxn.android.sdk.contracts.annotation.DashboardWidget
          import app.dqxn.android.sdk.contracts.widget.WidgetRenderer

          @DashboardWidget(typeId = "$typeId", displayName = "Test Widget")
          class TestWidgetRenderer : WidgetRenderer
          """
        .trimIndent(),
    )

  @Test
  fun `uppercase typeId produces error`() {
    val result = compile(widgetSource("Essentials:Speedometer"))

    assertThat(result.exitCode).isEqualTo(ExitCode.COMPILATION_ERROR)
    assertThat(result.messages).contains("typeId must match")
  }

  @Test
  fun `missing colon produces error`() {
    val result = compile(widgetSource("speedometer"))

    assertThat(result.exitCode).isEqualTo(ExitCode.COMPILATION_ERROR)
    assertThat(result.messages).contains("typeId must match")
  }

  @Test
  fun `empty typeId produces error`() {
    val result = compile(widgetSource(""))

    assertThat(result.exitCode).isEqualTo(ExitCode.COMPILATION_ERROR)
    assertThat(result.messages).contains("typeId must match")
  }

  @Test
  fun `special characters produce error`() {
    val result = compile(widgetSource("ess_entials:speed meter"))

    assertThat(result.exitCode).isEqualTo(ExitCode.COMPILATION_ERROR)
    assertThat(result.messages).contains("typeId must match")
  }

  @Test
  fun `valid typeId with hyphens compiles`() {
    val result = compile(widgetSource("essentials:gps-speed"))

    assertThat(result.exitCode).isEqualTo(ExitCode.OK)
  }

  @Test
  fun `typeId starting with number produces error`() {
    val result = compile(widgetSource("1pack:widget"))

    assertThat(result.exitCode).isEqualTo(ExitCode.COMPILATION_ERROR)
    assertThat(result.messages).contains("typeId must match")
  }

  @Test
  fun `widget not implementing WidgetRenderer produces error`() {
    val source =
      SourceFile.kotlin(
        "BadWidget.kt",
        """
            package test

            import app.dqxn.android.sdk.contracts.annotation.DashboardWidget

            @DashboardWidget(typeId = "test:valid-widget", displayName = "Bad Widget")
            class BadWidgetRenderer
            """
          .trimIndent(),
      )

    val result = compile(source)

    assertThat(result.exitCode).isEqualTo(ExitCode.COMPILATION_ERROR)
    assertThat(result.messages).contains("must implement WidgetRenderer")
  }
}
