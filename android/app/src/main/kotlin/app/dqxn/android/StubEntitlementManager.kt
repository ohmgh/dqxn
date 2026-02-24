package app.dqxn.android

import app.dqxn.android.sdk.contracts.entitlement.EntitlementManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Stub [EntitlementManager] that returns free-tier-only entitlements.
 *
 * All purchase and restore operations are no-ops. Phase 10 replaces this with a real
 * Play Billing implementation.
 */
public class StubEntitlementManager : EntitlementManager {

  private val activeEntitlements: Set<String> = setOf("free")

  override fun hasEntitlement(id: String): Boolean = id in activeEntitlements

  override fun getActiveEntitlements(): Set<String> = activeEntitlements

  override val entitlementChanges: Flow<Set<String>> =
    MutableStateFlow(activeEntitlements)
}
