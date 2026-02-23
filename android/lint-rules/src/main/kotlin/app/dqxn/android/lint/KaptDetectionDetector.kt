package app.dqxn.android.lint

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.GradleContext
import com.android.tools.lint.detector.api.GradleScanner
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity

/**
 * Detects usage of KAPT in Gradle build files.
 *
 * KAPT breaks Gradle configuration cache and is slower than KSP. All annotation processing in DQXN
 * must use KSP exclusively.
 */
class KaptDetectionDetector : Detector(), GradleScanner {

  override fun checkMethodCall(
    context: GradleContext,
    statement: String,
    parent: String?,
    namedArguments: Map<String, String>,
    unnamedArguments: List<String>,
    cookie: Any,
  ) {
    // Detect plugins { id("kotlin-kapt") } or apply plugin: 'kotlin-kapt'
    val statementTrimmed = statement.trim()
    if (statementTrimmed == "id" || statementTrimmed == "apply") {
      val pluginId =
        unnamedArguments.firstOrNull()?.removeSurrounding("'")?.removeSurrounding("\"")
          ?: namedArguments["plugin"]?.removeSurrounding("'")?.removeSurrounding("\"")
      if (pluginId == "kotlin-kapt" || pluginId == "org.jetbrains.kotlin.kapt") {
        context.report(
          ISSUE,
          cookie,
          context.getLocation(cookie),
          "KAPT is not allowed -- use KSP instead. KAPT breaks Gradle configuration cache.",
        )
      }
    }

    // Detect kapt("dep") as a method call in dependencies block
    if (statementTrimmed == "kapt" && parent == "dependencies") {
      context.report(
        ISSUE,
        cookie,
        context.getLocation(cookie),
        "KAPT is not allowed -- use KSP instead. KAPT breaks Gradle configuration cache.",
      )
    }
  }

  companion object {
    val ISSUE: Issue =
      Issue.create(
        id = "KaptDetection",
        briefDescription = "KAPT detected -- use KSP",
        explanation =
          "KAPT breaks Gradle configuration cache. All annotation processing must use KSP.",
        category = Category.CORRECTNESS,
        priority = 9,
        severity = Severity.ERROR,
        implementation =
          Implementation(
            KaptDetectionDetector::class.java,
            Scope.GRADLE_SCOPE,
          ),
      )
  }
}
