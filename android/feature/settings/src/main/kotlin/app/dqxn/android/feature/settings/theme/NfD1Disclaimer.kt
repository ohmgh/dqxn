package app.dqxn.android.feature.settings.theme

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.dqxn.android.feature.settings.R

/**
 * Speed disclaimer composable for NF-D1 compliance.
 *
 * Displays the speed disclaimer text in Info style. Reusable from WidgetInfoContent for
 * speed-related widgets (speedometer, speed-limit-circle, speed-limit-rect).
 *
 * NF-D1: "Speed readings are estimates derived from GPS or device sensors. Do not rely on this
 * display for legal speed compliance or safety-critical decisions. Always refer to your vehicle's
 * calibrated speedometer."
 */
@Composable
public fun NfD1Disclaimer(
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier.fillMaxWidth().padding(vertical = 8.dp).testTag("nf_d1_disclaimer"),
  ) {
    Text(
      text = "\u26A0",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.width(8.dp))
    Text(
      text = stringResource(R.string.widget_info_speed_disclaimer),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.weight(1f),
    )
  }
}
