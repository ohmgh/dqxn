package app.dqxn.android.sdk.contracts.widget

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Serializable
public enum class BackgroundStyle {
  NONE,
  SOLID,
}

@Serializable
@Immutable
public data class WidgetStyle(
  val backgroundStyle: BackgroundStyle,
  val opacity: Float,
  val showBorder: Boolean,
  val hasGlowEffect: Boolean,
  val cornerRadiusPercent: Int,
  val rimSizePercent: Int,
  val zLayer: Int,
) {
  public companion object {
    public val Default: WidgetStyle =
      WidgetStyle(
        backgroundStyle = BackgroundStyle.NONE,
        opacity = 1.0f,
        showBorder = false,
        hasGlowEffect = false,
        cornerRadiusPercent = 25,
        rimSizePercent = 0,
        zLayer = 0,
      )
  }
}
