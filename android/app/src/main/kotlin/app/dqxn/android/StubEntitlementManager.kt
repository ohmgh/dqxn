package app.dqxn.android

import app.dqxn.android.sdk.contracts.entitlement.EntitlementManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Stub [EntitlementManager] that returns free-tier-only entitlements.
 *
 * Supports programmatic entitlement simulation via [simulateRevocation], [simulateGrant], and
 * [reset] for chaos testing and debug tooling. Phase 10 replaces this with a real Play Billing
 * implementation.
 */
public class StubEntitlementManager : EntitlementManager {

  private val _entitlements = MutableStateFlow(setOf("free"))

  override fun hasEntitlement(id: String): Boolean = id in _entitlements.value

  override fun getActiveEntitlements(): Set<String> = _entitlements.value

  override val entitlementChanges: Flow<Set<String>> = _entitlements

  public fun simulateRevocation(id: String) {
    _entitlements.value = _entitlements.value - id
  }

  public fun simulateGrant(id: String) {
    _entitlements.value = _entitlements.value + id
  }

  public fun reset() {
    _entitlements.value = setOf("free")
  }
}
