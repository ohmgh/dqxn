package app.dqxn.android.feature.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.dqxn.android.data.device.ConnectionEvent
import app.dqxn.android.sdk.observability.health.ProviderStatus
import kotlinx.collections.immutable.ImmutableList

/**
 * Detail screen for a single data provider showing its current status and connection event log.
 *
 * The event log shows the rolling 50 events from [ConnectionEventStore], sorted newest first.
 * A "Retry Connection" button fires the [onRetry] callback with the provider's ID.
 *
 * @param providerStatus The current status of the provider, or null if not found.
 * @param events Connection events filtered for this provider, sorted newest first.
 * @param onRetry Callback fired when the "Retry Connection" button is tapped.
 */
@Composable
public fun ProviderDetailScreen(
  providerStatus: ProviderStatus?,
  events: ImmutableList<ConnectionEvent>,
  onRetry: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.fillMaxSize().testTag("provider_detail").padding(16.dp),
  ) {
    // Header
    if (providerStatus != null) {
      Text(
        text = providerStatus.displayName,
        style = MaterialTheme.typography.headlineSmall,
      )
      Text(
        text = if (providerStatus.isConnected) "Connected" else "Disconnected",
        style = MaterialTheme.typography.bodyMedium,
        color =
          if (providerStatus.isConnected) {
            MaterialTheme.colorScheme.primary
          } else {
            MaterialTheme.colorScheme.error
          },
      )
      Spacer(modifier = Modifier.height(16.dp))
    }

    // Event log
    if (events.isEmpty()) {
      Box(
        modifier = Modifier.weight(1f).fillMaxWidth(),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = "No connection events",
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    } else {
      LazyColumn(
        modifier = Modifier.weight(1f).fillMaxWidth(),
      ) {
        itemsIndexed(events) { index, event ->
          ConnectionEventRow(
            event = event,
            index = index,
          )
          if (index < events.lastIndex) {
            HorizontalDivider()
          }
        }
      }
    }

    // Retry button
    if (providerStatus != null) {
      Spacer(modifier = Modifier.height(16.dp))
      Button(
        onClick = { onRetry(providerStatus.providerId) },
        modifier = Modifier.fillMaxWidth().testTag("retry_button"),
      ) {
        Text(text = "Retry Connection")
      }
    }
  }
}

@Composable
private fun ConnectionEventRow(
  event: ConnectionEvent,
  index: Int,
) {
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .padding(vertical = 8.dp)
        .testTag("connection_event_$index"),
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = formatTimestamp(event.timestamp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
        text = event.eventType,
        style = MaterialTheme.typography.bodyMedium,
      )
      if (event.details.isNotBlank()) {
        Text(
          text = event.details,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

/**
 * Formats a timestamp into a simple time string for display in the event log.
 */
@Stable
internal fun formatTimestamp(timestampMs: Long): String {
  val seconds = (timestampMs / 1_000) % 60
  val minutes = (timestampMs / 60_000) % 60
  val hours = (timestampMs / 3_600_000) % 24
  return "%02d:%02d:%02d".format(hours, minutes, seconds)
}
