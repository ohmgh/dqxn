package app.dqxn.android.feature.settings.setup

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceScanStateMachineTest {

  private val testDispatcher = StandardTestDispatcher()
  private val testScope = TestScope(testDispatcher)

  private lateinit var machine: DeviceScanStateMachine

  private val testDevice = ScanDevice(
    name = "Test BLE Device",
    macAddress = "AA:BB:CC:DD:EE:FF",
    associationId = 42,
  )

  private val testDevice2 = ScanDevice(
    name = "Other Device",
    macAddress = "11:22:33:44:55:66",
    associationId = null,
  )

  @BeforeEach
  fun setup() {
    machine = DeviceScanStateMachine(
      scope = testScope,
      maxAttempts = 3,
      retryDelayMs = 2000L,
      autoReturnDelayMs = 1500L,
    )
  }

  @Nested
  @DisplayName("Initial state")
  inner class InitialState {

    @Test
    fun `starts in PreCDM state`() {
      assertThat(machine.state.value).isEqualTo(ScanState.PreCDM)
    }
  }

  @Nested
  @DisplayName("Happy path")
  inner class HappyPath {

    @Test
    fun `PreCDM to Waiting on scan started`() {
      machine.onScanStarted()

      assertThat(machine.state.value).isEqualTo(ScanState.Waiting)
    }

    @Test
    fun `Waiting to Verifying on device found`() {
      machine.onScanStarted()
      machine.onDeviceFound(testDevice)

      val state = machine.state.value
      assertThat(state).isInstanceOf(ScanState.Verifying::class.java)
      val verifying = state as ScanState.Verifying
      assertThat(verifying.device).isEqualTo(testDevice)
      assertThat(verifying.attempt).isEqualTo(1)
      assertThat(verifying.maxAttempts).isEqualTo(3)
    }

    @Test
    fun `Verifying to Success on positive verification`() {
      machine.onScanStarted()
      machine.onDeviceFound(testDevice)
      machine.onVerificationResult(success = true)

      val state = machine.state.value
      assertThat(state).isInstanceOf(ScanState.Success::class.java)
      assertThat((state as ScanState.Success).device).isEqualTo(testDevice)
    }

    @Test
    fun `full happy path PreCDM to Waiting to Verifying to Success`() {
      assertThat(machine.state.value).isEqualTo(ScanState.PreCDM)

      machine.onScanStarted()
      assertThat(machine.state.value).isEqualTo(ScanState.Waiting)

      machine.onDeviceFound(testDevice)
      assertThat(machine.state.value).isInstanceOf(ScanState.Verifying::class.java)

      machine.onVerificationResult(success = true)
      assertThat(machine.state.value).isInstanceOf(ScanState.Success::class.java)
    }
  }

  @Nested
  @DisplayName("Retry logic")
  inner class RetryLogic {

    @Test
    fun `verification failure retries with incremented attempt`() = testScope.runTest {
      machine.onScanStarted()
      machine.onDeviceFound(testDevice)

      // First attempt fails
      machine.onVerificationResult(success = false)

      // Advance past retry delay
      advanceTimeBy(2001L)

      val state = machine.state.value
      assertThat(state).isInstanceOf(ScanState.Verifying::class.java)
      val verifying = state as ScanState.Verifying
      assertThat(verifying.attempt).isEqualTo(2)
      assertThat(verifying.device).isEqualTo(testDevice)
    }

    @Test
    fun `second failure retries to attempt 3`() = testScope.runTest {
      machine.onScanStarted()
      machine.onDeviceFound(testDevice)

      // First attempt fails
      machine.onVerificationResult(success = false)
      advanceTimeBy(2001L)

      // Second attempt fails
      machine.onVerificationResult(success = false)
      advanceTimeBy(2001L)

      val state = machine.state.value
      assertThat(state).isInstanceOf(ScanState.Verifying::class.java)
      assertThat((state as ScanState.Verifying).attempt).isEqualTo(3)
    }

    @Test
    fun `success on third attempt after two failures`() = testScope.runTest {
      machine.onScanStarted()
      machine.onDeviceFound(testDevice)

      // First two attempts fail
      machine.onVerificationResult(success = false)
      advanceTimeBy(2001L)
      machine.onVerificationResult(success = false)
      advanceTimeBy(2001L)

      // Third attempt succeeds
      machine.onVerificationResult(success = true)

      val state = machine.state.value
      assertThat(state).isInstanceOf(ScanState.Success::class.java)
      assertThat((state as ScanState.Success).device).isEqualTo(testDevice)
    }

    @Test
    fun `exhausted retries transitions to Failed`() = testScope.runTest {
      machine.onScanStarted()
      machine.onDeviceFound(testDevice)

      // All three attempts fail
      machine.onVerificationResult(success = false)
      advanceTimeBy(2001L)
      machine.onVerificationResult(success = false)
      advanceTimeBy(2001L)
      machine.onVerificationResult(success = false)

      val state = machine.state.value
      assertThat(state).isInstanceOf(ScanState.Failed::class.java)
      assertThat((state as ScanState.Failed).device).isEqualTo(testDevice)
    }

    @Test
    fun `retry does not happen before delay elapses`() = testScope.runTest {
      machine.onScanStarted()
      machine.onDeviceFound(testDevice)

      machine.onVerificationResult(success = false)

      // Not enough time elapsed -- should still be in some intermediate state, not Verifying(attempt=2)
      advanceTimeBy(1000L)

      // State should NOT be Verifying(attempt=2) yet
      val state = machine.state.value
      if (state is ScanState.Verifying) {
        assertThat(state.attempt).isEqualTo(1)
      }
      // It's OK if it's in some transitional state, but must not be attempt=2
    }

    @Test
    fun `retry delay is exactly 2000ms`() = testScope.runTest {
      machine.onScanStarted()
      machine.onDeviceFound(testDevice)

      machine.onVerificationResult(success = false)

      // At 1999ms, not yet retried
      advanceTimeBy(1999L)
      val beforeState = machine.state.value
      if (beforeState is ScanState.Verifying) {
        assertThat(beforeState.attempt).isEqualTo(1)
      }

      // At 2000ms+, retry happens
      advanceTimeBy(2L)
      val afterState = machine.state.value
      assertThat(afterState).isInstanceOf(ScanState.Verifying::class.java)
      assertThat((afterState as ScanState.Verifying).attempt).isEqualTo(2)
    }
  }

  @Nested
  @DisplayName("Failed auto-return")
  inner class FailedAutoReturn {

    @Test
    fun `Failed transitions back to PreCDM after 1500ms`() = testScope.runTest {
      machine.onScanStarted()
      machine.onDeviceFound(testDevice)

      // Exhaust all retries
      machine.onVerificationResult(success = false)
      advanceTimeBy(2001L)
      machine.onVerificationResult(success = false)
      advanceTimeBy(2001L)
      machine.onVerificationResult(success = false)

      assertThat(machine.state.value).isInstanceOf(ScanState.Failed::class.java)

      // Advance past auto-return delay
      advanceTimeBy(1501L)

      assertThat(machine.state.value).isEqualTo(ScanState.PreCDM)
    }

    @Test
    fun `Failed does not auto-return before 1500ms`() = testScope.runTest {
      machine.onScanStarted()
      machine.onDeviceFound(testDevice)

      machine.onVerificationResult(success = false)
      advanceTimeBy(2001L)
      machine.onVerificationResult(success = false)
      advanceTimeBy(2001L)
      machine.onVerificationResult(success = false)

      assertThat(machine.state.value).isInstanceOf(ScanState.Failed::class.java)

      advanceTimeBy(1499L)
      assertThat(machine.state.value).isInstanceOf(ScanState.Failed::class.java)
    }
  }

  @Nested
  @DisplayName("User cancellation")
  inner class UserCancellation {

    @Test
    fun `cancel from Waiting returns to PreCDM`() {
      machine.onScanStarted()
      assertThat(machine.state.value).isEqualTo(ScanState.Waiting)

      machine.onUserCancelled()
      assertThat(machine.state.value).isEqualTo(ScanState.PreCDM)
    }

    @Test
    fun `cancel from Verifying returns to PreCDM`() {
      machine.onScanStarted()
      machine.onDeviceFound(testDevice)
      assertThat(machine.state.value).isInstanceOf(ScanState.Verifying::class.java)

      machine.onUserCancelled()
      assertThat(machine.state.value).isEqualTo(ScanState.PreCDM)
    }

    @Test
    fun `cancel during retry delay returns to PreCDM`() = testScope.runTest {
      machine.onScanStarted()
      machine.onDeviceFound(testDevice)
      machine.onVerificationResult(success = false)

      // In the middle of retry delay
      advanceTimeBy(1000L)

      machine.onUserCancelled()
      assertThat(machine.state.value).isEqualTo(ScanState.PreCDM)

      // Advance past original retry delay -- should stay PreCDM (job cancelled)
      advanceTimeBy(2000L)
      assertThat(machine.state.value).isEqualTo(ScanState.PreCDM)
    }
  }

  @Nested
  @DisplayName("CDM errors")
  inner class CdmErrors {

    @Test
    fun `CDM error transitions to Failed with error message`() {
      machine.onScanStarted()
      machine.onCdmError("bluetooth_off")

      val state = machine.state.value
      assertThat(state).isInstanceOf(ScanState.Failed::class.java)
      assertThat((state as ScanState.Failed).error).isEqualTo("bluetooth_off")
    }

    @Test
    fun `CDM error with user_rejected returns silently to PreCDM`() {
      machine.onScanStarted()
      machine.onCdmError("user_rejected")

      assertThat(machine.state.value).isEqualTo(ScanState.PreCDM)
    }

    @Test
    fun `CDM error with canceled returns silently to PreCDM`() {
      machine.onScanStarted()
      machine.onCdmError("canceled")

      assertThat(machine.state.value).isEqualTo(ScanState.PreCDM)
    }

    @Test
    fun `CDM error containing user_rejected in longer string returns silently`() {
      machine.onScanStarted()
      machine.onCdmError("error: user_rejected the request")

      assertThat(machine.state.value).isEqualTo(ScanState.PreCDM)
    }

    @Test
    fun `CDM error containing canceled in longer string returns silently`() {
      machine.onScanStarted()
      machine.onCdmError("request was canceled by system")

      assertThat(machine.state.value).isEqualTo(ScanState.PreCDM)
    }

    @Test
    fun `non-cancel CDM error includes device when in Verifying`() {
      machine.onScanStarted()
      machine.onDeviceFound(testDevice)
      machine.onCdmError("connection_failed")

      val state = machine.state.value
      assertThat(state).isInstanceOf(ScanState.Failed::class.java)
      val failed = state as ScanState.Failed
      assertThat(failed.error).isEqualTo("connection_failed")
      assertThat(failed.device).isEqualTo(testDevice)
    }
  }

  @Nested
  @DisplayName("Device limit")
  inner class DeviceLimit {

    @Test
    fun `at device limit when count equals max`() {
      assertThat(machine.isAtDeviceLimit(currentCount = 3, maxDevices = 3)).isTrue()
    }

    @Test
    fun `at device limit when count exceeds max`() {
      assertThat(machine.isAtDeviceLimit(currentCount = 5, maxDevices = 3)).isTrue()
    }

    @Test
    fun `not at device limit when count below max`() {
      assertThat(machine.isAtDeviceLimit(currentCount = 2, maxDevices = 3)).isFalse()
    }

    @Test
    fun `not at device limit with zero devices`() {
      assertThat(machine.isAtDeviceLimit(currentCount = 0, maxDevices = 3)).isFalse()
    }
  }

  @Nested
  @DisplayName("Reset")
  inner class Reset {

    @Test
    fun `reset from PreCDM stays PreCDM`() {
      machine.reset()
      assertThat(machine.state.value).isEqualTo(ScanState.PreCDM)
    }

    @Test
    fun `reset from Waiting returns to PreCDM`() {
      machine.onScanStarted()
      machine.reset()
      assertThat(machine.state.value).isEqualTo(ScanState.PreCDM)
    }

    @Test
    fun `reset from Verifying returns to PreCDM`() {
      machine.onScanStarted()
      machine.onDeviceFound(testDevice)
      machine.reset()
      assertThat(machine.state.value).isEqualTo(ScanState.PreCDM)
    }

    @Test
    fun `reset from Success returns to PreCDM`() {
      machine.onScanStarted()
      machine.onDeviceFound(testDevice)
      machine.onVerificationResult(success = true)
      machine.reset()
      assertThat(machine.state.value).isEqualTo(ScanState.PreCDM)
    }

    @Test
    fun `reset from Failed returns to PreCDM`() {
      machine.onScanStarted()
      machine.onCdmError("some_error")
      machine.reset()
      assertThat(machine.state.value).isEqualTo(ScanState.PreCDM)
    }

    @Test
    fun `reset cancels pending retry job`() = testScope.runTest {
      machine.onScanStarted()
      machine.onDeviceFound(testDevice)
      machine.onVerificationResult(success = false)

      // In the middle of retry delay
      advanceTimeBy(1000L)
      machine.reset()

      // Advance past original retry delay -- should stay PreCDM
      advanceTimeBy(2000L)
      assertThat(machine.state.value).isEqualTo(ScanState.PreCDM)
    }

    @Test
    fun `reset cancels pending auto-return job`() = testScope.runTest {
      machine.onScanStarted()
      machine.onDeviceFound(testDevice)

      // Exhaust retries to get to Failed
      machine.onVerificationResult(success = false)
      advanceTimeBy(2001L)
      machine.onVerificationResult(success = false)
      advanceTimeBy(2001L)
      machine.onVerificationResult(success = false)

      assertThat(machine.state.value).isInstanceOf(ScanState.Failed::class.java)

      // Reset before auto-return fires
      machine.reset()
      assertThat(machine.state.value).isEqualTo(ScanState.PreCDM)

      // Auto-return should NOT fire since we already reset
      advanceTimeBy(2000L)
      assertThat(machine.state.value).isEqualTo(ScanState.PreCDM)
    }
  }

  @Nested
  @DisplayName("Custom configuration")
  inner class CustomConfiguration {

    @Test
    fun `custom max attempts respected`() = testScope.runTest {
      val customMachine = DeviceScanStateMachine(
        scope = testScope,
        maxAttempts = 2,
        retryDelayMs = 100L,
        autoReturnDelayMs = 50L,
      )

      customMachine.onScanStarted()
      customMachine.onDeviceFound(testDevice)

      // First fail
      customMachine.onVerificationResult(success = false)
      advanceTimeBy(101L)

      // Second fail -- should be Failed since maxAttempts=2
      customMachine.onVerificationResult(success = false)

      assertThat(customMachine.state.value).isInstanceOf(ScanState.Failed::class.java)
    }

    @Test
    fun `custom retry delay respected`() = testScope.runTest {
      val customMachine = DeviceScanStateMachine(
        scope = testScope,
        maxAttempts = 3,
        retryDelayMs = 500L,
        autoReturnDelayMs = 1500L,
      )

      customMachine.onScanStarted()
      customMachine.onDeviceFound(testDevice)
      customMachine.onVerificationResult(success = false)

      // At 499ms, should not have retried
      advanceTimeBy(499L)
      val state = customMachine.state.value
      if (state is ScanState.Verifying) {
        assertThat(state.attempt).isEqualTo(1)
      }

      // At 501ms, should have retried
      advanceTimeBy(2L)
      val retried = customMachine.state.value
      assertThat(retried).isInstanceOf(ScanState.Verifying::class.java)
      assertThat((retried as ScanState.Verifying).attempt).isEqualTo(2)
    }

    @Test
    fun `custom auto-return delay respected`() = testScope.runTest {
      val customMachine = DeviceScanStateMachine(
        scope = testScope,
        maxAttempts = 1,
        retryDelayMs = 2000L,
        autoReturnDelayMs = 200L,
      )

      customMachine.onScanStarted()
      customMachine.onDeviceFound(testDevice)
      customMachine.onVerificationResult(success = false)

      assertThat(customMachine.state.value).isInstanceOf(ScanState.Failed::class.java)

      advanceTimeBy(201L)
      assertThat(customMachine.state.value).isEqualTo(ScanState.PreCDM)
    }
  }
}
