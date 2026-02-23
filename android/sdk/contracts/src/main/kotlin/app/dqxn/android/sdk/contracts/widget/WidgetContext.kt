package app.dqxn.android.sdk.contracts.widget

import androidx.compose.runtime.Immutable
import java.time.ZoneId
import java.util.Locale

@Immutable
public data class WidgetContext(
  val timezone: ZoneId,
  val locale: Locale,
  val region: String,
) {
  public companion object {
    public val DEFAULT: WidgetContext =
      WidgetContext(
        timezone = ZoneId.of("UTC"),
        locale = Locale.US,
        region = "US",
      )
  }
}
