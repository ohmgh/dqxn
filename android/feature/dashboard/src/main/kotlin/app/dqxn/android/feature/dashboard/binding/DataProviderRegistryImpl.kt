package app.dqxn.android.feature.dashboard.binding

import app.dqxn.android.sdk.contracts.entitlement.EntitlementManager
import app.dqxn.android.sdk.contracts.entitlement.isAccessible
import app.dqxn.android.sdk.contracts.provider.DataProvider
import app.dqxn.android.sdk.contracts.registry.DataProviderRegistry
import app.dqxn.android.sdk.observability.log.DqxnLogger
import app.dqxn.android.sdk.observability.log.LogTag
import app.dqxn.android.sdk.observability.log.info
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [DataProviderRegistry] backed by Hilt-injected [Set] of [DataProvider].
 *
 * Provides both unfiltered and entitlement-filtered views. Indexes providers by [DataProvider.dataType]
 * for fast lookup during widget binding resolution.
 */
@Singleton
public class DataProviderRegistryImpl
@Inject
constructor(
  private val providers: Set<@JvmSuppressWildcards DataProvider<*>>,
  private val entitlementManager: EntitlementManager,
  private val logger: DqxnLogger,
) : DataProviderRegistry {

  /** Map from dataType to list of providers supporting that type, sorted by priority. */
  private val providersByDataType: Map<String, List<DataProvider<*>>> by lazy {
    providers
      .groupBy { it.dataType }
      .mapValues { (_, list) -> list.sortedBy { it.priority.ordinal } }
  }

  init {
    logger.info(TAG) {
      "DataProvider registry initialized: ${providers.size} providers across " +
        "${providers.map { it.dataType }.toSet().size} data types"
    }
  }

  override fun getAll(): Set<DataProvider<*>> = providers

  override fun findByDataType(dataType: String): List<DataProvider<*>> =
    providersByDataType[dataType] ?: emptyList()

  override fun getFiltered(entitlementCheck: (String) -> Boolean): Set<DataProvider<*>> =
    providers.filter { it.isAccessible(entitlementCheck) }.toSet()

  /** Returns all providers accessible with current entitlements. */
  public fun availableProviders(): Set<DataProvider<*>> =
    getFiltered { entitlementManager.hasEntitlement(it) }

  internal companion object {
    val TAG: LogTag = LogTag("ProviderRegistry")
  }
}
