package app.dqxn.android.sdk.contracts.entitlement

import kotlinx.coroutines.flow.Flow

public interface EntitlementManager {
  public fun hasEntitlement(id: String): Boolean

  public fun getActiveEntitlements(): Set<String>

  public val entitlementChanges: Flow<Set<String>>
}
