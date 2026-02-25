package app.dqxn.android.sdk.contracts.settings

import kotlinx.collections.immutable.ImmutableMap
import kotlinx.coroutines.flow.Flow

/**
 * Persistence store for per-provider settings, namespaced by pack. Key format:
 * `{packId}:{providerId}:{key}` (F7.4).
 *
 * Values are type-safe via implementation-side serialization -- the store round-trips String, Int,
 * Long, Float, Double, Boolean, and null without loss.
 *
 * Interface defined in `:sdk:contracts` so packs can inject it. Implementation in `:data`.
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

  /**
   * Observe all provider settings across all packs/providers as a nested map. Outer key is
   * `{packId}:{providerId}`, inner key is the bare setting key. Used by data export (NF-P5).
   */
  public fun getAllProviderSettings(): Flow<Map<String, Map<String, String>>>

  /** Remove ALL provider settings across all packs/providers. Used by "Delete All Data" (F14.4). */
  public suspend fun clearAll()
}
