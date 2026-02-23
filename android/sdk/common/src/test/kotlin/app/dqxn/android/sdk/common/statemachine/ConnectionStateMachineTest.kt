package app.dqxn.android.sdk.common.statemachine

import app.dqxn.android.sdk.common.result.AppError
import com.google.common.truth.Truth.assertThat
import java.util.stream.Stream
import net.jqwik.api.Arbitraries
import net.jqwik.api.Arbitrary
import net.jqwik.api.ForAll
import net.jqwik.api.Property
import net.jqwik.api.Provide
import net.jqwik.api.constraints.Size
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

@Tag("fast")
class ConnectionStateMachineTest {

  private lateinit var machine: ConnectionStateMachine

  @BeforeEach
  fun setUp() {
    machine = ConnectionStateMachine()
  }

  @Nested
  inner class PortedTests {

    @Test
    fun `Idle + StartSearch transitions to Searching`() {
      val state = machine.transition(ConnectionEvent.StartSearch)
      assertThat(state).isEqualTo(ConnectionMachineState.Searching)
    }

    @Test
    fun `Searching + DeviceFound transitions to DeviceDiscovered`() {
      machine.transition(ConnectionEvent.StartSearch)
      val state = machine.transition(ConnectionEvent.DeviceFound("dev-1", "Device 1"))
      assertThat(state).isEqualTo(ConnectionMachineState.DeviceDiscovered("dev-1", "Device 1"))
    }

    @Test
    fun `DeviceDiscovered + Connect transitions to Connecting`() {
      machine.transition(ConnectionEvent.StartSearch)
      machine.transition(ConnectionEvent.DeviceFound("dev-1", "Device 1"))
      val state = machine.transition(ConnectionEvent.Connect)
      assertThat(state).isEqualTo(ConnectionMachineState.Connecting)
    }

    @Test
    fun `Connecting + ConnectionSuccess transitions to Connected`() {
      navigateToConnecting()
      val state = machine.transition(ConnectionEvent.ConnectionSuccess)
      assertThat(state).isEqualTo(ConnectionMachineState.Connected)
    }

    @Test
    fun `Connecting + ConnectionFailed transitions to Error on first failure`() {
      navigateToConnecting()
      val error = AppError.Bluetooth("connection lost")
      val state = machine.transition(ConnectionEvent.ConnectionFailed(error))
      assertThat(state).isEqualTo(ConnectionMachineState.Error(error))
    }

    @Test
    fun `Connected + Disconnect transitions to Idle`() {
      navigateToConnected()
      val state = machine.transition(ConnectionEvent.Disconnect)
      assertThat(state).isEqualTo(ConnectionMachineState.Idle)
    }

    @Test
    fun `Searching + Disconnect transitions to Idle`() {
      machine.transition(ConnectionEvent.StartSearch)
      val state = machine.transition(ConnectionEvent.Disconnect)
      assertThat(state).isEqualTo(ConnectionMachineState.Idle)
    }

    @Test
    fun `Error + StartSearch transitions to Searching (retry path)`() {
      navigateToError()
      val state = machine.transition(ConnectionEvent.StartSearch)
      assertThat(state).isEqualTo(ConnectionMachineState.Searching)
    }
  }

  @Nested
  inner class NewBehaviorTests {

    @Test
    fun `SearchTimeout from Searching transitions to Error`() {
      machine.transition(ConnectionEvent.StartSearch)
      val state = machine.transition(ConnectionEvent.SearchTimeout)
      assertThat(state).isInstanceOf(ConnectionMachineState.Error::class.java)
      val error = (state as ConnectionMachineState.Error).error
      assertThat(error).isInstanceOf(AppError.Device::class.java)
      assertThat(error.message).isEqualTo("Search timeout")
    }

    @Test
    fun `ConnectionFailed increments retry counter`() {
      navigateToConnecting()
      val error = AppError.Bluetooth("failed")
      machine.transition(ConnectionEvent.ConnectionFailed(error))
      assertThat(machine.retryCount).isEqualTo(1)
    }

    @Test
    fun `after 3 ConnectionFailed from Connecting goes to Idle with reset retryCount`() {
      val error = AppError.Bluetooth("failed")

      // First failure: Connecting -> Error (retryCount=1)
      navigateToConnecting()
      machine.transition(ConnectionEvent.ConnectionFailed(error))
      assertThat(machine.retryCount).isEqualTo(1)

      // Retry: Error -> Searching -> DeviceDiscovered -> Connecting
      machine.transition(ConnectionEvent.StartSearch)
      machine.transition(ConnectionEvent.DeviceFound("dev-1", "Device 1"))
      machine.transition(ConnectionEvent.Connect)

      // Second failure: Connecting -> Error (retryCount=2)
      machine.transition(ConnectionEvent.ConnectionFailed(error))
      assertThat(machine.retryCount).isEqualTo(2)

      // Retry again
      machine.transition(ConnectionEvent.StartSearch)
      machine.transition(ConnectionEvent.DeviceFound("dev-1", "Device 1"))
      machine.transition(ConnectionEvent.Connect)

      // Third failure: Connecting -> Idle (max retries exhausted, retryCount reset)
      val state = machine.transition(ConnectionEvent.ConnectionFailed(error))
      assertThat(state).isEqualTo(ConnectionMachineState.Idle)
      assertThat(machine.retryCount).isEqualTo(0)
    }

    @Test
    fun `ConnectionSuccess resets retry counter to 0`() {
      val error = AppError.Bluetooth("failed")

      // Fail once
      navigateToConnecting()
      machine.transition(ConnectionEvent.ConnectionFailed(error))
      assertThat(machine.retryCount).isEqualTo(1)

      // Retry and succeed
      machine.transition(ConnectionEvent.StartSearch)
      machine.transition(ConnectionEvent.DeviceFound("dev-1", "Device 1"))
      machine.transition(ConnectionEvent.Connect)
      machine.transition(ConnectionEvent.ConnectionSuccess)

      assertThat(machine.retryCount).isEqualTo(0)
      assertThat(machine.state.value).isEqualTo(ConnectionMachineState.Connected)
    }

    @Test
    fun `reset always returns to Idle with retryCount 0`() {
      val error = AppError.Bluetooth("failed")
      navigateToConnecting()
      machine.transition(ConnectionEvent.ConnectionFailed(error))
      assertThat(machine.retryCount).isGreaterThan(0)

      machine.reset()

      assertThat(machine.state.value).isEqualTo(ConnectionMachineState.Idle)
      assertThat(machine.retryCount).isEqualTo(0)
    }
  }

  @Nested
  inner class ExhaustiveTransitionMatrix {

    @ParameterizedTest(name = "{3}")
    @MethodSource(
      "app.dqxn.android.sdk.common.statemachine.ConnectionStateMachineTest#transitionMatrix"
    )
    fun `transition matrix is complete`(
      from: ConnectionMachineState,
      event: ConnectionEvent,
      expected: ConnectionMachineState,
      @Suppress("UNUSED_PARAMETER") description: String,
    ) {
      val machine = ConnectionStateMachine(initialState = from)
      val result = machine.transition(event)
      assertThat(result).isEqualTo(expected)
    }
  }

  // -- jqwik property tests --

  @Property(tries = 200)
  fun `no event sequence reaches an illegal state`(
    @ForAll("eventSequences") events: @Size(max = 100) List<ConnectionEvent>,
  ) {
    val m = ConnectionStateMachine()
    val validStates =
      setOf(
        ConnectionMachineState.Idle::class,
        ConnectionMachineState.Searching::class,
        ConnectionMachineState.DeviceDiscovered::class,
        ConnectionMachineState.Connecting::class,
        ConnectionMachineState.Connected::class,
        ConnectionMachineState.Error::class,
      )
    for (event in events) {
      val state = m.transition(event)
      assertThat(validStates).contains(state::class)
    }
  }

  @Property(tries = 200)
  fun `retry counter never exceeds maxRetries`(
    @ForAll("eventSequences") events: @Size(max = 100) List<ConnectionEvent>,
  ) {
    val m = ConnectionStateMachine()
    for (event in events) {
      m.transition(event)
      assertThat(m.retryCount).isAtMost(3)
    }
  }

  @Property(tries = 200)
  fun `state machine is deterministic`(
    @ForAll("eventSequences") events: @Size(max = 50) List<ConnectionEvent>,
  ) {
    val m1 = ConnectionStateMachine()
    val m2 = ConnectionStateMachine()
    for (event in events) {
      val s1 = m1.transition(event)
      val s2 = m2.transition(event)
      assertThat(s1).isEqualTo(s2)
    }
  }

  @Property(tries = 200)
  fun `reset always returns to Idle from any reachable state`(
    @ForAll("eventSequences") events: @Size(max = 50) List<ConnectionEvent>,
  ) {
    val m = ConnectionStateMachine()
    for (event in events) {
      m.transition(event)
    }
    m.reset()
    assertThat(m.state.value).isEqualTo(ConnectionMachineState.Idle)
    assertThat(m.retryCount).isEqualTo(0)
  }

  @Property(tries = 200)
  fun `all event sequences from Idle produce a valid terminal-or-intermediate state`(
    @ForAll("eventSequences") events: @Size(max = 100) List<ConnectionEvent>,
  ) {
    val m = ConnectionStateMachine()
    var lastState: ConnectionMachineState = ConnectionMachineState.Idle
    for (event in events) {
      lastState = m.transition(event)
    }
    // Final state must be one of the 6 valid ConnectionMachineState subtypes
    val validStateTypes =
      setOf(
        ConnectionMachineState.Idle::class,
        ConnectionMachineState.Searching::class,
        ConnectionMachineState.DeviceDiscovered::class,
        ConnectionMachineState.Connecting::class,
        ConnectionMachineState.Connected::class,
        ConnectionMachineState.Error::class,
      )
    assertThat(validStateTypes).contains(lastState::class)
  }

  @Provide
  fun eventSequences(): Arbitrary<List<ConnectionEvent>> {
    val singleEvent: Arbitrary<ConnectionEvent> =
      Arbitraries.of(
        ConnectionEvent.StartSearch,
        ConnectionEvent.Connect,
        ConnectionEvent.ConnectionSuccess,
        ConnectionEvent.Disconnect,
        ConnectionEvent.SearchTimeout,
        ConnectionEvent.DeviceFound("test-dev", "Test Device"),
        ConnectionEvent.ConnectionFailed(AppError.Bluetooth("test failure")),
      )
    return singleEvent.list().ofMinSize(1).ofMaxSize(100)
  }

  // -- helper methods --

  private fun navigateToConnecting() {
    machine.transition(ConnectionEvent.StartSearch)
    machine.transition(ConnectionEvent.DeviceFound("dev-1", "Device 1"))
    machine.transition(ConnectionEvent.Connect)
    assertThat(machine.state.value).isEqualTo(ConnectionMachineState.Connecting)
  }

  private fun navigateToConnected() {
    navigateToConnecting()
    machine.transition(ConnectionEvent.ConnectionSuccess)
    assertThat(machine.state.value).isEqualTo(ConnectionMachineState.Connected)
  }

  private fun navigateToError() {
    navigateToConnecting()
    machine.transition(ConnectionEvent.ConnectionFailed(AppError.Bluetooth("failed")))
    assertThat(machine.state.value).isInstanceOf(ConnectionMachineState.Error::class.java)
  }

  companion object {

    private val testError = AppError.Bluetooth("test error")
    private val searchTimeoutError = AppError.Device("Search timeout")

    // All 6 states (using sample instances for data classes)
    private val idle = ConnectionMachineState.Idle
    private val searching = ConnectionMachineState.Searching
    private val deviceDiscovered = ConnectionMachineState.DeviceDiscovered("dev-1", "Device 1")
    private val connecting = ConnectionMachineState.Connecting
    private val connected = ConnectionMachineState.Connected
    private val error = ConnectionMachineState.Error(testError)

    // All 7 events (using sample instances for data classes)
    private val startSearch = ConnectionEvent.StartSearch
    private val deviceFound = ConnectionEvent.DeviceFound("dev-1", "Device 1")
    private val connect = ConnectionEvent.Connect
    private val connectionSuccess = ConnectionEvent.ConnectionSuccess
    private val connectionFailed = ConnectionEvent.ConnectionFailed(testError)
    private val disconnect = ConnectionEvent.Disconnect
    private val searchTimeout = ConnectionEvent.SearchTimeout

    /**
     * Complete 6x7 = 42 transition matrix. Each row: (fromState, event, expectedState,
     * description). Invalid transitions (no-op) return the current state.
     *
     * Note: ConnectionFailed from Connecting produces Error on first attempt (retryCount=0 ->
     * retryCount=1, which is < maxRetries=3), so we expect Error state here.
     */
    @JvmStatic
    fun transitionMatrix(): Stream<org.junit.jupiter.params.provider.Arguments> =
      Stream.of(
        // Idle (7 events)
        args(idle, startSearch, searching, "Idle + StartSearch -> Searching"),
        args(idle, deviceFound, idle, "Idle + DeviceFound -> Idle (no-op)"),
        args(idle, connect, idle, "Idle + Connect -> Idle (no-op)"),
        args(idle, connectionSuccess, idle, "Idle + ConnectionSuccess -> Idle (no-op)"),
        args(idle, connectionFailed, idle, "Idle + ConnectionFailed -> Idle (no-op)"),
        args(idle, disconnect, idle, "Idle + Disconnect -> Idle (no-op)"),
        args(idle, searchTimeout, idle, "Idle + SearchTimeout -> Idle (no-op)"),
        // Searching (7 events)
        args(searching, startSearch, searching, "Searching + StartSearch -> Searching (no-op)"),
        args(
          searching,
          deviceFound,
          deviceDiscovered,
          "Searching + DeviceFound -> DeviceDiscovered",
        ),
        args(searching, connect, searching, "Searching + Connect -> Searching (no-op)"),
        args(
          searching,
          connectionSuccess,
          searching,
          "Searching + ConnectionSuccess -> Searching (no-op)",
        ),
        args(
          searching,
          connectionFailed,
          searching,
          "Searching + ConnectionFailed -> Searching (no-op)",
        ),
        args(searching, disconnect, idle, "Searching + Disconnect -> Idle"),
        args(
          searching,
          searchTimeout,
          ConnectionMachineState.Error(searchTimeoutError),
          "Searching + SearchTimeout -> Error",
        ),
        // DeviceDiscovered (7 events)
        args(
          deviceDiscovered,
          startSearch,
          deviceDiscovered,
          "DeviceDiscovered + StartSearch -> DeviceDiscovered (no-op)",
        ),
        args(
          deviceDiscovered,
          deviceFound,
          deviceDiscovered,
          "DeviceDiscovered + DeviceFound -> DeviceDiscovered (no-op)",
        ),
        args(deviceDiscovered, connect, connecting, "DeviceDiscovered + Connect -> Connecting"),
        args(
          deviceDiscovered,
          connectionSuccess,
          deviceDiscovered,
          "DeviceDiscovered + ConnectionSuccess -> DeviceDiscovered (no-op)",
        ),
        args(
          deviceDiscovered,
          connectionFailed,
          deviceDiscovered,
          "DeviceDiscovered + ConnectionFailed -> DeviceDiscovered (no-op)",
        ),
        args(deviceDiscovered, disconnect, idle, "DeviceDiscovered + Disconnect -> Idle"),
        args(
          deviceDiscovered,
          searchTimeout,
          deviceDiscovered,
          "DeviceDiscovered + SearchTimeout -> DeviceDiscovered (no-op)",
        ),
        // Connecting (7 events)
        args(
          connecting,
          startSearch,
          connecting,
          "Connecting + StartSearch -> Connecting (no-op)",
        ),
        args(
          connecting,
          deviceFound,
          connecting,
          "Connecting + DeviceFound -> Connecting (no-op)",
        ),
        args(connecting, connect, connecting, "Connecting + Connect -> Connecting (no-op)"),
        args(
          connecting,
          connectionSuccess,
          connected,
          "Connecting + ConnectionSuccess -> Connected"
        ),
        args(
          connecting,
          connectionFailed,
          ConnectionMachineState.Error(testError),
          "Connecting + ConnectionFailed -> Error (first attempt)",
        ),
        args(connecting, disconnect, idle, "Connecting + Disconnect -> Idle"),
        args(
          connecting,
          searchTimeout,
          connecting,
          "Connecting + SearchTimeout -> Connecting (no-op)",
        ),
        // Connected (7 events)
        args(connected, startSearch, connected, "Connected + StartSearch -> Connected (no-op)"),
        args(connected, deviceFound, connected, "Connected + DeviceFound -> Connected (no-op)"),
        args(connected, connect, connected, "Connected + Connect -> Connected (no-op)"),
        args(
          connected,
          connectionSuccess,
          connected,
          "Connected + ConnectionSuccess -> Connected (no-op)",
        ),
        args(
          connected,
          connectionFailed,
          connected,
          "Connected + ConnectionFailed -> Connected (no-op)",
        ),
        args(connected, disconnect, idle, "Connected + Disconnect -> Idle"),
        args(connected, searchTimeout, connected, "Connected + SearchTimeout -> Connected (no-op)"),
        // Error (7 events)
        args(error, startSearch, searching, "Error + StartSearch -> Searching"),
        args(error, deviceFound, error, "Error + DeviceFound -> Error (no-op)"),
        args(error, connect, error, "Error + Connect -> Error (no-op)"),
        args(error, connectionSuccess, error, "Error + ConnectionSuccess -> Error (no-op)"),
        args(error, connectionFailed, error, "Error + ConnectionFailed -> Error (no-op)"),
        args(error, disconnect, idle, "Error + Disconnect -> Idle"),
        args(error, searchTimeout, error, "Error + SearchTimeout -> Error (no-op)"),
      )

    private fun args(
      from: ConnectionMachineState,
      event: ConnectionEvent,
      expected: ConnectionMachineState,
      description: String,
    ): org.junit.jupiter.params.provider.Arguments =
      org.junit.jupiter.params.provider.Arguments.of(from, event, expected, description)
  }
}
