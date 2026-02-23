package app.dqxn.android.sdk.common.statemachine

import app.dqxn.android.sdk.common.result.AppError

public sealed interface ConnectionEvent {
  public data object StartSearch : ConnectionEvent

  public data class DeviceFound(
    val deviceId: String,
    val deviceName: String,
  ) : ConnectionEvent

  public data object Connect : ConnectionEvent

  public data object ConnectionSuccess : ConnectionEvent

  public data class ConnectionFailed(val error: AppError) : ConnectionEvent

  public data object Disconnect : ConnectionEvent

  public data object SearchTimeout : ConnectionEvent
}
