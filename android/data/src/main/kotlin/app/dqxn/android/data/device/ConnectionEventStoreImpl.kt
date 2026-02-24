package app.dqxn.android.data.device

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.dqxn.android.data.di.ProviderSettings
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Preferences DataStore-backed implementation of [ConnectionEventStore]. Events are stored as a
 * JSON-serialized list under a dedicated key with `__` prefix to avoid collision with the
 * `{packId}:{providerId}:{key}` namespace used by [ProviderSettingsStoreImpl].
 */
@Singleton
public class ConnectionEventStoreImpl
@Inject
constructor(
  @param:ProviderSettings private val dataStore: DataStore<Preferences>,
) : ConnectionEventStore {

  private val json = Json { ignoreUnknownKeys = true }
  private val listSerializer = ListSerializer(ConnectionEvent.serializer())

  override val events: Flow<ImmutableList<ConnectionEvent>> =
    dataStore.data.map { prefs -> deserializeEvents(prefs[EVENTS_KEY]).toImmutableList() }

  override suspend fun recordEvent(event: ConnectionEvent) {
    dataStore.edit { prefs ->
      val current = deserializeEvents(prefs[EVENTS_KEY]).toMutableList()
      current.add(event)
      // Enforce rolling window
      while (current.size > ConnectionEventStore.MAX_EVENTS) {
        current.removeAt(0)
      }
      prefs[EVENTS_KEY] = json.encodeToString(listSerializer, current)
    }
  }

  override suspend fun clear() {
    dataStore.edit { prefs -> prefs.remove(EVENTS_KEY) }
  }

  private fun deserializeEvents(raw: String?): List<ConnectionEvent> {
    if (raw.isNullOrBlank()) return emptyList()
    return try {
      json.decodeFromString(listSerializer, raw)
    } catch (_: Exception) {
      emptyList()
    }
  }

  private companion object {
    val EVENTS_KEY = stringPreferencesKey("__connection_events__")
  }
}
