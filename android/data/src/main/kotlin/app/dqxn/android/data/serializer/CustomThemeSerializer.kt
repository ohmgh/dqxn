package app.dqxn.android.data.serializer

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import app.dqxn.android.data.proto.CustomThemeStoreProto
import com.google.protobuf.InvalidProtocolBufferException
import java.io.InputStream
import java.io.OutputStream

/** Proto DataStore serializer for [CustomThemeStoreProto]. */
public object CustomThemeSerializer : Serializer<CustomThemeStoreProto> {

  override val defaultValue: CustomThemeStoreProto = CustomThemeStoreProto.getDefaultInstance()

  override suspend fun readFrom(input: InputStream): CustomThemeStoreProto =
    try {
      CustomThemeStoreProto.parseFrom(input)
    } catch (e: InvalidProtocolBufferException) {
      throw CorruptionException("Cannot read CustomThemeStoreProto", e)
    }

  override suspend fun writeTo(t: CustomThemeStoreProto, output: OutputStream) {
    t.writeTo(output)
  }
}
