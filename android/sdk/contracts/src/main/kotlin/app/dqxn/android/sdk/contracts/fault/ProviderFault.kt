package app.dqxn.android.sdk.contracts.fault

import app.dqxn.android.sdk.contracts.provider.DataSnapshot

public sealed interface ProviderFault {
  public data object Kill : ProviderFault

  public data class Delay(val delayMs: Long) : ProviderFault

  public data class Error(val exception: Exception) : ProviderFault

  public data class ErrorOnNext(val exception: Exception) : ProviderFault

  public data class Corrupt(val transform: (DataSnapshot) -> DataSnapshot) : ProviderFault

  public data class Flap(val onMillis: Long, val offMillis: Long) : ProviderFault

  public data object Stall : ProviderFault
}
