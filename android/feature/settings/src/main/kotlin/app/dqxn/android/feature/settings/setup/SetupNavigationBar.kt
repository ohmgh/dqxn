package app.dqxn.android.feature.settings.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.dqxn.android.core.design.token.DashboardSpacing
import app.dqxn.android.core.design.token.DashboardTypography
import app.dqxn.android.core.design.token.TextEmphasis
import app.dqxn.android.sdk.ui.theme.LocalDashboardTheme

/**
 * Bottom navigation bar for the setup wizard flow.
 *
 * Contains:
 * - Back button (hidden on page 0)
 * - Page indicator dots
 * - Next/Done button with alpha-dimming when gated (NOT disabled per Pitfall 6)
 *
 * All buttons meet the 76dp minimum touch target (F10.4).
 */
@Composable
internal fun SetupNavigationBar(
  currentPage: Int,
  totalPages: Int,
  isPageSatisfied: Boolean,
  onBack: () -> Unit,
  onNext: () -> Unit,
  onDone: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val theme = LocalDashboardTheme.current
  val isLastPage = currentPage >= totalPages - 1

  Row(
    modifier = modifier
      .fillMaxWidth()
      .padding(vertical = DashboardSpacing.SpaceS),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    // Back button -- invisible (but space-holding) on page 0
    Box(
      modifier = Modifier
        .sizeIn(minWidth = 76.dp, minHeight = 76.dp)
        .testTag("setup_back_button")
        .alpha(if (currentPage > 0) 1f else 0f)
        .then(
          if (currentPage > 0) {
            Modifier
              .semantics { role = Role.Button }
              .clickable(onClick = onBack)
          } else {
            Modifier
          },
        ),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
        contentDescription = "Back",
        tint = theme.primaryTextColor.copy(alpha = TextEmphasis.Medium),
      )
    }

    // Page indicator dots
    Row(
      horizontalArrangement = Arrangement.spacedBy(DashboardSpacing.SpaceXS),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      repeat(totalPages) { index ->
        Box(
          modifier = Modifier
            .testTag("setup_page_dot_$index")
            .size(8.dp)
            .background(
              color = if (index == currentPage) {
                theme.accentColor
              } else {
                theme.secondaryTextColor.copy(alpha = TextEmphasis.Disabled)
              },
              shape = CircleShape,
            ),
        )
      }
    }

    // Next / Done button
    if (isLastPage) {
      Box(
        modifier = Modifier
          .sizeIn(minWidth = 76.dp, minHeight = 76.dp)
          .testTag("setup_done_button")
          .alpha(if (isPageSatisfied) 1f else 0.5f)
          .semantics { role = Role.Button }
          .clickable(onClick = onDone),
        contentAlignment = Alignment.Center,
      ) {
        Row(
          horizontalArrangement = Arrangement.spacedBy(DashboardSpacing.SpaceXXS),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "Done",
            style = DashboardTypography.label,
            color = theme.accentColor,
          )
          Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = "Done",
            tint = theme.accentColor,
          )
        }
      }
    } else {
      Box(
        modifier = Modifier
          .sizeIn(minWidth = 76.dp, minHeight = 76.dp)
          .testTag("setup_next_button")
          .alpha(if (isPageSatisfied) 1f else 0.5f)
          .semantics { role = Role.Button }
          .clickable(onClick = onNext),
        contentAlignment = Alignment.Center,
      ) {
        Row(
          horizontalArrangement = Arrangement.spacedBy(DashboardSpacing.SpaceXXS),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "Next",
            style = DashboardTypography.label,
            color = theme.accentColor,
          )
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = "Next",
            tint = theme.accentColor,
          )
        }
      }
    }
  }
}
