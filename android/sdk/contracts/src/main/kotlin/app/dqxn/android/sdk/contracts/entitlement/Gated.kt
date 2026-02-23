package app.dqxn.android.sdk.contracts.entitlement

public interface Gated {
  public val requiredAnyEntitlement: Set<String>?
}

public fun Gated.isAccessible(hasEntitlement: (String) -> Boolean): Boolean {
  val required = requiredAnyEntitlement
  if (required.isNullOrEmpty()) return true
  return required.any { hasEntitlement(it) }
}
