package app.dqxn.android.feature.dashboard.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.dqxn.android.core.design.motion.DashboardMotion
import app.dqxn.android.core.design.token.DashboardSpacing
import app.dqxn.android.sdk.ui.theme.LocalDashboardTheme

/**
 * Auto-hiding bottom bar floating over the dashboard canvas (F1.9).
 *
 * Contents:
 * - Settings FAB (left)
 * - Add Widget FAB (right, always visible)
 *
 * No edit button — long-press empty space enters edit mode via [BlankSpaceGestureHandler].
 *
 * Transparent background, 120dp height, buttons at bottom. FABs are 56dp accent-colored circles.
 * Auto-hides after [AUTO_HIDE_DELAY_MS] inactivity via [LaunchedEffect] + [delay].
 * Tap to reveal. Floats over canvas (no layout shift).
 */
@Composable
public fun DashboardButtonBar(
  isVisible: Boolean,
  isEditMode: Boolean,
  onSettingsClick: () -> Unit,
  onAddWidgetClick: () -> Unit,
  onInteraction: () -> Unit,
  modifier: Modifier = Modifier,
) {
  AnimatedVisibility(
    visible = isVisible,
    enter = DashboardMotion.sheetEnter,
    exit = DashboardMotion.sheetExit,
    modifier = modifier.testTag("bottom_bar"),
  ) {
    val theme = LocalDashboardTheme.current
    val accentColor = theme.accentColor
    val accentContentColor = if (accentColor.luminance() > 0.5f) Color.Black else Color.White

    Box(
      modifier =
        Modifier.fillMaxWidth()
          .height(120.dp)
          .clickable(
            indication = null,
            interactionSource = remember { MutableInteractionSource() },
            onClick = onInteraction,
          ),
    ) {
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .align(Alignment.BottomCenter)
            .padding(
              bottom = DashboardSpacing.SpaceXL,
              start = DashboardSpacing.SpaceL,
              end = DashboardSpacing.SpaceL,
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        // Settings FAB (left)
        FloatingActionButton(
          onClick = {
            onInteraction()
            onSettingsClick()
          },
          modifier =
            Modifier.size(56.dp).testTag("settings_button").semantics {
              contentDescription = "Settings"
            },
          shape = CircleShape,
          containerColor = accentColor,
          contentColor = accentContentColor,
          elevation =
            androidx.compose.material3.FloatingActionButtonDefaults.elevation(
              defaultElevation = 6.dp,
            ),
        ) {
          Icon(
            imageVector = Icons.Filled.Settings,
            contentDescription = "Settings",
          )
        }

        // Add Widget FAB (right, only in edit mode)
        AnimatedVisibility(
          visible = isEditMode,
          enter = DashboardMotion.sheetEnter,
          exit = DashboardMotion.sheetExit,
        ) {
          FloatingActionButton(
            onClick = {
              onInteraction()
              onAddWidgetClick()
            },
            modifier =
              Modifier.size(56.dp).testTag("add_widget_button").semantics {
                contentDescription = "Add widget"
              },
            shape = CircleShape,
            containerColor = accentColor,
            contentColor = accentContentColor,
            elevation =
              androidx.compose.material3.FloatingActionButtonDefaults.elevation(
                defaultElevation = 6.dp,
              ),
          ) {
            Icon(
              imageVector = Icons.Filled.Add,
              contentDescription = "Add widget",
            )
          }
        }
      }
    }
  }
}

/** Auto-hide delay for the bottom bar. */
public const val AUTO_HIDE_DELAY_MS: Long = 3_000L
