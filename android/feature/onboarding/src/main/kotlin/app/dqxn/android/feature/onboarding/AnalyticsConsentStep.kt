package app.dqxn.android.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * Analytics opt-IN consent step (F12.5).
 *
 * Explains data collected, purpose, and revocation rights. Two buttons:
 * - "Enable Analytics" (primary accent) calls [onConsent] with `true`
 * - "Skip" (outlined secondary) calls [onConsent] with `false`
 *
 * Both advance to the next onboarding step.
 */
@Composable
public fun AnalyticsConsentStep(
  onConsent: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.testTag("analytics_consent_step").padding(24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
      text = stringResource(R.string.consent_title),
      style = MaterialTheme.typography.headlineMedium,
    )

    Spacer(modifier = Modifier.height(16.dp))

    Text(
      text = stringResource(R.string.consent_body),
      style = MaterialTheme.typography.bodyMedium,
    )

    Spacer(modifier = Modifier.weight(1f))

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
    ) {
      OutlinedButton(
        onClick = { onConsent(false) },
        modifier = Modifier.testTag("consent_skip"),
      ) {
        Text(text = stringResource(R.string.consent_skip))
      }

      Button(
        onClick = { onConsent(true) },
        modifier = Modifier.testTag("consent_enable"),
      ) {
        Text(text = stringResource(R.string.consent_enable))
      }
    }
  }
}
