package app.dqxn.android.codegen.plugin.handlers

import app.dqxn.android.codegen.plugin.model.ThemeProviderInfo
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.ksp.toClassName

internal class ThemeProviderHandler(
  private val resolver: Resolver,
  private val logger: KSPLogger,
) {

  fun process(): List<ThemeProviderInfo> {
    val symbols =
      resolver
        .getSymbolsWithAnnotation(DASHBOARD_THEME_PROVIDER_FQN)
        .filterIsInstance<KSClassDeclaration>()

    return symbols.mapNotNull { classDecl -> extractThemeProviderInfo(classDecl) }.toList()
  }

  private fun extractThemeProviderInfo(classDecl: KSClassDeclaration): ThemeProviderInfo? {
    // Validate class implements ThemeProvider
    val implementsThemeProvider =
      classDecl.superTypes.any {
        it.resolve().declaration.qualifiedName?.asString() == THEME_PROVIDER_FQN
      }
    if (!implementsThemeProvider) {
      logger.error(
        "@DashboardThemeProvider class must implement ThemeProvider: " +
          classDecl.simpleName.asString(),
        classDecl,
      )
      return null
    }

    val containingFile = classDecl.containingFile
    if (containingFile == null) {
      logger.error(
        "Cannot resolve containing file for ${classDecl.simpleName.asString()}",
        classDecl,
      )
      return null
    }

    return ThemeProviderInfo(
      className = classDecl.simpleName.asString(),
      packageName = classDecl.packageName.asString(),
      typeName = classDecl.toClassName(),
      originatingFile = containingFile,
    )
  }

  private companion object {
    const val DASHBOARD_THEME_PROVIDER_FQN =
      "app.dqxn.android.sdk.contracts.annotation.DashboardThemeProvider"
    const val THEME_PROVIDER_FQN = "app.dqxn.android.sdk.contracts.theme.ThemeProvider"
  }
}
