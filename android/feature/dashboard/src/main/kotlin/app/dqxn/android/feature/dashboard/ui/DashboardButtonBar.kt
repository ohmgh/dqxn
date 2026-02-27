package app.dqxn.android.feature.dashboard.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.delay

/**
 * Auto-hiding bottom bar floating over the dashboard canvas (F1.9).
 *
 * Contents:
 * - Settings FAB (left)
 * - Edit FAB (view mode) / Add Widget FAB (edit mode) with animated swap (right)
 *
 * Transparent background, 120dp height, buttons at bottom. FABs are 56dp accent-colored circles.
 * Auto-hides after [AUTO_HIDE_DELAY_MS] inactivity via [LaunchedEffect] + [delay].
 * Tap to reveal. Floats over canvas (no layout shift).
 */
@Composable
public fun DashboardButtonBar(
  isEditMode: Boolean,
  isVisible: Boolean,
  onSettingsClick: () -> Unit,
  onAddWidgetClick: () -> Unit,
  onEditModeToggle: () -> Unit,
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

        // Edit FAB (view mode) ↔ Add Widget FAB (edit mode) with animated swap
        AnimatedContent(
          targetState = isEditMode,
          transitionSpec = {
            val enterSpec = spring<Float>(dampingRatio = 0.65f, stiffness = 300f)
            (scaleIn(enterSpec) + fadeIn(enterSpec))
              .togetherWith(scaleOut(enterSpec) + fadeOut(enterSpec))
          },
          label = "edit_add_swap",
        ) { inEditMode ->
          if (inEditMode) {
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
          } else {
            FloatingActionButton(
              onClick = {
                onInteraction()
                onEditModeToggle()
              },
              modifier =
                Modifier.size(56.dp).testTag("edit_mode_toggle").semantics {
                  contentDescription = "Enter edit mode"
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
                imageVector = Icons.Filled.Edit,
                contentDescription = "Enter edit mode",
              )
            }
          }
        }
      }
    }
  }
}

/** Auto-hide delay for the bottom bar. */
public const val AUTO_HIDE_DELAY_MS: Long = 3_000L
