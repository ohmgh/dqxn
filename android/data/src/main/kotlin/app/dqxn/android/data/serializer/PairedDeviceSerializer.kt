package app.dqxn.android.data.serializer

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import app.dqxn.android.data.proto.PairedDeviceStoreProto
import com.google.protobuf.InvalidProtocolBufferException
import java.io.InputStream
import java.io.OutputStream

/** Proto DataStore serializer for [PairedDeviceStoreProto]. */
public object PairedDeviceSerializer : Serializer<PairedDeviceStoreProto> {

  override val defaultValue: PairedDeviceStoreProto = PairedDeviceStoreProto.getDefaultInstance()

  override suspend fun readFrom(input: InputStream): PairedDeviceStoreProto =
    try {
      PairedDeviceStoreProto.parseFrom(input)
    } catch (e: InvalidProtocolBufferException) {
      throw CorruptionException("Cannot read PairedDeviceStoreProto", e)
    }

  override suspend fun writeTo(t: PairedDeviceStoreProto, output: OutputStream) {
    t.writeTo(output)
  }
}
