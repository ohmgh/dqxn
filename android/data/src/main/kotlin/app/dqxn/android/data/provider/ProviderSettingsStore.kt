package app.dqxn.android.data.provider

import kotlinx.collections.immutable.ImmutableMap
import kotlinx.coroutines.flow.Flow

/**
 * Persistence store for per-provider settings, namespaced by pack. Key format:
 * `{packId}:{providerId}:{key}` (F7.4).
 *
 * Values are type-safe via [SettingsSerialization] — the store round-trips String, Int, Long,
 * Float, Double, Boolean, and null without loss.
 */
public interface ProviderSettingsStore {

  /** Observe a single setting. Emits `null` for missing keys. */
  public fun getSetting(packId: String, providerId: String, key: String): Flow<Any?>

  /** Write a setting value. Pass `null` to remove the key. */
  public suspend fun setSetting(packId: String, providerId: String, key: String, value: Any?)

  /** Observe all settings for a specific provider, keyed by the bare setting key. */
  public fun getAllSettings(packId: String, providerId: String): Flow<ImmutableMap<String, Any?>>

  /** Remove all settings belonging to a single provider. */
  public suspend fun clearSettings(packId: String, providerId: String)
}
