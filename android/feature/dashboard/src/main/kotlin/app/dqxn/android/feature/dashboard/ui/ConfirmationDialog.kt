package app.dqxn.android.feature.dashboard.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.dqxn.android.core.design.motion.DashboardMotion

/**
 * Reusable modal confirmation dialog with scrim + animation.
 *
 * Uses [DashboardMotion.dialogEnter] / [DashboardMotion.dialogExit] for scale+fade transitions.
 * Ported from old codebase confirmation pattern. Reused by overlays in Phase 10.
 *
 * The scrim uses [DashboardMotion.dialogScrimEnter] / [DashboardMotion.dialogScrimExit] for
 * fade-only transitions at the route level.
 */
@Composable
public fun ConfirmationDialog(
  visible: Boolean,
  title: String,
  message: String,
  confirmLabel: String = "Confirm",
  dismissLabel: String = "Cancel",
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
) {
  AnimatedVisibility(
    visible = visible,
    enter = DashboardMotion.dialogScrimEnter,
    exit = DashboardMotion.dialogScrimExit,
  ) {
    // Scrim layer
    Box(
      modifier =
        modifier
          .fillMaxSize()
          .background(Color.Black.copy(alpha = 0.5f))
          .clickable(
            indication = null,
            interactionSource = remember { MutableInteractionSource() },
            onClick = onDismiss,
          ),
      contentAlignment = Alignment.Center,
    ) {
      // Dialog card with DashboardMotion animations
      AnimatedVisibility(
        visible = visible,
        enter = DashboardMotion.dialogEnter,
        exit = DashboardMotion.dialogExit,
      ) {
        Column(
          modifier =
            Modifier.fillMaxWidth(0.85f)
              .clip(RoundedCornerShape(24.dp))
              .background(MaterialTheme.colorScheme.surface)
              .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = {}, // Consume click to prevent scrim dismiss
              )
              .padding(24.dp),
        ) {
          Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Spacer(modifier = Modifier.height(12.dp))
          Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(modifier = Modifier.height(24.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
          ) {
            TextButton(onClick = onDismiss) { Text(text = dismissLabel) }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = onConfirm) { Text(text = confirmLabel) }
          }
        }
      }
    }
  }
}
