package app.dqxn.android.feature.settings.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.dqxn.android.core.design.token.CardSize
import app.dqxn.android.core.design.token.DashboardSpacing
import app.dqxn.android.core.design.token.DashboardTypography
import app.dqxn.android.core.design.token.SemanticColors
import app.dqxn.android.core.design.token.TextEmphasis
import app.dqxn.android.feature.settings.R
import app.dqxn.android.feature.settings.overlay.OverlayScaffold
import app.dqxn.android.feature.settings.overlay.OverlayType
import app.dqxn.android.sdk.ui.theme.DashboardThemeDefinition
import app.dqxn.android.sdk.ui.theme.LocalDashboardTheme

/**
 * Main settings screen with 4 sections:
 * 1. **Appearance** - Theme mode selection, status bar toggle
 * 2. **Behavior** - Orientation lock, keep screen on, Dash Packs navigation
 * 3. **Data & Privacy** - Analytics consent toggle, diagnostics navigation
 * 4. **Danger Zone** - Delete All Data button
 *
 * Wraps content in [OverlayScaffold] with [OverlayType.Hub]. All interactive elements meet 76dp
 * minimum touch target (F10.4). Analytics consent shows [AnalyticsConsentDialog] before enabling
 * (F12.5). Delete All Data shows [DeleteAllDataDialog] confirmation (F14.4).
 */
@Composable
public fun MainSettings(
  analyticsConsent: Boolean,
  showStatusBar: Boolean,
  keepScreenOn: Boolean,
  onSetAnalyticsConsent: (Boolean) -> Unit,
  onSetShowStatusBar: (Boolean) -> Unit,
  onSetKeepScreenOn: (Boolean) -> Unit,
  onDeleteAllData: () -> Unit,
  onNavigateToThemeMode: () -> Unit,
  onNavigateToDashPacks: () -> Unit,
  onNavigateToDiagnostics: () -> Unit,
  onClose: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val theme = LocalDashboardTheme.current

  var showDeleteDialog by remember { mutableStateOf(false) }
  var showAnalyticsDialog by remember { mutableStateOf(false) }

  Box(modifier = modifier.fillMaxSize()) {
    OverlayScaffold(
      title = stringResource(R.string.main_settings_title),
      overlayType = OverlayType.Hub,
      onClose = onClose,
    ) {
      Column(
        modifier =
          Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .testTag("main_settings_content"),
        verticalArrangement = Arrangement.spacedBy(DashboardSpacing.SectionGap),
      ) {
        // Section 1: Appearance
        SectionHeader(
          title = stringResource(R.string.main_settings_section_appearance),
          theme = theme,
        )
        NavigationRow(
          label = stringResource(R.string.main_settings_theme_mode),
          theme = theme,
          onClick = onNavigateToThemeMode,
          testTag = "main_settings_theme_mode",
        )
        ToggleRow(
          label = stringResource(R.string.main_settings_status_bar),
          checked = showStatusBar,
          onCheckedChange = onSetShowStatusBar,
          theme = theme,
          testTag = "main_settings_status_bar",
        )

        SectionDivider(theme = theme)

        // Section 2: Behavior
        SectionHeader(
          title = stringResource(R.string.main_settings_section_behavior),
          theme = theme,
        )
        ToggleRow(
          label = stringResource(R.string.main_settings_keep_screen_on),
          checked = keepScreenOn,
          onCheckedChange = onSetKeepScreenOn,
          theme = theme,
          testTag = "main_settings_keep_screen_on",
        )
        NavigationRow(
          label = stringResource(R.string.main_settings_dash_packs),
          theme = theme,
          onClick = onNavigateToDashPacks,
          testTag = "main_settings_dash_packs",
        )

        SectionDivider(theme = theme)

        // Section 3: Data & Privacy
        SectionHeader(
          title = stringResource(R.string.main_settings_section_data_privacy),
          theme = theme,
        )
        AnalyticsToggleRow(
          checked = analyticsConsent,
          onToggle = { enabled ->
            if (enabled) {
              showAnalyticsDialog = true
            } else {
              onSetAnalyticsConsent(false)
            }
          },
          theme = theme,
        )
        NavigationRow(
          label = stringResource(R.string.main_settings_diagnostics),
          theme = theme,
          onClick = onNavigateToDiagnostics,
          testTag = "main_settings_diagnostics",
        )

        SectionDivider(theme = theme)

        // Section 4: Danger Zone
        SectionHeader(
          title = stringResource(R.string.main_settings_section_danger_zone),
          theme = theme,
        )
        DeleteAllDataButton(
          theme = theme,
          onClick = { showDeleteDialog = true },
        )

        Spacer(modifier = Modifier.height(DashboardSpacing.SpaceXL))
      }
    }

    // Dialogs rendered above overlay scaffold
    DeleteAllDataDialog(
      visible = showDeleteDialog,
      onConfirm = {
        showDeleteDialog = false
        onDeleteAllData()
      },
      onDismiss = { showDeleteDialog = false },
    )

    AnalyticsConsentDialog(
      visible = showAnalyticsDialog,
      onConfirm = {
        showAnalyticsDialog = false
        onSetAnalyticsConsent(true)
      },
      onDismiss = { showAnalyticsDialog = false },
    )
  }
}

@Composable
private fun SectionHeader(
  title: String,
  theme: DashboardThemeDefinition,
  modifier: Modifier = Modifier,
) {
  Text(
    text = title.uppercase(),
    style = DashboardTypography.sectionHeader,
    color = theme.primaryTextColor.copy(alpha = TextEmphasis.Medium),
    modifier = modifier.testTag("section_header_$title"),
  )
}

@Composable
private fun SectionDivider(
  theme: DashboardThemeDefinition,
  modifier: Modifier = Modifier,
) {
  HorizontalDivider(
    color = theme.widgetBorderColor.copy(alpha = TextEmphasis.Disabled),
    modifier = modifier,
  )
}

@Composable
private fun ToggleRow(
  label: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  theme: DashboardThemeDefinition,
  testTag: String,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .sizeIn(minHeight = 76.dp)
        .clickable { onCheckedChange(!checked) }
        .testTag(testTag),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = label,
      style = DashboardTypography.itemTitle,
      color = theme.primaryTextColor,
    )
    Switch(
      checked = checked,
      onCheckedChange = onCheckedChange,
      colors =
        SwitchDefaults.colors(
          checkedThumbColor = theme.accentColor,
          checkedTrackColor = theme.accentColor.copy(alpha = 0.3f),
          uncheckedThumbColor = theme.secondaryTextColor,
          uncheckedTrackColor = theme.secondaryTextColor.copy(alpha = 0.1f),
        ),
    )
  }
}

@Composable
private fun AnalyticsToggleRow(
  checked: Boolean,
  onToggle: (Boolean) -> Unit,
  theme: DashboardThemeDefinition,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .sizeIn(minHeight = 76.dp)
        .clickable { onToggle(!checked) }
        .testTag("main_settings_analytics"),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = stringResource(R.string.main_settings_analytics_consent),
        style = DashboardTypography.itemTitle,
        color = theme.primaryTextColor,
      )
      Text(
        text = stringResource(R.string.main_settings_analytics_description),
        style = DashboardTypography.description,
        color = theme.primaryTextColor.copy(alpha = TextEmphasis.Medium),
      )
    }
    Switch(
      checked = checked,
      onCheckedChange = { onToggle(it) },
      colors =
        SwitchDefaults.colors(
          checkedThumbColor = theme.accentColor,
          checkedTrackColor = theme.accentColor.copy(alpha = 0.3f),
          uncheckedThumbColor = theme.secondaryTextColor,
          uncheckedTrackColor = theme.secondaryTextColor.copy(alpha = 0.1f),
        ),
    )
  }
}

@Composable
private fun NavigationRow(
  label: String,
  theme: DashboardThemeDefinition,
  onClick: () -> Unit,
  testTag: String,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .sizeIn(minHeight = 76.dp)
        .clickable(onClick = onClick)
        .testTag(testTag),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = label,
      style = DashboardTypography.itemTitle,
      color = theme.primaryTextColor,
    )
    Icon(
      imageVector = Icons.Filled.ChevronRight,
      contentDescription = null,
      tint = theme.primaryTextColor.copy(alpha = TextEmphasis.Medium),
    )
  }
}

@Composable
private fun DeleteAllDataButton(
  theme: DashboardThemeDefinition,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val shape = RoundedCornerShape(CardSize.SMALL.cornerRadius)

  Box(
    modifier =
      modifier
        .fillMaxWidth()
        .sizeIn(minHeight = 76.dp)
        .clip(shape)
        .background(SemanticColors.Error.copy(alpha = 0.1f))
        .clickable(onClick = onClick)
        .semantics { role = Role.Button }
        .testTag("main_settings_delete_all"),
    contentAlignment = Alignment.Center,
  ) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Text(
        text = stringResource(R.string.main_settings_delete_all_data),
        style = DashboardTypography.primaryButtonLabel,
        color = SemanticColors.Error,
      )
      Text(
        text = stringResource(R.string.main_settings_delete_all_description),
        style = DashboardTypography.caption,
        color = SemanticColors.Error.copy(alpha = TextEmphasis.Medium),
      )
    }
  }
}
