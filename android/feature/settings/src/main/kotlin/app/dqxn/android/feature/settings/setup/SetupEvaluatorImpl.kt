package app.dqxn.android.feature.settings.setup

import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import app.dqxn.android.data.device.PairedDevice
import app.dqxn.android.sdk.contracts.setup.ServiceType
import app.dqxn.android.sdk.contracts.setup.SetupDefinition
import app.dqxn.android.sdk.contracts.setup.SetupPageDefinition
import app.dqxn.android.sdk.contracts.setup.SetupResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.collections.immutable.ImmutableList

/**
 * Concrete [SetupEvaluator][app.dqxn.android.sdk.contracts.setup.SetupEvaluator] with two
 * evaluation modes:
 * - [evaluate]: Real-time checks only. `DeviceScan` checks live connection status via BLE.
 * - [evaluateWithPersistence]: Persistence-aware. `DeviceScan` uses the provided paired-device
 *   snapshot -- a previously paired device counts as satisfied even if currently disconnected.
 *
 * Display-only types (`Setting`, `Instruction`, `Info`) always return `satisfied = true`.
 *
 * `RuntimePermission` with `minSdk > Build.VERSION.SDK_INT` returns `null` (skipped entirely).
 */
@Singleton
class SetupEvaluatorImpl
@Inject
constructor(
  @param:ApplicationContext private val context: Context,
) : app.dqxn.android.sdk.contracts.setup.SetupEvaluator {

  override fun evaluate(schema: List<SetupPageDefinition>): List<SetupResult> {
    return schema.flatMap { page ->
      page.definitions.mapNotNull { definition ->
        evaluateDefinition(definition, pairedDevices = null)
      }
    }
  }

  /**
   * Persistence-aware evaluation. [pairedDevices] is a snapshot of the current paired device list
   * from [PairedDeviceStore][app.dqxn.android.data.device.PairedDeviceStore]. For `DeviceScan`
   * definitions, a device matching the definition's `handlerId` in [pairedDevices] counts as
   * satisfied even if not currently connected.
   */
  fun evaluateWithPersistence(
    schema: List<SetupPageDefinition>,
    pairedDevices: ImmutableList<PairedDevice>,
  ): List<SetupResult> {
    return schema.flatMap { page ->
      page.definitions.mapNotNull { definition ->
        evaluateDefinition(definition, pairedDevices = pairedDevices)
      }
    }
  }

  /**
   * Evaluates a single definition. Returns `null` for `RuntimePermission` with `minSdk` above the
   * current SDK version (skip entirely -- renderer never sees it).
   */
  private fun evaluateDefinition(
    definition: SetupDefinition,
    pairedDevices: ImmutableList<PairedDevice>?,
  ): SetupResult? {
    return when (definition) {
      is SetupDefinition.RuntimePermission -> evaluatePermission(definition)
      is SetupDefinition.SystemServiceToggle -> evaluateServiceToggle(definition)
      is SetupDefinition.SystemService -> evaluateSystemService(definition)
      is SetupDefinition.DeviceScan -> evaluateDeviceScan(definition, pairedDevices)
      is SetupDefinition.Instruction,
      is SetupDefinition.Info,
      is SetupDefinition.Setting ->
        SetupResult(
          definitionId = definition.id,
          satisfied = true,
        )
    }
  }

  private fun evaluatePermission(definition: SetupDefinition.RuntimePermission): SetupResult? {
    if (definition.minSdk > Build.VERSION.SDK_INT) return null

    val allGranted =
      definition.permissions.all { permission ->
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
      }
    return SetupResult(
      definitionId = definition.id,
      satisfied = allGranted,
    )
  }

  private fun evaluateServiceToggle(
    definition: SetupDefinition.SystemServiceToggle,
  ): SetupResult {
    val enabled = checkServiceEnabled(definition.serviceType)
    return SetupResult(
      definitionId = definition.id,
      satisfied = enabled,
    )
  }

  private fun evaluateSystemService(definition: SetupDefinition.SystemService): SetupResult {
    val enabled = checkServiceEnabled(definition.serviceType)
    return SetupResult(
      definitionId = definition.id,
      satisfied = enabled,
    )
  }

  private fun evaluateDeviceScan(
    definition: SetupDefinition.DeviceScan,
    pairedDevices: ImmutableList<PairedDevice>?,
  ): SetupResult {
    val satisfied =
      if (pairedDevices != null) {
        // Persistence mode: check if any device was paired for this handler
        pairedDevices.any { it.definitionId == definition.handlerId }
      } else {
        // Real-time mode: check live BLE connection
        isDeviceConnected(definition)
      }
    return SetupResult(
      definitionId = definition.id,
      satisfied = satisfied,
    )
  }

  private fun checkServiceEnabled(serviceType: ServiceType): Boolean {
    return when (serviceType) {
      ServiceType.BLUETOOTH -> {
        val bluetoothManager =
          context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothManager?.adapter?.isEnabled == true
      }
      ServiceType.LOCATION -> {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        locationManager?.isLocationEnabled == true
      }
      ServiceType.WIFI -> {
        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        wifiManager?.isWifiEnabled == true
      }
    }
  }

  private fun isDeviceConnected(definition: SetupDefinition.DeviceScan): Boolean {
    val bluetoothManager =
      context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager ?: return false
    val connectedDevices =
      try {
        @Suppress("MissingPermission")
        bluetoothManager.getConnectedDevices(android.bluetooth.BluetoothProfile.GATT)
      } catch (_: SecurityException) {
        return false
      }
    return connectedDevices.any { device ->
      @Suppress("MissingPermission")
      val name =
        try {
          device.name
        } catch (_: SecurityException) {
          null
        }
      val pattern = definition.deviceNamePattern
      if (pattern != null && name != null) {
        Regex(pattern).containsMatchIn(name)
      } else {
        // No pattern to match -- just check if any BLE device is connected
        true
      }
    }
  }
}
