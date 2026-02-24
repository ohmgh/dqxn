package app.dqxn.android.codegen.plugin.generation

import app.dqxn.android.codegen.plugin.model.ProviderInfo
import app.dqxn.android.codegen.plugin.model.WidgetInfo
import com.google.devtools.ksp.processing.CodeGenerator
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.addOriginatingKSFile
import com.squareup.kotlinpoet.ksp.writeTo

internal class ManifestGenerator(
  private val codeGenerator: CodeGenerator,
) {

  fun generate(packId: String, widgets: List<WidgetInfo>, providers: List<ProviderInfo>) {
    val objectName = "${packId.replaceFirstChar { it.uppercase() }}GeneratedManifest"
    val packageName = "app.dqxn.android.pack.$packId.generated"

    val manifestBuilder = TypeSpec.objectBuilder(objectName)

    // Track originating files for incremental processing
    for (widget in widgets) {
      manifestBuilder.addOriginatingKSFile(widget.originatingFile)
    }
    for (provider in providers) {
      manifestBuilder.addOriginatingKSFile(provider.originatingFile)
    }

    // Build the manifest property
    val manifestInitializer = buildManifestInitializer(packId, widgets, providers)
    manifestBuilder.addProperty(
      PropertySpec.builder("manifest", DASHBOARD_PACK_MANIFEST)
        .initializer(manifestInitializer)
        .build()
    )

    val fileSpec =
      FileSpec.builder(packageName, objectName).addType(manifestBuilder.build()).build()

    // Manifest aggregates all annotated symbols
    fileSpec.writeTo(codeGenerator, aggregating = true)
  }

  private fun buildManifestInitializer(
    packId: String,
    widgets: List<WidgetInfo>,
    providers: List<ProviderInfo>,
  ): CodeBlock {
    val builder = CodeBlock.builder()

    builder.add(
      "%T(\n",
      DASHBOARD_PACK_MANIFEST,
    )
    builder.indent()
    builder.addStatement("packId = %S,", packId)
    builder.addStatement("displayName = %S,", packId)
    builder.addStatement("description = %S,", "")
    builder.addStatement("version = 1,")

    // Widgets
    if (widgets.isEmpty()) {
      builder.addStatement("widgets = %M(),", PERSISTENT_LIST_OF)
    } else {
      builder.add("widgets = %M(\n", PERSISTENT_LIST_OF)
      builder.indent()
      for (widget in widgets) {
        builder.addStatement(
          "%T(typeId = %S, displayName = %S),",
          PACK_WIDGET_REF,
          widget.typeId,
          widget.displayName,
        )
      }
      builder.unindent()
      builder.addStatement("),")
    }

    // Themes (always empty -- generated manifest doesn't handle themes)
    builder.addStatement("themes = %M(),", PERSISTENT_LIST_OF)

    // Data providers
    if (providers.isEmpty()) {
      builder.addStatement("dataProviders = %M(),", PERSISTENT_LIST_OF)
    } else {
      builder.add("dataProviders = %M(\n", PERSISTENT_LIST_OF)
      builder.indent()
      for (provider in providers) {
        val sourceId = "$packId:${provider.localId}"
        builder.addStatement(
          "%T(sourceId = %S, displayName = %S, dataType = %S),",
          PACK_DATA_PROVIDER_REF,
          sourceId,
          provider.displayName,
          provider.dataType,
        )
      }
      builder.unindent()
      builder.addStatement("),")
    }

    builder.addStatement("category = %T.ESSENTIALS,", PACK_CATEGORY)
    builder.addStatement("entitlementId = null,")
    builder.unindent()
    builder.add(")")

    return builder.build()
  }

  private companion object {
    val DASHBOARD_PACK_MANIFEST =
      ClassName("app.dqxn.android.sdk.contracts.pack", "DashboardPackManifest")
    val PACK_WIDGET_REF = ClassName("app.dqxn.android.sdk.contracts.pack", "PackWidgetRef")
    val PACK_DATA_PROVIDER_REF =
      ClassName("app.dqxn.android.sdk.contracts.pack", "PackDataProviderRef")
    val PACK_CATEGORY = ClassName("app.dqxn.android.sdk.contracts.pack", "PackCategory")
    val PERSISTENT_LIST_OF =
      MemberName("kotlinx.collections.immutable", "persistentListOf")
  }
}
