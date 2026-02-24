package app.dqxn.android.core.agentic

/**
 * Filter criteria for querying the semantics tree via [SemanticsOwnerHolder.query]. All non-null
 * fields must match for a node to be included in results.
 *
 * @param testTag Match nodes with this exact test tag.
 * @param text Match nodes whose text contains this substring.
 * @param contentDescription Match nodes whose content description contains this substring.
 * @param hasAction Match nodes that expose this semantics action (e.g., "OnClick").
 */
data class SemanticsFilter(
  val testTag: String? = null,
  val text: String? = null,
  val contentDescription: String? = null,
  val hasAction: String? = null,
)

/**
 * Returns true if this filter matches the given [node]. All non-null filter fields must match.
 * Null fields are ignored (treated as wildcards).
 */
fun SemanticsFilter.matches(node: SemanticsSnapshot.Node): Boolean {
  if (testTag != null && node.testTag != testTag) return false
  if (text != null && (node.text == null || text !in node.text)) return false
  if (contentDescription != null &&
    (node.contentDescription == null || contentDescription !in node.contentDescription)
  ) {
    return false
  }
  if (hasAction != null && hasAction !in node.actions) return false
  return true
}
