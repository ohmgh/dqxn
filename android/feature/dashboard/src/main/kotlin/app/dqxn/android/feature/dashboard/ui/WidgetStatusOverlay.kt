package app.dqxn.android.feature.dashboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.dqxn.android.sdk.contracts.status.WidgetRenderState
import app.dqxn.android.sdk.ui.theme.LocalDashboardTheme

/**
 * Status overlay rendered on top of a widget when its [WidgetRenderState] is not [Ready].
 *
 * Provides themed, per-type-differentiated overlays per F2.5, F3.14, and F11.7:
 * - [SetupRequired]: 60% scrim, settings icon (32dp, centered), tappable
 * - [ConnectionError]: 30% scrim, error icon (32dp, centered), not tappable
 * - [Disconnected]: 15% scrim, block icon (20dp, corner-positioned), not tappable
 * - [EntitlementRevoked]: 60% scrim, lock icon (32dp, centered), tappable
 * - [ProviderMissing]: 60% scrim, warning icon (32dp, centered), not tappable
 * - [DataTimeout]: 30% scrim, hourglass icon (32dp, centered), not tappable
 * - [DataStale]: 15% scrim, warning icon (20dp, centered), not tappable
 *
 * All icons use the theme accent color. Overlay clips to [cornerRadiusDp] via [RoundedCornerShape].
 */
@Composable
internal fun WidgetStatusOverlay(
  renderState: WidgetRenderState,
  cornerRadiusDp: Float = 12f,
  onSetupTap: (() -> Unit)? = null,
  onEntitlementTap: (() -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  val theme = LocalDashboardTheme.current
  val accentColor = theme.accentColor
  val shape = RoundedCornerShape(cornerRadiusDp.dp)

  when (renderState) {
    is WidgetRenderState.SetupRequired -> {
      Box(
        modifier =
          modifier
            .fillMaxSize()
            .clip(shape)
            .background(Color.Black.copy(alpha = 0.60f))
            .then(
              if (onSetupTap != null) Modifier.clickable(onClick = onSetupTap) else Modifier,
            )
            .testTag("status_setup_required"),
        contentAlignment = Alignment.Center,
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Icon(
            imageVector = Icons.Filled.Settings,
            contentDescription = renderState.message ?: "Setup required",
            modifier = Modifier.size(32.dp),
            tint = accentColor,
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = renderState.message ?: "Setup required",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.9f),
          )
        }
      }
    }
    is WidgetRenderState.Disconnected -> {
      // Corner-positioned (top-end), not centered
      Box(
        modifier =
          modifier
            .fillMaxSize()
            .clip(shape)
            .background(Color.Black.copy(alpha = 0.15f))
            .testTag("status_disconnected"),
        contentAlignment = Alignment.TopEnd,
      ) {
        Icon(
          imageVector = Icons.Filled.Block,
          contentDescription = "Disconnected",
          modifier = Modifier.padding(4.dp).size(20.dp),
          tint = accentColor,
        )
      }
    }
    is WidgetRenderState.ConnectionError -> {
      Box(
        modifier =
          modifier
            .fillMaxSize()
            .clip(shape)
            .background(Color.Black.copy(alpha = 0.30f))
            .testTag("status_connection_error"),
        contentAlignment = Alignment.Center,
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Icon(
            imageVector = Icons.Filled.ErrorOutline,
            contentDescription = renderState.message ?: "Connection error",
            modifier = Modifier.size(32.dp),
            tint = accentColor,
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = renderState.message ?: "Connection error",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.9f),
          )
        }
      }
    }
    is WidgetRenderState.EntitlementRevoked -> {
      Box(
        modifier =
          modifier
            .fillMaxSize()
            .clip(shape)
            .background(Color.Black.copy(alpha = 0.60f))
            .then(
              if (onEntitlementTap != null) Modifier.clickable(onClick = onEntitlementTap)
              else Modifier,
            )
            .testTag("status_entitlement_revoked"),
        contentAlignment = Alignment.Center,
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = "Upgrade required",
            modifier = Modifier.size(32.dp),
            tint = accentColor,
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = "Upgrade required",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.9f),
          )
        }
      }
    }
    is WidgetRenderState.ProviderMissing -> {
      Box(
        modifier =
          modifier
            .fillMaxSize()
            .clip(shape)
            .background(Color.Black.copy(alpha = 0.60f))
            .testTag("status_provider_missing"),
        contentAlignment = Alignment.Center,
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = "No data provider",
            modifier = Modifier.size(32.dp),
            tint = accentColor,
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = "No data provider",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.9f),
          )
        }
      }
    }
    is WidgetRenderState.DataTimeout -> {
      Box(
        modifier =
          modifier
            .fillMaxSize()
            .clip(shape)
            .background(Color.Black.copy(alpha = 0.30f))
            .testTag("status_data_timeout"),
        contentAlignment = Alignment.Center,
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Icon(
            imageVector = Icons.Filled.HourglassEmpty,
            contentDescription = renderState.message ?: "Waiting for data",
            modifier = Modifier.size(32.dp),
            tint = accentColor,
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = renderState.message ?: "Waiting for data",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.9f),
          )
        }
      }
    }
    is WidgetRenderState.DataStale -> {
      Box(
        modifier =
          modifier
            .fillMaxSize()
            .clip(shape)
            .background(Color.Black.copy(alpha = 0.15f))
            .testTag("status_data_stale"),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = Icons.Filled.Warning,
          contentDescription = "Data may be stale",
          modifier = Modifier.size(20.dp),
          tint = accentColor.copy(alpha = 0.7f),
        )
      }
    }
    else -> return // Ready or unknown -- no overlay
  }
}
