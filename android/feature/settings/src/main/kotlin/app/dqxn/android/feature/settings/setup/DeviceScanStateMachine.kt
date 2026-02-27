package app.dqxn.android.feature.settings.setup

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Device discovered via Companion Device Manager scan. Uses MAC address string instead of
 * BluetoothDevice to keep this class testable without Android runtime.
 */
data class ScanDevice(
  val name: String,
  val macAddress: String,
  val associationId: Int? = null,
)

/** States for the BLE device pairing lifecycle via Companion Device Manager. */
sealed interface ScanState {
  /** Initial state, ready to start a scan. */
  data object PreCDM : ScanState

  /** CDM dialog shown, waiting for user to select a device. */
  data object Waiting : ScanState

  /** Device selected, running verification callback. */
  data class Verifying(
    val device: ScanDevice,
    val attempt: Int,
    val maxAttempts: Int,
  ) : ScanState

  /** Verification passed, device is paired. */
  data class Success(val device: ScanDevice) : ScanState

  /** Verification failed or error occurred. */
  data class Failed(
    val device: ScanDevice? = null,
    val error: String? = null,
  ) : ScanState
}

/**
 * Pure-logic state machine for BLE device pairing via Companion Device Manager.
 *
 * Encapsulates the 5-state CDM pairing lifecycle as a testable non-UI class. All transition methods
 * are non-suspend -- delays (retries, auto-return) launch coroutines internally on the provided
 * [scope].
 *
 * @param scope CoroutineScope for delay-based transitions
 * @param maxAttempts Maximum verification attempts before failing (default 3)
 * @param retryDelayMs Delay between verification retry attempts in ms (default 2000)
 * @param autoReturnDelayMs Delay before Failed auto-returns to PreCDM in ms (default 1500)
 */
class DeviceScanStateMachine(
  private val scope: CoroutineScope,
  private val maxAttempts: Int = 3,
  private val retryDelayMs: Long = 2000L,
  private val autoReturnDelayMs: Long = 1500L,
) {

  private val _state: MutableStateFlow<ScanState> = MutableStateFlow(ScanState.PreCDM)
  val state: StateFlow<ScanState> = _state.asStateFlow()

  /** Active job for retry delay or auto-return delay. Cancelled on user cancel or reset. */
  private var verificationJob: Job? = null

  /** Transition from PreCDM to Waiting when CDM scan is launched. */
  fun onScanStarted() {
    _state.value = ScanState.Waiting
  }

  /** Transition from Waiting to Verifying(attempt=1) when user selects a device in CDM dialog. */
  fun onDeviceFound(device: ScanDevice) {
    _state.value =
      ScanState.Verifying(
        device = device,
        attempt = 1,
        maxAttempts = maxAttempts,
      )
  }

  /**
   * Process verification result.
   *
   * On success: transitions to [ScanState.Success]. On failure with remaining attempts: schedules
   * retry after [retryDelayMs]. On failure with exhausted attempts: transitions to
   * [ScanState.Failed] with auto-return.
   */
  fun onVerificationResult(success: Boolean) {
    val current = _state.value
    if (current !is ScanState.Verifying) return

    if (success) {
      cancelPendingJobs()
      _state.value = ScanState.Success(current.device)
      return
    }

    // Failure path
    if (current.attempt >= current.maxAttempts) {
      // Exhausted all retries
      cancelPendingJobs()
      _state.value = ScanState.Failed(device = current.device)
      scheduleAutoReturn()
    } else {
      // Schedule retry with delay
      cancelPendingJobs()
      verificationJob =
        scope.launch {
          delay(retryDelayMs)
          _state.value =
            ScanState.Verifying(
              device = current.device,
              attempt = current.attempt + 1,
              maxAttempts = current.maxAttempts,
            )
        }
    }
  }

  /** User explicitly cancelled the scan/pairing flow. Returns silently to PreCDM. */
  fun onUserCancelled() {
    cancelPendingJobs()
    _state.value = ScanState.PreCDM
  }

  /**
   * Handle CDM system error.
   *
   * If the error indicates user cancellation ("user_rejected" or "canceled" substring), silently
   * returns to PreCDM. Otherwise transitions to [ScanState.Failed] with the error.
   */
  fun onCdmError(error: String) {
    cancelPendingJobs()

    if (isCancelError(error)) {
      _state.value = ScanState.PreCDM
      return
    }

    val currentDevice = (_state.value as? ScanState.Verifying)?.device
    _state.value = ScanState.Failed(device = currentDevice, error = error)
    scheduleAutoReturn()
  }

  /** Check if the current device count has reached the maximum allowed. */
  fun isAtDeviceLimit(currentCount: Int, maxDevices: Int): Boolean {
    return currentCount >= maxDevices
  }

  /** Reset state machine to PreCDM from any state. Cancels all pending jobs. */
  fun reset() {
    cancelPendingJobs()
    _state.value = ScanState.PreCDM
  }

  private fun scheduleAutoReturn() {
    verificationJob =
      scope.launch {
        delay(autoReturnDelayMs)
        _state.value = ScanState.PreCDM
      }
  }

  private fun cancelPendingJobs() {
    verificationJob?.cancel()
    verificationJob = null
  }

  private fun isCancelError(error: String): Boolean {
    return "user_rejected" in error || "canceled" in error
  }
}
