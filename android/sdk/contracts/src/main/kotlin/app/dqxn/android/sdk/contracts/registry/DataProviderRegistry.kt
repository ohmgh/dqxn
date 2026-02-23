package app.dqxn.android.sdk.contracts.registry

import app.dqxn.android.sdk.contracts.provider.DataProvider

/**
 * Registry of all available data providers.
 *
 * Supports entitlement-filtered views per F3.7. Interface defined in Phase 2 for consumption by
 * `MetricsCollector` and `WidgetHealthMonitor` (Phase 3). Implementation in Phase 7.
 */
public interface DataProviderRegistry {
  public fun getAll(): Set<DataProvider<*>>

  public fun findByDataType(dataType: String): List<DataProvider<*>>

  public fun getFiltered(entitlementCheck: (String) -> Boolean): Set<DataProvider<*>>
}
