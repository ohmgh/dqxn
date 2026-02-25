package app.dqxn.android.feature.settings.setup

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import app.dqxn.android.data.device.PairedDevice
import app.dqxn.android.sdk.contracts.setup.ServiceType
import app.dqxn.android.sdk.contracts.setup.SetupDefinition
import app.dqxn.android.sdk.contracts.setup.SetupPageDefinition
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.collections.immutable.persistentListOf
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("SetupEvaluatorImpl")
class SetupEvaluatorImplTest {

  private val context: Context = mockk(relaxed = true)
  private lateinit var evaluator: SetupEvaluatorImpl

  @BeforeEach
  fun setUp() {
    mockkStatic(android.os.Build.VERSION::class)
    evaluator = SetupEvaluatorImpl(context)
  }

  @AfterEach
  fun tearDown() {
    unmockkStatic(android.os.Build.VERSION::class)
  }

  private fun wrapInPage(vararg definitions: SetupDefinition): List<SetupPageDefinition> {
    return listOf(
      SetupPageDefinition(
        id = "test-page",
        title = "Test",
        definitions = definitions.toList(),
      ),
    )
  }

  @Nested
  @DisplayName("RuntimePermission")
  inner class PermissionTests {

    @Test
    fun `permission granted returns satisfied true`() {
      mockkStatic(androidx.core.content.ContextCompat::class)
      every {
        androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
      } returns PackageManager.PERMISSION_GRANTED

      val schema = wrapInPage(
        SetupDefinition.RuntimePermission(
          id = "camera-perm",
          label = "Camera",
          permissions = listOf(Manifest.permission.CAMERA),
        ),
      )

      val results = evaluator.evaluate(schema)
      assertThat(results).hasSize(1)
      assertThat(results[0].definitionId).isEqualTo("camera-perm")
      assertThat(results[0].satisfied).isTrue()

      unmockkStatic(androidx.core.content.ContextCompat::class)
    }

    @Test
    fun `permission denied returns satisfied false`() {
      mockkStatic(androidx.core.content.ContextCompat::class)
      every {
        androidx.core.content.ContextCompat.checkSelfPermission(
          context,
          Manifest.permission.ACCESS_FINE_LOCATION,
        )
      } returns PackageManager.PERMISSION_DENIED

      val schema = wrapInPage(
        SetupDefinition.RuntimePermission(
          id = "location-perm",
          label = "Location",
          permissions = listOf(Manifest.permission.ACCESS_FINE_LOCATION),
        ),
      )

      val results = evaluator.evaluate(schema)
      assertThat(results).hasSize(1)
      assertThat(results[0].satisfied).isFalse()

      unmockkStatic(androidx.core.content.ContextCompat::class)
    }

    @Test
    fun `minSdk above current SDK returns null (skipped)`() {
      val schema = wrapInPage(
        SetupDefinition.RuntimePermission(
          id = "future-perm",
          label = "Future Permission",
          permissions = listOf("android.permission.FUTURE"),
          minSdk = Build.VERSION.SDK_INT + 1,
        ),
      )

      val results = evaluator.evaluate(schema)
      assertThat(results).isEmpty()
    }

    @Test
    fun `multiple permissions all granted returns satisfied true`() {
      mockkStatic(androidx.core.content.ContextCompat::class)
      every {
        androidx.core.content.ContextCompat.checkSelfPermission(context, any())
      } returns PackageManager.PERMISSION_GRANTED

      val schema = wrapInPage(
        SetupDefinition.RuntimePermission(
          id = "multi-perm",
          label = "Multi",
          permissions = listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
          ),
        ),
      )

      val results = evaluator.evaluate(schema)
      assertThat(results[0].satisfied).isTrue()

      unmockkStatic(androidx.core.content.ContextCompat::class)
    }

    @Test
    fun `multiple permissions one denied returns satisfied false`() {
      mockkStatic(androidx.core.content.ContextCompat::class)
      every {
        androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
      } returns PackageManager.PERMISSION_GRANTED
      every {
        androidx.core.content.ContextCompat.checkSelfPermission(
          context,
          Manifest.permission.RECORD_AUDIO,
        )
      } returns PackageManager.PERMISSION_DENIED

      val schema = wrapInPage(
        SetupDefinition.RuntimePermission(
          id = "partial-perm",
          label = "Partial",
          permissions = listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
          ),
        ),
      )

      val results = evaluator.evaluate(schema)
      assertThat(results[0].satisfied).isFalse()

      unmockkStatic(androidx.core.content.ContextCompat::class)
    }
  }

  @Nested
  @DisplayName("SystemServiceToggle")
  inner class ServiceToggleTests {

    @Test
    fun `bluetooth enabled returns satisfied true`() {
      val bluetoothManager: BluetoothManager = mockk()
      val adapter: BluetoothAdapter = mockk()
      every { context.getSystemService(Context.BLUETOOTH_SERVICE) } returns bluetoothManager
      every { bluetoothManager.adapter } returns adapter
      every { adapter.isEnabled } returns true

      val schema = wrapInPage(
        SetupDefinition.SystemServiceToggle(
          id = "bt-toggle",
          label = "Bluetooth",
          settingsAction = "android.settings.BLUETOOTH_SETTINGS",
          serviceType = ServiceType.BLUETOOTH,
        ),
      )

      val results = evaluator.evaluate(schema)
      assertThat(results[0].satisfied).isTrue()
    }

    @Test
    fun `bluetooth disabled returns satisfied false`() {
      val bluetoothManager: BluetoothManager = mockk()
      val adapter: BluetoothAdapter = mockk()
      every { context.getSystemService(Context.BLUETOOTH_SERVICE) } returns bluetoothManager
      every { bluetoothManager.adapter } returns adapter
      every { adapter.isEnabled } returns false

      val schema = wrapInPage(
        SetupDefinition.SystemServiceToggle(
          id = "bt-toggle",
          label = "Bluetooth",
          settingsAction = "android.settings.BLUETOOTH_SETTINGS",
          serviceType = ServiceType.BLUETOOTH,
        ),
      )

      val results = evaluator.evaluate(schema)
      assertThat(results[0].satisfied).isFalse()
    }

    @Test
    fun `location enabled returns satisfied true`() {
      val locationManager: LocationManager = mockk()
      every { context.getSystemService(Context.LOCATION_SERVICE) } returns locationManager
      every { locationManager.isLocationEnabled } returns true

      val schema = wrapInPage(
        SetupDefinition.SystemServiceToggle(
          id = "loc-toggle",
          label = "Location",
          settingsAction = "android.settings.LOCATION_SOURCE_SETTINGS",
          serviceType = ServiceType.LOCATION,
        ),
      )

      val results = evaluator.evaluate(schema)
      assertThat(results[0].satisfied).isTrue()
    }

    @Test
    fun `wifi enabled returns satisfied true`() {
      val wifiManager: WifiManager = mockk()
      every { context.getSystemService(Context.WIFI_SERVICE) } returns wifiManager
      every { wifiManager.isWifiEnabled } returns true

      val schema = wrapInPage(
        SetupDefinition.SystemServiceToggle(
          id = "wifi-toggle",
          label = "Wi-Fi",
          settingsAction = "android.settings.WIFI_SETTINGS",
          serviceType = ServiceType.WIFI,
        ),
      )

      val results = evaluator.evaluate(schema)
      assertThat(results[0].satisfied).isTrue()
    }
  }

  @Nested
  @DisplayName("SystemService")
  inner class SystemServiceTests {

    @Test
    fun `system service available returns satisfied true`() {
      val locationManager: LocationManager = mockk()
      every { context.getSystemService(Context.LOCATION_SERVICE) } returns locationManager
      every { locationManager.isLocationEnabled } returns true

      val schema = wrapInPage(
        SetupDefinition.SystemService(
          id = "loc-service",
          label = "Location Service",
          serviceType = ServiceType.LOCATION,
        ),
      )

      val results = evaluator.evaluate(schema)
      assertThat(results[0].satisfied).isTrue()
    }
  }

  @Nested
  @DisplayName("DeviceScan")
  inner class DeviceScanTests {

    private val deviceScanDefinition = SetupDefinition.DeviceScan(
      id = "obd-scan",
      label = "OBD Scanner",
      handlerId = "essentials:obd",
    )

    @Test
    fun `real-time not connected returns satisfied false`() {
      // No BluetoothManager available -- simulates no BLE
      every { context.getSystemService(Context.BLUETOOTH_SERVICE) } returns null

      val schema = wrapInPage(deviceScanDefinition)
      val results = evaluator.evaluate(schema)
      assertThat(results[0].satisfied).isFalse()
    }

    @Test
    fun `persistence wasPaired true returns satisfied true even if disconnected`() {
      every { context.getSystemService(Context.BLUETOOTH_SERVICE) } returns null

      val pairedDevices = persistentListOf(
        PairedDevice(
          definitionId = "essentials:obd",
          displayName = "OBD-II Scanner",
          macAddress = "AA:BB:CC:DD:EE:FF",
          lastConnected = System.currentTimeMillis(),
          associationId = 1,
        ),
      )

      val schema = wrapInPage(deviceScanDefinition)
      val results = evaluator.evaluateWithPersistence(schema, pairedDevices)
      assertThat(results[0].satisfied).isTrue()
    }

    @Test
    fun `persistence no matching device returns satisfied false`() {
      every { context.getSystemService(Context.BLUETOOTH_SERVICE) } returns null

      val pairedDevices = persistentListOf(
        PairedDevice(
          definitionId = "other:device",
          displayName = "Other Device",
          macAddress = "11:22:33:44:55:66",
          lastConnected = System.currentTimeMillis(),
          associationId = 2,
        ),
      )

      val schema = wrapInPage(deviceScanDefinition)
      val results = evaluator.evaluateWithPersistence(schema, pairedDevices)
      assertThat(results[0].satisfied).isFalse()
    }
  }

  @Nested
  @DisplayName("Display-only types")
  inner class DisplayOnlyTests {

    @Test
    fun `Setting always returns satisfied true`() {
      val schema = wrapInPage(
        SetupDefinition.Setting(
          definition = app.dqxn.android.sdk.contracts.settings.SettingDefinition.BooleanSetting(
            key = "test-bool",
            label = "Test Boolean",
            default = false,
          ),
        ),
      )

      val results = evaluator.evaluate(schema)
      assertThat(results).hasSize(1)
      assertThat(results[0].satisfied).isTrue()
    }

    @Test
    fun `Info always returns satisfied true`() {
      val schema = wrapInPage(
        SetupDefinition.Info(
          id = "info-card",
          label = "Information",
        ),
      )

      val results = evaluator.evaluate(schema)
      assertThat(results).hasSize(1)
      assertThat(results[0].satisfied).isTrue()
    }

    @Test
    fun `Instruction always returns satisfied true`() {
      val schema = wrapInPage(
        SetupDefinition.Instruction(
          id = "step-1",
          label = "Step 1",
          stepNumber = 1,
        ),
      )

      val results = evaluator.evaluate(schema)
      assertThat(results).hasSize(1)
      assertThat(results[0].satisfied).isTrue()
    }
  }

  @Nested
  @DisplayName("Multi-page evaluation")
  inner class MultiPageTests {

    @Test
    fun `evaluates all definitions across multiple pages`() {
      mockkStatic(androidx.core.content.ContextCompat::class)
      every {
        androidx.core.content.ContextCompat.checkSelfPermission(context, any())
      } returns PackageManager.PERMISSION_GRANTED

      val schema = listOf(
        SetupPageDefinition(
          id = "page-1",
          title = "Page 1",
          definitions = listOf(
            SetupDefinition.RuntimePermission(
              id = "perm-1",
              label = "Camera",
              permissions = listOf(Manifest.permission.CAMERA),
            ),
          ),
        ),
        SetupPageDefinition(
          id = "page-2",
          title = "Page 2",
          definitions = listOf(
            SetupDefinition.Info(id = "info-1", label = "Info"),
            SetupDefinition.Instruction(id = "step-1", label = "Step", stepNumber = 1),
          ),
        ),
      )

      val results = evaluator.evaluate(schema)
      assertThat(results).hasSize(3)
      assertThat(results.map { it.definitionId }).containsExactly("perm-1", "info-1", "step-1")

      unmockkStatic(androidx.core.content.ContextCompat::class)
    }
  }
}
