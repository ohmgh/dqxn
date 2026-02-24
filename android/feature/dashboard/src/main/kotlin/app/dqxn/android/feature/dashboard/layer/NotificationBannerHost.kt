package app.dqxn.android.feature.dashboard.layer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.dqxn.android.sdk.contracts.notification.InAppNotification
import app.dqxn.android.sdk.contracts.notification.NotificationPriority
import kotlinx.collections.immutable.ImmutableList

/**
 * Layer 0.5: renders non-critical banners stacked at top of the dashboard.
 *
 * Banners animate in/out with vertical expand + fade. Each banner shows title, message, optional
 * actions, and a dismiss button (if dismissible).
 *
 * Filters out CRITICAL priority banners -- those are handled by [CriticalBannerHost] at Layer 1.5.
 */
@Composable
public fun NotificationBannerHost(
  banners: ImmutableList<InAppNotification.Banner>,
  onDismiss: (String) -> Unit,
  onAction: (String, String) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp, vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    for (banner in banners) {
      // Skip CRITICAL banners -- handled by CriticalBannerHost
      if (banner.priority == NotificationPriority.CRITICAL) continue

      AnimatedVisibility(
        visible = true,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
      ) {
        BannerCard(
          banner = banner,
          onDismiss = onDismiss,
          onAction = onAction,
          modifier = Modifier.testTag("banner_${banner.id}"),
        )
      }
    }
  }
}

@Composable
private fun BannerCard(
  banner: InAppNotification.Banner,
  onDismiss: (String) -> Unit,
  onAction: (String, String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val backgroundColor = when (banner.priority) {
    NotificationPriority.HIGH -> MaterialTheme.colorScheme.errorContainer
    NotificationPriority.NORMAL -> MaterialTheme.colorScheme.secondaryContainer
    NotificationPriority.LOW -> MaterialTheme.colorScheme.surfaceVariant
    else -> MaterialTheme.colorScheme.secondaryContainer
  }

  val contentColor = when (banner.priority) {
    NotificationPriority.HIGH -> MaterialTheme.colorScheme.onErrorContainer
    NotificationPriority.NORMAL -> MaterialTheme.colorScheme.onSecondaryContainer
    NotificationPriority.LOW -> MaterialTheme.colorScheme.onSurfaceVariant
    else -> MaterialTheme.colorScheme.onSecondaryContainer
  }

  Row(
    modifier = modifier
      .fillMaxWidth()
      .background(backgroundColor, RoundedCornerShape(12.dp))
      .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = banner.title,
        style = MaterialTheme.typography.titleSmall,
        color = contentColor,
      )
      Text(
        text = banner.message,
        style = MaterialTheme.typography.bodySmall,
        color = contentColor.copy(alpha = 0.8f),
      )
      if (banner.actions.isNotEmpty()) {
        Row(modifier = Modifier.padding(top = 4.dp)) {
          for (action in banner.actions) {
            TextButton(onClick = { onAction(banner.id, action.actionId) }) {
              Text(text = action.label, color = contentColor)
            }
            Spacer(modifier = Modifier.width(4.dp))
          }
        }
      }
    }
    if (banner.dismissible) {
      IconButton(onClick = { onDismiss(banner.id) }) {
        Icon(
          imageVector = Icons.Filled.Close,
          contentDescription = "Dismiss",
          tint = contentColor,
        )
      }
    }
  }
}
