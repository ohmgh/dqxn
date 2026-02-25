package app.dqxn.android.feature.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.dqxn.android.core.design.motion.DashboardMotion
import kotlinx.coroutines.launch

/**
 * Reusable progressive tip composable.
 *
 * Observes [tipManager].[ProgressiveTipManager.shouldShowTip] flow for the given [tipKey]. Shows a
 * dismissable card with the [message] text. Dismiss persists via [ProgressiveTipManager.dismissTip].
 *
 * Uses [DashboardMotion.expandEnter]/[DashboardMotion.expandExit] for enter/exit animations.
 */
@Composable
public fun ProgressiveTip(
  tipKey: String,
  message: String,
  tipManager: ProgressiveTipManager,
  modifier: Modifier = Modifier,
) {
  val shouldShow by tipManager.shouldShowTip(tipKey).collectAsState(initial = false)
  val scope = rememberCoroutineScope()

  AnimatedVisibility(
    visible = shouldShow,
    enter = DashboardMotion.expandEnter,
    exit = DashboardMotion.expandExit,
    modifier = modifier.testTag("tip_$tipKey"),
  ) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
      Row(
        modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = message,
          style = MaterialTheme.typography.bodySmall,
          modifier = Modifier.weight(1f),
        )

        Spacer(modifier = Modifier.width(8.dp))

        TextButton(
          onClick = { scope.launch { tipManager.dismissTip(tipKey) } },
          modifier = Modifier.testTag("tip_dismiss_$tipKey"),
        ) {
          Text(text = stringResource(R.string.tip_dismiss))
        }
      }
    }
  }
}
