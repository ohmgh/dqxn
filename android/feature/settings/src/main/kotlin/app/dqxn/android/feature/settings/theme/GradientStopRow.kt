package app.dqxn.android.feature.settings.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.dqxn.android.core.design.token.DashboardSpacing
import app.dqxn.android.core.design.token.DashboardTypography
import app.dqxn.android.sdk.ui.theme.GradientStop
import app.dqxn.android.sdk.ui.theme.LocalDashboardTheme
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList

/** Minimum number of gradient stops allowed. */
internal const val MIN_GRADIENT_STOPS: Int = 2

/** Maximum number of gradient stops allowed. */
internal const val MAX_GRADIENT_STOPS: Int = 5

/**
 * Gradient stop editor: 2-5 stops, each with position slider and color picker.
 * - Add button disabled at [MAX_GRADIENT_STOPS] (5)
 * - Remove button per stop disabled at [MIN_GRADIENT_STOPS] (2)
 * - Position clamped to [0.0, 1.0]
 */
@Composable
public fun GradientStopRow(
  stops: ImmutableList<GradientStop>,
  onStopsChanged: (ImmutableList<GradientStop>) -> Unit,
  modifier: Modifier = Modifier,
) {
  val theme = LocalDashboardTheme.current
  val canAdd = stops.size < MAX_GRADIENT_STOPS
  val canRemove = stops.size > MIN_GRADIENT_STOPS

  Column(
    modifier = modifier.fillMaxWidth().testTag("gradient_stop_row"),
    verticalArrangement = Arrangement.spacedBy(DashboardSpacing.ItemGap),
  ) {
    // -- Add stop button --
    IconButton(
      onClick = {
        if (canAdd) {
          // Insert new stop at midpoint of existing range
          val midPosition =
            if (stops.size >= 2) {
              (stops[stops.size - 2].position + stops[stops.size - 1].position) / 2f
            } else {
              0.5f
            }
          val newStop = GradientStop(color = Color.Gray.value.toLong(), position = midPosition)
          onStopsChanged((stops.toPersistentList().add(newStop)).toImmutableList())
        }
      },
      enabled = canAdd,
      modifier = Modifier.align(Alignment.End).testTag("gradient_stop_add"),
    ) {
      Icon(
        imageVector = Icons.Filled.Add,
        contentDescription = "Add stop",
        tint = if (canAdd) theme.accentColor else theme.secondaryTextColor.copy(alpha = 0.3f),
      )
    }

    // -- Individual stops --
    stops.forEachIndexed { index, stop ->
      GradientStopItem(
        index = index,
        stop = stop,
        canRemove = canRemove,
        onPositionChanged = { newPosition ->
          val clamped = newPosition.coerceIn(0f, 1f)
          val updated = stops.toPersistentList().set(index, stop.copy(position = clamped))
          onStopsChanged(updated.toImmutableList())
        },
        onColorChanged = { newColor ->
          val updated =
            stops.toPersistentList().set(index, stop.copy(color = newColor.value.toLong()))
          onStopsChanged(updated.toImmutableList())
        },
        onRemove = {
          if (canRemove) {
            val updated = stops.toPersistentList().removeAt(index)
            onStopsChanged(updated.toImmutableList())
          }
        },
        accentColor = theme.accentColor,
        textColor = theme.primaryTextColor,
        secondaryTextColor = theme.secondaryTextColor,
      )
    }
  }
}

/** A single gradient stop: position slider + remove button + expandable color picker. */
@Composable
private fun GradientStopItem(
  index: Int,
  stop: GradientStop,
  canRemove: Boolean,
  onPositionChanged: (Float) -> Unit,
  onColorChanged: (Color) -> Unit,
  onRemove: () -> Unit,
  accentColor: Color,
  textColor: Color,
  secondaryTextColor: Color,
) {
  Column(
    modifier =
      Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("gradient_stop_item_$index"),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(
        text = "Stop ${index + 1}",
        style = DashboardTypography.caption,
        color = textColor,
      )
      Slider(
        value = stop.position.coerceIn(0f, 1f),
        onValueChange = onPositionChanged,
        valueRange = 0f..1f,
        colors =
          SliderDefaults.colors(
            thumbColor = accentColor,
            activeTrackColor = accentColor,
          ),
        modifier = Modifier.weight(1f).height(32.dp).testTag("gradient_stop_position_$index"),
      )
      Text(
        text = "%.2f".format(stop.position.coerceIn(0f, 1f)),
        style = DashboardTypography.caption,
        color = secondaryTextColor,
      )
      IconButton(
        onClick = onRemove,
        enabled = canRemove,
        modifier = Modifier.testTag("gradient_stop_remove_$index"),
      ) {
        Icon(
          imageVector = Icons.Filled.Remove,
          contentDescription = "Remove stop $index",
          tint = if (canRemove) accentColor else secondaryTextColor.copy(alpha = 0.3f),
        )
      }
    }

    // -- Color picker for this stop --
    InlineColorPicker(
      color = Color(stop.color.toULong()),
      onColorChanged = onColorChanged,
      modifier = Modifier.padding(start = 16.dp),
    )
  }
}
