package app.dqxn.android.data.style

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.dqxn.android.data.di.WidgetStyles
import app.dqxn.android.sdk.contracts.widget.WidgetStyle
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

/**
 * Preferences DataStore-backed implementation of [WidgetStyleStore]. Each widget instance's style
 * is JSON-serialized under its instance ID key.
 */
@Singleton
public class WidgetStyleStoreImpl
@Inject
constructor(
  @param:WidgetStyles private val dataStore: DataStore<Preferences>,
) : WidgetStyleStore {

  private val json = Json { ignoreUnknownKeys = true }

  override fun getStyle(instanceId: String): Flow<WidgetStyle> {
    val prefKey = stringPreferencesKey(instanceId)
    return dataStore.data.map { prefs ->
      val raw = prefs[prefKey]
      if (raw != null) {
        try {
          json.decodeFromString(WidgetStyle.serializer(), raw)
        } catch (_: Exception) {
          WidgetStyle.Default
        }
      } else {
        WidgetStyle.Default
      }
    }
  }

  override suspend fun setStyle(instanceId: String, style: WidgetStyle) {
    val prefKey = stringPreferencesKey(instanceId)
    dataStore.edit { prefs ->
      prefs[prefKey] = json.encodeToString(WidgetStyle.serializer(), style)
    }
  }

  override suspend fun removeStyle(instanceId: String) {
    val prefKey = stringPreferencesKey(instanceId)
    dataStore.edit { prefs -> prefs.remove(prefKey) }
  }
}
