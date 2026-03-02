package app.dqxn.android

import android.content.SharedPreferences
import app.dqxn.android.sdk.contracts.entitlement.EntitlementManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Stub [EntitlementManager] that returns free-tier-only entitlements.
 *
 * Supports programmatic entitlement simulation via [simulateRevocation], [simulateGrant], and
 * [reset] for chaos testing and debug tooling. Persists simulated entitlements to
 * [SharedPreferences] so the toggle survives process restarts. Phase 10 replaces this with a real
 * Play Billing implementation.
 */
public class StubEntitlementManager(
  private val prefs: SharedPreferences,
) : EntitlementManager {

  private val _entitlements = MutableStateFlow(loadEntitlements())

  override fun hasEntitlement(id: String): Boolean = id in _entitlements.value

  override fun getActiveEntitlements(): Set<String> = _entitlements.value

  override val entitlementChanges: Flow<Set<String>> = _entitlements

  public fun simulateRevocation(id: String) {
    updateEntitlements(_entitlements.value - id)
  }

  public fun simulateGrant(id: String) {
    updateEntitlements(_entitlements.value + id)
  }

  public fun reset() {
    updateEntitlements(DEFAULT_ENTITLEMENTS)
  }

  private fun updateEntitlements(entitlements: Set<String>) {
    _entitlements.value = entitlements
    prefs.edit().putStringSet(KEY_ENTITLEMENTS, entitlements).apply()
  }

  private fun loadEntitlements(): Set<String> =
    prefs.getStringSet(KEY_ENTITLEMENTS, null) ?: DEFAULT_ENTITLEMENTS

  private companion object {
    const val KEY_ENTITLEMENTS = "stub_entitlements"
    val DEFAULT_ENTITLEMENTS = setOf("free")
  }
}
