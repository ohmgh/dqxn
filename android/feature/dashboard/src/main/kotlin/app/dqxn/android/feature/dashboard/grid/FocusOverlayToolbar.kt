package app.dqxn.android.feature.dashboard.grid

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.dqxn.android.sdk.ui.theme.LocalDashboardTheme

/**
 * Overlay toolbar rendered above a focused widget in edit mode (F1.8).
 *
 * Contains delete and settings action buttons as accent-colored FABs with press-scale animation:
 * 0.85f spring(DampingRatioMediumBouncy, StiffnessMedium) on press.
 *
 * Each button: 56dp touch target containing 40dp visual circle, accent container color,
 * luminance-computed B/W content color, 6dp elevation.
 *
 * Positioned above the widget by the caller (DashboardGrid), not self-positioning.
 * Must have the highest z-index in the grid to avoid clipping behind adjacent widgets.
 *
 * Per F1.8: no translate or scale on the widget itself. Only the toolbar animates.
 */
@Composable
internal fun FocusOverlayToolbar(
    widgetId: String,
    onDelete: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .testTag("focus_toolbar_$widgetId"),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Delete button
        ActionButton(
            icon = Icons.Filled.Delete,
            description = "Delete widget",
            testTag = "focus_delete_$widgetId",
            onClick = onDelete,
        )

        // Settings button
        ActionButton(
            icon = Icons.Filled.Settings,
            description = "Widget settings",
            testTag = "focus_settings_$widgetId",
            onClick = onSettings,
        )
    }
}

@Composable
private fun ActionButton(
    icon: ImageVector,
    description: String,
    testTag: String,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "action_press_scale",
    )

    val theme = LocalDashboardTheme.current
    val accentColor = theme.accentColor
    val contentColor = if (accentColor.luminance() > 0.5f) Color.Black else Color.White

    // 56dp touch target wrapping a 40dp visual FAB
    Box(
        modifier = Modifier
            .size(56.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .testTag(testTag)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        FloatingActionButton(
            onClick = onClick,
            modifier = Modifier.size(40.dp),
            shape = CircleShape,
            containerColor = accentColor,
            contentColor = contentColor,
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 6.dp,
            ),
            interactionSource = interactionSource,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
