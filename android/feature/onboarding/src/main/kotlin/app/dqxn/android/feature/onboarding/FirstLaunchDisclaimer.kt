package app.dqxn.android.feature.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * First-launch informational disclaimer (NF-D3).
 *
 * Displays a brief notice that speed, navigation, and speed limit data are for informational
 * purposes only. Single "Got it" dismiss button. Shown once during first-run onboarding.
 */
@Composable
public fun FirstLaunchDisclaimer(
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.testTag("first_launch_disclaimer").padding(24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
      text = stringResource(R.string.disclaimer_text),
      style = MaterialTheme.typography.bodyMedium,
    )

    Spacer(modifier = Modifier.weight(1f))

    Button(
      onClick = onDismiss,
      modifier = Modifier.fillMaxWidth().testTag("disclaimer_dismiss"),
    ) {
      Text(text = stringResource(R.string.disclaimer_dismiss))
    }

    Spacer(modifier = Modifier.height(16.dp))
  }
}
