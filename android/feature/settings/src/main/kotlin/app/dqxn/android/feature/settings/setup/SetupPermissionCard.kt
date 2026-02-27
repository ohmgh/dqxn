package app.dqxn.android.feature.settings.setup

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.dqxn.android.core.design.token.CardSize
import app.dqxn.android.core.design.token.DashboardSpacing
import app.dqxn.android.core.design.token.DashboardTypography
import app.dqxn.android.core.design.token.SemanticColors
import app.dqxn.android.core.design.token.TextEmphasis
import app.dqxn.android.sdk.contracts.setup.SetupDefinition
import app.dqxn.android.sdk.ui.theme.LocalDashboardTheme

/**
 * Permission card with 3 states:
 * 1. **Granted**: Accent-tinted "Granted" label with checkmark, no button.
 * 2. **Can request**: "Grant" button triggers [RequestMultiplePermissions] via native API.
 * 3. **Permanently denied**: "Open Settings" button opens app settings via intent.
 *
 * Critical: [hasRequestedPermissions] local state guards against false permanent-denial detection
 * (Pitfall 2 from replication advisory). Before any permission request, the pre-request state is
 * indistinguishable from permanent denial without this guard.
 */
@Composable
internal fun SetupPermissionCard(
  definition: SetupDefinition.RuntimePermission,
  satisfied: Boolean,
  onPermissionRequest: () -> Unit,
  onOpenSettings: () -> Unit,
) {
  val theme = LocalDashboardTheme.current
  val context = LocalContext.current

  // Guard against false permanent-denial detection (Pitfall 2)
  var hasRequestedPermissions by remember { mutableStateOf(false) }

  val permissionLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
      hasRequestedPermissions = true
      // Parent recomposition will update `satisfied` via re-evaluation
      onPermissionRequest()
    }

  Card(
    shape = RoundedCornerShape(CardSize.MEDIUM.cornerRadius),
    colors =
      CardDefaults.cardColors(
        containerColor =
          if (satisfied) {
            SemanticColors.Success.copy(alpha = 0.1f)
          } else {
            theme.widgetBorderColor.copy(alpha = 0.05f)
          },
      ),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.fillMaxWidth().padding(DashboardSpacing.CardInternalPadding),
    ) {
      // Icon
      Icon(
        imageVector = if (satisfied) Icons.Filled.CheckCircle else Icons.Filled.Lock,
        contentDescription = if (satisfied) "Granted" else "Required",
        tint = if (satisfied) SemanticColors.Success else theme.secondaryTextColor,
        modifier = Modifier.size(24.dp),
      )

      // Label + description
      Column(
        modifier = Modifier.weight(1f).padding(start = DashboardSpacing.IconTextGap),
      ) {
        Text(
          text = definition.label,
          style = DashboardTypography.itemTitle,
          color = theme.primaryTextColor,
        )
        definition.description?.let { desc ->
          Text(
            text = desc,
            style = DashboardTypography.description,
            color = theme.primaryTextColor.copy(alpha = TextEmphasis.Medium),
            modifier = Modifier.padding(top = DashboardSpacing.SpaceXXS),
          )
        }
      }

      // State-dependent action
      when {
        // State 1: All granted
        satisfied -> {
          Text(
            text = "Granted",
            style = DashboardTypography.buttonLabel,
            color = SemanticColors.Success,
          )
        }

        // State 3: Permanently denied (only after we've actually requested)
        hasRequestedPermissions && !satisfied -> {
          Button(
            onClick = {
              val intent =
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                  data = Uri.fromParts("package", context.packageName, null)
                  addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
              context.startActivity(intent)
              onOpenSettings()
            },
            colors =
              ButtonDefaults.buttonColors(
                containerColor = SemanticColors.Warning,
                contentColor = Color.White,
              ),
            modifier = Modifier.defaultMinSize(minWidth = 76.dp, minHeight = 76.dp),
          ) {
            Text(text = "Open Settings", style = DashboardTypography.buttonLabel)
          }
        }

        // State 2: Can request
        else -> {
          Button(
            onClick = { permissionLauncher.launch(definition.permissions.toTypedArray()) },
            colors =
              ButtonDefaults.buttonColors(
                containerColor = theme.accentColor,
                contentColor = Color.White,
              ),
            modifier = Modifier.defaultMinSize(minWidth = 76.dp, minHeight = 76.dp),
          ) {
            Text(text = "Grant", style = DashboardTypography.buttonLabel)
          }
        }
      }
    }
  }
}
