package app.dqxn.android.data.serializer

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import app.dqxn.android.data.proto.DashboardStoreProto
import com.google.protobuf.InvalidProtocolBufferException
import java.io.InputStream
import java.io.OutputStream

/** Proto DataStore serializer for [DashboardStoreProto]. */
public object DashboardStoreSerializer : Serializer<DashboardStoreProto> {

  override val defaultValue: DashboardStoreProto = DashboardStoreProto.getDefaultInstance()

  override suspend fun readFrom(input: InputStream): DashboardStoreProto =
    try {
      DashboardStoreProto.parseFrom(input)
    } catch (e: InvalidProtocolBufferException) {
      throw CorruptionException("Cannot read DashboardStoreProto", e)
    }

  override suspend fun writeTo(t: DashboardStoreProto, output: OutputStream) {
    t.writeTo(output)
  }
}
