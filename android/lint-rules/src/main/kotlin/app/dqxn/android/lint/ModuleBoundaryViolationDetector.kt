package app.dqxn.android.lint

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UImportStatement

/**
 * Detects module boundary violations in pack modules.
 *
 * Pack modules can only depend on :sdk:* and :pack:*:snapshots. They must never import from
 * :feature:*, :core:*, or :data.
 */
class ModuleBoundaryViolationDetector : Detector(), SourceCodeScanner {

  override fun getApplicableUastTypes(): List<Class<out UElement>> =
    listOf(UImportStatement::class.java)

  override fun createUastHandler(context: JavaContext): UElementHandler =
    object : UElementHandler() {
      override fun visitImportStatement(node: UImportStatement) {
        val importRef = node.importReference?.asSourceString() ?: return

        // Only enforce on files within pack modules
        val packageName = context.uastFile?.packageName ?: return
        if (!isPackModule(packageName)) return

        when {
          importRef.startsWith("app.dqxn.android.feature.") -> {
            context.report(
              ISSUE,
              node,
              context.getLocation(node),
              "Pack modules cannot import from :feature:* modules. " +
                "If this type is needed by packs, move it to :sdk:contracts.",
            )
          }
          importRef.startsWith("app.dqxn.android.core.") -> {
            context.report(
              ISSUE,
              node,
              context.getLocation(node),
              "Pack modules cannot import from :core:* modules. " +
                "If this type is needed by packs, move it to :sdk:contracts.",
            )
          }
          importRef.startsWith("app.dqxn.android.data.") -> {
            context.report(
              ISSUE,
              node,
              context.getLocation(node),
              "Pack modules cannot import from the :data module. " +
                "If this type is needed by packs, move it to :sdk:contracts.",
            )
          }
        }
      }
    }

  companion object {
    /**
     * Checks if the file's package indicates it lives in a pack module. Pack modules use the
     * package pattern: app.dqxn.android.pack.* Snapshot sub-modules
     * (app.dqxn.android.pack.*.snapshots) are also pack code.
     */
    private fun isPackModule(packageName: String): Boolean =
      packageName.startsWith("app.dqxn.android.pack.")

    val ISSUE: Issue =
      Issue.create(
        id = "ModuleBoundaryViolation",
        briefDescription = "Pack module boundary violation",
        explanation =
          "Pack modules (:pack:*) can only depend on :sdk:* and :pack:*:snapshots. " +
            "Imports from :feature:*, :core:*, or :data violate the module boundary contract. " +
            "If pack code needs a type from these modules, the type should be extracted to :sdk:contracts.",
        category = Category.CORRECTNESS,
        priority = 9,
        severity = Severity.ERROR,
        implementation =
          Implementation(
            ModuleBoundaryViolationDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
          ),
      )
  }
}
