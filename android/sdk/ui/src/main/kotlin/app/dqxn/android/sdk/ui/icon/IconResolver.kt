package app.dqxn.android.sdk.ui.icon

import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.vector.ImageVector
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves string icon names (from `SetupDefinition.iconName` and theme configs) to Material
 * [ImageVector] instances via reflection.
 *
 * Searches [Icons.Default] (aliased as Filled) and [Icons.Rounded] in that order. Results are
 * cached in a [ConcurrentHashMap] for fast repeat lookups.
 *
 * Returns `null` for unknown names -- callers provide their own fallback.
 */
public object IconResolver {
  // Wrapper to allow null values in ConcurrentHashMap (which doesn't support null keys/values).
  private class CacheEntry(val icon: ImageVector?)

  private val cache = ConcurrentHashMap<String, CacheEntry>()

  // Object instances paired with their class for reflection. Using the known singletons directly
  // avoids kotlin-reflect dependency.
  private val searchTargets: List<Pair<Any, Class<*>>> by lazy {
    listOf(
      Icons.Default to Icons.Default::class.java,
      Icons.Rounded to Icons.Rounded::class.java,
    )
  }

  /**
   * Resolves [iconName] to an [ImageVector] or `null` if not found.
   *
   * Accepts camelCase (`arrowBack`) or PascalCase (`ArrowBack`) -- both resolve to the same icon.
   */
  public fun resolve(iconName: String): ImageVector? {
    return cache.getOrPut(iconName) { CacheEntry(findIcon(iconName)) }.icon
  }

  private fun findIcon(name: String): ImageVector? {
    if (name.isBlank()) return null
    val pascalName = name.replaceFirstChar { it.uppercaseChar() }
    for ((instance, clazz) in searchTargets) {
      val result = resolveFromObject(instance, clazz, pascalName)
      if (result != null) return result
    }
    return null
  }

  private fun resolveFromObject(instance: Any, clazz: Class<*>, name: String): ImageVector? {
    return try {
      // Material icons are extension properties on Icons.Filled/Rounded objects.
      // At the JVM level, the Compose icons-extended library generates Kt top-level files
      // with static getter methods. The getter on the receiver object class has the form
      // get<Name>() and returns ImageVector.
      val getter =
        clazz.methods.firstOrNull { method ->
          method.name == "get$name" && method.parameterCount == 0
        }
      getter?.invoke(instance) as? ImageVector
    } catch (_: Exception) {
      null
    }
  }

  /** Clears the icon cache. Primarily for testing. */
  internal fun clearCache() {
    cache.clear()
  }
}
