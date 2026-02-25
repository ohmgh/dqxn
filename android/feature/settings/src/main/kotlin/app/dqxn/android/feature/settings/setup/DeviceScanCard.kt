package app.dqxn.android.feature.settings.setup

import androidx.compose.runtime.Composable
import app.dqxn.android.sdk.contracts.setup.SetupDefinition

/** Stub -- fully implemented in Task 2. */
@Composable
internal fun DeviceScanCard(
  definition: SetupDefinition.DeviceScan,
  stateMachine: DeviceScanStateMachine?,
  pairedDevices: List<ScanDevice>,
  onDeviceForget: (String) -> Unit,
) {
}
