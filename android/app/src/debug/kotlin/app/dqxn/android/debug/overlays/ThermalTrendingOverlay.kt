package app.dqxn.android.debug.overlays

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dqxn.android.core.thermal.ThermalLevel
import app.dqxn.android.core.thermal.ThermalMonitor

/**
 * Debug overlay showing thermal state and render configuration. Positioned at bottom-right.
 *
 * Displays current thermal level (NORMAL/WARM/DEGRADED/CRITICAL), target FPS from
 * [app.dqxn.android.core.thermal.RenderConfig], glow enabled state, and gradient fallback status.
 *
 * Reads [ThermalMonitor.thermalLevel] and [ThermalMonitor.renderConfig] flows via
 * [collectAsState]. Layer 0 pattern per CLAUDE.md.
 */
@Composable
internal fun ThermalTrendingOverlay(
  thermalMonitor: ThermalMonitor,
  modifier: Modifier = Modifier,
) {
  val thermalLevel by thermalMonitor.thermalLevel.collectAsState()
  val renderConfig by thermalMonitor.renderConfig.collectAsState()

  val levelColor = remember(thermalLevel) { colorForLevel(thermalLevel) }

  Column(
    modifier =
      modifier
        .graphicsLayer()
        .background(OverlayBg)
        .padding(8.dp),
  ) {
    Text(
      text = "Thermal",
      style = HeaderStyle,
    )
    Text(
      text = thermalLevel.name,
      style = BodyStyle.copy(color = levelColor),
    )
    Text(
      text = "Target: ${renderConfig.targetFps.toInt()} fps",
      style = BodyStyle,
    )
    Text(
      text = "Glow: ${if (renderConfig.glowEnabled) "ON" else "OFF"}",
      style = BodyStyle,
    )
    if (renderConfig.useGradientFallback) {
      Text(
        text = "Gradient fallback: ON",
        style = BodyStyle.copy(color = Color(0xFFFFC107)),
      )
    }
  }
}

private fun colorForLevel(level: ThermalLevel): Color =
  when (level) {
    ThermalLevel.NORMAL -> Color(0xFF4CAF50)
    ThermalLevel.WARM -> Color(0xFFFFC107)
    ThermalLevel.DEGRADED -> Color(0xFFFF9800)
    ThermalLevel.CRITICAL -> Color(0xFFF44336)
  }

private val OverlayBg = Color(0xCC1A1A1A)

private val HeaderStyle = TextStyle(
  color = Color(0xFFEEEEEE),
  fontSize = 14.sp,
  fontFamily = FontFamily.Monospace,
)

private val BodyStyle = TextStyle(
  color = Color(0xFFCCCCCC),
  fontSize = 12.sp,
  fontFamily = FontFamily.Monospace,
)
