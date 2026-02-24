package app.dqxn.android.data.provider

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.dqxn.android.data.di.ProviderSettings
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Preferences DataStore-backed implementation of [ProviderSettingsStore]. All values are stored as
 * type-prefixed strings under `{packId}:{providerId}:{key}` keys via [SettingsSerialization].
 */
@Singleton
public class ProviderSettingsStoreImpl
@Inject
constructor(
  @param:ProviderSettings private val dataStore: DataStore<Preferences>,
) : ProviderSettingsStore {

  override fun getSetting(packId: String, providerId: String, key: String): Flow<Any?> {
    val prefKey = stringPreferencesKey(composeKey(packId, providerId, key))
    return dataStore.data.map { prefs -> SettingsSerialization.deserializeValue(prefs[prefKey]) }
  }

  override suspend fun setSetting(packId: String, providerId: String, key: String, value: Any?) {
    val prefKey = stringPreferencesKey(composeKey(packId, providerId, key))
    dataStore.edit { prefs ->
      if (value == null) {
        prefs.remove(prefKey)
      } else {
        prefs[prefKey] = SettingsSerialization.serializeValue(value)
      }
    }
  }

  override fun getAllSettings(
    packId: String,
    providerId: String,
  ): Flow<ImmutableMap<String, Any?>> {
    val prefix = "$packId:$providerId:"
    return dataStore.data.map { prefs ->
      prefs
        .asMap()
        .entries
        .filter { it.key.name.startsWith(prefix) }
        .associate { (key, value) ->
          val bareKey = key.name.removePrefix(prefix)
          bareKey to SettingsSerialization.deserializeValue(value as? String)
        }
        .toImmutableMap()
    }
  }

  override suspend fun clearSettings(packId: String, providerId: String) {
    val prefix = "$packId:$providerId:"
    dataStore.edit { prefs ->
      val keysToRemove = prefs.asMap().keys.filter { it.name.startsWith(prefix) }
      keysToRemove.forEach { prefs.remove(it) }
    }
  }

  private fun composeKey(packId: String, providerId: String, key: String): String =
    "$packId:$providerId:$key"
}
