package app.dqxn.android.sdk.contracts.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.dqxn.android.sdk.contracts.entitlement.Gated
import kotlinx.collections.immutable.ImmutableMap

public interface WidgetRenderer : WidgetSpec, Gated {
  @Composable
  public fun Render(
    isEditMode: Boolean,
    style: WidgetStyle,
    settings: ImmutableMap<String, Any>,
    modifier: Modifier,
  )

  public fun accessibilityDescription(data: WidgetData): String

  public fun onTap(widgetId: String, settings: ImmutableMap<String, Any>): Boolean
}
