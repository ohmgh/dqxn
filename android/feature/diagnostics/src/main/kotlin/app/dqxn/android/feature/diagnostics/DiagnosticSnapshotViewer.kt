package app.dqxn.android.feature.diagnostics

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.dqxn.android.sdk.observability.diagnostic.DiagnosticSnapshotDto
import kotlinx.collections.immutable.ImmutableList

/**
 * Snapshot filter types matching diagnostic pool categories plus ALL.
 */
internal val SNAPSHOT_FILTER_TYPES: List<String> = listOf("ALL", "CRASH", "ANR", "ANOMALY", "PERF")

/**
 * Browse diagnostic snapshots captured by [DiagnosticSnapshotCapture].
 *
 * Displays filter chips for type-based filtering and a scrollable list of snapshot rows.
 * Tapping a row expands it to show full snapshot details.
 *
 * @param snapshots All available diagnostic snapshots, sorted newest first.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
public fun DiagnosticSnapshotViewer(
  snapshots: ImmutableList<DiagnosticSnapshotDto>,
  modifier: Modifier = Modifier,
) {
  var selectedFilter by remember { mutableStateOf("ALL") }
  var expandedIndex by remember { mutableIntStateOf(-1) }

  val filteredSnapshots =
    remember(snapshots, selectedFilter) {
      if (selectedFilter == "ALL") {
        snapshots
      } else {
        snapshots.filter { it.triggerType.uppercase().contains(selectedFilter) }
      }
    }

  Column(
    modifier = modifier.fillMaxSize().testTag("snapshot_viewer").padding(16.dp),
  ) {
    // Filter chips
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      SNAPSHOT_FILTER_TYPES.forEach { filterType ->
        FilterChip(
          selected = selectedFilter == filterType,
          onClick = { selectedFilter = filterType },
          label = { Text(filterType) },
          modifier = Modifier.testTag("snapshot_filter_$filterType"),
        )
      }
    }

    Spacer(modifier = Modifier.height(12.dp))

    if (filteredSnapshots.isEmpty()) {
      Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = "No snapshots",
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    } else {
      LazyColumn(modifier = Modifier.fillMaxSize()) {
        itemsIndexed(filteredSnapshots) { index, snapshot ->
          SnapshotRow(
            snapshot = snapshot,
            isExpanded = expandedIndex == index,
            onClick = { expandedIndex = if (expandedIndex == index) -1 else index },
            modifier = Modifier.testTag("snapshot_row_$index"),
          )
          if (index < filteredSnapshots.lastIndex) {
            HorizontalDivider()
          }
        }
      }
    }
  }
}

@Composable
private fun SnapshotRow(
  snapshot: DiagnosticSnapshotDto,
  isExpanded: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
  ) {
    Text(
      text = snapshot.triggerType,
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.primary,
    )
    Text(
      text = formatTimestamp(snapshot.timestamp),
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      text = snapshot.triggerDescription,
      style = MaterialTheme.typography.bodySmall,
      maxLines = if (isExpanded) Int.MAX_VALUE else 1,
    )

    AnimatedVisibility(visible = isExpanded) {
      Column(modifier = Modifier.padding(top = 8.dp)) {
        if (snapshot.activeSpans.isNotEmpty()) {
          Text("Active Spans:", style = MaterialTheme.typography.labelSmall)
          snapshot.activeSpans.forEach { span ->
            Text(
              text = "  $span",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        if (snapshot.logTail.isNotEmpty()) {
          Spacer(modifier = Modifier.height(4.dp))
          Text("Log Tail:", style = MaterialTheme.typography.labelSmall)
          snapshot.logTail.takeLast(10).forEach { log ->
            Text(
              text = "  $log",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        if (snapshot.agenticTraceId != null) {
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = "Trace: ${snapshot.agenticTraceId}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}
