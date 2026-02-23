package app.dqxn.android.sdk.contracts.widget

public sealed interface WidgetAction {
  public data class Tap(val widgetId: String) : WidgetAction

  public data class MediaControl(val command: String) : WidgetAction

  public data class TripReset(val tripId: String) : WidgetAction

  public data class Custom(
    val actionId: String,
    val params: Map<String, Any?>,
  ) : WidgetAction
}
