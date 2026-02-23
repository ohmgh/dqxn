package app.dqxn.android.sdk.contracts.widget

public interface SettingsAwareSizer {
  public fun computeSize(settings: Map<String, Any?>): WidgetDefaults
}
