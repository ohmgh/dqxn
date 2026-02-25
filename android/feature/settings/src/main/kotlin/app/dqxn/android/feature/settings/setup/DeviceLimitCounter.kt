package app.dqxn.android.feature.settings.setup

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import app.dqxn.android.core.design.token.DashboardTypography
import app.dqxn.android.core.design.token.TextEmphasis
import app.dqxn.android.sdk.ui.theme.LocalDashboardTheme

/**
 * "N/M devices" text counter, only visible when [pairedCount] >= 2.
 */
@Composable
internal fun DeviceLimitCounter(
  pairedCount: Int,
  maxDevices: Int,
) {
  if (pairedCount < 2) return

  val theme = LocalDashboardTheme.current

  Text(
    text = "$pairedCount/$maxDevices devices",
    style = DashboardTypography.caption,
    color = theme.primaryTextColor.copy(alpha = TextEmphasis.Medium),
    textAlign = TextAlign.End,
    modifier = Modifier.fillMaxWidth(),
  )
}
