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
import org.jetbrains.uast.UQualifiedReferenceExpression

/**
 * Detects Dispatchers.Main usage in agentic command handlers.
 *
 * Agentic commands run in a debug/diagnostic context where main thread dispatching is unnecessary
 * and potentially harmful (blocking UI during diagnostic operations). Use Dispatchers.Default or
 * Dispatchers.IO.
 */
class AgenticMainThreadBanDetector : Detector(), SourceCodeScanner {

  override fun getApplicableUastTypes(): List<Class<out UElement>> =
    listOf(UQualifiedReferenceExpression::class.java)

  override fun createUastHandler(context: JavaContext): UElementHandler =
    object : UElementHandler() {
      override fun visitQualifiedReferenceExpression(node: UQualifiedReferenceExpression) {
        val packageName = context.uastFile?.packageName ?: return
        if (!isAgenticModule(packageName)) return

        val source = node.asSourceString()
        if (source == "Dispatchers.Main" || source == "Dispatchers.Main.immediate") {
          context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Dispatchers.Main is banned in agentic command handlers. " +
              "Use Dispatchers.Default or Dispatchers.IO.",
          )
        }
      }
    }

  companion object {
    private fun isAgenticModule(packageName: String): Boolean =
      packageName.startsWith("dev.agentic.android") ||
        packageName.startsWith("app.dqxn.android.agentic")

    val ISSUE: Issue =
      Issue.create(
        id = "AgenticMainThreadBan",
        briefDescription = "Main dispatcher in agentic handler",
        explanation =
          "Dispatchers.Main is banned in agentic command handlers (dev.agentic.android + app.dqxn.android.agentic). " +
            "Agentic commands are debug/diagnostic operations that should never dispatch to the main thread. " +
            "Use Dispatchers.Default for CPU work or Dispatchers.IO for I/O operations.",
        category = Category.PERFORMANCE,
        priority = 8,
        severity = Severity.ERROR,
        implementation =
          Implementation(
            AgenticMainThreadBanDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
          ),
      )
  }
}
