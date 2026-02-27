package app.dqxn.android.feature.settings.row

import android.media.RingtoneManager
import android.net.Uri
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.dqxn.android.core.design.token.DashboardTypography
import app.dqxn.android.core.design.token.TextEmphasis
import app.dqxn.android.sdk.contracts.settings.SettingDefinition
import app.dqxn.android.sdk.ui.theme.DashboardThemeDefinition

/**
 * Sound picker setting row that shows the current ringtone name.
 *
 * Ringtone name resolved via [RingtoneManager.getRingtone] with fallback to "Default". The action
 * button fires [onSoundPickerRequested] which the parent composable connects to an
 * [ActivityResultLauncher] for the system `ACTION_RINGTONE_PICKER`.
 */
@Composable
internal fun SoundPickerSettingRow(
  definition: SettingDefinition.SoundPickerSetting,
  currentValue: String?,
  theme: DashboardThemeDefinition,
  onSoundPickerRequested: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val ringtoneName = resolveRingtoneName(context, currentValue)

  Row(
    modifier = modifier.fillMaxWidth().defaultMinSize(minHeight = 76.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    SettingLabel(
      label = definition.label,
      description = definition.description,
      theme = theme,
      modifier = Modifier.weight(1f),
    )
    Text(
      text = ringtoneName,
      style = DashboardTypography.description,
      color = theme.primaryTextColor.copy(alpha = TextEmphasis.Medium),
      modifier = Modifier.padding(end = 8.dp),
    )
    Button(
      onClick = { onSoundPickerRequested(definition.key) },
      colors =
        ButtonDefaults.buttonColors(
          containerColor = theme.accentColor,
          contentColor = Color.White,
        ),
      modifier = Modifier.defaultMinSize(minHeight = 48.dp),
    ) {
      Text(text = "Change", style = DashboardTypography.buttonLabel)
    }
  }
}

/**
 * Resolves a ringtone URI string to its display title.
 *
 * Falls back to "Default" if the URI is null or resolution fails.
 */
private fun resolveRingtoneName(context: android.content.Context, uriString: String?): String =
  try {
    if (uriString != null) {
      val uri = Uri.parse(uriString)
      val ringtone = RingtoneManager.getRingtone(context, uri)
      ringtone?.getTitle(context) ?: "Default"
    } else {
      "Default"
    }
  } catch (_: Exception) {
    "Default"
  }
