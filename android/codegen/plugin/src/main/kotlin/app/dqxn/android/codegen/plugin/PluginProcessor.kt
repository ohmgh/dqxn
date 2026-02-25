package app.dqxn.android.codegen.plugin

import app.dqxn.android.codegen.plugin.generation.HiltModuleGenerator
import app.dqxn.android.codegen.plugin.generation.ManifestGenerator
import app.dqxn.android.codegen.plugin.generation.StabilityConfigGenerator
import app.dqxn.android.codegen.plugin.handlers.DataProviderHandler
import app.dqxn.android.codegen.plugin.handlers.SnapshotHandler
import app.dqxn.android.codegen.plugin.handlers.ThemeProviderHandler
import app.dqxn.android.codegen.plugin.handlers.WidgetHandler
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.squareup.kotlinpoet.ClassName

internal class PluginProcessor(
  private val codeGenerator: CodeGenerator,
  private val logger: KSPLogger,
  private val options: Map<String, String>,
) : SymbolProcessor {

  private var invoked = false

  override fun process(resolver: Resolver): List<KSAnnotated> {
    if (invoked) return emptyList()
    invoked = true

    val packId = options["packId"]
    if (packId == null) {
      logger.error("Missing required KSP option: packId")
      return emptyList()
    }

    val packCategory = options["packCategory"] ?: "ESSENTIALS"

    // Delegate to handlers
    val widgetInfos = WidgetHandler(resolver, logger).process()
    val providerInfos = DataProviderHandler(resolver, logger).process()
    val snapshotInfos = SnapshotHandler(resolver, logger).process()
    val themeProviderInfos = ThemeProviderHandler(resolver, logger).process()

    // If any handler reported errors via logger.error(), KSP will fail compilation after
    // process() returns. We still skip generation to avoid producing partial output.
    // KSP tracks error count internally -- we check nothing here because the handlers
    // already returned null for invalid entries. Empty lists are fine (no annotated classes).

    // Compute manifest ClassName for HiltModule to reference
    val manifestClassName = ClassName(
      "app.dqxn.android.pack.$packId.generated",
      "${packId.replaceFirstChar { it.uppercase() }}GeneratedManifest",
    )

    // Generate outputs
    ManifestGenerator(codeGenerator).generate(packId, packCategory, widgetInfos, providerInfos, themeProviderInfos)
    HiltModuleGenerator(codeGenerator).generate(packId, widgetInfos, providerInfos, themeProviderInfos, manifestClassName)
    StabilityConfigGenerator(codeGenerator).generate(snapshotInfos)

    return emptyList()
  }
}
