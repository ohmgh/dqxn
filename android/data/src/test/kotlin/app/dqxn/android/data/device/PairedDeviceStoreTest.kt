package app.dqxn.android.data.device

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import app.cash.turbine.test
import app.dqxn.android.data.proto.PairedDeviceStoreProto
import app.dqxn.android.data.serializer.PairedDeviceSerializer
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.nio.file.Path
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir

@OptIn(ExperimentalCoroutinesApi::class)
class PairedDeviceStoreTest {

  @TempDir lateinit var tempDir: Path

  private lateinit var dataStore: DataStore<PairedDeviceStoreProto>
  private lateinit var testScope: TestScope
  private lateinit var store: PairedDeviceStoreImpl

  private val testDevice =
    PairedDevice(
      definitionId = "sg-erp2",
      displayName = "SG ERP2 Reader",
      macAddress = "AA:BB:CC:DD:EE:FF",
      lastConnected = 1000L,
      associationId = 42,
    )

  @BeforeEach
  fun setup() {
    val testDispatcher = StandardTestDispatcher()
    testScope = TestScope(testDispatcher)
    dataStore =
      DataStoreFactory.create(
        serializer = PairedDeviceSerializer,
        produceFile = { File(tempDir.toFile(), "test_paired_devices.pb") },
      )
    store = PairedDeviceStoreImpl(dataStore)
  }

  @Test
  fun `addDevice appears in devices flow`() =
    testScope.runTest {
      store.addDevice(testDevice)
      store.devices.test {
        val devices = awaitItem()
        assertThat(devices).hasSize(1)
        assertThat(devices[0].macAddress).isEqualTo("AA:BB:CC:DD:EE:FF")
        assertThat(devices[0].displayName).isEqualTo("SG ERP2 Reader")
        cancelAndIgnoreRemainingEvents()
      }
    }

  @Test
  fun `duplicate MAC throws IllegalArgumentException`() =
    testScope.runTest {
      store.addDevice(testDevice)
      val exception = assertThrows<IllegalArgumentException> { store.addDevice(testDevice) }
      assertThat(exception).hasMessageThat().contains("AA:BB:CC:DD:EE:FF")
    }

  @Test
  fun `removeDevice removes device by MAC`() =
    testScope.runTest {
      store.addDevice(testDevice)
      store.removeDevice("AA:BB:CC:DD:EE:FF")
      store.devices.test {
        assertThat(awaitItem()).isEmpty()
        cancelAndIgnoreRemainingEvents()
      }
    }

  @Test
  fun `updateLastConnected updates timestamp`() =
    testScope.runTest {
      store.addDevice(testDevice)
      store.updateLastConnected("AA:BB:CC:DD:EE:FF", 9999L)
      store.devices.test {
        val devices = awaitItem()
        assertThat(devices).hasSize(1)
        assertThat(devices[0].lastConnected).isEqualTo(9999L)
        cancelAndIgnoreRemainingEvents()
      }
    }

  @Test
  fun `persistence across store instances`() =
    testScope.runTest {
      store.addDevice(testDevice)

      // Create a new impl pointing at the same file
      val store2 = PairedDeviceStoreImpl(dataStore)
      store2.devices.test {
        val devices = awaitItem()
        assertThat(devices).hasSize(1)
        assertThat(devices[0].definitionId).isEqualTo("sg-erp2")
        cancelAndIgnoreRemainingEvents()
      }
    }
}
