package app.dqxn.android.feature.dashboard.grid

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.dqxn.android.sdk.ui.theme.LocalDashboardTheme

/**
 * Overlay toolbar rendered above a focused widget in edit mode (F1.8).
 *
 * Contains delete and settings action buttons with press-scale animation:
 * 0.85f spring(DampingRatioMediumBouncy, StiffnessMedium) on press.
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
            icon = Icons.Filled.Close,
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

    FilledIconButton(
        onClick = onClick,
        modifier = Modifier
            .size(40.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .testTag(testTag)
            .semantics { contentDescription = description },
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = theme.primaryTextColor.copy(alpha = 0.15f),
            contentColor = theme.primaryTextColor,
        ),
        interactionSource = interactionSource,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            modifier = Modifier.size(20.dp),
        )
    }
}
