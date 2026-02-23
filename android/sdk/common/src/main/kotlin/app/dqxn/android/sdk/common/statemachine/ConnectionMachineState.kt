package app.dqxn.android.sdk.common.statemachine

import app.dqxn.android.sdk.common.result.AppError

public sealed interface ConnectionMachineState {
  public data object Idle : ConnectionMachineState

  public data object Searching : ConnectionMachineState

  public data class DeviceDiscovered(
    val deviceId: String,
    val deviceName: String,
  ) : ConnectionMachineState

  public data object Connecting : ConnectionMachineState

  public data object Connected : ConnectionMachineState

  public data class Error(val error: AppError) : ConnectionMachineState
}
