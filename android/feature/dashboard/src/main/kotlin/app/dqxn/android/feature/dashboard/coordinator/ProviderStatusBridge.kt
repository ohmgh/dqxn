package app.dqxn.android.feature.dashboard.coordinator

import app.dqxn.android.sdk.contracts.provider.DataProvider
import app.dqxn.android.sdk.contracts.registry.DataProviderRegistry
import app.dqxn.android.sdk.observability.health.ProviderStatus
import app.dqxn.android.sdk.observability.health.ProviderStatusProvider
import app.dqxn.android.sdk.observability.log.DqxnLogger
import app.dqxn.android.sdk.observability.log.LogTag
import app.dqxn.android.sdk.observability.log.debug
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

/**
 * Singleton bridge that exposes [ProviderStatusProvider] by deriving status from the
 * [DataProviderRegistry]'s registered [DataProvider] instances.
 *
 * Each provider's [DataProvider.connectionState] and [DataProvider.connectionErrorDescription]
 * flows are combined into a single [Map] of [ProviderStatus] keyed by [DataProvider.sourceId].
 *
 * This class lives in `:feature:dashboard` (where the registry implementation is) and is bound to
 * the [ProviderStatusProvider] interface in [DashboardModule], making it injectable by
 * `:feature:diagnostics` via `:sdk:observability` without cross-feature dependency.
 */
@Singleton
public class ProviderStatusBridge
@Inject
constructor(
  private val providerRegistry: DataProviderRegistry,
  private val logger: DqxnLogger,
) : ProviderStatusProvider {

  override fun providerStatuses(): Flow<Map<String, ProviderStatus>> {
    val providers = providerRegistry.getAll()

    if (providers.isEmpty()) {
      return flowOf(emptyMap())
    }

    // For each provider, combine connectionState + connectionErrorDescription into a ProviderStatus
    val statusFlows: List<Flow<Pair<String, ProviderStatus>>> =
      providers.map { provider -> providerStatusFlow(provider) }

    // Combine all individual status flows into a single map
    return combine(statusFlows) { statuses -> statuses.associate { it } }
      .onEach { map -> logger.debug(TAG) { "Provider statuses updated: ${map.size} providers" } }
  }

  /**
   * Creates a [Flow] that emits a [Pair] of (providerId, [ProviderStatus]) by combining the
   * provider's [DataProvider.connectionState] and [DataProvider.connectionErrorDescription].
   */
  private fun providerStatusFlow(
    provider: DataProvider<*>,
  ): Flow<Pair<String, ProviderStatus>> =
    combine(
      provider.connectionState,
      provider.connectionErrorDescription,
    ) { isConnected, errorDesc ->
      provider.sourceId to
        ProviderStatus(
          providerId = provider.sourceId,
          displayName = provider.displayName,
          isConnected = isConnected,
          lastUpdateTimestamp = System.currentTimeMillis(),
          errorDescription = errorDesc,
        )
    }

  internal companion object {
    val TAG: LogTag = LogTag("ProviderStatus")
  }
}
