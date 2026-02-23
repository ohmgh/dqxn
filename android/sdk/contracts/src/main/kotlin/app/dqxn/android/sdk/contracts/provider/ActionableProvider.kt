package app.dqxn.android.sdk.contracts.provider

import app.dqxn.android.sdk.contracts.widget.WidgetAction

public interface ActionableProvider<T : DataSnapshot> : DataProvider<T> {
  public fun onAction(action: WidgetAction)
}
