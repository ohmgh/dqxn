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
 * Detects coroutine scope bypass in widget Render() functions.
 *
 * Widgets must use `LocalWidgetScope.current` for coroutine work to maintain per-widget isolation
 * via SupervisorJob. Direct use of `rememberCoroutineScope()`, `GlobalScope`, or constructed
 * `CoroutineScope()` bypasses the isolation boundary.
 *
 * Only applies to files in pack widget packages (matching `app.dqxn.android.pack.*.widgets.*`).
 */
class WidgetScopeBypassDetector : Detector(), SourceCodeScanner {

  override fun getApplicableUastTypes(): List<Class<out UElement>> =
    listOf(UImportStatement::class.java)

  override fun createUastHandler(context: JavaContext): UElementHandler =
    object : UElementHandler() {
      override fun visitImportStatement(node: UImportStatement) {
        val packageName = context.uastFile?.packageName ?: return
        if (!isWidgetPackage(packageName)) return

        val importRef = node.importReference?.asSourceString() ?: return

        for ((import, message) in PROHIBITED_IMPORTS) {
          if (importRef == import) {
            context.report(ISSUE, node, context.getLocation(node), message)
            return
          }
        }
      }
    }

  companion object {
    private val PROHIBITED_IMPORTS: List<Pair<String, String>> =
      listOf(
        "androidx.compose.runtime.rememberCoroutineScope" to
          "Use `LocalWidgetScope.current` instead of `rememberCoroutineScope()` " +
            "in widget Render functions. Widget coroutine isolation requires the " +
            "supervised WidgetCoroutineScope.",
        "kotlinx.coroutines.GlobalScope" to
          "Use `LocalWidgetScope.current` instead of `GlobalScope` " +
            "in widget Render functions. Widget coroutine isolation requires the " +
            "supervised WidgetCoroutineScope.",
        "kotlinx.coroutines.CoroutineScope" to
          "Use `LocalWidgetScope.current` instead of constructing `CoroutineScope()` " +
            "in widget Render functions. Widget coroutine isolation requires the " +
            "supervised WidgetCoroutineScope.",
      )

    /**
     * Detects widget packages: `app.dqxn.android.pack.{packId}.widgets` or sub-packages.
     *
     * The `widgets` sub-package indicates this is a widget renderer file in a pack module.
     */
    private fun isWidgetPackage(packageName: String): Boolean {
      if (!packageName.startsWith("app.dqxn.android.pack.")) return false
      val remainder = packageName.removePrefix("app.dqxn.android.pack.")
      val parts = remainder.split('.')
      return parts.size >= 2 && parts[1] == "widgets"
    }

    val ISSUE: Issue =
      Issue.create(
        id = "WidgetScopeBypass",
        briefDescription = "Coroutine scope bypass in widget Render function",
        explanation =
          "Widget Render() functions must use `LocalWidgetScope.current` for all coroutine work. " +
            "Using `rememberCoroutineScope()`, `GlobalScope`, or constructed `CoroutineScope()` " +
            "bypasses the per-widget SupervisorJob isolation boundary. A crash in one widget's " +
            "coroutine could then cancel siblings or propagate to the dashboard scope.",
        category = Category.CORRECTNESS,
        priority = 8,
        severity = Severity.ERROR,
        implementation =
          Implementation(
            WidgetScopeBypassDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
          ),
      )
  }
}
