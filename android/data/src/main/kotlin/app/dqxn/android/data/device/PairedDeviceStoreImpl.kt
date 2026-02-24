package app.dqxn.android.data.device

import androidx.datastore.core.DataStore
import app.dqxn.android.data.proto.PairedDeviceMetadataProto
import app.dqxn.android.data.proto.PairedDeviceStoreProto
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Proto DataStore-backed implementation of [PairedDeviceStore]. Converts between
 * [PairedDeviceMetadataProto] and domain [PairedDevice].
 */
@Singleton
public class PairedDeviceStoreImpl
@Inject
constructor(
  private val dataStore: DataStore<PairedDeviceStoreProto>,
) : PairedDeviceStore {

  override val devices: Flow<ImmutableList<PairedDevice>> =
    dataStore.data.map { store -> store.devicesList.map { it.toDomain() }.toImmutableList() }

  override suspend fun addDevice(device: PairedDevice) {
    dataStore.updateData { store ->
      val existing = store.devicesList.any { it.macAddress == device.macAddress }
      require(!existing) { "Device with MAC ${device.macAddress} already paired" }
      store.toBuilder().addDevices(device.toProto()).build()
    }
  }

  override suspend fun removeDevice(macAddress: String) {
    dataStore.updateData { store ->
      val filtered = store.devicesList.filter { it.macAddress != macAddress }
      store.toBuilder().clearDevices().addAllDevices(filtered).build()
    }
  }

  override suspend fun updateLastConnected(macAddress: String, timestamp: Long) {
    dataStore.updateData { store ->
      val updated =
        store.devicesList.map { proto ->
          if (proto.macAddress == macAddress) {
            proto.toBuilder().setLastConnected(timestamp).build()
          } else {
            proto
          }
        }
      store.toBuilder().clearDevices().addAllDevices(updated).build()
    }
  }

  private companion object {
    fun PairedDeviceMetadataProto.toDomain(): PairedDevice =
      PairedDevice(
        definitionId = definitionId,
        displayName = displayName,
        macAddress = macAddress,
        lastConnected = lastConnected,
        associationId = associationId,
      )

    fun PairedDevice.toProto(): PairedDeviceMetadataProto =
      PairedDeviceMetadataProto.newBuilder()
        .setDefinitionId(definitionId)
        .setDisplayName(displayName)
        .setMacAddress(macAddress)
        .setLastConnected(lastConnected)
        .setAssociationId(associationId)
        .build()
  }
}
