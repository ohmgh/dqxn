package app.dqxn.android.codegen.plugin.validation

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.Modifier

internal object SnapshotValidator {

  private const val DATA_SNAPSHOT_FQN = "app.dqxn.android.sdk.contracts.provider.DataSnapshot"
  private const val IMMUTABLE_FQN = "androidx.compose.runtime.Immutable"

  fun validate(classDecl: KSClassDeclaration, logger: KSPLogger): Boolean {
    var valid = true

    // Must be a data class
    if (classDecl.classKind != ClassKind.CLASS || Modifier.DATA !in classDecl.modifiers) {
      logger.error("@DashboardSnapshot must be applied to a data class", classDecl)
      valid = false
    }

    // Must implement DataSnapshot
    val implementsDataSnapshot =
      classDecl.superTypes.any {
        it.resolve().declaration.qualifiedName?.asString() == DATA_SNAPSHOT_FQN
      }
    if (!implementsDataSnapshot) {
      logger.error("@DashboardSnapshot class must implement DataSnapshot", classDecl)
      valid = false
    }

    // Must have @Immutable annotation
    val hasImmutable =
      classDecl.annotations.any {
        it.annotationType.resolve().declaration.qualifiedName?.asString() == IMMUTABLE_FQN
      }
    if (!hasImmutable) {
      logger.error("@DashboardSnapshot class must be annotated with @Immutable", classDecl)
      valid = false
    }

    // All properties must be val (no var)
    classDecl.getAllProperties().forEach { prop ->
      if (prop.isMutable) {
        logger.error(
          "@DashboardSnapshot properties must be val, not var: ${prop.simpleName.asString()}",
          prop,
        )
        valid = false
      }
    }

    return valid
  }
}
