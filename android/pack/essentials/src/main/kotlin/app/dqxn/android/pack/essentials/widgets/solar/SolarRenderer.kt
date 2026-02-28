package app.dqxn.android.pack.essentials.widgets.solar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.dqxn.android.pack.essentials.snapshots.SolarSnapshot
import app.dqxn.android.sdk.contracts.annotation.DashboardWidget
import app.dqxn.android.sdk.contracts.provider.DataSnapshot
import app.dqxn.android.sdk.contracts.settings.SettingDefinition
import app.dqxn.android.sdk.contracts.widget.WidgetContext
import app.dqxn.android.sdk.contracts.widget.WidgetData
import app.dqxn.android.sdk.contracts.widget.WidgetDefaults
import app.dqxn.android.sdk.contracts.widget.WidgetRenderer
import app.dqxn.android.sdk.contracts.widget.WidgetStyle
import app.dqxn.android.sdk.ui.widget.LocalWidgetData
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import kotlin.math.cos
import kotlin.math.sin
import kotlin.reflect.KClass
import kotlinx.collections.immutable.ImmutableMap

/** Display mode for the Solar widget. */
public enum class SolarDisplayMode {
  NEXT_EVENT,
  SUNRISE_SUNSET,
  ARC,
}

/** Arc size option. */
public enum class ArcSize {
  SMALL,
  MEDIUM,
  LARGE,
}

/**
 * Solar widget with sunrise/sunset times and optional 24h arc visualization.
 *
 * Three display modes:
 * - [SolarDisplayMode.NEXT_EVENT]: Countdown to next sunrise or sunset.
 * - [SolarDisplayMode.SUNRISE_SUNSET]: Both times displayed.
 * - [SolarDisplayMode.ARC]: Full 24h circular arc with dawn/day/dusk/night color bands plus
 *   sun/moon marker at current position. Uses `drawWithCache` for arc band geometry (recalculated
 *   on size change) and defers marker position reads to the draw phase.
 */
@DashboardWidget(typeId = "essentials:solar", displayName = "Solar")
public class SolarRenderer @Inject constructor() : WidgetRenderer {

  override val typeId: String = "essentials:solar"
  override val displayName: String = "Solar"
  override val description: String = "Sunrise, sunset times and 24h solar arc"
  override val compatibleSnapshots: Set<KClass<out DataSnapshot>> = setOf(SolarSnapshot::class)
  override val aspectRatio: Float? = null
  override val supportsTap: Boolean = false
  override val priority: Int = 40
  override val requiredAnyEntitlement: Set<String>? = null

  override val settingsSchema: List<SettingDefinition<*>> =
    listOf(
      SettingDefinition.EnumSetting(
        key = "displayMode",
        label = "Display Mode",
        description = "How to show solar information",
        default = SolarDisplayMode.NEXT_EVENT,
        options = SolarDisplayMode.entries,
        optionLabels =
          mapOf(
            SolarDisplayMode.NEXT_EVENT to "Next Event",
            SolarDisplayMode.SUNRISE_SUNSET to "Sunrise / Sunset",
            SolarDisplayMode.ARC to "24h Arc",
          ),
      ),
      SettingDefinition.EnumSetting(
        key = "arcSize",
        label = "Arc Size",
        description = "Size of the arc visualization",
        default = ArcSize.MEDIUM,
        options = ArcSize.entries,
        optionLabels =
          mapOf(
            ArcSize.SMALL to "Small",
            ArcSize.MEDIUM to "Medium",
            ArcSize.LARGE to "Large",
          ),
        visibleWhen = { settings -> settings["displayMode"]?.toString() == "ARC" },
      ),
      SettingDefinition.TimezoneSetting(
        key = "timezoneId",
        label = "Timezone",
        description = "Override timezone for solar calculation",
        default = null,
      ),
    )

  override fun getDefaults(context: WidgetContext): WidgetDefaults =
    WidgetDefaults(widthUnits = 10, heightUnits = 8, aspectRatio = null, settings = emptyMap())

  @Composable
  override fun Render(
    isEditMode: Boolean,
    style: WidgetStyle,
    settings: ImmutableMap<String, Any>,
    modifier: Modifier,
  ) {
    val widgetData = LocalWidgetData.current

    // State<T> (no `by`) — deferred to draw phase for ARC mode
    val solarState: State<SolarSnapshot?> = remember {
      derivedStateOf { widgetData.snapshot<SolarSnapshot>() }
    }

    val solar = solarState.value
    val displayMode = (settings["displayMode"] as? SolarDisplayMode) ?: SolarDisplayMode.NEXT_EVENT
    val arcSize = (settings["arcSize"] as? ArcSize) ?: ArcSize.MEDIUM

    if (solar == null) {
      EmptyState(modifier = modifier)
      return
    }

    when (displayMode) {
      SolarDisplayMode.NEXT_EVENT -> NextEventContent(solar = solar, modifier = modifier)
      SolarDisplayMode.SUNRISE_SUNSET -> SunriseSunsetContent(solar = solar, modifier = modifier)
      SolarDisplayMode.ARC ->
        ArcContent(solar = solar, arcSize = arcSize, solarState = solarState, modifier = modifier)
    }
  }

  override fun accessibilityDescription(data: WidgetData): String {
    val solar = data.snapshot<SolarSnapshot>()
    if (solar == null) return "Solar: no data available"

    val sunriseTime = formatTime(solar.sunriseEpochMillis)
    val sunsetTime = formatTime(solar.sunsetEpochMillis)
    val currentTime = System.currentTimeMillis()

    return if (solar.isDaytime) {
      val delta = solar.sunsetEpochMillis - currentTime
      if (delta > 0) {
        "Solar: sunset at $sunsetTime, in ${formatCountdown(delta)}"
      } else {
        "Solar: sunrise at $sunriseTime, sunset at $sunsetTime"
      }
    } else {
      val delta = solar.sunriseEpochMillis - currentTime
      if (delta > 0) {
        "Solar: sunrise at $sunriseTime, in ${formatCountdown(delta)}"
      } else {
        "Solar: sunrise at $sunriseTime, sunset at $sunsetTime"
      }
    }
  }

  override fun onTap(widgetId: String, settings: ImmutableMap<String, Any>): Boolean = false

  companion object {
    /** Arc start angle (6 o'clock position, bottom of circle). */
    internal const val ARC_START_ANGLE: Float = 90f

    /** Full arc sweep (full circle). */
    internal const val ARC_SWEEP_ANGLE: Float = 360f

    /** Dawn band duration: 30 minutes before sunrise. */
    internal const val DAWN_DURATION_MILLIS: Long = 30L * 60L * 1000L

    /** Dusk band duration: 30 minutes after sunset. */
    internal const val DUSK_DURATION_MILLIS: Long = 30L * 60L * 1000L

    // -- Arc band colors --
    internal val DAWN_COLOR = Color(0xFFFF9800) // Orange
    internal val DAY_COLOR = Color(0xFFFFC107) // Amber/Yellow
    internal val DUSK_COLOR = Color(0xFFFF5722) // Deep Orange
    internal val NIGHT_COLOR = Color(0xFF1A237E) // Indigo dark

    /**
     * Computes the sun's position as a fraction of the day arc.
     *
     * Returns 0.0 at sunrise, 0.5 at solar noon, 1.0 at sunset. Values outside sunrise-sunset range
     * are clamped to [0.0, 1.0].
     *
     * @param currentTimeMillis current time in epoch milliseconds
     * @param sunriseMillis sunrise time in epoch milliseconds
     * @param sunsetMillis sunset time in epoch milliseconds
     */
    fun computeSunPosition(
      currentTimeMillis: Long,
      sunriseMillis: Long,
      sunsetMillis: Long,
    ): Float {
      val dayDuration = sunsetMillis - sunriseMillis
      if (dayDuration <= 0L) return 0f
      val elapsed = currentTimeMillis - sunriseMillis
      return (elapsed.toFloat() / dayDuration.toFloat()).coerceIn(0f, 1f)
    }

    /**
     * Maps a sun position [0.0, 1.0] to an arc angle in degrees.
     *
     * @param sunPosition fraction from 0.0 (sunrise) to 1.0 (sunset)
     * @param arcStartAngle starting angle of the arc in degrees
     * @param arcSweepAngle total sweep of the arc in degrees
     */
    fun computeArcAngle(
      sunPosition: Float,
      arcStartAngle: Float,
      arcSweepAngle: Float,
    ): Float = arcStartAngle + sunPosition * arcSweepAngle

    /**
     * Formats a time delta in milliseconds to a human-readable countdown string.
     *
     * Examples: "2h 15m", "45m", "0m".
     *
     * @param deltaMillis time difference in milliseconds (must be >= 0)
     */
    fun formatCountdown(deltaMillis: Long): String {
      val totalMinutes = (deltaMillis / 60_000L).coerceAtLeast(0)
      val hours = totalMinutes / 60
      val minutes = totalMinutes % 60
      return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }
  }
}

// -- Private rendering helpers --

@Composable
private fun EmptyState(modifier: Modifier) {
  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Text(
      text = "Waiting for solar data",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
    )
  }
}

@Composable
private fun NextEventContent(solar: SolarSnapshot, modifier: Modifier) {
  val currentTime = remember { System.currentTimeMillis() }
  val isDay = solar.isDaytime

  val (label, targetTime) =
    if (isDay) {
      "Sunset" to solar.sunsetEpochMillis
    } else {
      "Sunrise" to solar.sunriseEpochMillis
    }

  val delta = (targetTime - currentTime).coerceAtLeast(0L)
  val countdownText = SolarRenderer.formatCountdown(delta)
  val timeText = formatTime(targetTime)

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Text(
        text = "$label in $countdownText",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
      )
      Text(
        text = timeText,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )
    }
  }
}

@Composable
private fun SunriseSunsetContent(solar: SolarSnapshot, modifier: Modifier) {
  val sunriseText = formatTime(solar.sunriseEpochMillis)
  val sunsetText = formatTime(solar.sunsetEpochMillis)

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Text(
        text = "Sunrise $sunriseText",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
      )
      Text(
        text = "Sunset $sunsetText",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
      )
    }
  }
}

@Composable
private fun ArcContent(
  solar: SolarSnapshot,
  arcSize: ArcSize,
  solarState: State<SolarSnapshot?>,
  modifier: Modifier,
) {
  val currentTime = remember { System.currentTimeMillis() }
  val sunriseMillis = solar.sunriseEpochMillis
  val sunsetMillis = solar.sunsetEpochMillis

  val arcStrokeWidth =
    when (arcSize) {
      ArcSize.SMALL -> 12.dp
      ArcSize.MEDIUM -> 18.dp
      ArcSize.LARGE -> 24.dp
    }

  // Precompute arc band boundaries (fraction of full day, 0 = midnight, 1 = next midnight)
  val dayStartMillis = sunriseMillis - (sunriseMillis % (24L * 3600L * 1000L))
  val dawnStartFraction =
    ((sunriseMillis - SolarRenderer.DAWN_DURATION_MILLIS - dayStartMillis).toFloat() /
        (24f * 3600f * 1000f))
      .coerceIn(0f, 1f)
  val sunriseFraction =
    ((sunriseMillis - dayStartMillis).toFloat() / (24f * 3600f * 1000f)).coerceIn(0f, 1f)
  val sunsetFraction =
    ((sunsetMillis - dayStartMillis).toFloat() / (24f * 3600f * 1000f)).coerceIn(0f, 1f)
  val duskEndFraction =
    ((sunsetMillis + SolarRenderer.DUSK_DURATION_MILLIS - dayStartMillis).toFloat() /
        (24f * 3600f * 1000f))
      .coerceIn(0f, 1f)

  // Current position as fraction of full day
  val currentFraction =
    ((currentTime - dayStartMillis).toFloat() / (24f * 3600f * 1000f)).coerceIn(0f, 1f)

  val sunPosition = SolarRenderer.computeSunPosition(currentTime, sunriseMillis, sunsetMillis)

  Spacer(
    modifier =
      modifier.fillMaxSize().padding(8.dp).drawWithCache {
        val arcStrokePx = arcStrokeWidth.toPx()
        val padding = arcStrokePx / 2f
        val arcRect = Size(size.width - arcStrokePx, size.height - arcStrokePx)
        val arcTopLeft = Offset(padding, padding)

        // Cached Stroke (one instance, reused for all arc bands)
        val arcStroke = Stroke(width = arcStrokePx)

        // Pre-compute arc band angles (only change when sunrise/sunset data changes, not per frame)
        val dawnStartAngle = SolarRenderer.ARC_START_ANGLE + dawnStartFraction * 360f
        val dawnSweep = (sunriseFraction - dawnStartFraction) * 360f
        val dayStartAngle = SolarRenderer.ARC_START_ANGLE + sunriseFraction * 360f
        val daySweep = (sunsetFraction - sunriseFraction) * 360f
        val duskStartAngle = SolarRenderer.ARC_START_ANGLE + sunsetFraction * 360f
        val duskSweep = (duskEndFraction - sunsetFraction) * 360f

        // Marker geometry
        val cx = size.width / 2f
        val cy = size.height / 2f
        val markerArcRadius = (size.width - arcStrokePx) / 2f
        val markerRadius = arcStrokePx * 0.6f

        onDrawBehind {
          // Night band (full circle background)
          drawArc(
            color = SolarRenderer.NIGHT_COLOR,
            startAngle = SolarRenderer.ARC_START_ANGLE,
            sweepAngle = SolarRenderer.ARC_SWEEP_ANGLE,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcRect,
            style = arcStroke,
          )

          // Dawn band
          if (dawnSweep > 0f) {
            drawArc(
              color = SolarRenderer.DAWN_COLOR,
              startAngle = dawnStartAngle,
              sweepAngle = dawnSweep,
              useCenter = false,
              topLeft = arcTopLeft,
              size = arcRect,
              style = arcStroke,
            )
          }

          // Day band
          if (daySweep > 0f) {
            drawArc(
              color = SolarRenderer.DAY_COLOR,
              startAngle = dayStartAngle,
              sweepAngle = daySweep,
              useCenter = false,
              topLeft = arcTopLeft,
              size = arcRect,
              style = arcStroke,
            )
          }

          // Dusk band
          if (duskSweep > 0f) {
            drawArc(
              color = SolarRenderer.DUSK_COLOR,
              startAngle = duskStartAngle,
              sweepAngle = duskSweep,
              useCenter = false,
              topLeft = arcTopLeft,
              size = arcRect,
              style = arcStroke,
            )
          }

          // Sun/moon marker at current position
          val markerAngle =
            SolarRenderer.ARC_START_ANGLE + currentFraction * SolarRenderer.ARC_SWEEP_ANGLE
          val markerAngleRad = Math.toRadians(markerAngle.toDouble())
          val markerX = cx + markerArcRadius * cos(markerAngleRad).toFloat()
          val markerY = cy + markerArcRadius * sin(markerAngleRad).toFloat()

          // Read latest solar state for isDaytime check in draw phase
          val currentSolar = solarState.value
          val markerColor =
            if (currentSolar?.isDaytime == true) SolarRenderer.DAY_COLOR else Color.White
          drawCircle(
            color = markerColor,
            radius = markerRadius,
            center = Offset(markerX, markerY),
          )
        }
      },
  )
}

/** Formats epoch millis to a locale-appropriate time string (e.g., "6:45 AM"). */
private fun formatTime(epochMillis: Long): String {
  val formatter = SimpleDateFormat("h:mm a", Locale.getDefault())
  formatter.timeZone = TimeZone.getDefault()
  return formatter.format(Date(epochMillis))
}
