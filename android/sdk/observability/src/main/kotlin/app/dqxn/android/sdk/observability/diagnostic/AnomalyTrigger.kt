package app.dqxn.android.sdk.observability.diagnostic

/** Sealed hierarchy of anomaly triggers that initiate diagnostic snapshot captures. */
public sealed interface AnomalyTrigger {

  public data class WidgetCrash(
    val typeId: String,
    val widgetId: String,
    val exception: String,
  ) : AnomalyTrigger

  public data class AnrDetected(
    val mainThreadStackTrace: String,
    val fdCount: Int,
  ) : AnomalyTrigger

  public data class ThermalEscalation(
    val fromTier: String,
    val toTier: String,
  ) : AnomalyTrigger

  public data class JankSpike(
    val consecutiveFrames: Int,
  ) : AnomalyTrigger

  public data class ProviderTimeout(
    val providerId: String,
    val timeoutMs: Long,
  ) : AnomalyTrigger

  public data class EscalatedStaleness(
    val typeId: String,
    val staleSeconds: Long,
  ) : AnomalyTrigger

  public data class BindingStalled(
    val widgetId: String,
    val stalledMs: Long,
  ) : AnomalyTrigger

  public data class DataStoreCorruption(
    val storeName: String,
  ) : AnomalyTrigger
}
