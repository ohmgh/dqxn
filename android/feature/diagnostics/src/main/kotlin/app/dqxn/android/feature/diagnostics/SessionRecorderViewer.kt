package app.dqxn.android.feature.diagnostics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.dqxn.android.core.design.token.DashboardSpacing
import app.dqxn.android.core.design.token.DashboardTypography
import app.dqxn.android.core.design.token.SemanticColors
import app.dqxn.android.core.design.token.TextEmphasis
import app.dqxn.android.sdk.observability.session.SessionEvent
import app.dqxn.android.sdk.ui.theme.LocalDashboardTheme
import java.text.NumberFormat
import kotlinx.collections.immutable.ImmutableList

/**
 * Session recording viewer showing a toggle button, event timeline, clear button, and event count.
 *
 * @param isRecording Whether the session recorder is currently active.
 * @param events The current session events to display.
 * @param maxEvents The maximum event capacity of the ring buffer.
 * @param onToggleRecording Callback to toggle recording on/off.
 * @param onClear Callback to clear all recorded events.
 */
@Composable
public fun SessionRecorderViewer(
  isRecording: Boolean,
  events: ImmutableList<SessionEvent>,
  maxEvents: Int,
  onToggleRecording: () -> Unit,
  onClear: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val theme = LocalDashboardTheme.current
  val numberFormat = NumberFormat.getNumberInstance()

  Column(
    modifier = modifier.fillMaxWidth().testTag("session_viewer"),
  ) {
    // Recording toggle row
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Button(
          onClick = onToggleRecording,
          colors = ButtonDefaults.buttonColors(containerColor = theme.accentColor),
          modifier = Modifier.testTag("recording_toggle"),
        ) {
          if (isRecording) {
            Box(
              modifier =
                Modifier.size(8.dp)
                  .clip(CircleShape)
                  .background(SemanticColors.Error)
                  .testTag("recording_indicator"),
            )
            Spacer(modifier = Modifier.width(DashboardSpacing.SpaceXS))
          }
          Text(text = if (isRecording) "Stop Recording" else "Start Recording")
        }
      }

      OutlinedButton(
        onClick = onClear,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = theme.accentColor),
        border = BorderStroke(1.dp, theme.widgetBorderColor.copy(alpha = TextEmphasis.Medium)),
        modifier = Modifier.testTag("clear_button"),
      ) {
        Text("Clear")
      }
    }

    Spacer(modifier = Modifier.height(DashboardSpacing.SpaceXS))

    // Event count
    Text(
      text = "${numberFormat.format(events.size)} / ${numberFormat.format(maxEvents)} events",
      style = DashboardTypography.buttonLabel,
      color = theme.primaryTextColor.copy(alpha = TextEmphasis.Medium),
      modifier = Modifier.testTag("event_count"),
    )

    Spacer(modifier = Modifier.height(DashboardSpacing.SpaceS))

    // Event timeline
    if (events.isEmpty()) {
      Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = "No session events",
          style = DashboardTypography.description,
          color = theme.primaryTextColor.copy(alpha = TextEmphasis.Medium),
        )
      }
    } else {
      LazyColumn(modifier = Modifier.fillMaxWidth()) {
        itemsIndexed(events) { index, event ->
          SessionEventRow(
            event = event,
            modifier = Modifier.testTag("session_event_$index"),
          )
        }
      }
    }
  }
}

@Composable
private fun SessionEventRow(
  event: SessionEvent,
  modifier: Modifier = Modifier,
) {
  val theme = LocalDashboardTheme.current

  Row(
    modifier = modifier.fillMaxWidth().padding(vertical = DashboardSpacing.SpaceXXS),
    horizontalArrangement = Arrangement.spacedBy(DashboardSpacing.SpaceS),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = formatTimestamp(event.timestamp),
      style = DashboardTypography.caption,
      color = theme.primaryTextColor.copy(alpha = TextEmphasis.Medium),
    )
    Text(
      text = event.type.name,
      style = DashboardTypography.buttonLabel,
      color = theme.accentColor,
    )
    if (event.details.isNotBlank()) {
      Text(
        text = event.details,
        style = DashboardTypography.caption,
        color = theme.primaryTextColor.copy(alpha = TextEmphasis.Medium),
        modifier = Modifier.weight(1f),
      )
    }
  }
}
