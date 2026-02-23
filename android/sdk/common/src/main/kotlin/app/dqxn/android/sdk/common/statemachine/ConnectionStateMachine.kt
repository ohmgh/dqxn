package app.dqxn.android.sdk.common.statemachine

import app.dqxn.android.sdk.common.result.AppError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

public class ConnectionStateMachine(
  initialState: ConnectionMachineState = ConnectionMachineState.Idle,
  private val maxRetries: Int = 3,
) {
  private val _state: MutableStateFlow<ConnectionMachineState> = MutableStateFlow(initialState)
  public val state: StateFlow<ConnectionMachineState> = _state.asStateFlow()

  public var retryCount: Int = 0
    private set

  public val retryDelay: Long
    get() = 1000L * (1 shl (retryCount - 1).coerceAtLeast(0))

  public fun transition(event: ConnectionEvent): ConnectionMachineState {
    val newState = computeTransition(_state.value, event)
    _state.value = newState
    return newState
  }

  public fun reset() {
    retryCount = 0
    _state.value = ConnectionMachineState.Idle
  }

  private fun computeTransition(
    current: ConnectionMachineState,
    event: ConnectionEvent,
  ): ConnectionMachineState =
    when (current) {
      is ConnectionMachineState.Idle ->
        when (event) {
          is ConnectionEvent.StartSearch -> ConnectionMachineState.Searching
          else -> current
        }
      is ConnectionMachineState.Searching ->
        when (event) {
          is ConnectionEvent.DeviceFound ->
            ConnectionMachineState.DeviceDiscovered(event.deviceId, event.deviceName)
          is ConnectionEvent.SearchTimeout ->
            ConnectionMachineState.Error(AppError.Device("Search timeout"))
          is ConnectionEvent.Disconnect -> {
            retryCount = 0
            ConnectionMachineState.Idle
          }
          else -> current
        }
      is ConnectionMachineState.DeviceDiscovered ->
        when (event) {
          is ConnectionEvent.Connect -> ConnectionMachineState.Connecting
          is ConnectionEvent.Disconnect -> {
            retryCount = 0
            ConnectionMachineState.Idle
          }
          else -> current
        }
      is ConnectionMachineState.Connecting ->
        when (event) {
          is ConnectionEvent.ConnectionSuccess -> {
            retryCount = 0
            ConnectionMachineState.Connected
          }
          is ConnectionEvent.ConnectionFailed -> {
            retryCount++
            if (retryCount >= maxRetries) {
              retryCount = 0
              ConnectionMachineState.Idle
            } else {
              ConnectionMachineState.Error(event.error)
            }
          }
          is ConnectionEvent.Disconnect -> {
            retryCount = 0
            ConnectionMachineState.Idle
          }
          else -> current
        }
      is ConnectionMachineState.Connected ->
        when (event) {
          is ConnectionEvent.Disconnect -> {
            retryCount = 0
            ConnectionMachineState.Idle
          }
          else -> current
        }
      is ConnectionMachineState.Error ->
        when (event) {
          is ConnectionEvent.StartSearch -> ConnectionMachineState.Searching
          is ConnectionEvent.Disconnect -> {
            retryCount = 0
            ConnectionMachineState.Idle
          }
          else -> current
        }
    }
}
