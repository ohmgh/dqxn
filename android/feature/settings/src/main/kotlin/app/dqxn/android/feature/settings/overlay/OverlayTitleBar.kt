package app.dqxn.android.feature.settings.overlay

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import app.dqxn.android.core.design.token.DashboardTypography
import app.dqxn.android.core.design.token.TextEmphasis
import app.dqxn.android.sdk.ui.theme.LocalDashboardTheme

/**
 * Title bar for overlay surfaces.
 *
 * Row layout with title on the left and a close button on the right. Close button meets the 76dp
 * minimum touch target requirement (F10.4). Title uses [DashboardTypography.title] with the theme's
 * [primaryTextColor][app.dqxn.android.sdk.ui.theme.DashboardThemeDefinition.primaryTextColor].
 * Close icon uses [TextEmphasis.Medium] (0.7f) alpha -- NOT inline 0.6f per replication advisory.
 */
@Composable
internal fun OverlayTitleBar(
  title: String,
  onClose: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val theme = LocalDashboardTheme.current

  Row(
    modifier = modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = title,
      style = DashboardTypography.title,
      color = theme.primaryTextColor,
    )

    Box(
      modifier =
        Modifier.sizeIn(minWidth = 76.dp, minHeight = 76.dp)
          .testTag("overlay_close_button")
          .semantics { role = Role.Button }
          .clickable(onClick = onClose),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = Icons.Filled.Close,
        contentDescription = "Close",
        tint = theme.primaryTextColor.copy(alpha = TextEmphasis.Medium),
      )
    }
  }
}
