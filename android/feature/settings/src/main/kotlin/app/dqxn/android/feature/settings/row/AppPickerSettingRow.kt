package app.dqxn.android.feature.settings.row

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.dqxn.android.core.design.token.DashboardTypography
import app.dqxn.android.core.design.token.TextEmphasis
import app.dqxn.android.feature.settings.SettingNavigation
import app.dqxn.android.sdk.contracts.settings.SettingDefinition
import app.dqxn.android.sdk.ui.theme.DashboardThemeDefinition

/**
 * App picker setting row that shows the current app name resolved from package name.
 *
 * Package name -> app name resolution via [PackageManager.getApplicationLabel] with fallback to
 * package name. Taps fire [SettingNavigation.ToAppPicker]. 76dp row height per F10.4.
 */
@Composable
internal fun AppPickerSettingRow(
  definition: SettingDefinition.AppPickerSetting,
  currentValue: String?,
  theme: DashboardThemeDefinition,
  onNavigate: ((SettingNavigation) -> Unit)?,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val appName =
    if (currentValue != null) {
      resolveAppName(context, currentValue)
    } else {
      "None selected"
    }

  Row(
    modifier =
      modifier.fillMaxWidth().defaultMinSize(minHeight = 76.dp).clickable {
        onNavigate?.invoke(
          SettingNavigation.ToAppPicker(
            settingKey = definition.key,
            currentPackage = currentValue,
          )
        )
      },
    verticalAlignment = Alignment.CenterVertically,
  ) {
    SettingLabel(
      label = definition.label,
      description = definition.description,
      theme = theme,
      modifier = Modifier.weight(1f),
    )
    Text(
      text = appName,
      style = DashboardTypography.description,
      color = theme.primaryTextColor.copy(alpha = TextEmphasis.Medium),
      modifier = Modifier.padding(end = 4.dp),
    )
    Icon(
      imageVector = Icons.Filled.ChevronRight,
      contentDescription = "Select app",
      tint = theme.secondaryTextColor,
    )
  }
}

/**
 * Resolves a package name to its human-readable app name via [PackageManager.getApplicationLabel].
 *
 * Falls back to the raw package name if resolution fails.
 */
private fun resolveAppName(context: android.content.Context, packageName: String): String =
  try {
    val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
    context.packageManager.getApplicationLabel(appInfo).toString()
  } catch (_: Exception) {
    packageName
  }
