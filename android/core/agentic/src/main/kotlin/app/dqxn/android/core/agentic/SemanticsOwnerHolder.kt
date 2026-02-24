package app.dqxn.android.core.agentic

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * Singleton holder providing access to the Compose semantics tree for agentic commands
 * (`dump-semantics`, `query-semantics`).
 *
 * The semantics owner is registered by the dashboard layer via [register] and cleared on disposal
 * via [unregister]. A [WeakReference] prevents memory leaks if the caller forgets to unregister
 * (Pitfall 6 from research).
 *
 * Thread-safe: [register] and [unregister] are synchronized. [snapshot] and [query] read
 * the volatile reference without locking -- a stale null is acceptable (returns empty result).
 */
@Singleton
class SemanticsOwnerHolder @Inject constructor() {

  @Volatile private var ownerRef: WeakReference<SemanticsOwner>? = null

  /**
   * Registers a [SemanticsOwner] for semantics tree access. Called by the dashboard Compose layer
   * when the root composition is created.
   *
   * @param owner The Compose [SemanticsOwner] to hold. Stored as a [WeakReference].
   */
  @Synchronized
  fun register(owner: Any) {
    if (owner is SemanticsOwner) {
      ownerRef = WeakReference(owner)
    }
  }

  /** Clears the registered owner. Called when the dashboard composition is disposed. */
  @Synchronized
  fun unregister() {
    ownerRef = null
  }

  /**
   * Captures a snapshot of the current semantics tree.
   *
   * @return A [SemanticsSnapshot] of the full tree, or null if no owner is registered or the
   *   reference has been garbage collected.
   */
  fun snapshot(): SemanticsSnapshot? {
    val owner = ownerRef?.get() ?: return null
    return try {
      val rootNode = owner.rootSemanticsNode
      SemanticsSnapshot(
        nodes = persistentListOf(mapNode(rootNode)),
        capturedAtMs = System.currentTimeMillis(),
      )
    } catch (_: Exception) {
      null
    }
  }

  /**
   * Queries the semantics tree with the given [filter].
   *
   * @return Matching nodes from the tree. Empty list if no owner is registered.
   */
  fun query(filter: SemanticsFilter): List<SemanticsSnapshot.Node> {
    val snapshot = snapshot() ?: return emptyList()
    return collectMatching(snapshot.nodes, filter)
  }

  private fun mapNode(node: SemanticsNode): SemanticsSnapshot.Node {
    val config = node.config
    val testTag =
      config.getOrNull(SemanticsProperties.TestTag)
    val textValues =
      config.getOrNull(SemanticsProperties.Text)
    val text = textValues?.firstOrNull()?.text
    val contentDescValues =
      config.getOrNull(SemanticsProperties.ContentDescription)
    val contentDescription = contentDescValues?.firstOrNull()

    val boundsRect = node.boundsInWindow
    val bounds =
      SemanticsSnapshot.Bounds(
        left = boundsRect.left,
        top = boundsRect.top,
        right = boundsRect.right,
        bottom = boundsRect.bottom,
      )

    val actions: ImmutableList<String> =
      config
        .filter { it.key.name.startsWith("") } // include all entries
        .mapNotNull { entry ->
          // Action keys have names like "OnClick", "SetText", etc.
          if (entry.key.name != entry.key.name.replaceFirstChar { it.uppercase() }) null
          else entry.key.name
        }
        .filter { it.first().isUpperCase() }
        .toImmutableList()

    val children: ImmutableList<SemanticsSnapshot.Node> =
      node.children.map { mapNode(it) }.toImmutableList()

    return SemanticsSnapshot.Node(
      id = node.id,
      testTag = testTag,
      text = text,
      contentDescription = contentDescription,
      bounds = bounds,
      children = children,
      actions = actions,
    )
  }

  private fun collectMatching(
    nodes: ImmutableList<SemanticsSnapshot.Node>,
    filter: SemanticsFilter,
  ): List<SemanticsSnapshot.Node> = buildList {
    for (node in nodes) {
      if (filter.matches(node)) add(node)
      addAll(collectMatching(node.children, filter))
    }
  }
}
