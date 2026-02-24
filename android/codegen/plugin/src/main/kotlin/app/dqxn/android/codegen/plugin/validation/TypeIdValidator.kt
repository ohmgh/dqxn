package app.dqxn.android.codegen.plugin.validation

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSNode

internal object TypeIdValidator {

  private val TYPE_ID_PATTERN = Regex("^[a-z][a-z0-9]*:[a-z][a-z0-9-]*$")

  fun validate(typeId: String, logger: KSPLogger, node: KSNode): Boolean {
    if (!TYPE_ID_PATTERN.matches(typeId)) {
      logger.error(
        "@DashboardWidget typeId must match '{packId}:{widget-name}' format " +
          "(lowercase letters, digits, hyphens after colon), got: $typeId",
        node,
      )
      return false
    }
    return true
  }
}
