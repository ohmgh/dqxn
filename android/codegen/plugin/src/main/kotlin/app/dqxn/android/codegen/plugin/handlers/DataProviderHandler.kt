package app.dqxn.android.codegen.plugin.handlers

import app.dqxn.android.codegen.plugin.model.ProviderInfo
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.ksp.toClassName

internal class DataProviderHandler(
  private val resolver: Resolver,
  private val logger: KSPLogger,
) {

  fun process(): List<ProviderInfo> {
    val symbols =
      resolver
        .getSymbolsWithAnnotation(DASHBOARD_DATA_PROVIDER_FQN)
        .filterIsInstance<KSClassDeclaration>()

    return symbols.mapNotNull { classDecl -> extractProviderInfo(classDecl) }.toList()
  }

  private fun extractProviderInfo(classDecl: KSClassDeclaration): ProviderInfo? {
    val annotation =
      classDecl.annotations.firstOrNull {
        it.annotationType.resolve().declaration.qualifiedName?.asString() ==
          DASHBOARD_DATA_PROVIDER_FQN
      } ?: return null

    val localId = annotation.arguments.first { it.name?.asString() == "localId" }.value as String
    val displayName =
      annotation.arguments.first { it.name?.asString() == "displayName" }.value as String
    val description =
      annotation.arguments.firstOrNull { it.name?.asString() == "description" }?.value as? String
        ?: ""

    // Validate class implements DataProvider<*>
    val implementsDataProvider =
      classDecl.superTypes.any { superTypeRef ->
        val resolved = superTypeRef.resolve()
        resolved.declaration.qualifiedName?.asString() == DATA_PROVIDER_FQN
      }
    if (!implementsDataProvider) {
      logger.error(
        "@DashboardDataProvider class must implement DataProvider<*>: " +
          classDecl.simpleName.asString(),
        classDecl,
      )
      return null
    }

    // Try to resolve DataProvider's type argument T to extract dataType
    val dataType = resolveDataType(classDecl)

    val containingFile = classDecl.containingFile
    if (containingFile == null) {
      logger.error(
        "Cannot resolve containing file for ${classDecl.simpleName.asString()}",
        classDecl,
      )
      return null
    }

    return ProviderInfo(
      className = classDecl.simpleName.asString(),
      packageName = classDecl.packageName.asString(),
      localId = localId,
      displayName = displayName,
      description = description,
      dataType = dataType,
      typeName = classDecl.toClassName(),
      originatingFile = containingFile,
    )
  }

  /**
   * Attempts to resolve the DataProvider type argument's @DashboardSnapshot.dataType. If the
   * snapshot type is in a different module or doesn't have @DashboardSnapshot, returns empty
   * string. This is a documented limitation -- the runtime DataProviderRegistry has the actual
   * dataType from the provider instance.
   */
  private fun resolveDataType(classDecl: KSClassDeclaration): String {
    val dataProviderSuperType: KSType? =
      classDecl.superTypes
        .map { it.resolve() }
        .firstOrNull { it.declaration.qualifiedName?.asString() == DATA_PROVIDER_FQN }

    if (dataProviderSuperType == null) return ""

    val typeArg = dataProviderSuperType.arguments.firstOrNull()?.type?.resolve() ?: return ""
    val typeArgDecl = typeArg.declaration as? KSClassDeclaration ?: return ""

    // Look for @DashboardSnapshot annotation on the type argument
    val snapshotAnnotation =
      typeArgDecl.annotations.firstOrNull {
        it.annotationType.resolve().declaration.qualifiedName?.asString() ==
          DASHBOARD_SNAPSHOT_FQN
      } ?: return ""

    return snapshotAnnotation.arguments.firstOrNull { it.name?.asString() == "dataType" }?.value
      as? String ?: ""
  }

  private companion object {
    const val DASHBOARD_DATA_PROVIDER_FQN =
      "app.dqxn.android.sdk.contracts.annotation.DashboardDataProvider"
    const val DATA_PROVIDER_FQN = "app.dqxn.android.sdk.contracts.provider.DataProvider"
    const val DASHBOARD_SNAPSHOT_FQN =
      "app.dqxn.android.sdk.contracts.annotation.DashboardSnapshot"
  }
}
