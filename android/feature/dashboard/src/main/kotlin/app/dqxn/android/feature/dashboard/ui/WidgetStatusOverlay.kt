package app.dqxn.android.feature.dashboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.dqxn.android.sdk.contracts.status.WidgetRenderState

/**
 * Status overlay rendered on top of a widget when its [WidgetRenderState] is not [Ready].
 *
 * Provides scrim + icon + message for each status variant per F3.14 and F3.15:
 * - [SetupRequired]: 60% scrim, settings icon
 * - [ConnectionError]: 30% scrim, error icon
 * - [Disconnected]: 15% scrim, block icon
 * - [EntitlementRevoked]: 60% scrim, lock icon
 * - [ProviderMissing]: 60% scrim, warning icon
 * - [DataTimeout]: 30% scrim, hourglass icon
 * - [DataStale]: 15% scrim, warning icon
 */
@Composable
internal fun WidgetStatusOverlay(
  renderState: WidgetRenderState,
  modifier: Modifier = Modifier,
) {
  val (scrimAlpha, icon, message) = when (renderState) {
    is WidgetRenderState.SetupRequired -> Triple(
      0.60f,
      Icons.Filled.Settings,
      renderState.message ?: "Setup required",
    )
    is WidgetRenderState.ConnectionError -> Triple(
      0.30f,
      Icons.Filled.ErrorOutline,
      renderState.message ?: "Connection error",
    )
    is WidgetRenderState.Disconnected -> Triple(
      0.15f,
      Icons.Filled.Block,
      "Disconnected",
    )
    is WidgetRenderState.EntitlementRevoked -> Triple(
      0.60f,
      Icons.Filled.Lock,
      "Upgrade required",
    )
    is WidgetRenderState.ProviderMissing -> Triple(
      0.60f,
      Icons.Filled.Warning,
      "No data provider",
    )
    is WidgetRenderState.DataTimeout -> Triple(
      0.30f,
      Icons.Filled.HourglassEmpty,
      renderState.message ?: "Waiting for data",
    )
    is WidgetRenderState.DataStale -> Triple(
      0.15f,
      Icons.Filled.Warning,
      "Data may be stale",
    )
    else -> return // Ready or unknown -- no overlay
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(Color.Black.copy(alpha = scrimAlpha)),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = message,
        modifier = Modifier.size(24.dp),
        tint = Color.White.copy(alpha = 0.9f),
      )
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = message,
        style = MaterialTheme.typography.labelSmall,
        color = Color.White.copy(alpha = 0.9f),
      )
    }
  }
}
