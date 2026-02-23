package app.dqxn.android.sdk.contracts.widget

import androidx.compose.runtime.Immutable

@Immutable
public data class WidgetDefaults(
  val widthUnits: Int,
  val heightUnits: Int,
  val aspectRatio: Float?,
  val settings: Map<String, Any?>,
)
