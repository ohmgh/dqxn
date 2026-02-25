package app.dqxn.android.feature.diagnostics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.dqxn.android.sdk.observability.session.SessionEvent
import kotlinx.collections.immutable.ImmutableList
import java.text.NumberFormat

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
  val numberFormat = NumberFormat.getNumberInstance()

  Column(
    modifier = modifier.fillMaxSize().testTag("session_viewer").padding(16.dp),
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
          modifier = Modifier.testTag("recording_toggle"),
        ) {
          if (isRecording) {
            Box(
              modifier =
                Modifier
                  .size(8.dp)
                  .clip(CircleShape)
                  .background(Color.Red)
                  .testTag("recording_indicator"),
            )
            Spacer(modifier = Modifier.width(8.dp))
          }
          Text(text = if (isRecording) "Stop Recording" else "Start Recording")
        }
      }

      OutlinedButton(
        onClick = onClear,
        modifier = Modifier.testTag("clear_button"),
      ) {
        Text("Clear")
      }
    }

    Spacer(modifier = Modifier.height(8.dp))

    // Event count
    Text(
      text = "${numberFormat.format(events.size)} / ${numberFormat.format(maxEvents)} events",
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.testTag("event_count"),
    )

    Spacer(modifier = Modifier.height(12.dp))

    // Event timeline
    if (events.isEmpty()) {
      Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = "No session events",
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    } else {
      LazyColumn(modifier = Modifier.fillMaxSize()) {
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
  Row(
    modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = formatTimestamp(event.timestamp),
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      text = event.type.name,
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.primary,
    )
    if (event.details.isNotBlank()) {
      Text(
        text = event.details,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.weight(1f),
      )
    }
  }
}
