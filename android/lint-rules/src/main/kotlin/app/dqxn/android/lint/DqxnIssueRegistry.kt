package app.dqxn.android.lint

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.Vendor
import com.android.tools.lint.detector.api.CURRENT_API
import com.android.tools.lint.detector.api.Issue

/**
 * Lint issue registry for DQXN custom lint rules.
 *
 * Registers all architectural constraint detectors:
 * 1. KaptDetection -- no KAPT (configuration cache)
 * 2. NoHardcodedSecrets -- no secrets in source
 * 3. ModuleBoundaryViolation -- pack isolation
 * 4. ComposeInNonUiModule -- Compose compiler scope
 * 5. AgenticMainThreadBan -- agentic threading safety
 * 6. WidgetScopeBypass -- widget coroutine isolation
 */
class DqxnIssueRegistry : IssueRegistry() {

  override val issues: List<Issue> =
    listOf(
      KaptDetectionDetector.ISSUE,
      NoHardcodedSecretsDetector.ISSUE,
      ModuleBoundaryViolationDetector.ISSUE,
      ComposeInNonUiModuleDetector.ISSUE,
      AgenticMainThreadBanDetector.ISSUE,
      WidgetScopeBypassDetector.ISSUE,
    )

  override val api: Int = CURRENT_API

  override val minApi: Int = CURRENT_API

  override val vendor: Vendor =
    Vendor(
      vendorName = "DQXN",
      identifier = "app.dqxn.android.lint",
      feedbackUrl = "https://github.com/dqxn/android/issues",
    )
}
