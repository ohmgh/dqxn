package app.dqxn.android.data.di

import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import app.dqxn.android.data.proto.DashboardStoreProto
import app.dqxn.android.data.serializer.DashboardStoreSerializer
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.nio.file.Path
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Behavioral test for NF43: verifies that [ReplaceFileCorruptionHandler] actually recovers from
 * garbage data at runtime (not just compilation check).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CorruptionHandlerTest {

  @TempDir lateinit var tempDir: Path

  @Test
  fun `proto datastore recovers from corrupted file to default instance`() = runTest {
    val file = File(tempDir.toFile(), "corrupted_dashboard.pb")
    file.writeBytes("NOT_VALID_PROTO_DATA".toByteArray())

    val dataStore =
      DataStoreFactory.create(
        serializer = DashboardStoreSerializer,
        corruptionHandler =
          ReplaceFileCorruptionHandler { DashboardStoreProto.getDefaultInstance() },
        scope = this,
        produceFile = { file },
      )

    // First read should recover to default instance without throwing
    val result = dataStore.data.first()
    assertThat(result).isEqualTo(DashboardStoreProto.getDefaultInstance())

    // Subsequent reads also succeed (the corrupted file was replaced)
    val secondRead = dataStore.data.first()
    assertThat(secondRead).isEqualTo(DashboardStoreProto.getDefaultInstance())
  }

  @Test
  fun `preferences datastore recovers from corrupted file to empty preferences`() = runTest {
    val file = File(tempDir.toFile(), "corrupted_prefs.preferences_pb")
    file.writeBytes("GARBAGE_BYTES_NOT_VALID_PREFERENCES".toByteArray())

    val dataStore =
      PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        scope = this,
        produceFile = { file },
      )

    // First read should recover to empty preferences without throwing
    val result = dataStore.data.first()
    assertThat(result.asMap()).isEmpty()
  }

  @Test
  fun `write after corruption recovery succeeds and persists`() = runTest {
    val file = File(tempDir.toFile(), "recoverable_prefs.preferences_pb")
    file.writeBytes("CORRUPT".toByteArray())

    val dataStore =
      PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        scope = this,
        produceFile = { file },
      )

    // Recover from corruption
    val initial = dataStore.data.first()
    assertThat(initial.asMap()).isEmpty()

    // Write after recovery should work
    val testKey = stringPreferencesKey("test")
    dataStore.edit { prefs -> prefs[testKey] = "value" }

    // Verify the write persisted
    val afterWrite = dataStore.data.first()
    assertThat(afterWrite[testKey]).isEqualTo("value")
  }
}
