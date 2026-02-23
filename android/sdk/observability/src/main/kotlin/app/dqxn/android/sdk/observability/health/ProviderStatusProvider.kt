package app.dqxn.android.sdk.observability.health

import kotlinx.coroutines.flow.Flow

/**
 * Provider status interface consumed by `:feature:diagnostics` without cross-feature dependency.
 * Implementation deferred to `WidgetBindingCoordinator` in Phase 7.
 */
public interface ProviderStatusProvider {

  /** Emits the current status of all registered data providers. */
  public fun providerStatuses(): Flow<Map<String, ProviderStatus>>
}

/** Status of a single data provider. */
public data class ProviderStatus(
  val providerId: String,
  val displayName: String,
  val isConnected: Boolean,
  val lastUpdateTimestamp: Long,
  val errorDescription: String? = null,
)
