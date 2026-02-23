package app.dqxn.android.sdk.analytics

import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentMap

/**
 * Scoped analytics wrapper for individual packs. Prepends [packId] to all tracked event params so
 * analytics events are attributable to the originating pack.
 */
public class PackAnalytics(
  private val packId: String,
  private val delegate: AnalyticsTracker,
) {

  /** Tracks the given event with `pack_id` prepended to its params. */
  public fun track(event: AnalyticsEvent) {
    val enrichedParams =
      persistentMapOf<String, Any>("pack_id" to packId).putAll(event.params.toPersistentMap())
    val enrichedEvent = EnrichedEvent(name = event.name, params = enrichedParams)
    delegate.track(enrichedEvent)
  }
}

/**
 * Internal event wrapper used by [PackAnalytics] to enrich events with pack attribution. Not part
 * of the [AnalyticsEvent] sealed hierarchy -- these are transient wrappers, not distinct event
 * types.
 */
public data class EnrichedEvent(
  override val name: String,
  override val params: ImmutableMap<String, Any>,
) : AnalyticsEvent
