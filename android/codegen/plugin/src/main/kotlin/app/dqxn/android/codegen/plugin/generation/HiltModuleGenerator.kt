package app.dqxn.android.codegen.plugin.generation

import app.dqxn.android.codegen.plugin.model.ProviderInfo
import app.dqxn.android.codegen.plugin.model.WidgetInfo
import com.google.devtools.ksp.processing.CodeGenerator
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.addOriginatingKSFile
import com.squareup.kotlinpoet.ksp.writeTo

internal class HiltModuleGenerator(
  private val codeGenerator: CodeGenerator,
) {

  fun generate(packId: String, widgets: List<WidgetInfo>, providers: List<ProviderInfo>) {
    if (widgets.isEmpty() && providers.isEmpty()) return

    val moduleName = "${packId.replaceFirstChar { it.uppercase() }}HiltModule"
    val packageName = "app.dqxn.android.pack.$packId.generated"

    val moduleBuilder =
      TypeSpec.interfaceBuilder(moduleName)
        .addAnnotation(DAGGER_MODULE)
        .addAnnotation(
          AnnotationSpec.builder(DAGGER_INSTALL_IN)
            .addMember("%T::class", DAGGER_SINGLETON_COMPONENT)
            .build()
        )

    // Add @Binds @IntoSet for each widget
    for (widget in widgets) {
      moduleBuilder.addFunction(
        FunSpec.builder("bind${widget.className}")
          .addModifiers(KModifier.ABSTRACT)
          .addAnnotation(DAGGER_BINDS)
          .addAnnotation(DAGGER_INTO_SET)
          .addParameter("impl", widget.typeName)
          .returns(WIDGET_RENDERER)
          .build()
      )
      moduleBuilder.addOriginatingKSFile(widget.originatingFile)
    }

    // Add @Binds @IntoSet for each provider
    for (provider in providers) {
      moduleBuilder.addFunction(
        FunSpec.builder("bind${provider.className}")
          .addModifiers(KModifier.ABSTRACT)
          .addAnnotation(DAGGER_BINDS)
          .addAnnotation(DAGGER_INTO_SET)
          .addParameter("impl", provider.typeName)
          .returns(DATA_PROVIDER.parameterizedBy(STAR))
          .build()
      )
      moduleBuilder.addOriginatingKSFile(provider.originatingFile)
    }

    val fileSpec =
      FileSpec.builder(packageName, moduleName).addType(moduleBuilder.build()).build()

    // Per-class isolation for incremental processing
    fileSpec.writeTo(codeGenerator, aggregating = false)
  }

  private companion object {
    val DAGGER_MODULE = ClassName("dagger", "Module")
    val DAGGER_BINDS = ClassName("dagger", "Binds")
    val DAGGER_INTO_SET = ClassName("dagger.multibindings", "IntoSet")
    val DAGGER_INSTALL_IN = ClassName("dagger.hilt", "InstallIn")
    val DAGGER_SINGLETON_COMPONENT = ClassName("dagger.hilt.components", "SingletonComponent")
    val WIDGET_RENDERER = ClassName("app.dqxn.android.sdk.contracts.widget", "WidgetRenderer")
    val DATA_PROVIDER = ClassName("app.dqxn.android.sdk.contracts.provider", "DataProvider")
  }
}
