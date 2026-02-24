package app.dqxn.android.data.device

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.nio.file.Path
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionEventStoreTest {

  @TempDir lateinit var tempDir: Path

  private lateinit var dataStore: DataStore<Preferences>
  private lateinit var testScope: TestScope
  private lateinit var store: ConnectionEventStoreImpl

  @BeforeEach
  fun setup() {
    val testDispatcher = StandardTestDispatcher()
    testScope = TestScope(testDispatcher)
    dataStore =
      PreferenceDataStoreFactory.create(
        produceFile = { File(tempDir.toFile(), "test_connection_events.preferences_pb") },
      )
    store = ConnectionEventStoreImpl(dataStore)
  }

  @Test
  fun `recordEvent appears in events flow`() =
    testScope.runTest {
      val event =
        ConnectionEvent(
          timestamp = 1000L,
          deviceMac = "AA:BB:CC:DD:EE:FF",
          eventType = "CONNECTED",
          details = "BLE link established",
        )
      store.recordEvent(event)
      store.events.test {
        val events = awaitItem()
        assertThat(events).hasSize(1)
        assertThat(events[0].deviceMac).isEqualTo("AA:BB:CC:DD:EE:FF")
        assertThat(events[0].eventType).isEqualTo("CONNECTED")
        cancelAndIgnoreRemainingEvents()
      }
    }

  @Test
  fun `rolling 50-event window evicts oldest`() =
    testScope.runTest {
      // Add 51 events
      for (i in 1..51) {
        store.recordEvent(
          ConnectionEvent(
            timestamp = i.toLong(),
            deviceMac = "AA:BB:CC:DD:EE:FF",
            eventType = "EVENT_$i",
          )
        )
      }
      store.events.test {
        val events = awaitItem()
        assertThat(events).hasSize(50)
        // Oldest event (timestamp=1) should be evicted
        assertThat(events[0].timestamp).isEqualTo(2L)
        // Newest should be present
        assertThat(events[49].timestamp).isEqualTo(51L)
        cancelAndIgnoreRemainingEvents()
      }
    }

  @Test
  fun `event ordering preserved — newest last`() =
    testScope.runTest {
      store.recordEvent(ConnectionEvent(timestamp = 100L, deviceMac = "A", eventType = "FIRST"))
      store.recordEvent(ConnectionEvent(timestamp = 200L, deviceMac = "B", eventType = "SECOND"))
      store.recordEvent(ConnectionEvent(timestamp = 300L, deviceMac = "C", eventType = "THIRD"))
      store.events.test {
        val events = awaitItem()
        assertThat(events).hasSize(3)
        assertThat(events[0].eventType).isEqualTo("FIRST")
        assertThat(events[1].eventType).isEqualTo("SECOND")
        assertThat(events[2].eventType).isEqualTo("THIRD")
        cancelAndIgnoreRemainingEvents()
      }
    }

  @Test
  fun `clear removes all events`() =
    testScope.runTest {
      store.recordEvent(ConnectionEvent(timestamp = 1L, deviceMac = "A", eventType = "X"))
      store.recordEvent(ConnectionEvent(timestamp = 2L, deviceMac = "B", eventType = "Y"))
      store.clear()
      store.events.test {
        assertThat(awaitItem()).isEmpty()
        cancelAndIgnoreRemainingEvents()
      }
    }
}
