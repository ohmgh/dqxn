package app.dqxn.android.feature.settings.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.SettingsBrightness
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
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
 * Main settings screen with old codebase item order:
 * 1. **About App Banner** -- tagline, attribution, DQXN, version
 * 2. **Dash Packs** -- navigation row with icon and dynamic count subtitle
 * 3. **Theme Items** -- Theme Mode, Light Theme, Dark Theme navigation rows
 * 4. **Show Status Bar** -- toggle with subtitle
 * 5. **Reset Dash** -- red text with subtitle
 * 6. **Advanced** -- Keep Screen On, Analytics, Diagnostics, Delete All Data
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
  lightThemeName: String = "",
  darkThemeName: String = "",
  packCount: Int = 0,
  themeCount: Int = 0,
  widgetCount: Int = 0,
  providerCount: Int = 0,
  autoSwitchModeDescription: String = "",
  versionName: String = "",
  onSetAnalyticsConsent: (Boolean) -> Unit,
  onSetShowStatusBar: (Boolean) -> Unit,
  onSetKeepScreenOn: (Boolean) -> Unit,
  onDeleteAllData: () -> Unit,
  onNavigateToThemeMode: () -> Unit,
  onNavigateToLightTheme: () -> Unit = {},
  onNavigateToDarkTheme: () -> Unit = {},
  onNavigateToDashPacks: () -> Unit,
  onNavigateToDiagnostics: () -> Unit,
  onResetDash: () -> Unit = {},
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
      onBack = onClose,
    ) {
      Column(
        modifier =
          Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .testTag("main_settings_content"),
        verticalArrangement = Arrangement.spacedBy(DashboardSpacing.SectionGap),
      ) {
        // 1. About App Banner
        AboutAppBanner(versionName = versionName, theme = theme)

        SectionDivider(theme = theme)

        // 2. Dash Packs
        SettingsItemRow(
          label = stringResource(R.string.main_settings_dash_packs),
          subtitle =
            "$packCount packs, $themeCount themes, $widgetCount widgets, $providerCount providers",
          icon = Icons.Default.Dashboard,
          accentColor = theme.accentColor,
          theme = theme,
          onClick = onNavigateToDashPacks,
          testTag = "main_settings_dash_packs",
        )

        SectionDivider(theme = theme)

        // 3. Theme Items Group (4dp spacing between)
        Column(verticalArrangement = Arrangement.spacedBy(DashboardSpacing.SpaceXXS)) {
          SettingsItemRow(
            label = stringResource(R.string.main_settings_theme_mode),
            subtitle = autoSwitchModeDescription,
            icon = Icons.Default.SettingsBrightness,
            accentColor = theme.accentColor,
            theme = theme,
              onClick = onNavigateToThemeMode,
            testTag = "main_settings_theme_mode",
          )
          SettingsItemRow(
            label = stringResource(R.string.main_settings_light_theme),
            subtitle = lightThemeName,
            icon = Icons.Default.LightMode,
            accentColor = theme.accentColor,
            theme = theme,
              onClick = onNavigateToLightTheme,
            testTag = "main_settings_light_theme",
          )
          SettingsItemRow(
            label = stringResource(R.string.main_settings_dark_theme),
            subtitle = darkThemeName,
            icon = Icons.Default.DarkMode,
            accentColor = theme.accentColor,
            theme = theme,
              onClick = onNavigateToDarkTheme,
            testTag = "main_settings_dark_theme",
          )
        }

        SectionDivider(theme = theme)

        // 4. Show Status Bar + Keep Screen On
        Column(verticalArrangement = Arrangement.spacedBy(DashboardSpacing.SpaceXXS)) {
          ToggleRow(
            label = stringResource(R.string.main_settings_status_bar),
            subtitle = stringResource(R.string.main_settings_status_bar_subtitle),
            checked = showStatusBar,
            onCheckedChange = onSetShowStatusBar,
            theme = theme,
            testTag = "main_settings_status_bar",
          )
          ToggleRow(
            label = stringResource(R.string.main_settings_keep_screen_on),
            subtitle = stringResource(R.string.main_settings_keep_screen_on_subtitle),
            checked = keepScreenOn,
            onCheckedChange = onSetKeepScreenOn,
            theme = theme,
            testTag = "main_settings_keep_screen_on",
          )
        }

        SectionDivider(theme = theme)

        // 5. Analytics + Diagnostics
        Column(verticalArrangement = Arrangement.spacedBy(DashboardSpacing.SpaceXXS)) {
          AnalyticsToggleRow(
            checked = analyticsConsent,
            onToggle = { enabled ->
              if (enabled) showAnalyticsDialog = true else onSetAnalyticsConsent(false)
            },
            theme = theme,
          )
          NavigationRow(
            label = stringResource(R.string.main_settings_diagnostics),
            subtitle = stringResource(R.string.main_settings_diagnostics_subtitle),
            theme = theme,
            onClick = onNavigateToDiagnostics,
            testTag = "main_settings_diagnostics",
          )
        }

        SectionDivider(theme = theme)

        // 6. Destructive actions
        Column(verticalArrangement = Arrangement.spacedBy(DashboardSpacing.SpaceXXS)) {
          ResetDashRow(theme = theme, onClick = onResetDash)
          DeleteAllDataButton(theme = theme, onClick = { showDeleteDialog = true })
        }

        Spacer(modifier = Modifier.height(DashboardSpacing.SpaceL))
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
private fun AboutAppBanner(
  versionName: String,
  theme: DashboardThemeDefinition,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .height(112.dp)
        .padding(vertical = DashboardSpacing.SpaceS)
        .testTag("about_app_banner"),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.Center,
  ) {
    Image(
      painter = painterResource(id = R.drawable.dqxn_logo_cyber_dog),
      contentDescription = "DQXN Cyber-Dog",
      modifier = Modifier.fillMaxHeight(),
      contentScale = ContentScale.FillHeight,
      alignment = Alignment.CenterStart,
    )

    Spacer(modifier = Modifier.width(DashboardSpacing.SpaceS))

    Column(
      modifier =
        Modifier.fillMaxHeight()
          .padding(top = DashboardSpacing.SpaceM, bottom = DashboardSpacing.SpaceXS),
      verticalArrangement = Arrangement.Center,
    ) {
      Text(
        text = stringResource(R.string.about_app_tagline),
        style =
          DashboardTypography.title.copy(
            fontWeight = FontWeight.Normal,
            fontStyle = FontStyle.Italic,
          ),
        color = theme.accentColor,
      )
      Spacer(modifier = Modifier.height(2.dp))
      Text(
        text = stringResource(R.string.about_app_attribution),
        style = DashboardTypography.buttonLabel,
        color = theme.secondaryTextColor,
      )
      Spacer(modifier = Modifier.height(DashboardSpacing.SpaceXXS))
      Row(verticalAlignment = Alignment.Bottom) {
        Text(
          text = "DQXN",
          style = DashboardTypography.caption,
          color = theme.primaryTextColor,
        )
        Spacer(modifier = Modifier.width(DashboardSpacing.SpaceXXS))
        if (versionName.isNotEmpty()) {
          Text(
            text = versionName,
            style = DashboardTypography.caption,
            color = theme.secondaryTextColor,
          )
        }
      }
    }
  }
}

@Composable
private fun SettingsItemRow(
  label: String,
  subtitle: String,
  icon: ImageVector,
  accentColor: Color,
  theme: DashboardThemeDefinition,
  onClick: () -> Unit,
  testTag: String,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .clickable(onClick = onClick)
        .padding(vertical = DashboardSpacing.SpaceXS)
        .testTag(testTag),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(DashboardSpacing.SpaceM),
  ) {
    // 40dp rounded-square icon box with accentColor.copy(alpha=0.1f) background
    Box(
      modifier =
        Modifier.size(40.dp)
          .clip(RoundedCornerShape(8.dp))
          .background(accentColor.copy(alpha = 0.1f)),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = accentColor,
        modifier = Modifier.size(24.dp)
      )
    }
    Column(modifier = Modifier.weight(1f)) {
      Text(text = label, style = DashboardTypography.itemTitle, color = theme.primaryTextColor)
      Text(
        text = subtitle,
        style = DashboardTypography.description,
        color = theme.primaryTextColor.copy(alpha = TextEmphasis.Medium),
        maxLines = 1,
      )
    }
  }
}

@Composable
private fun ResetDashRow(
  theme: DashboardThemeDefinition,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val shape = RoundedCornerShape(CardSize.SMALL.cornerRadius)

  Box(
    modifier =
      modifier
        .fillMaxWidth()
        .clip(shape)
        .background(SemanticColors.Error.copy(alpha = 0.1f))
        .clickable(onClick = onClick)
        .padding(vertical = DashboardSpacing.SpaceXS)
        .semantics { role = Role.Button }
        .testTag("main_settings_reset_dash"),
    contentAlignment = Alignment.Center,
  ) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Text(
        text = stringResource(R.string.main_settings_reset_dash),
        style = DashboardTypography.primaryButtonLabel,
        color = SemanticColors.Error,
      )
      Text(
        text = stringResource(R.string.main_settings_reset_dash_subtitle),
        style = DashboardTypography.caption,
        color = SemanticColors.Error.copy(alpha = TextEmphasis.Medium),
      )
    }
  }
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
  subtitle: String? = null,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .clickable { onCheckedChange(!checked) }
        .padding(vertical = DashboardSpacing.SpaceXS)
        .testTag(testTag),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = label,
        style = DashboardTypography.itemTitle,
        color = theme.primaryTextColor,
      )
      if (subtitle != null) {
        Text(
          text = subtitle,
          style = DashboardTypography.description,
          color = theme.primaryTextColor.copy(alpha = TextEmphasis.Medium),
        )
      }
    }
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
        .clickable { onToggle(!checked) }
        .padding(vertical = DashboardSpacing.SpaceXS)
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
  subtitle: String? = null,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .clickable(onClick = onClick)
        .padding(vertical = DashboardSpacing.SpaceXS)
        .testTag(testTag),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = label,
        style = DashboardTypography.itemTitle,
        color = theme.primaryTextColor,
      )
      if (subtitle != null) {
        Text(
          text = subtitle,
          style = DashboardTypography.description,
          color = theme.primaryTextColor.copy(alpha = TextEmphasis.Medium),
        )
      }
    }
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
        .clip(shape)
        .background(SemanticColors.Error.copy(alpha = 0.1f))
        .clickable(onClick = onClick)
        .padding(vertical = DashboardSpacing.SpaceXS)
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
