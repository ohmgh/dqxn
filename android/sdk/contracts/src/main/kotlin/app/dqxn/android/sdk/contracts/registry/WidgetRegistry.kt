package app.dqxn.android.sdk.contracts.registry

import app.dqxn.android.sdk.contracts.widget.WidgetRenderer

/**
 * Registry of all available widget renderers.
 *
 * Interface defined in Phase 2 for consumption by `MetricsCollector` (Phase 3). Implementation
 * wraps Hilt `Set<WidgetRenderer>` in Phase 7.
 */
public interface WidgetRegistry {
  public fun getAll(): Set<WidgetRenderer>

  public fun findByTypeId(typeId: String): WidgetRenderer?

  public fun getTypeIds(): Set<String>
}
