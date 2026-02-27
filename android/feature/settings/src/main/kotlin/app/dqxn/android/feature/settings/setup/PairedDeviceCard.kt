package app.dqxn.android.feature.settings.setup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.dqxn.android.core.design.token.CardSize
import app.dqxn.android.core.design.token.DashboardSpacing
import app.dqxn.android.core.design.token.DashboardTypography
import app.dqxn.android.core.design.token.SemanticColors
import app.dqxn.android.core.design.token.TextEmphasis
import app.dqxn.android.sdk.ui.theme.LocalDashboardTheme

/**
 * Paired device card with 3-state border:
 * - **Connected**: accent border
 * - **Disconnected**: secondary border
 * - **Forgetting**: error border (while forget confirmation is shown)
 *
 * "Forget" button with [AlertDialog] confirmation. [forgettingMac] guard prevents double-tap.
 */
@Composable
internal fun PairedDeviceCard(
  device: ScanDevice,
  isConnected: Boolean,
  onForget: () -> Unit,
) {
  val theme = LocalDashboardTheme.current
  var showConfirmDialog by remember { mutableStateOf(false) }
  var forgettingMac by remember { mutableStateOf<String?>(null) }

  val isForgetting = forgettingMac == device.macAddress
  val borderColor =
    when {
      isForgetting -> SemanticColors.Error
      isConnected -> theme.accentColor
      else -> theme.secondaryTextColor.copy(alpha = 0.3f)
    }

  Card(
    shape = RoundedCornerShape(CardSize.SMALL.cornerRadius),
    border = BorderStroke(1.dp, borderColor),
    colors =
      CardDefaults.cardColors(
        containerColor =
          if (isForgetting) {
            SemanticColors.Error.copy(alpha = 0.05f)
          } else {
            theme.widgetBorderColor.copy(alpha = 0.03f)
          },
      ),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier =
        Modifier.fillMaxWidth()
          .defaultMinSize(minHeight = 56.dp)
          .padding(
            horizontal = DashboardSpacing.CardInternalPadding,
            vertical = DashboardSpacing.SpaceXS,
          ),
    ) {
      Icon(
        imageVector =
          if (isConnected) {
            Icons.Filled.BluetoothConnected
          } else {
            Icons.Filled.BluetoothDisabled
          },
        contentDescription = if (isConnected) "Connected" else "Disconnected",
        tint = if (isConnected) theme.accentColor else theme.secondaryTextColor,
        modifier = Modifier.size(20.dp),
      )

      Text(
        text = device.name,
        style = DashboardTypography.label,
        color = theme.primaryTextColor,
        modifier = Modifier.weight(1f).padding(start = DashboardSpacing.IconTextGap),
      )

      Text(
        text = if (isConnected) "Connected" else "Disconnected",
        style = DashboardTypography.caption,
        color =
          if (isConnected) {
            theme.accentColor
          } else {
            theme.primaryTextColor.copy(alpha = TextEmphasis.Disabled)
          },
        modifier = Modifier.padding(end = DashboardSpacing.SpaceXS),
      )

      IconButton(
        onClick = { showConfirmDialog = true },
        enabled = forgettingMac == null,
        modifier = Modifier.size(48.dp),
      ) {
        Icon(
          imageVector = Icons.Filled.Delete,
          contentDescription = "Forget device",
          tint = if (isForgetting) SemanticColors.Error else theme.secondaryTextColor,
          modifier = Modifier.size(20.dp),
        )
      }
    }
  }

  if (showConfirmDialog) {
    AlertDialog(
      onDismissRequest = { showConfirmDialog = false },
      title = { Text(text = "Forget device?", style = DashboardTypography.title) },
      text = {
        Text(
          text = "Remove \"${device.name}\" from paired devices. You can pair it again later.",
          style = DashboardTypography.description,
        )
      },
      confirmButton = {
        TextButton(
          onClick = {
            showConfirmDialog = false
            forgettingMac = device.macAddress
            onForget()
          },
        ) {
          Text(
            text = "Forget",
            style = DashboardTypography.buttonLabel,
            color = SemanticColors.Error,
          )
        }
      },
      dismissButton = {
        TextButton(onClick = { showConfirmDialog = false }) {
          Text(
            text = "Cancel",
            style = DashboardTypography.buttonLabel,
          )
        }
      },
    )
  }
}
