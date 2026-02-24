@file:OptIn(ExperimentalCompilerApi::class)

package app.dqxn.android.codegen.agentic

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.configureKsp
import com.tschuchort.compiletesting.kspSourcesDir
import com.tschuchort.compiletesting.symbolProcessorProviders
import java.io.File
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class AgenticProcessorTest {

  @TempDir lateinit var tempDir: File

  private lateinit var lastCompilation: KotlinCompilation

  private fun compile(vararg sources: SourceFile): JvmCompilationResult {
    val compilation =
      KotlinCompilation().apply {
        this.sources = agenticStubs().toList() + daggerStubs().toList() + sources.toList()
        workingDir = tempDir
        inheritClassPath = true
        configureKsp { symbolProcessorProviders += AgenticProcessorProvider() }
      }
    lastCompilation = compilation
    return compilation.compile()
  }

  private fun generatedFiles(): List<File> =
    lastCompilation.kspSourcesDir
      .walkTopDown()
      .filter { it.isFile && it.extension == "kt" }
      .toList()

  // -- Positive tests --

  @Test
  fun `valid AgenticCommand generates Hilt module`() {
    val source =
      SourceFile.kotlin(
        "PingHandler.kt",
        """
            package test

            import app.dqxn.android.core.agentic.AgenticCommand
            import app.dqxn.android.core.agentic.CommandHandler

            @AgenticCommand(name = "ping", description = "Health check", category = "diagnostics")
            class PingHandler : CommandHandler {
                override val name: String = "ping"
                override val description: String = "Health check"
                override val category: String = "diagnostics"
                override val aliases: List<String> = emptyList()
                override suspend fun execute(params: Any, commandId: String): Any = Unit
                override fun paramsSchema(): Any = Unit
            }
            """
          .trimIndent(),
      )

    val result = compile(source)

    assertThat(result.exitCode).isEqualTo(ExitCode.OK)

    val generated = generatedFiles()
    val hiltModule = generated.firstOrNull { it.name == "AgenticHiltModule.kt" }
    assertThat(hiltModule).isNotNull()

    val content = hiltModule!!.readText()
    assertThat(content).contains("@Module")
    assertThat(content).contains("@InstallIn")
    assertThat(content).contains("SingletonComponent::class")
    assertThat(content).contains("@Binds")
    assertThat(content).contains("@IntoSet")
    assertThat(content).contains("bindPingHandler")
    assertThat(content).contains("CommandHandler")
  }

  @Test
  fun `valid AgenticCommand generates schema`() {
    val source =
      SourceFile.kotlin(
        "PingHandler.kt",
        """
            package test

            import app.dqxn.android.core.agentic.AgenticCommand
            import app.dqxn.android.core.agentic.CommandHandler

            @AgenticCommand(name = "ping", description = "Health check", category = "diagnostics")
            class PingHandler : CommandHandler {
                override val name: String = "ping"
                override val description: String = "Health check"
                override val category: String = "diagnostics"
                override val aliases: List<String> = emptyList()
                override suspend fun execute(params: Any, commandId: String): Any = Unit
                override fun paramsSchema(): Any = Unit
            }
            """
          .trimIndent(),
      )

    val result = compile(source)

    assertThat(result.exitCode).isEqualTo(ExitCode.OK)

    val generated = generatedFiles()
    val schema = generated.firstOrNull { it.name == "GeneratedCommandSchema.kt" }
    assertThat(schema).isNotNull()

    val content = schema!!.readText()
    assertThat(content).contains("GeneratedCommandSchema")
    assertThat(content).contains("CommandSchemaEntry")
    assertThat(content).contains("""name = "ping"""")
    assertThat(content).contains("""description = "Health check"""")
    assertThat(content).contains("""category = "diagnostics"""")
  }

  @Test
  fun `multiple commands generate combined module and schema`() {
    val source =
      SourceFile.kotlin(
        "Handlers.kt",
        """
            package test

            import app.dqxn.android.core.agentic.AgenticCommand
            import app.dqxn.android.core.agentic.CommandHandler

            @AgenticCommand(name = "ping", description = "Health check", category = "diagnostics")
            class PingHandler : CommandHandler {
                override val name: String = "ping"
                override val description: String = "Health check"
                override val category: String = "diagnostics"
                override val aliases: List<String> = emptyList()
                override suspend fun execute(params: Any, commandId: String): Any = Unit
                override fun paramsSchema(): Any = Unit
            }

            @AgenticCommand(name = "navigate", description = "Navigate to route", category = "navigation")
            class NavigateHandler : CommandHandler {
                override val name: String = "navigate"
                override val description: String = "Navigate to route"
                override val category: String = "navigation"
                override val aliases: List<String> = emptyList()
                override suspend fun execute(params: Any, commandId: String): Any = Unit
                override fun paramsSchema(): Any = Unit
            }
            """
          .trimIndent(),
      )

    val result = compile(source)

    assertThat(result.exitCode).isEqualTo(ExitCode.OK)

    val generated = generatedFiles()

    val hiltModule = generated.first { it.name == "AgenticHiltModule.kt" }.readText()
    assertThat(hiltModule).contains("bindPingHandler")
    assertThat(hiltModule).contains("bindNavigateHandler")

    val schema = generated.first { it.name == "GeneratedCommandSchema.kt" }.readText()
    assertThat(schema).contains("""name = "ping"""")
    assertThat(schema).contains("""name = "navigate"""")
  }

  @Test
  fun `no annotated classes produces no output`() {
    val source =
      SourceFile.kotlin(
        "PlainClass.kt",
        """
            package test

            class PlainClass
            """
          .trimIndent(),
      )

    val result = compile(source)

    assertThat(result.exitCode).isEqualTo(ExitCode.OK)

    val generated = generatedFiles()
    assertThat(generated).isEmpty()
  }

  // -- Negative tests --

  @Test
  fun `class not implementing CommandHandler produces error`() {
    val source =
      SourceFile.kotlin(
        "BadHandler.kt",
        """
            package test

            import app.dqxn.android.core.agentic.AgenticCommand

            @AgenticCommand(name = "bad", description = "Missing interface", category = "test")
            class BadHandler
            """
          .trimIndent(),
      )

    val result = compile(source)

    assertThat(result.exitCode).isEqualTo(ExitCode.COMPILATION_ERROR)
    assertThat(result.messages).contains("must implement CommandHandler")
  }

  @Test
  fun `duplicate command names produce error`() {
    val source =
      SourceFile.kotlin(
        "DuplicateHandlers.kt",
        """
            package test

            import app.dqxn.android.core.agentic.AgenticCommand
            import app.dqxn.android.core.agentic.CommandHandler

            @AgenticCommand(name = "ping", description = "First ping", category = "diagnostics")
            class PingHandler : CommandHandler {
                override val name: String = "ping"
                override val description: String = "First ping"
                override val category: String = "diagnostics"
                override val aliases: List<String> = emptyList()
                override suspend fun execute(params: Any, commandId: String): Any = Unit
                override fun paramsSchema(): Any = Unit
            }

            @AgenticCommand(name = "ping", description = "Second ping", category = "diagnostics")
            class AnotherPingHandler : CommandHandler {
                override val name: String = "ping"
                override val description: String = "Second ping"
                override val category: String = "diagnostics"
                override val aliases: List<String> = emptyList()
                override suspend fun execute(params: Any, commandId: String): Any = Unit
                override fun paramsSchema(): Any = Unit
            }
            """
          .trimIndent(),
      )

    val result = compile(source)

    assertThat(result.exitCode).isEqualTo(ExitCode.COMPILATION_ERROR)
    assertThat(result.messages).contains("Duplicate @AgenticCommand name")
  }
}
