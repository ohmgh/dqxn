package app.dqxn.android.data.preset

import java.util.TimeZone

/**
 * Simple timezone-based region heuristic for preset selection. Phase 8's `RegionDetector` can
 * enhance this with locale/SIM data.
 */
internal object FallbackRegionDetector {

  fun detectRegion(): String {
    val tzId = TimeZone.getDefault().id
    return when {
      tzId.startsWith("America/") -> "US"
      tzId == "Europe/London" || tzId == "Europe/Dublin" -> "GB"
      tzId == "Asia/Singapore" -> "SG"
      tzId == "Asia/Tokyo" -> "JP"
      tzId.startsWith("Europe/") -> "EU"
      else -> "DEFAULT"
    }
  }
}
