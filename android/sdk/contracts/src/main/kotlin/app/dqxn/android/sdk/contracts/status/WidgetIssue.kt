package app.dqxn.android.sdk.contracts.status

import androidx.compose.runtime.Immutable

/** A specific issue affecting a widget, with optional resolution action. */
@Immutable
public data class WidgetIssue(
  val type: IssueType,
  val message: String,
  val resolution: ResolutionAction? = null,
)

/** Category of widget issue. */
public enum class IssueType {
  SETUP,
  CONNECTION,
  ENTITLEMENT,
  DATA,
  PROVIDER,
}

/** Action to resolve a [WidgetIssue]. */
public sealed interface ResolutionAction {
  public data class OpenSetup(val sourceId: String) : ResolutionAction

  public data class RequestPermission(val widgetId: String, val sourceId: String) :
    ResolutionAction

  public data object OpenSettings : ResolutionAction

  public data class Retry(val widgetId: String) : ResolutionAction
}
