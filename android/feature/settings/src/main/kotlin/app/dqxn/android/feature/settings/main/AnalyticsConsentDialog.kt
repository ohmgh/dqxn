package app.dqxn.android.feature.settings.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.dqxn.android.core.design.motion.DashboardMotion
import app.dqxn.android.core.design.token.CardSize
import app.dqxn.android.core.design.token.DashboardSpacing
import app.dqxn.android.core.design.token.DashboardTypography
import app.dqxn.android.core.design.token.TextEmphasis
import app.dqxn.android.feature.settings.R
import app.dqxn.android.sdk.ui.theme.DashboardThemeDefinition
import app.dqxn.android.sdk.ui.theme.LocalDashboardTheme

/**
 * Analytics consent explanation dialog.
 *
 * Shown before enabling analytics to explain data collected and the user's right to revoke consent
 * at any time (F12.5, PDPA/GDPR compliance). Uses [DashboardMotion.dialogEnter]/[dialogExit]
 * animation.
 */
@Composable
internal fun AnalyticsConsentDialog(
  visible: Boolean,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val theme = LocalDashboardTheme.current

  AnimatedVisibility(
    visible = visible,
    enter = DashboardMotion.dialogScrimEnter,
    exit = DashboardMotion.dialogScrimExit,
  ) {
    Box(
      modifier =
        modifier
          .fillMaxSize()
          .background(theme.primaryTextColor.copy(alpha = 0.4f))
          .clickable(onClick = onDismiss)
          .testTag("analytics_consent_dialog_scrim"),
      contentAlignment = Alignment.Center,
    ) {
      AnimatedVisibility(
        visible = visible,
        enter = DashboardMotion.dialogEnter,
        exit = DashboardMotion.dialogExit,
      ) {
        AnalyticsConsentDialogCard(
          theme = theme,
          onConfirm = onConfirm,
          onDismiss = onDismiss,
        )
      }
    }
  }
}

@Composable
private fun AnalyticsConsentDialogCard(
  theme: DashboardThemeDefinition,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val shape = RoundedCornerShape(CardSize.LARGE.cornerRadius)

  Column(
    modifier =
      modifier
        .fillMaxWidth(0.85f)
        .clip(shape)
        .background(theme.backgroundBrush, shape)
        .padding(DashboardSpacing.SpaceL)
        .testTag("analytics_consent_dialog"),
    verticalArrangement = Arrangement.spacedBy(DashboardSpacing.SectionGap),
  ) {
    Text(
      text = stringResource(R.string.analytics_consent_dialog_title),
      style = DashboardTypography.title,
      color = theme.primaryTextColor,
    )

    Text(
      text = stringResource(R.string.analytics_consent_dialog_body),
      style = DashboardTypography.description,
      color = theme.primaryTextColor.copy(alpha = TextEmphasis.Medium),
      modifier = Modifier.verticalScroll(rememberScrollState()),
    )

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(DashboardSpacing.ButtonGap, Alignment.End),
    ) {
      Box(
        modifier =
          Modifier.sizeIn(minWidth = 76.dp, minHeight = 76.dp)
            .clip(RoundedCornerShape(CardSize.SMALL.cornerRadius))
            .clickable(onClick = onDismiss)
            .testTag("analytics_consent_cancel"),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = stringResource(R.string.analytics_consent_dialog_cancel),
          style = DashboardTypography.buttonLabel,
          color = theme.primaryTextColor.copy(alpha = TextEmphasis.Medium),
        )
      }

      Box(
        modifier =
          Modifier.sizeIn(minWidth = 76.dp, minHeight = 76.dp)
            .clip(RoundedCornerShape(CardSize.SMALL.cornerRadius))
            .background(theme.accentColor.copy(alpha = 0.15f))
            .clickable(onClick = onConfirm)
            .testTag("analytics_consent_confirm"),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = stringResource(R.string.analytics_consent_dialog_confirm),
          style = DashboardTypography.buttonLabel,
          color = theme.accentColor,
          modifier = Modifier.padding(horizontal = DashboardSpacing.SpaceM),
        )
      }
    }
  }
}
