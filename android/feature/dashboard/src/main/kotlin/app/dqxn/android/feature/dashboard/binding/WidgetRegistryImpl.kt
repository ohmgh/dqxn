package app.dqxn.android.feature.dashboard.binding

import app.dqxn.android.sdk.contracts.registry.WidgetRegistry
import app.dqxn.android.sdk.contracts.widget.WidgetRenderer
import app.dqxn.android.sdk.observability.log.DqxnLogger
import app.dqxn.android.sdk.observability.log.LogTag
import app.dqxn.android.sdk.observability.log.info
import app.dqxn.android.sdk.observability.log.warn
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableMap

/**
 * Implementation of [WidgetRegistry] backed by Hilt-injected [Set] of [WidgetRenderer].
 *
 * Indexes renderers by [WidgetRenderer.typeId] at construction time into an [ImmutableMap] for O(1)
 * lookup. Duplicate typeIds are resolved with last-write-wins and a warning log.
 */
@Singleton
public class WidgetRegistryImpl
@Inject
constructor(
  renderers: Set<@JvmSuppressWildcards WidgetRenderer>,
  private val logger: DqxnLogger,
) : WidgetRegistry {

  private val rendererIndex: ImmutableMap<String, WidgetRenderer>

  init {
    val mutable = mutableMapOf<String, WidgetRenderer>()
    for (renderer in renderers) {
      val existing = mutable.put(renderer.typeId, renderer)
      if (existing != null) {
        logger.warn(TAG) {
          "Duplicate typeId '${renderer.typeId}' — replacing ${existing::class.simpleName} " +
            "with ${renderer::class.simpleName}"
        }
      }
    }
    rendererIndex = mutable.toImmutableMap()
    logger.info(TAG) { "Widget registry initialized: ${rendererIndex.size} renderers" }
  }

  override fun getAll(): Set<WidgetRenderer> = rendererIndex.values.toSet()

  override fun findByTypeId(typeId: String): WidgetRenderer? = rendererIndex[typeId]

  override fun getTypeIds(): Set<String> = rendererIndex.keys

  internal companion object {
    val TAG: LogTag = LogTag("WidgetRegistry")
  }
}
