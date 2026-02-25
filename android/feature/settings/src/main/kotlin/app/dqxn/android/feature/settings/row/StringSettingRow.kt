package app.dqxn.android.feature.settings.row

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.dqxn.android.core.design.token.DashboardSpacing
import app.dqxn.android.core.design.token.DashboardTypography
import app.dqxn.android.sdk.contracts.settings.SettingDefinition
import app.dqxn.android.sdk.ui.theme.DashboardThemeDefinition

/**
 * String setting row with inline text editing.
 *
 * [OutlinedTextField] with [maxLength][SettingDefinition.StringSetting.maxLength] filtering and
 * keyboard Done action for auto-defocus. Uses [DashboardTypography.itemTitle] for label.
 */
@Composable
internal fun StringSettingRow(
  definition: SettingDefinition.StringSetting,
  currentValue: String,
  theme: DashboardThemeDefinition,
  onValueChanged: (String, Any?) -> Unit,
  modifier: Modifier = Modifier,
) {
  val focusManager = LocalFocusManager.current
  var textValue by remember(currentValue) { mutableStateOf(currentValue) }

  Column(modifier = modifier.fillMaxWidth().defaultMinSize(minHeight = 76.dp)) {
    SettingLabel(label = definition.label, description = definition.description, theme = theme)
    OutlinedTextField(
      value = textValue,
      onValueChange = { newValue ->
        val filtered =
          if (definition.maxLength != null) newValue.take(definition.maxLength!!) else newValue
        textValue = filtered
        onValueChanged(definition.key, filtered)
      },
      placeholder =
        definition.placeholder?.let {
          { Text(text = it, style = DashboardTypography.description, color = theme.secondaryTextColor) }
        },
      singleLine = true,
      textStyle = DashboardTypography.itemTitle.copy(color = theme.primaryTextColor),
      colors =
        OutlinedTextFieldDefaults.colors(
          focusedTextColor = theme.primaryTextColor,
          unfocusedTextColor = theme.primaryTextColor,
          focusedBorderColor = theme.accentColor,
          unfocusedBorderColor = theme.widgetBorderColor.copy(alpha = 0.3f),
          cursorColor = theme.accentColor,
        ),
      keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
      keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
      modifier = Modifier.fillMaxWidth().padding(top = DashboardSpacing.SpaceXS),
    )
  }
}
