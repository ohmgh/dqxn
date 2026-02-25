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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import app.dqxn.android.core.design.token.SemanticColors
import app.dqxn.android.core.design.token.TextEmphasis
import app.dqxn.android.feature.settings.R
import app.dqxn.android.sdk.ui.theme.DashboardThemeDefinition
import app.dqxn.android.sdk.ui.theme.LocalDashboardTheme

/**
 * Confirmation dialog for Delete All Data action.
 *
 * Uses destructive styling (red confirm button) with [DashboardMotion.dialogEnter]/[dialogExit]
 * animation. Scrim covers the full screen behind the dialog card.
 */
@Composable
internal fun DeleteAllDataDialog(
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
          .testTag("delete_all_data_dialog_scrim"),
      contentAlignment = Alignment.Center,
    ) {
      AnimatedVisibility(
        visible = visible,
        enter = DashboardMotion.dialogEnter,
        exit = DashboardMotion.dialogExit,
      ) {
        DeleteAllDataDialogCard(
          theme = theme,
          onConfirm = onConfirm,
          onDismiss = onDismiss,
        )
      }
    }
  }
}

@Composable
private fun DeleteAllDataDialogCard(
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
        .testTag("delete_all_data_dialog"),
    verticalArrangement = Arrangement.spacedBy(DashboardSpacing.SectionGap),
  ) {
    Text(
      text = stringResource(R.string.delete_all_dialog_title),
      style = DashboardTypography.title,
      color = SemanticColors.Error,
    )

    Text(
      text = stringResource(R.string.delete_all_dialog_body),
      style = DashboardTypography.description,
      color = theme.primaryTextColor.copy(alpha = TextEmphasis.Medium),
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
            .testTag("delete_all_data_cancel"),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = stringResource(R.string.delete_all_dialog_cancel),
          style = DashboardTypography.buttonLabel,
          color = theme.primaryTextColor.copy(alpha = TextEmphasis.Medium),
        )
      }

      Box(
        modifier =
          Modifier.sizeIn(minWidth = 76.dp, minHeight = 76.dp)
            .clip(RoundedCornerShape(CardSize.SMALL.cornerRadius))
            .background(SemanticColors.Error.copy(alpha = 0.15f))
            .clickable(onClick = onConfirm)
            .testTag("delete_all_data_confirm"),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = stringResource(R.string.delete_all_dialog_confirm),
          style = DashboardTypography.buttonLabel,
          color = SemanticColors.Error,
          modifier = Modifier.padding(horizontal = DashboardSpacing.SpaceM),
        )
      }
    }
  }
}
