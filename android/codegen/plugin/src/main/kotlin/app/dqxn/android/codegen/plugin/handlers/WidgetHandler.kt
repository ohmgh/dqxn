package app.dqxn.android.codegen.plugin.handlers

import app.dqxn.android.codegen.plugin.model.WidgetInfo
import app.dqxn.android.codegen.plugin.validation.TypeIdValidator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.ksp.toClassName

internal class WidgetHandler(
  private val resolver: Resolver,
  private val logger: KSPLogger,
) {

  fun process(): List<WidgetInfo> {
    val symbols =
      resolver.getSymbolsWithAnnotation(DASHBOARD_WIDGET_FQN).filterIsInstance<KSClassDeclaration>()

    return symbols.mapNotNull { classDecl -> extractWidgetInfo(classDecl) }.toList()
  }

  private fun extractWidgetInfo(classDecl: KSClassDeclaration): WidgetInfo? {
    val annotation =
      classDecl.annotations.firstOrNull {
        it.annotationType.resolve().declaration.qualifiedName?.asString() == DASHBOARD_WIDGET_FQN
      } ?: return null

    val typeId = annotation.arguments.first { it.name?.asString() == "typeId" }.value as String
    val displayName =
      annotation.arguments.first { it.name?.asString() == "displayName" }.value as String
    val icon =
      annotation.arguments.firstOrNull { it.name?.asString() == "icon" }?.value as? String ?: ""

    // Validate typeId format
    if (!TypeIdValidator.validate(typeId, logger, classDecl)) {
      return null
    }

    // Validate class implements WidgetRenderer
    val implementsWidgetRenderer =
      classDecl.superTypes.any {
        it.resolve().declaration.qualifiedName?.asString() == WIDGET_RENDERER_FQN
      }
    if (!implementsWidgetRenderer) {
      logger.error(
        "@DashboardWidget class must implement WidgetRenderer: ${classDecl.simpleName.asString()}",
        classDecl,
      )
      return null
    }

    val containingFile = classDecl.containingFile
    if (containingFile == null) {
      logger.error("Cannot resolve containing file for ${classDecl.simpleName.asString()}", classDecl)
      return null
    }

    return WidgetInfo(
      className = classDecl.simpleName.asString(),
      packageName = classDecl.packageName.asString(),
      typeId = typeId,
      displayName = displayName,
      icon = icon,
      typeName = classDecl.toClassName(),
      originatingFile = containingFile,
    )
  }

  private companion object {
    const val DASHBOARD_WIDGET_FQN = "app.dqxn.android.sdk.contracts.annotation.DashboardWidget"
    const val WIDGET_RENDERER_FQN = "app.dqxn.android.sdk.contracts.widget.WidgetRenderer"
  }
}
