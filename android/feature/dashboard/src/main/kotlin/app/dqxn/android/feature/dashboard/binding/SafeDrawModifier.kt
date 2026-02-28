package app.dqxn.android.feature.dashboard.binding

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo

/**
 * Wraps `drawContent()` in a try-catch to intercept draw-phase exceptions from widget renderers.
 *
 * Only catches [Exception], not [Error] (OOM, LinkageError, etc. must propagate). On exception,
 * invokes [onDrawError] with the throwable. The draw is simply skipped for that frame — the
 * composable layer above (WidgetSlot) handles showing [WidgetErrorFallback].
 */
internal fun Modifier.safeWidgetDraw(onDrawError: (Exception) -> Unit): Modifier =
  this then SafeDrawElement(onDrawError)

private data class SafeDrawElement(
  val onDrawError: (Exception) -> Unit,
) : ModifierNodeElement<SafeDrawNode>() {
  override fun create(): SafeDrawNode = SafeDrawNode(onDrawError)

  override fun update(node: SafeDrawNode) {
    node.onDrawError = onDrawError
  }

  override fun InspectorInfo.inspectableProperties() {
    name = "safeWidgetDraw"
  }
}

private class SafeDrawNode(
  var onDrawError: (Exception) -> Unit,
) : Modifier.Node(), DrawModifierNode {
  override fun ContentDrawScope.draw() {
    try {
      drawContent()
    } catch (e: Exception) {
      onDrawError(e)
    }
  }
}
