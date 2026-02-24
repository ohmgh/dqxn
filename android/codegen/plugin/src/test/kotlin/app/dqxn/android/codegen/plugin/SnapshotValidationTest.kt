@file:OptIn(ExperimentalCompilerApi::class)

package app.dqxn.android.codegen.plugin

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

class SnapshotValidationTest {

  @TempDir lateinit var tempDir: File

  private lateinit var lastCompilation: KotlinCompilation

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
    lastCompilation = compilation
    return compilation.compile()
  }

  @Test
  fun `non-data class produces error`() {
    val source =
      SourceFile.kotlin(
        "BadSnapshot.kt",
        """
            package test

            import app.dqxn.android.sdk.contracts.annotation.DashboardSnapshot
            import app.dqxn.android.sdk.contracts.provider.DataSnapshot
            import androidx.compose.runtime.Immutable

            @DashboardSnapshot(dataType = "SPEED")
            @Immutable
            class SpeedSnapshot(override val timestamp: Long) : DataSnapshot
            """
          .trimIndent(),
      )

    val result = compile(source)

    assertThat(result.exitCode).isEqualTo(ExitCode.COMPILATION_ERROR)
    assertThat(result.messages).contains("must be applied to a data class")
  }

  @Test
  fun `missing @Immutable produces error`() {
    val source =
      SourceFile.kotlin(
        "BadSnapshot.kt",
        """
            package test

            import app.dqxn.android.sdk.contracts.annotation.DashboardSnapshot
            import app.dqxn.android.sdk.contracts.provider.DataSnapshot

            @DashboardSnapshot(dataType = "SPEED")
            data class SpeedSnapshot(override val timestamp: Long, val speed: Float) : DataSnapshot
            """
          .trimIndent(),
      )

    val result = compile(source)

    assertThat(result.exitCode).isEqualTo(ExitCode.COMPILATION_ERROR)
    assertThat(result.messages).contains("must be annotated with @Immutable")
  }

  @Test
  fun `not implementing DataSnapshot produces error`() {
    val source =
      SourceFile.kotlin(
        "BadSnapshot.kt",
        """
            package test

            import app.dqxn.android.sdk.contracts.annotation.DashboardSnapshot
            import androidx.compose.runtime.Immutable

            @DashboardSnapshot(dataType = "SPEED")
            @Immutable
            data class SpeedSnapshot(val speed: Float)
            """
          .trimIndent(),
      )

    val result = compile(source)

    assertThat(result.exitCode).isEqualTo(ExitCode.COMPILATION_ERROR)
    assertThat(result.messages).contains("must implement DataSnapshot")
  }

  @Test
  fun `mutable property produces error`() {
    val source =
      SourceFile.kotlin(
        "BadSnapshot.kt",
        """
            package test

            import app.dqxn.android.sdk.contracts.annotation.DashboardSnapshot
            import app.dqxn.android.sdk.contracts.provider.DataSnapshot
            import androidx.compose.runtime.Immutable

            @DashboardSnapshot(dataType = "SPEED")
            @Immutable
            data class SpeedSnapshot(override val timestamp: Long, var speed: Float) : DataSnapshot
            """
          .trimIndent(),
      )

    val result = compile(source)

    assertThat(result.exitCode).isEqualTo(ExitCode.COMPILATION_ERROR)
    assertThat(result.messages).contains("must be val, not var")
    assertThat(result.messages).contains("speed")
  }

  @Test
  fun `duplicate dataType in same module produces error`() {
    val source =
      SourceFile.kotlin(
        "DuplicateSnapshots.kt",
        """
            package test

            import app.dqxn.android.sdk.contracts.annotation.DashboardSnapshot
            import app.dqxn.android.sdk.contracts.provider.DataSnapshot
            import androidx.compose.runtime.Immutable

            @DashboardSnapshot(dataType = "SPEED")
            @Immutable
            data class SpeedSnapshot1(override val timestamp: Long, val speed: Float) : DataSnapshot

            @DashboardSnapshot(dataType = "SPEED")
            @Immutable
            data class SpeedSnapshot2(override val timestamp: Long, val velocity: Float) : DataSnapshot
            """
          .trimIndent(),
      )

    val result = compile(source)

    assertThat(result.exitCode).isEqualTo(ExitCode.COMPILATION_ERROR)
    assertThat(result.messages).contains("duplicate dataType")
  }

  @Test
  fun `valid snapshot compiles successfully`() {
    val source =
      SourceFile.kotlin(
        "ValidSnapshot.kt",
        """
            package test

            import app.dqxn.android.sdk.contracts.annotation.DashboardSnapshot
            import app.dqxn.android.sdk.contracts.provider.DataSnapshot
            import androidx.compose.runtime.Immutable

            @DashboardSnapshot(dataType = "SPEED")
            @Immutable
            data class SpeedSnapshot(override val timestamp: Long, val speed: Float) : DataSnapshot
            """
          .trimIndent(),
      )

    val result = compile(source)

    assertThat(result.exitCode).isEqualTo(ExitCode.OK)
  }

  @Test
  fun `multiple valid snapshots with different dataTypes compile`() {
    val source =
      SourceFile.kotlin(
        "MultiSnapshots.kt",
        """
            package test

            import app.dqxn.android.sdk.contracts.annotation.DashboardSnapshot
            import app.dqxn.android.sdk.contracts.provider.DataSnapshot
            import androidx.compose.runtime.Immutable

            @DashboardSnapshot(dataType = "SPEED")
            @Immutable
            data class SpeedSnapshot(override val timestamp: Long, val speed: Float) : DataSnapshot

            @DashboardSnapshot(dataType = "BATTERY")
            @Immutable
            data class BatterySnapshot(override val timestamp: Long, val level: Int) : DataSnapshot
            """
          .trimIndent(),
      )

    val result = compile(source)

    assertThat(result.exitCode).isEqualTo(ExitCode.OK)

    // Verify stability config contains both FQNs
    val resources =
      lastCompilation.kspSourcesDir
        .walkTopDown()
        .filter { it.isFile && it.extension == "txt" }
        .toList()
    val stabilityConfig = resources.firstOrNull { it.name == "compose_stability_config.txt" }
    assertThat(stabilityConfig).isNotNull()

    val content = stabilityConfig!!.readText()
    assertThat(content).contains("test.SpeedSnapshot")
    assertThat(content).contains("test.BatterySnapshot")
  }
}
