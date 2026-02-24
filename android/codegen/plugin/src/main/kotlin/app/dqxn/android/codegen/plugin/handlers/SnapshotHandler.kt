package app.dqxn.android.codegen.plugin.handlers

import app.dqxn.android.codegen.plugin.model.SnapshotInfo
import app.dqxn.android.codegen.plugin.validation.SnapshotValidator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration

internal class SnapshotHandler(
  private val resolver: Resolver,
  private val logger: KSPLogger,
) {

  fun process(): List<SnapshotInfo> {
    val symbols =
      resolver
        .getSymbolsWithAnnotation(DASHBOARD_SNAPSHOT_FQN)
        .filterIsInstance<KSClassDeclaration>()

    val results = mutableListOf<SnapshotInfo>()
    val seenDataTypes = mutableSetOf<String>()

    for (classDecl in symbols) {
      val info = extractSnapshotInfo(classDecl, seenDataTypes) ?: continue
      results.add(info)
    }

    return results
  }

  private fun extractSnapshotInfo(
    classDecl: KSClassDeclaration,
    seenDataTypes: MutableSet<String>,
  ): SnapshotInfo? {
    // Validate snapshot class structure
    if (!SnapshotValidator.validate(classDecl, logger)) {
      return null
    }

    val annotation =
      classDecl.annotations.firstOrNull {
        it.annotationType.resolve().declaration.qualifiedName?.asString() ==
          DASHBOARD_SNAPSHOT_FQN
      } ?: return null

    val dataType =
      annotation.arguments.first { it.name?.asString() == "dataType" }.value as String

    // Check for duplicate dataType within this module
    if (!seenDataTypes.add(dataType)) {
      logger.error(
        "@DashboardSnapshot duplicate dataType '$dataType' in module. " +
          "Each dataType must be unique within a module.",
        classDecl,
      )
      return null
    }

    val qualifiedName = classDecl.qualifiedName?.asString()
    if (qualifiedName == null) {
      logger.error("Cannot resolve qualified name for ${classDecl.simpleName.asString()}", classDecl)
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

    return SnapshotInfo(
      className = classDecl.simpleName.asString(),
      packageName = classDecl.packageName.asString(),
      qualifiedName = qualifiedName,
      dataType = dataType,
      originatingFile = containingFile,
    )
  }

  private companion object {
    const val DASHBOARD_SNAPSHOT_FQN =
      "app.dqxn.android.sdk.contracts.annotation.DashboardSnapshot"
  }
}
