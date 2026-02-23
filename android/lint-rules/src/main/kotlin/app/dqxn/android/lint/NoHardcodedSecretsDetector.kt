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
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.getParentOfType

/**
 * Detects hardcoded secrets (API keys, tokens, credentials) in Kotlin/Java source.
 *
 * Secrets should be stored in local.properties, environment variables,
 * or a secrets management solution -- never in source code.
 */
class NoHardcodedSecretsDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(ULiteralExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitLiteralExpression(node: ULiteralExpression) {
                // Skip test files
                val filePath = context.file.path
                if (filePath.contains("/test/") || filePath.contains("/androidTest/")) {
                    return
                }

                val value = node.value as? String ?: return
                if (value.length <= 8) return

                // Skip placeholder values
                if (isPlaceholder(value)) return

                // Check if this string is assigned to a suspiciously-named variable/field
                val variable = node.getParentOfType<UVariable>()
                if (variable != null) {
                    val name = variable.name ?: return
                    if (SUSPICIOUS_NAME_PATTERN.containsMatchIn(name)) {
                        context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            "Hardcoded secret detected in `$name`. " +
                                "Use local.properties or a secrets plugin instead.",
                        )
                    }
                }
            }
        }

    companion object {
        private val SUSPICIOUS_NAME_PATTERN = Regex(
            """(?i)(api[_]?key|secret[_]?key|access[_]?token|auth[_]?token|client[_]?secret|private[_]?key|api[_]?secret|secret[_]?token)""",
        )

        private val PLACEHOLDER_PATTERNS = listOf(
            "YOUR_KEY_HERE",
            "YOUR_TOKEN_HERE",
            "PLACEHOLDER",
            "TODO",
            "REPLACE_ME",
            "INSERT_KEY",
            "xxx",
        )

        private fun isPlaceholder(value: String): Boolean {
            val upper = value.uppercase()
            return PLACEHOLDER_PATTERNS.any { upper.contains(it) }
        }

        val ISSUE: Issue = Issue.create(
            id = "NoHardcodedSecrets",
            briefDescription = "Hardcoded secret detected",
            explanation = "API keys, tokens, and credentials must not be hardcoded in source. " +
                "Use local.properties, environment variables, or a secrets management plugin.",
            category = Category.SECURITY,
            priority = 9,
            severity = Severity.ERROR,
            implementation = Implementation(
                NoHardcodedSecretsDetector::class.java,
                Scope.JAVA_FILE_SCOPE,
            ),
        )
    }
}
