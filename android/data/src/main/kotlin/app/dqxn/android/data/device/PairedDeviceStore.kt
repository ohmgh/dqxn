package app.dqxn.android.data.device

import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.Flow

/**
 * Domain representation of a paired BLE device. Persisted in Proto DataStore via
 * [PairedDeviceStoreImpl].
 */
public data class PairedDevice(
  val definitionId: String,
  val displayName: String,
  val macAddress: String,
  val lastConnected: Long,
  val associationId: Int,
)

/** CRUD interface for paired BLE device persistence (F7.5). */
public interface PairedDeviceStore {

  /** Observe the list of all paired devices. */
  public val devices: Flow<ImmutableList<PairedDevice>>

  /** Add a device. Throws [IllegalArgumentException] if a device with the same MAC exists. */
  public suspend fun addDevice(device: PairedDevice)

  /** Remove a device by MAC address. No-op if not found. */
  public suspend fun removeDevice(macAddress: String)

  /** Update the `lastConnected` timestamp for the given MAC address. No-op if not found. */
  public suspend fun updateLastConnected(macAddress: String, timestamp: Long)
}
