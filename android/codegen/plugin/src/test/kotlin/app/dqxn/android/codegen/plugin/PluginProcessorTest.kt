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

  private fun compile(
    vararg sources: SourceFile,
    packId: String = "essentials",
    packCategory: String = "ESSENTIALS",
    includePackCategory: Boolean = true,
  ): JvmCompilationResult {
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
          processorOptions["packId"] = packId
          if (includePackCategory) {
            processorOptions["packCategory"] = packCategory
          }
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
    // Manifest @Provides is always present
    assertThat(content).contains("provideManifest")
    assertThat(content).contains("DashboardPackManifest")
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
    assertThat(content).contains("PackCategory.ESSENTIALS")
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

    // Hilt module should have 4 @Binds methods (2 widgets + 2 providers) + provideManifest
    val hiltModule = generated.first { it.name == "EssentialsHiltModule.kt" }.readText()
    assertThat(hiltModule).contains("bindSpeedometerRenderer")
    assertThat(hiltModule).contains("bindClockRenderer")
    assertThat(hiltModule).contains("bindGpsSpeedProvider")
    assertThat(hiltModule).contains("bindBatteryProvider")
    assertThat(hiltModule).contains("provideManifest")

    // Manifest should have all refs
    val manifest = generated.first { it.name == "EssentialsGeneratedManifest.kt" }.readText()
    assertThat(manifest).contains("""typeId = "essentials:speedometer"""")
    assertThat(manifest).contains("""typeId = "essentials:clock"""")
    assertThat(manifest).contains("""sourceId = "essentials:gps-speed"""")
    assertThat(manifest).contains("""sourceId = "essentials:battery"""")
  }

  @Test
  fun `module with no annotations produces manifest and Hilt module`() {
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

    // ManifestGenerator always runs (pack always needs a manifest).
    // HiltModule is also always generated (contains manifest @Provides).
    val generated = generatedKtFiles()
    assertThat(generated.firstOrNull { it.name == "EssentialsGeneratedManifest.kt" }).isNotNull()
    assertThat(generated.firstOrNull { it.name == "EssentialsHiltModule.kt" }).isNotNull()

    // HiltModule should have provideManifest but no @Binds methods
    val hiltContent = generated.first { it.name == "EssentialsHiltModule.kt" }.readText()
    assertThat(hiltContent).contains("provideManifest")
    assertThat(hiltContent).doesNotContain("@Binds")

    // No stability config since there are no snapshots
    val resources = generatedResourceFiles()
    assertThat(resources).isEmpty()
  }

  // -- Theme provider tests --

  @Test
  fun `DashboardThemeProvider generates Hilt binding`() {
    val source =
      SourceFile.kotlin(
        "TestThemeProvider.kt",
        """
            package test

            import app.dqxn.android.sdk.contracts.annotation.DashboardThemeProvider
            import app.dqxn.android.sdk.contracts.theme.ThemeProvider

            @DashboardThemeProvider
            class TestThemeProvider : ThemeProvider {
                override val packId: String = "essentials"
            }
            """
          .trimIndent(),
      )

    val result = compile(source)

    assertThat(result.exitCode).isEqualTo(ExitCode.OK)

    val generated = generatedKtFiles()
    val hiltModule = generated.firstOrNull { it.name == "EssentialsHiltModule.kt" }
    assertThat(hiltModule).isNotNull()

    val content = hiltModule!!.readText()
    assertThat(content).contains("@Binds")
    assertThat(content).contains("@IntoSet")
    assertThat(content).contains("bindTestThemeProvider")
    assertThat(content).contains("ThemeProvider")
    assertThat(content).contains("provideManifest")
  }

  @Test
  fun `DashboardThemeProvider on non-ThemeProvider fails`() {
    val source =
      SourceFile.kotlin(
        "BadThemeProvider.kt",
        """
            package test

            import app.dqxn.android.sdk.contracts.annotation.DashboardThemeProvider

            @DashboardThemeProvider
            class BadThemeProvider
            """
          .trimIndent(),
      )

    val result = compile(source)

    assertThat(result.exitCode).isEqualTo(ExitCode.COMPILATION_ERROR)
    assertThat(result.messages).contains("must implement ThemeProvider")
  }

  @Test
  fun `packCategory arg produces correct manifest category`() {
    val source =
      SourceFile.kotlin(
        "PremiumWidget.kt",
        """
            package test

            import app.dqxn.android.sdk.contracts.annotation.DashboardWidget
            import app.dqxn.android.sdk.contracts.widget.WidgetRenderer

            @DashboardWidget(typeId = "themes:custom-gauge", displayName = "Custom Gauge")
            class CustomGaugeRenderer : WidgetRenderer
            """
          .trimIndent(),
      )

    val result = compile(source, packId = "themes", packCategory = "PREMIUM")

    assertThat(result.exitCode).isEqualTo(ExitCode.OK)

    val generated = generatedKtFiles()
    val manifest = generated.firstOrNull { it.name == "ThemesGeneratedManifest.kt" }
    assertThat(manifest).isNotNull()

    val content = manifest!!.readText()
    assertThat(content).contains("PackCategory.PREMIUM")
    assertThat(content).doesNotContain("PackCategory.ESSENTIALS")
  }

  @Test
  fun `generated manifest is Hilt-injected via Provides IntoSet`() {
    val source =
      SourceFile.kotlin(
        "SimpleWidget.kt",
        """
            package test

            import app.dqxn.android.sdk.contracts.annotation.DashboardWidget
            import app.dqxn.android.sdk.contracts.widget.WidgetRenderer

            @DashboardWidget(typeId = "essentials:simple", displayName = "Simple")
            class SimpleRenderer : WidgetRenderer
            """
          .trimIndent(),
      )

    val result = compile(source)

    assertThat(result.exitCode).isEqualTo(ExitCode.OK)

    val generated = generatedKtFiles()
    val hiltModule = generated.first { it.name == "EssentialsHiltModule.kt" }.readText()
    assertThat(hiltModule).contains("@Provides")
    assertThat(hiltModule).contains("@IntoSet")
    assertThat(hiltModule).contains("provideManifest")
    assertThat(hiltModule).contains("DashboardPackManifest")
    assertThat(hiltModule).contains("EssentialsGeneratedManifest.manifest")
  }

  @Test
  fun `packCategory defaults to ESSENTIALS when missing`() {
    val source =
      SourceFile.kotlin(
        "DefaultCategoryWidget.kt",
        """
            package test

            import app.dqxn.android.sdk.contracts.annotation.DashboardWidget
            import app.dqxn.android.sdk.contracts.widget.WidgetRenderer

            @DashboardWidget(typeId = "essentials:default-cat", displayName = "Default Category")
            class DefaultCategoryRenderer : WidgetRenderer
            """
          .trimIndent(),
      )

    val result = compile(source, includePackCategory = false)

    assertThat(result.exitCode).isEqualTo(ExitCode.OK)

    val generated = generatedKtFiles()
    val manifest = generated.firstOrNull { it.name == "EssentialsGeneratedManifest.kt" }
    assertThat(manifest).isNotNull()

    val content = manifest!!.readText()
    assertThat(content).contains("PackCategory.ESSENTIALS")
  }
}
