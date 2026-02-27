package app.dqxn.android.feature.dashboard.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.dqxn.android.core.design.motion.DashboardMotion
import app.dqxn.android.feature.dashboard.coordinator.ProfileInfo
import app.dqxn.android.sdk.ui.theme.LocalDashboardTheme
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.delay

/**
 * Auto-hiding bottom bar floating over the dashboard canvas (F1.9).
 *
 * Contents:
 * - Settings button (always)
 * - Profile icons (when 2+ profiles, active highlighted)
 * - Add Widget button (edit mode only)
 * - Edit mode toggle
 * - Quick theme toggle (F10.9 -- cycles theme mode)
 *
 * 76dp touch targets per F10.4. Auto-hides after [AUTO_HIDE_DELAY_MS] inactivity via
 * [LaunchedEffect] + [delay]. Tap to reveal. Floats over canvas (no layout shift).
 */
@Composable
public fun DashboardButtonBar(
  isEditMode: Boolean,
  profiles: ImmutableList<ProfileInfo>,
  activeProfileId: String,
  isVisible: Boolean,
  onSettingsClick: () -> Unit,
  onProfileClick: (String) -> Unit,
  onAddWidgetClick: () -> Unit,
  onEditModeToggle: () -> Unit,
  onThemeToggle: () -> Unit,
  onInteraction: () -> Unit,
  modifier: Modifier = Modifier,
) {
  AnimatedVisibility(
    visible = isVisible,
    enter = DashboardMotion.sheetEnter,
    exit = DashboardMotion.sheetExit,
    modifier = modifier.testTag("bottom_bar"),
  ) {
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .height(76.dp)
          .background(
            MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f),
            RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
          )
          .clickable(
            indication = null,
            interactionSource = remember { MutableInteractionSource() },
            onClick = onInteraction,
          ),
      horizontalArrangement = Arrangement.SpaceEvenly,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      // Settings button (accent-colored FAB)
      val theme = LocalDashboardTheme.current
      val accentColor = theme.accentColor
      val accentContentColor =
        if (accentColor.luminance() > 0.5f) Color.Black else Color.White

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
      ) {
        Icon(
          imageVector = Icons.Filled.Settings,
          contentDescription = "Settings",
        )
      }

      // Profile icons (when 2+ profiles)
      if (profiles.size >= 2) {
        for (profile in profiles) {
          val isActive = profile.id == activeProfileId
          IconButton(
            onClick = {
              onInteraction()
              onProfileClick(profile.id)
            },
            modifier =
              Modifier.size(76.dp).testTag("profile_${profile.id}").semantics {
                contentDescription = "Profile: ${profile.displayName}"
              },
          ) {
            Box(
              modifier =
                Modifier.size(if (isActive) 36.dp else 28.dp)
                  .clip(CircleShape)
                  .background(
                    if (isActive) {
                      MaterialTheme.colorScheme.primary
                    } else {
                      MaterialTheme.colorScheme.surfaceVariant
                    },
                  ),
              contentAlignment = Alignment.Center,
            ) {
              val initial = profile.displayName.firstOrNull()?.uppercase() ?: "?"
              androidx.compose.material3.Text(
                text = initial,
                style = MaterialTheme.typography.labelSmall,
                color =
                  if (isActive) {
                    MaterialTheme.colorScheme.onPrimary
                  } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                  },
              )
            }
          }
        }
      }

      // Edit mode toggle
      IconButton(
        onClick = {
          onInteraction()
          onEditModeToggle()
        },
        modifier =
          Modifier.size(76.dp).testTag("edit_mode_toggle").semantics {
            contentDescription = if (isEditMode) "Exit edit mode" else "Enter edit mode"
          },
      ) {
        Icon(
          imageVector = Icons.Filled.Edit,
          contentDescription = if (isEditMode) "Exit edit mode" else "Enter edit mode",
          tint =
            if (isEditMode) {
              MaterialTheme.colorScheme.primary
            } else {
              MaterialTheme.colorScheme.onSurface
            },
        )
      }

      // Add Widget button (edit mode only)
      if (isEditMode) {
        IconButton(
          onClick = {
            onInteraction()
            onAddWidgetClick()
          },
          modifier =
            Modifier.size(76.dp).testTag("add_widget_button").semantics {
              contentDescription = "Add widget"
            },
        ) {
          Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = "Add widget",
            tint = MaterialTheme.colorScheme.primary,
          )
        }
      }

      // Quick theme toggle (F10.9)
      IconButton(
        onClick = {
          onInteraction()
          onThemeToggle()
        },
        modifier = Modifier.size(76.dp).semantics { contentDescription = "Cycle theme mode" },
      ) {
        Icon(
          imageVector = Icons.Filled.LightMode,
          contentDescription = "Cycle theme mode",
          tint = MaterialTheme.colorScheme.onSurface,
        )
      }
    }
  }
}

/** Auto-hide delay for the bottom bar. */
public const val AUTO_HIDE_DELAY_MS: Long = 3_000L
