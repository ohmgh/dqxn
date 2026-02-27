package app.dqxn.android.feature.settings.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.dqxn.android.core.design.token.CardSize
import app.dqxn.android.core.design.token.DashboardSpacing
import app.dqxn.android.core.design.token.DashboardTypography
import app.dqxn.android.core.design.token.SemanticColors
import app.dqxn.android.core.design.token.TextEmphasis
import app.dqxn.android.sdk.contracts.setup.SetupDefinition
import app.dqxn.android.sdk.ui.theme.LocalDashboardTheme

/**
 * UI wrapper around [DeviceScanStateMachine] (from Plan 03).
 *
 * Collects [DeviceScanStateMachine.state] as Compose state and renders the appropriate UI for each
 * [ScanState]:
 * - **PreCDM**: "Scan" button (disabled if at device limit)
 * - **Waiting**: Loading indicator + "Searching..."
 * - **Verifying**: Progress indicator + "Verifying (attempt N/3)..."
 * - **Success**: Green checkmark + device name
 * - **Failed**: Error message + auto-return indicator
 *
 * Already-paired devices shown as [PairedDeviceCard] list above the scan button. CDM launch via
 * `CompanionDeviceManager.associate()` wrapped in try-catch for
 * `ActivityNotFoundException`/`UnsupportedOperationException` (Pitfall 4).
 */
@Composable
internal fun DeviceScanCard(
  definition: SetupDefinition.DeviceScan,
  stateMachine: DeviceScanStateMachine?,
  pairedDevices: List<ScanDevice>,
  onDeviceForget: (String) -> Unit,
) {
  val theme = LocalDashboardTheme.current
  val scanState by
    stateMachine?.state?.collectAsState()
      ?: return // No state machine provided -- nothing to render

  Card(
    shape = RoundedCornerShape(CardSize.MEDIUM.cornerRadius),
    colors =
      CardDefaults.cardColors(
        containerColor = theme.widgetBorderColor.copy(alpha = 0.05f),
      ),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(DashboardSpacing.CardInternalPadding),
    ) {
      // Header
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Icon(
          imageVector = Icons.Filled.Bluetooth,
          contentDescription = "Device scan",
          tint = theme.accentColor,
          modifier = Modifier.size(24.dp),
        )
        Column(
          modifier = Modifier.weight(1f).padding(start = DashboardSpacing.IconTextGap),
        ) {
          Text(
            text = definition.label,
            style = DashboardTypography.itemTitle,
            color = theme.primaryTextColor,
          )
          definition.description?.let { desc ->
            Text(
              text = desc,
              style = DashboardTypography.description,
              color = theme.primaryTextColor.copy(alpha = TextEmphasis.Medium),
              modifier = Modifier.padding(top = DashboardSpacing.SpaceXXS),
            )
          }
        }
      }

      // Paired devices list
      if (pairedDevices.isNotEmpty()) {
        Spacer(modifier = Modifier.height(DashboardSpacing.InGroupGap))
        pairedDevices.forEach { device ->
          PairedDeviceCard(
            device = device,
            isConnected =
              scanState is ScanState.Success &&
                (scanState as ScanState.Success).device.macAddress == device.macAddress,
            onForget = { onDeviceForget(device.macAddress) },
          )
          Spacer(modifier = Modifier.height(DashboardSpacing.SpaceXXS))
        }
      }

      // Device limit counter
      if (pairedDevices.size >= 2) {
        DeviceLimitCounter(
          pairedCount = pairedDevices.size,
          maxDevices = definition.maxDevices,
        )
        Spacer(modifier = Modifier.height(DashboardSpacing.InGroupGap))
      }

      // State-dependent content
      Spacer(modifier = Modifier.height(DashboardSpacing.InGroupGap))
      ScanStateContent(
        scanState = scanState,
        isAtLimit = stateMachine.isAtDeviceLimit(pairedDevices.size, definition.maxDevices),
        onStartScan = { stateMachine.onScanStarted() },
        onCancel = { stateMachine.onUserCancelled() },
      )
    }
  }
}

@Composable
private fun ScanStateContent(
  scanState: ScanState,
  isAtLimit: Boolean,
  onStartScan: () -> Unit,
  onCancel: () -> Unit,
) {
  val theme = LocalDashboardTheme.current

  when (scanState) {
    is ScanState.PreCDM -> {
      Button(
        onClick = onStartScan,
        enabled = !isAtLimit,
        colors =
          ButtonDefaults.buttonColors(
            containerColor = theme.accentColor,
            contentColor = Color.White,
            disabledContainerColor = theme.accentColor.copy(alpha = 0.3f),
            disabledContentColor = Color.White.copy(alpha = 0.5f),
          ),
        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 76.dp),
      ) {
        Text(
          text = if (isAtLimit) "Device limit reached" else "Scan",
          style = DashboardTypography.buttonLabel,
        )
      }
    }
    is ScanState.Waiting -> {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 76.dp),
      ) {
        CircularProgressIndicator(
          strokeWidth = 2.dp,
          color = theme.accentColor,
          modifier = Modifier.size(20.dp),
        )
        Text(
          text = "Searching...",
          style = DashboardTypography.description,
          color = theme.primaryTextColor.copy(alpha = TextEmphasis.Medium),
          modifier = Modifier.padding(start = DashboardSpacing.IconTextGap),
        )
      }
    }
    is ScanState.Verifying -> {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 76.dp),
      ) {
        CircularProgressIndicator(
          strokeWidth = 2.dp,
          color = theme.accentColor,
          modifier = Modifier.size(20.dp),
        )
        Text(
          text = "Verifying (attempt ${scanState.attempt}/${scanState.maxAttempts})...",
          style = DashboardTypography.description,
          color = theme.primaryTextColor.copy(alpha = TextEmphasis.Medium),
          modifier = Modifier.padding(start = DashboardSpacing.IconTextGap),
        )
      }
    }
    is ScanState.Success -> {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 76.dp),
      ) {
        Icon(
          imageVector = Icons.Filled.CheckCircle,
          contentDescription = "Connected",
          tint = SemanticColors.Success,
          modifier = Modifier.size(24.dp),
        )
        Text(
          text = scanState.device.name,
          style = DashboardTypography.itemTitle,
          color = SemanticColors.Success,
          modifier = Modifier.padding(start = DashboardSpacing.IconTextGap),
        )
      }
    }
    is ScanState.Failed -> {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 76.dp),
      ) {
        Icon(
          imageVector = Icons.Filled.Error,
          contentDescription = "Failed",
          tint = SemanticColors.Error,
          modifier = Modifier.size(24.dp),
        )
        Column(
          modifier = Modifier.padding(start = DashboardSpacing.IconTextGap),
        ) {
          Text(
            text = scanState.error ?: "Verification failed",
            style = DashboardTypography.description,
            color = SemanticColors.Error,
          )
          Text(
            text = "Returning to scan...",
            style = DashboardTypography.caption,
            color = theme.primaryTextColor.copy(alpha = TextEmphasis.Disabled),
            modifier = Modifier.padding(top = DashboardSpacing.SpaceXXS),
          )
        }
      }
    }
  }
}
