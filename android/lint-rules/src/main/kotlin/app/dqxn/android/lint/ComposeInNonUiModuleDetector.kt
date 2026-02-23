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
 * Detects Compose imports in modules that should not have the Compose compiler.
 *
 * Non-UI modules: sdk.contracts, sdk.common, sdk.observability, sdk.analytics, core.thermal,
 * core.firebase, core.agentic, data.
 *
 * Exception: sdk.contracts may use @Composable, @Immutable, @Stable from compose.runtime via
 * compileOnly -- these specific imports are allowed.
 */
class ComposeInNonUiModuleDetector : Detector(), SourceCodeScanner {

  override fun getApplicableUastTypes(): List<Class<out UElement>> =
    listOf(UImportStatement::class.java)

  override fun createUastHandler(context: JavaContext): UElementHandler =
    object : UElementHandler() {
      override fun visitImportStatement(node: UImportStatement) {
        val importRef = node.importReference?.asSourceString() ?: return
        if (!importRef.startsWith("androidx.compose.")) return

        val packageName = context.uastFile?.packageName ?: return
        val moduleType = classifyModule(packageName) ?: return

        when (moduleType) {
          ModuleType.CONTRACTS -> {
            // sdk:contracts allows specific compose.runtime annotations only
            if (!isAllowedContractsImport(importRef)) {
              context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Compose import $importRef is not allowed in :sdk:contracts. " +
                  "Only @Composable, @Immutable, and @Stable from " +
                  "androidx.compose.runtime are permitted (via compileOnly).",
              )
            }
          }
          ModuleType.NON_UI -> {
            context.report(
              ISSUE,
              node,
              context.getLocation(node),
              "Compose import $importRef is not allowed in a non-UI module. " +
                "This module does not have the Compose compiler. " +
                "Move Compose code to :sdk:ui, :core:design, or a :feature:* module.",
            )
          }
        }
      }
    }

  companion object {
    private enum class ModuleType {
      CONTRACTS,
      NON_UI,
    }

    /** Allowed compose.runtime imports in :sdk:contracts (via compileOnly). */
    private val ALLOWED_CONTRACTS_IMPORTS =
      setOf(
        "androidx.compose.runtime.Composable",
        "androidx.compose.runtime.Immutable",
        "androidx.compose.runtime.Stable",
      )

    /** Package prefixes for non-UI modules (no Compose compiler). */
    private val NON_UI_PACKAGES =
      listOf(
        "app.dqxn.android.sdk.common",
        "app.dqxn.android.sdk.observability",
        "app.dqxn.android.sdk.analytics",
        "app.dqxn.android.core.thermal",
        "app.dqxn.android.core.firebase",
        "app.dqxn.android.core.agentic",
        "app.dqxn.android.data",
      )

    private fun classifyModule(packageName: String): ModuleType? {
      if (packageName.startsWith("app.dqxn.android.sdk.contracts")) {
        return ModuleType.CONTRACTS
      }
      for (prefix in NON_UI_PACKAGES) {
        if (packageName == prefix || packageName.startsWith("$prefix.")) {
          return ModuleType.NON_UI
        }
      }
      return null // UI module or pack module -- Compose allowed
    }

    private fun isAllowedContractsImport(importRef: String): Boolean =
      importRef in ALLOWED_CONTRACTS_IMPORTS

    val ISSUE: Issue =
      Issue.create(
        id = "ComposeInNonUiModule",
        briefDescription = "Compose import in non-UI module",
        explanation =
          "This module does not have the Compose compiler enabled. " +
            "Compose UI code belongs in modules with the `dqxn.android.compose` or `dqxn.pack` plugin. " +
            "Non-UI modules: :sdk:contracts (limited runtime annotations only), :sdk:common, " +
            ":sdk:observability, :sdk:analytics, :core:thermal, :core:firebase, :core:agentic, :data.",
        category = Category.CORRECTNESS,
        priority = 8,
        severity = Severity.ERROR,
        implementation =
          Implementation(
            ComposeInNonUiModuleDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
          ),
      )
  }
}
