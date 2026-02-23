package app.dqxn.android.sdk.contracts.provider

import app.dqxn.android.sdk.contracts.entitlement.Gated

public interface DataProviderSpec : Gated {
  public val sourceId: String
  public val displayName: String
  public val description: String
  public val dataType: String
  public val priority: ProviderPriority
}
