package app.dqxn.android.feature.dashboard.grid

import android.app.Activity
import android.content.res.Configuration
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.IntRect
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import app.dqxn.android.sdk.observability.log.DqxnLogger
import app.dqxn.android.sdk.observability.log.LogTag
import app.dqxn.android.sdk.observability.log.debug
import app.dqxn.android.sdk.observability.log.info
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * A configuration boundary representing a viewport rectangle for a display configuration.
 *
 * Used for no-straddle snap enforcement (F1.27) and edit-mode boundary visualization (F1.26).
 * On foldable devices this represents the fold area; on non-foldable devices it represents the
 * alternate-orientation viewport.
 */
@Immutable
public data class ConfigurationBoundary(
  val name: String,
  val rect: IntRect,
)

/**
 * Detects display configuration boundaries for no-straddle snap enforcement.
 *
 * For foldable devices, extracts [FoldingFeature] bounds from [WindowInfoTracker]. For non-foldable
 * devices, computes the alternate-orientation viewport rectangle by swapping width/height from
 * current display metrics. Emits [emptyList] if the device has a single fixed orientation.
 */
public class ConfigurationBoundaryDetector
@Inject
constructor(
  private val windowInfoTracker: WindowInfoTracker,
  private val logger: DqxnLogger,
) {

  private val _boundaries: MutableStateFlow<ImmutableList<ConfigurationBoundary>> =
    MutableStateFlow(persistentListOf())

  public val boundaries: StateFlow<ImmutableList<ConfigurationBoundary>> = _boundaries.asStateFlow()

  /**
   * Start observing window layout info for the given [activity]. Must be called from a lifecycle-
   * aware scope (e.g. ViewModel init via DashboardScreen providing Activity reference).
   */
  public fun observe(activity: Activity, scope: CoroutineScope) {
    scope.launch {
      windowInfoTracker.windowLayoutInfo(activity).collect { layoutInfo ->
        val foldingFeatures =
          layoutInfo.displayFeatures.filterIsInstance<FoldingFeature>()

        val newBoundaries =
          if (foldingFeatures.isNotEmpty()) {
            foldingFeatures
              .map { fold ->
                val bounds = fold.bounds
                ConfigurationBoundary(
                  name = "fold",
                  rect = IntRect(bounds.left, bounds.top, bounds.right, bounds.bottom),
                )
              }
              .toImmutableList()
          } else {
            computeAlternateOrientationBoundary(activity)
          }

        logger.debug(TAG) { "Boundaries updated: ${newBoundaries.size} entries" }
        _boundaries.value = newBoundaries
      }
    }

    logger.info(TAG) { "Configuration boundary observation started" }
  }

  /**
   * For non-foldable devices, compute the alternate-orientation viewport. If the device has a fixed
   * orientation (rotation locked and no multi-window), returns [emptyList].
   */
  private fun computeAlternateOrientationBoundary(
    activity: Activity,
  ): ImmutableList<ConfigurationBoundary> {
    val config = activity.resources.configuration
    val metrics = activity.resources.displayMetrics

    // If orientation is undefined or the device is in a fixed state, no boundary needed
    if (config.orientation == Configuration.ORIENTATION_UNDEFINED) {
      return persistentListOf()
    }

    val currentWidth = metrics.widthPixels
    val currentHeight = metrics.heightPixels

    // The alternate orientation swaps width and height
    val altWidth = currentHeight
    val altHeight = currentWidth

    val boundaryName =
      if (config.orientation == Configuration.ORIENTATION_LANDSCAPE) "portrait" else "landscape"

    return persistentListOf(
      ConfigurationBoundary(
        name = boundaryName,
        rect = IntRect(left = 0, top = 0, right = altWidth, bottom = altHeight),
      )
    )
  }

  private companion object {
    val TAG = LogTag("ConfigBoundary")
  }
}
