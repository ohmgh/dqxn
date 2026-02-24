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

class PluginProcessorTest {

  @TempDir lateinit var tempDir: File

  private lateinit var lastCompilation: KotlinCompilation

  private fun compile(vararg sources: SourceFile, packId: String = "essentials"): JvmCompilationResult {
    val compilation =
      KotlinCompilation().apply {
        this.sources =
          contractStubs().toList() +
            daggerStubs().toList() +
            manifestStubs().toList() +
            sources.toList()
        workingDir = tempDir
        inheritClassPath = true
        configureKsp(useKsp2 = true) {
          symbolProcessorProviders += PluginProcessorProvider()
          processorOptions["packId"] = packId
        }
      }
    lastCompilation = compilation
    return compilation.compile()
  }

  private fun generatedKtFiles(): List<File> =
    lastCompilation.kspSourcesDir
      .walkTopDown()
      .filter { it.isFile && it.extension == "kt" }
      .toList()

  private fun generatedResourceFiles(): List<File> =
    lastCompilation.kspSourcesDir
      .walkTopDown()
      .filter { it.isFile && it.extension == "txt" }
      .toList()

  // -- Positive tests --

  @Test
  fun `valid DashboardWidget generates Hilt module`() {
    val source =
      SourceFile.kotlin(
        "SpeedometerRenderer.kt",
        """
            package test

            import app.dqxn.android.sdk.contracts.annotation.DashboardWidget
            import app.dqxn.android.sdk.contracts.widget.WidgetRenderer

            @DashboardWidget(typeId = "essentials:speedometer", displayName = "Speedometer")
            class SpeedometerRenderer : WidgetRenderer
            """
          .trimIndent(),
      )

    val result = compile(source)

    assertThat(result.exitCode).isEqualTo(ExitCode.OK)

    val generated = generatedKtFiles()
    val hiltModule = generated.firstOrNull { it.name == "EssentialsHiltModule.kt" }
    assertThat(hiltModule).isNotNull()

    val content = hiltModule!!.readText()
    assertThat(content).contains("@Module")
    assertThat(content).contains("@InstallIn")
    assertThat(content).contains("SingletonComponent::class")
    assertThat(content).contains("@Binds")
    assertThat(content).contains("@IntoSet")
    assertThat(content).contains("bindSpeedometerRenderer")
    assertThat(content).contains("WidgetRenderer")
  }

  @Test
  fun `valid DashboardDataProvider generates Hilt module`() {
    val source =
      SourceFile.kotlin(
        "GpsSpeedProvider.kt",
        """
            package test

            import app.dqxn.android.sdk.contracts.annotation.DashboardDataProvider
            import app.dqxn.android.sdk.contracts.annotation.DashboardSnapshot
            import app.dqxn.android.sdk.contracts.provider.DataProvider
            import app.dqxn.android.sdk.contracts.provider.DataSnapshot
            import androidx.compose.runtime.Immutable

            @DashboardSnapshot(dataType = "SPEED")
            @Immutable
            data class SpeedSnapshot(override val timestamp: Long, val speed: Float) : DataSnapshot

            @DashboardDataProvider(localId = "gps-speed", displayName = "GPS Speed")
            class GpsSpeedProvider : DataProvider<SpeedSnapshot>
            """
          .trimIndent(),
      )

    val result = compile(source)

    assertThat(result.exitCode).isEqualTo(ExitCode.OK)

    val generated = generatedKtFiles()
    val hiltModule = generated.firstOrNull { it.name == "EssentialsHiltModule.kt" }
    assertThat(hiltModule).isNotNull()

    val content = hiltModule!!.readText()
    assertThat(content).contains("bindGpsSpeedProvider")
    assertThat(content).contains("DataProvider")
  }

  @Test
  fun `valid DashboardSnapshot generates stability config`() {
    val source =
      SourceFile.kotlin(
        "SpeedSnapshot.kt",
        """
            package test.snapshots

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

    val resources = generatedResourceFiles()
    val stabilityConfig = resources.firstOrNull { it.name == "compose_stability_config.txt" }
    assertThat(stabilityConfig).isNotNull()

    val content = stabilityConfig!!.readText()
    assertThat(content).contains("test.snapshots.SpeedSnapshot")
  }

  @Test
  fun `PackManifest object is generated with widget and provider refs`() {
    val source =
      SourceFile.kotlin(
        "Combined.kt",
        """
            package test

            import app.dqxn.android.sdk.contracts.annotation.DashboardWidget
            import app.dqxn.android.sdk.contracts.annotation.DashboardDataProvider
            import app.dqxn.android.sdk.contracts.annotation.DashboardSnapshot
            import app.dqxn.android.sdk.contracts.widget.WidgetRenderer
            import app.dqxn.android.sdk.contracts.provider.DataProvider
            import app.dqxn.android.sdk.contracts.provider.DataSnapshot
            import androidx.compose.runtime.Immutable

            @DashboardSnapshot(dataType = "SPEED")
            @Immutable
            data class SpeedSnapshot(override val timestamp: Long, val speed: Float) : DataSnapshot

            @DashboardWidget(typeId = "essentials:speedometer", displayName = "Speedometer")
            class SpeedometerRenderer : WidgetRenderer

            @DashboardDataProvider(localId = "gps-speed", displayName = "GPS Speed")
            class GpsSpeedProvider : DataProvider<SpeedSnapshot>
            """
          .trimIndent(),
      )

    val result = compile(source)

    assertThat(result.exitCode).isEqualTo(ExitCode.OK)

    val generated = generatedKtFiles()
    val manifest = generated.firstOrNull { it.name == "EssentialsGeneratedManifest.kt" }
    assertThat(manifest).isNotNull()

    val content = manifest!!.readText()
    assertThat(content).contains("PackWidgetRef")
    assertThat(content).contains("PackDataProviderRef")
    assertThat(content).contains("""typeId = "essentials:speedometer"""")
    assertThat(content).contains("""displayName = "Speedometer"""")
    assertThat(content).contains("""sourceId = "essentials:gps-speed"""")
  }

  @Test
  fun `multiple widgets and providers in same module`() {
    val source =
      SourceFile.kotlin(
        "MultipleAnnotations.kt",
        """
            package test

            import app.dqxn.android.sdk.contracts.annotation.DashboardWidget
            import app.dqxn.android.sdk.contracts.annotation.DashboardDataProvider
            import app.dqxn.android.sdk.contracts.annotation.DashboardSnapshot
            import app.dqxn.android.sdk.contracts.widget.WidgetRenderer
            import app.dqxn.android.sdk.contracts.provider.DataProvider
            import app.dqxn.android.sdk.contracts.provider.DataSnapshot
            import androidx.compose.runtime.Immutable

            @DashboardSnapshot(dataType = "SPEED")
            @Immutable
            data class SpeedSnapshot(override val timestamp: Long, val speed: Float) : DataSnapshot

            @DashboardSnapshot(dataType = "BATTERY")
            @Immutable
            data class BatterySnapshot(override val timestamp: Long, val level: Int) : DataSnapshot

            @DashboardWidget(typeId = "essentials:speedometer", displayName = "Speedometer")
            class SpeedometerRenderer : WidgetRenderer

            @DashboardWidget(typeId = "essentials:clock", displayName = "Clock")
            class ClockRenderer : WidgetRenderer

            @DashboardDataProvider(localId = "gps-speed", displayName = "GPS Speed")
            class GpsSpeedProvider : DataProvider<SpeedSnapshot>

            @DashboardDataProvider(localId = "battery", displayName = "Battery Level")
            class BatteryProvider : DataProvider<BatterySnapshot>
            """
          .trimIndent(),
      )

    val result = compile(source)

    assertThat(result.exitCode).isEqualTo(ExitCode.OK)

    val generated = generatedKtFiles()

    // Hilt module should have 4 @Binds methods (2 widgets + 2 providers)
    val hiltModule = generated.first { it.name == "EssentialsHiltModule.kt" }.readText()
    assertThat(hiltModule).contains("bindSpeedometerRenderer")
    assertThat(hiltModule).contains("bindClockRenderer")
    assertThat(hiltModule).contains("bindGpsSpeedProvider")
    assertThat(hiltModule).contains("bindBatteryProvider")

    // Manifest should have all refs
    val manifest = generated.first { it.name == "EssentialsGeneratedManifest.kt" }.readText()
    assertThat(manifest).contains("""typeId = "essentials:speedometer"""")
    assertThat(manifest).contains("""typeId = "essentials:clock"""")
    assertThat(manifest).contains("""sourceId = "essentials:gps-speed"""")
    assertThat(manifest).contains("""sourceId = "essentials:battery"""")
  }

  @Test
  fun `module with no annotations produces only manifest`() {
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

    // ManifestGenerator always runs (pack always needs a manifest), but HiltModule
    // should NOT be generated when there are no widgets or providers.
    val generated = generatedKtFiles()
    assertThat(generated.map { it.name }).doesNotContain("EssentialsHiltModule.kt")
    assertThat(generated.firstOrNull { it.name == "EssentialsGeneratedManifest.kt" }).isNotNull()

    // No stability config since there are no snapshots
    val resources = generatedResourceFiles()
    assertThat(resources).isEmpty()
  }
}
