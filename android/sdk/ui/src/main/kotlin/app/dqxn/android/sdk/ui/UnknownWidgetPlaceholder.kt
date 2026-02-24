package app.dqxn.android.sdk.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Placeholder shown when [WidgetRegistry.findByTypeId] returns null (F2.13).
 *
 * Renders a warning icon and "Unknown widget: {typeId}" text. Used when a widget type has been
 * uninstalled or the pack providing it is unavailable.
 */
@Composable
public fun UnknownWidgetPlaceholder(
  typeId: String,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Icon(
      imageVector = Icons.Filled.Warning,
      contentDescription = "Unknown widget",
      modifier = Modifier.size(32.dp),
      tint = MaterialTheme.colorScheme.error,
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
      text = "Unknown widget: $typeId",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}
