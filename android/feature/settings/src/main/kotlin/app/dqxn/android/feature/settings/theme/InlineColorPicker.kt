package app.dqxn.android.feature.settings.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.dqxn.android.core.design.token.DashboardSpacing
import app.dqxn.android.core.design.token.DashboardTypography
import app.dqxn.android.sdk.ui.theme.LocalDashboardTheme

/**
 * Inline HSL color picker with 4 sliders (H, S, L, A) and a hex text editor.
 *
 * Bidirectional sync: slider changes update hex, hex changes update sliders. Uses
 * [colorToHsl]/[hslToColor] from ColorConversion.kt for conversion.
 */
@Composable
public fun InlineColorPicker(
  color: Color,
  onColorChanged: (Color) -> Unit,
  modifier: Modifier = Modifier,
) {
  val theme = LocalDashboardTheme.current
  val hsl = remember(color) { colorToHsl(color) }

  var hue by remember(color) { mutableFloatStateOf(hsl[0]) }
  var saturation by remember(color) { mutableFloatStateOf(hsl[1]) }
  var lightness by remember(color) { mutableFloatStateOf(hsl[2]) }
  var alpha by remember(color) { mutableFloatStateOf(color.alpha) }
  var hexText by remember(color) { mutableStateOf(colorToHex(color)) }

  fun updateFromSliders() {
    val newColor = hslToColor(floatArrayOf(hue, saturation, lightness)).copy(alpha = alpha)
    hexText = colorToHex(newColor)
    onColorChanged(newColor)
  }

  Column(
    modifier = modifier.fillMaxWidth().testTag("inline_color_picker"),
    verticalArrangement = Arrangement.spacedBy(DashboardSpacing.ItemGap),
  ) {
    // -- Color preview swatch --
    Box(
      modifier =
        Modifier.size(40.dp)
          .clip(CircleShape)
          .background(color)
          .border(1.dp, theme.widgetBorderColor, CircleShape)
          .align(Alignment.CenterHorizontally)
          .testTag("color_preview_swatch"),
    )

    // -- H slider (0-360) --
    LabeledSlider(
      label = "H",
      value = hue,
      valueRange = 0f..360f,
      onValueChange = {
        hue = it
        updateFromSliders()
      },
      displayValue = "${hue.toInt()}",
      accentColor = theme.accentColor,
      textColor = theme.primaryTextColor,
      testTag = "slider_hue",
    )

    // -- S slider (0-1) --
    LabeledSlider(
      label = "S",
      value = saturation,
      valueRange = 0f..1f,
      onValueChange = {
        saturation = it
        updateFromSliders()
      },
      displayValue = "%.2f".format(saturation),
      accentColor = theme.accentColor,
      textColor = theme.primaryTextColor,
      testTag = "slider_saturation",
    )

    // -- L slider (0-1) --
    LabeledSlider(
      label = "L",
      value = lightness,
      valueRange = 0f..1f,
      onValueChange = {
        lightness = it
        updateFromSliders()
      },
      displayValue = "%.2f".format(lightness),
      accentColor = theme.accentColor,
      textColor = theme.primaryTextColor,
      testTag = "slider_lightness",
    )

    // -- A slider (0-1) --
    LabeledSlider(
      label = "A",
      value = alpha,
      valueRange = 0f..1f,
      onValueChange = {
        alpha = it
        updateFromSliders()
      },
      displayValue = "%.2f".format(alpha),
      accentColor = theme.accentColor,
      textColor = theme.primaryTextColor,
      testTag = "slider_alpha",
    )

    // -- Hex text field (bidirectional) --
    TextField(
      value = hexText,
      onValueChange = { newHex ->
        hexText = newHex
        val parsed = parseHexToColor(newHex)
        if (parsed != null) {
          val parsedHsl = colorToHsl(parsed)
          hue = parsedHsl[0]
          saturation = parsedHsl[1]
          lightness = parsedHsl[2]
          alpha = parsed.alpha
          onColorChanged(parsed)
        }
      },
      label = { Text("Hex", style = DashboardTypography.caption) },
      textStyle = DashboardTypography.description,
      singleLine = true,
      colors =
        TextFieldDefaults.colors(
          focusedTextColor = theme.primaryTextColor,
          unfocusedTextColor = theme.secondaryTextColor,
          cursorColor = theme.accentColor,
        ),
      modifier = Modifier.fillMaxWidth().testTag("hex_text_field"),
    )
  }
}

/** Internal labeled slider row for consistent color picker layout. */
@Composable
private fun LabeledSlider(
  label: String,
  value: Float,
  valueRange: ClosedFloatingPointRange<Float>,
  onValueChange: (Float) -> Unit,
  displayValue: String,
  accentColor: Color,
  textColor: Color,
  testTag: String,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(
      text = label,
      style = DashboardTypography.caption,
      color = textColor,
      modifier = Modifier.testTag("${testTag}_label"),
    )
    Slider(
      value = value,
      onValueChange = onValueChange,
      valueRange = valueRange,
      colors =
        SliderDefaults.colors(
          thumbColor = accentColor,
          activeTrackColor = accentColor,
        ),
      modifier = Modifier.weight(1f).height(32.dp).testTag(testTag),
    )
    Text(
      text = displayValue,
      style = DashboardTypography.caption,
      color = textColor,
      modifier = Modifier.testTag("${testTag}_value"),
    )
  }
}
