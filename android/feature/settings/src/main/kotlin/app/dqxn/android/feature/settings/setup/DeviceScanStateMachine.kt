package app.dqxn.android.feature.settings.setup

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Device discovered via Companion Device Manager scan.
 * Uses MAC address string instead of BluetoothDevice to keep this class testable without Android runtime.
 */
data class ScanDevice(
  val name: String,
  val macAddress: String,
  val associationId: Int? = null,
)

/**
 * States for the BLE device pairing lifecycle via Companion Device Manager.
 */
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
 * Encapsulates the 5-state CDM pairing lifecycle as a testable non-UI class.
 * All transition methods are non-suspend -- delays (retries, auto-return) launch
 * coroutines internally on the provided [scope].
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

  fun onScanStarted(): Unit = TODO("Not yet implemented")

  fun onDeviceFound(device: ScanDevice): Unit = TODO("Not yet implemented")

  fun onVerificationResult(success: Boolean): Unit = TODO("Not yet implemented")

  fun onUserCancelled(): Unit = TODO("Not yet implemented")

  fun onCdmError(error: String): Unit = TODO("Not yet implemented")

  fun isAtDeviceLimit(currentCount: Int, maxDevices: Int): Boolean = TODO("Not yet implemented")

  fun reset(): Unit = TODO("Not yet implemented")
}
