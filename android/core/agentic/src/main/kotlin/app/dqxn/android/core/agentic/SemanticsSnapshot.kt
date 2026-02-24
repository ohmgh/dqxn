package app.dqxn.android.core.agentic

import kotlinx.collections.immutable.ImmutableList
import kotlinx.serialization.Serializable

/**
 * Serializable snapshot of the Compose semantics tree at a point in time. Produced by
 * [SemanticsOwnerHolder.snapshot] and consumed by `dump-semantics` / `query-semantics` agentic
 * commands.
 *
 * @param nodes Top-level semantics nodes in the tree.
 * @param capturedAtMs Epoch milliseconds when the snapshot was captured.
 */
@Serializable
data class SemanticsSnapshot(
  val nodes: ImmutableList<Node>,
  val capturedAtMs: Long,
) {

  /**
   * A single node in the semantics tree.
   *
   * @param id Compose-assigned semantics node ID.
   * @param testTag Value of the `testTag` semantics property, if set.
   * @param text Merged text content of the node, if any.
   * @param contentDescription Accessibility content description, if set.
   * @param bounds Pixel bounds of the node in the window coordinate space.
   * @param children Child nodes in the semantics tree.
   * @param actions Available semantics actions (e.g., "OnClick", "SetText").
   */
  @Serializable
  data class Node(
    val id: Int,
    val testTag: String? = null,
    val text: String? = null,
    val contentDescription: String? = null,
    val bounds: Bounds? = null,
    val children: ImmutableList<Node>,
    val actions: ImmutableList<String>,
  )

  /**
   * Pixel bounds of a semantics node.
   *
   * @param left Left edge x coordinate.
   * @param top Top edge y coordinate.
   * @param right Right edge x coordinate.
   * @param bottom Bottom edge y coordinate.
   */
  @Serializable
  data class Bounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
  )
}
