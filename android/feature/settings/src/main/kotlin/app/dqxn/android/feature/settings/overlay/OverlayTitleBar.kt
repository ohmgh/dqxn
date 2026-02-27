package app.dqxn.android.feature.settings.overlay

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.dqxn.android.core.design.token.DashboardSpacing
import app.dqxn.android.core.design.token.DashboardTypography
import app.dqxn.android.sdk.ui.theme.LocalDashboardTheme

/**
 * Title bar for overlay surfaces.
 *
 * Layout: [ArrowBack] [Title ..weight(1f)..] [actions()].
 * Back button on left, meeting 76dp minimum touch target (F10.4).
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
    // Back button (left side)
    Box(
      modifier =
        Modifier.sizeIn(minWidth = 76.dp, minHeight = 76.dp)
          .testTag("overlay_back_button")
          .semantics { role = Role.Button }
          .clickable(onClick = onBack),
      contentAlignment = Alignment.Center,
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
