package app.dqxn.android.sdk.contracts.status

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** Cached status for a widget: current overlay state plus accumulated issues. */
@Immutable
public data class WidgetStatusCache(
  val overlayState: WidgetRenderState,
  val issues: ImmutableList<WidgetIssue>,
) {
  public companion object {
    public val EMPTY: WidgetStatusCache =
      WidgetStatusCache(
        overlayState = WidgetRenderState.Ready,
        issues = persistentListOf(),
      )
  }
}
