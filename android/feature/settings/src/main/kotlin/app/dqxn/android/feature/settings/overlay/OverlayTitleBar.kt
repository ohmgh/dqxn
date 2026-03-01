package app.dqxn.android.feature.settings.overlay

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import app.dqxn.android.core.design.token.DashboardSpacing
import app.dqxn.android.core.design.token.DashboardTypography
import app.dqxn.android.sdk.ui.theme.LocalDashboardTheme

/**
 * Title bar for overlay surfaces.
 *
 * Layout: [ArrowBack] [Title ..weight(1f)..] [actions()].
 * Back button uses M3 [IconButton] (48dp touch target, matching old codebase).
 * Title uses [DashboardTypography.title] with theme's primaryTextColor.
 * Back arrow tint: secondaryTextColor at 0.6f alpha (old pattern).
 */
@Composable
internal fun OverlayTitleBar(
  title: String,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  actions: @Composable RowScope.() -> Unit = {},
) {
  val theme = LocalDashboardTheme.current

  Row(
    modifier = modifier.fillMaxWidth().padding(vertical = DashboardSpacing.SpaceXXS),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    // Back button (left side) — IconButton provides 48dp touch target (M3 default)
    IconButton(
      onClick = onBack,
      modifier = Modifier.testTag("overlay_back_button"),
    ) {
      Icon(
        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
        contentDescription = "Back",
        tint = theme.secondaryTextColor.copy(alpha = 0.6f),
      )
    }

    // Title (fills remaining space)
    Text(
      text = title,
      style = DashboardTypography.title,
      color = theme.primaryTextColor,
      modifier = Modifier.weight(1f),
    )

    // Trailing actions slot
    actions()
  }
}
