package app.dqxn.android.agentic.overlays

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dqxn.android.sdk.observability.health.WidgetHealthMonitor

/**
 * Debug overlay showing per-widget health status. Positioned at bottom-left.
 *
 * Displays each tracked widget's health (ACTIVE, STALE_DATA, STALLED_RENDER, CRASHED,
 * SETUP_REQUIRED) with a color indicator. Shows stalled render count.
 *
 * At Phase 6, no widgets exist -- overlay shows "No widgets" until the essentials pack registers
 * widgets in Phase 8.
 */
@Composable
internal fun WidgetHealthOverlay(
  healthMonitor: WidgetHealthMonitor,
  modifier: Modifier = Modifier,
) {
  val statuses by remember { derivedStateOf { healthMonitor.allStatuses() } }

  Column(
    modifier = modifier.graphicsLayer().background(OverlayBg).padding(8.dp),
  ) {
    Text(
      text = "Widget Health",
      style = HeaderStyle,
    )

    if (statuses.isEmpty()) {
      Text(
        text = "No widgets",
        style = BodyStyle,
      )
    } else {
      val stalledCount by remember {
        derivedStateOf {
          statuses.values.count { it.status == WidgetHealthMonitor.Status.STALLED_RENDER }
        }
      }

      statuses.forEach { (widgetId, health) ->
        val statusColor = remember(health.status) { colorForStatus(health.status) }
        Text(
          text = "${health.typeId} ($widgetId): ${health.status.name}",
          style = BodyStyle.copy(color = statusColor),
        )
      }

      Text(
        text = "Stalled: $stalledCount",
        style = BodyStyle,
      )
    }
  }
}

private fun colorForStatus(status: WidgetHealthMonitor.Status): Color =
  when (status) {
    WidgetHealthMonitor.Status.ACTIVE -> Color(0xFF4CAF50)
    WidgetHealthMonitor.Status.STALE_DATA -> Color(0xFFFFC107)
    WidgetHealthMonitor.Status.STALLED_RENDER -> Color(0xFFFF9800)
    WidgetHealthMonitor.Status.CRASHED -> Color(0xFFF44336)
    WidgetHealthMonitor.Status.SETUP_REQUIRED -> Color(0xFF9E9E9E)
  }

private val OverlayBg = Color(0xCC1A1A1A)

private val HeaderStyle =
  TextStyle(
    color = Color(0xFFEEEEEE),
    fontSize = 14.sp,
    fontFamily = FontFamily.Monospace,
  )

private val BodyStyle =
  TextStyle(
    color = Color(0xFFCCCCCC),
    fontSize = 12.sp,
    fontFamily = FontFamily.Monospace,
  )
