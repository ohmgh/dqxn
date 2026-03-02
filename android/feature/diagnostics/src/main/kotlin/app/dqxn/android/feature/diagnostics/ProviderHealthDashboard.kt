package app.dqxn.android.feature.diagnostics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.dqxn.android.core.design.token.DashboardSpacing
import app.dqxn.android.core.design.token.DashboardTypography
import app.dqxn.android.core.design.token.SemanticColors
import app.dqxn.android.core.design.token.TextEmphasis
import app.dqxn.android.sdk.observability.health.ProviderStatus
import app.dqxn.android.sdk.ui.theme.LocalDashboardTheme
import kotlinx.collections.immutable.ImmutableList

/** Staleness threshold in milliseconds. Providers not updated in this window show a warning. */
internal const val STALENESS_THRESHOLD_MS: Long = 10_000L

/**
 * Displays a list of data provider statuses with connection indicators and staleness warnings.
 *
 * Each row shows the provider name, connection state (green/red dot), relative last-update time,
 * and an amber warning icon when the provider is stale (>[STALENESS_THRESHOLD_MS]).
 *
 * @param statuses The list of provider statuses to display.
 * @param currentTimeMs The current time in milliseconds for staleness calculation.
 * @param onProviderClick Callback fired when a provider row is tapped.
 */
@Composable
public fun ProviderHealthDashboard(
  statuses: ImmutableList<ProviderStatus>,
  currentTimeMs: Long,
  onProviderClick: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val theme = LocalDashboardTheme.current

  if (statuses.isEmpty()) {
    Box(
      modifier = modifier.fillMaxWidth().testTag("provider_health_list"),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        text = "No providers registered",
        style = DashboardTypography.description,
        color = theme.primaryTextColor.copy(alpha = TextEmphasis.Medium),
      )
    }
    return
  }

  LazyColumn(
    modifier = modifier.fillMaxWidth().testTag("provider_health_list"),
  ) {
    items(statuses, key = { it.providerId }) { status ->
      ProviderStatusRow(
        status = status,
        currentTimeMs = currentTimeMs,
        onClick = { onProviderClick(status.providerId) },
      )
    }
  }
}

@Composable
private fun ProviderStatusRow(
  status: ProviderStatus,
  currentTimeMs: Long,
  onClick: () -> Unit,
) {
  val theme = LocalDashboardTheme.current
  val elapsed = currentTimeMs - status.lastUpdateTimestamp
  val isStale = elapsed > STALENESS_THRESHOLD_MS

  Row(
    modifier =
      Modifier.fillMaxWidth()
        .clickable(onClick = onClick)
        .padding(vertical = DashboardSpacing.SpaceS)
        .testTag("provider_row_${status.providerId}"),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.weight(1f),
    ) {
      // Connection state indicator dot
      Box(
        modifier =
          Modifier.size(10.dp)
            .clip(CircleShape)
            .background(if (status.isConnected) SemanticColors.Success else SemanticColors.Error)
            .testTag("connection_indicator_${status.providerId}"),
      )
      Spacer(modifier = Modifier.width(DashboardSpacing.SpaceS))
      Column {
        Text(
          text = status.displayName,
          style = DashboardTypography.description,
          color = theme.primaryTextColor,
        )
        Text(
          text =
            if (status.errorDescription != null) {
              status.errorDescription!!
            } else {
              formatElapsed(elapsed)
            },
          style = DashboardTypography.caption,
          color =
            if (status.errorDescription != null) {
              theme.errorColor
            } else {
              theme.primaryTextColor.copy(alpha = TextEmphasis.Medium)
            },
        )
      }
    }

    if (isStale) {
      Text(
        text = "\u26A0", // Warning sign
        color = SemanticColors.Warning,
        modifier = Modifier.testTag("staleness_indicator_${status.providerId}"),
      )
    }
  }
}

/** Formats elapsed milliseconds into a human-readable relative time string. */
@Stable
internal fun formatElapsed(elapsedMs: Long): String =
  when {
    elapsedMs < 1_000L -> "just now"
    elapsedMs < 60_000L -> "${elapsedMs / 1_000}s ago"
    elapsedMs < 3_600_000L -> "${elapsedMs / 60_000}m ago"
    else -> "${elapsedMs / 3_600_000}h ago"
  }
