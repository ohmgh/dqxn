package app.dqxn.android.sdk.contracts.settings

import app.dqxn.android.sdk.contracts.entitlement.Gated

// Stub sealed interface — Plan 03 fleshes out with 12 concrete subtypes
public sealed interface SettingDefinition<T> : Gated {
  public val key: String
  public val label: String
  public val description: String?
  public val default: T
  public val visibleWhen: ((Map<String, Any?>) -> Boolean)?
  public val groupId: String?
  public val hidden: Boolean
}
