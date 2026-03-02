package app.dqxn.android.feature.diagnostics

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
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
import app.dqxn.android.core.design.token.DashboardSpacing
import app.dqxn.android.core.design.token.DashboardTypography
import app.dqxn.android.core.design.token.TextEmphasis
import app.dqxn.android.sdk.observability.diagnostic.DiagnosticSnapshotDto
import app.dqxn.android.sdk.ui.theme.LocalDashboardTheme
import kotlinx.collections.immutable.ImmutableList

/** Snapshot filter types matching diagnostic pool categories plus ALL. */
internal val SNAPSHOT_FILTER_TYPES: List<String> = listOf("ALL", "CRASH", "ANR", "ANOMALY", "PERF")

/**
 * Browse diagnostic snapshots captured by [DiagnosticSnapshotCapture].
 *
 * Displays filter chips for type-based filtering and a scrollable list of snapshot rows. Tapping a
 * row expands it to show full snapshot details.
 *
 * @param snapshots All available diagnostic snapshots, sorted newest first.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
public fun DiagnosticSnapshotViewer(
  snapshots: ImmutableList<DiagnosticSnapshotDto>,
  modifier: Modifier = Modifier,
) {
  val theme = LocalDashboardTheme.current
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
    modifier = modifier.fillMaxWidth().testTag("snapshot_viewer"),
  ) {
    // Filter chips
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(DashboardSpacing.SpaceXS),
    ) {
      SNAPSHOT_FILTER_TYPES.forEach { filterType ->
        FilterChip(
          selected = selectedFilter == filterType,
          onClick = { selectedFilter = filterType },
          label = { Text(filterType) },
          colors =
            FilterChipDefaults.filterChipColors(
              selectedContainerColor = theme.accentColor.copy(alpha = 0.15f),
              selectedLabelColor = theme.accentColor,
              labelColor = theme.primaryTextColor.copy(alpha = TextEmphasis.Medium),
            ),
          modifier = Modifier.testTag("snapshot_filter_$filterType"),
        )
      }
    }

    Spacer(modifier = Modifier.height(DashboardSpacing.SpaceS))

    if (filteredSnapshots.isEmpty()) {
      Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = "No snapshots",
          style = DashboardTypography.description,
          color = theme.primaryTextColor.copy(alpha = TextEmphasis.Medium),
        )
      }
    } else {
      LazyColumn(modifier = Modifier.fillMaxWidth()) {
        itemsIndexed(filteredSnapshots) { index, snapshot ->
          SnapshotRow(
            snapshot = snapshot,
            isExpanded = expandedIndex == index,
            onClick = { expandedIndex = if (expandedIndex == index) -1 else index },
            modifier = Modifier.testTag("snapshot_row_$index"),
          )
          if (index < filteredSnapshots.lastIndex) {
            HorizontalDivider(
              color = theme.widgetBorderColor.copy(alpha = TextEmphasis.Disabled),
            )
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
  val theme = LocalDashboardTheme.current

  Column(
    modifier =
      modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = DashboardSpacing.SpaceXS),
  ) {
    Text(
      text = snapshot.triggerType,
      style = DashboardTypography.buttonLabel,
      color = theme.accentColor,
    )
    Text(
      text = formatTimestamp(snapshot.timestamp),
      style = DashboardTypography.caption,
      color = theme.primaryTextColor.copy(alpha = TextEmphasis.Medium),
    )
    Text(
      text = snapshot.triggerDescription,
      style = DashboardTypography.caption,
      color = theme.primaryTextColor,
      maxLines = if (isExpanded) Int.MAX_VALUE else 1,
    )

    AnimatedVisibility(visible = isExpanded) {
      Column(modifier = Modifier.padding(top = DashboardSpacing.SpaceXS)) {
        if (snapshot.activeSpans.isNotEmpty()) {
          Text(
            text = "Active Spans:",
            style = DashboardTypography.caption,
            color = theme.primaryTextColor,
          )
          snapshot.activeSpans.forEach { span ->
            Text(
              text = "  $span",
              style = DashboardTypography.caption,
              color = theme.primaryTextColor.copy(alpha = TextEmphasis.Medium),
            )
          }
        }
        if (snapshot.logTail.isNotEmpty()) {
          Spacer(modifier = Modifier.height(DashboardSpacing.SpaceXXS))
          Text(
            text = "Log Tail:",
            style = DashboardTypography.caption,
            color = theme.primaryTextColor,
          )
          snapshot.logTail.takeLast(10).forEach { log ->
            Text(
              text = "  $log",
              style = DashboardTypography.caption,
              color = theme.primaryTextColor.copy(alpha = TextEmphasis.Medium),
            )
          }
        }
        if (snapshot.agenticTraceId != null) {
          Spacer(modifier = Modifier.height(DashboardSpacing.SpaceXXS))
          Text(
            text = "Trace: ${snapshot.agenticTraceId}",
            style = DashboardTypography.caption,
            color = theme.primaryTextColor.copy(alpha = TextEmphasis.Medium),
          )
        }
      }
    }
  }
}
