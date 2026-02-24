package app.dqxn.android.feature.dashboard.layer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
 * Layer 1.5: renders CRITICAL banners above all overlays.
 *
 * CRITICAL banners (e.g., safe mode) must be visible even over settings/pickers. Positioned at the
 * top of the Z-order, above Layer 1 overlays.
 *
 * Safe mode banner provides "Reset" and "Report" actions.
 */
@Composable
public fun CriticalBannerHost(
  banners: ImmutableList<InAppNotification.Banner>,
  onAction: (String, String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val criticalBanners = banners.filter { it.priority == NotificationPriority.CRITICAL }

  Column(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp, vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    for (banner in criticalBanners) {
      AnimatedVisibility(
        visible = true,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
      ) {
        CriticalBannerCard(
          banner = banner,
          onAction = onAction,
          modifier = Modifier.testTag("banner_${banner.id}"),
        )
      }
    }
  }
}

@Composable
private fun CriticalBannerCard(
  banner: InAppNotification.Banner,
  onAction: (String, String) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .background(MaterialTheme.colorScheme.error, RoundedCornerShape(12.dp))
      .padding(16.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(
        imageVector = Icons.Filled.Warning,
        contentDescription = "Critical",
        modifier = Modifier.size(24.dp),
        tint = MaterialTheme.colorScheme.onError,
      )
      Spacer(modifier = Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = banner.title,
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.onError,
        )
        Text(
          text = banner.message,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onError.copy(alpha = 0.9f),
        )
      }
    }

    Spacer(modifier = Modifier.height(12.dp))

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.End,
    ) {
      if (banner.actions.isNotEmpty()) {
        for (action in banner.actions) {
          TextButton(onClick = { onAction(banner.id, action.actionId) }) {
            Text(text = action.label, color = MaterialTheme.colorScheme.onError)
          }
          Spacer(modifier = Modifier.width(8.dp))
        }
      } else {
        // Default safe mode actions: Reset and Report
        Button(
          onClick = { onAction(banner.id, "reset") },
          colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.onError,
            contentColor = MaterialTheme.colorScheme.error,
          ),
        ) {
          Text("Reset")
        }
        Spacer(modifier = Modifier.width(8.dp))
        TextButton(onClick = { onAction(banner.id, "report") }) {
          Text("Report", color = MaterialTheme.colorScheme.onError)
        }
      }
    }
  }
}
