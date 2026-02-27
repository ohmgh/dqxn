package app.dqxn.android.feature.settings.theme

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.dqxn.android.core.design.token.DashboardSpacing
import app.dqxn.android.core.design.token.DashboardTypography
import app.dqxn.android.core.design.token.TextEmphasis
import app.dqxn.android.feature.settings.overlay.OverlayScaffold
import app.dqxn.android.feature.settings.overlay.OverlayType
import app.dqxn.android.sdk.ui.theme.DashboardThemeDefinition
import app.dqxn.android.sdk.ui.theme.LocalDashboardTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop

/**
 * Custom theme CRUD composable with auto-save.
 *
 * Uses [ThemeStudioStateHolder] for decomposed state management. Theme ID stability via remembered
 * ID. Auto-saves via [LaunchedEffect] on state changes.
 *
 * When [customThemeCount] >= [MAX_CUSTOM_THEMES] (12), shows an info banner and disables the "New
 * Theme" action.
 *
 * Delete-while-previewing: calls [onClearPreview] BEFORE [onDelete].
 */
@Composable
public fun ThemeStudio(
  existingTheme: DashboardThemeDefinition?,
  customThemeCount: Int,
  onAutoSave: (DashboardThemeDefinition) -> Unit,
  onDelete: (String) -> Unit,
  onClearPreview: () -> Unit,
  onClose: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val theme = LocalDashboardTheme.current
  val stateHolder = remember(existingTheme) { ThemeStudioStateHolder(existingTheme) }

  // Stable theme ID: remember existing or generate once
  val stableThemeId =
    remember(existingTheme) { existingTheme?.themeId ?: "custom_${System.currentTimeMillis()}" }

  // -- Swatch selection state --
  var selectedSwatch by remember { mutableStateOf(SwatchType.PRIMARY_TEXT) }

  // -- Auto-save via LaunchedEffect on state changes --
  LaunchedEffect(stateHolder) {
    snapshotFlow { stateHolder.isDirty }
      .drop(1) // Skip initial emission
      .collectLatest { isDirty ->
        if (isDirty) {
          onAutoSave(stateHolder.buildCustomTheme(stableThemeId))
        }
      }
  }

  val atMaxThemes = customThemeCount >= MAX_CUSTOM_THEMES

  OverlayScaffold(
    title = "Theme Studio",
    overlayType = OverlayType.Preview,
    onBack = onClose,
    modifier = modifier.testTag("theme_studio"),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(DashboardSpacing.SectionGap),
    ) {
      // -- Max themes banner --
      if (atMaxThemes) {
        Text(
          text = "Maximum of $MAX_CUSTOM_THEMES custom themes reached.",
          style = DashboardTypography.description,
          color = theme.warningColor,
          modifier = Modifier.fillMaxWidth().padding(8.dp).testTag("max_themes_banner"),
        )
      }

      // -- Custom header: editable title + undo/delete buttons --
      Row(
        modifier = Modifier.fillMaxWidth().testTag("theme_studio_header"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DashboardSpacing.SpaceXS),
      ) {
        // Editable theme name
        BasicTextField(
          value = stateHolder.displayName,
          onValueChange = { stateHolder.displayName = it },
          textStyle = DashboardTypography.itemTitle.copy(color = theme.primaryTextColor),
          singleLine = true,
          cursorBrush = SolidColor(theme.accentColor),
          modifier =
            Modifier
              .weight(1f)
              .testTag("editable_title"),
        )

        // Undo button -- alpha-dimmed when not dirty
        Box(
          modifier =
            Modifier
              .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
              .alpha(if (stateHolder.isDirty) 1f else TextEmphasis.Disabled)
              .testTag("undo_button")
              .semantics { role = Role.Button }
              .clickable(enabled = stateHolder.isDirty) { stateHolder.reset() },
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.Undo,
            contentDescription = "Undo changes",
            tint =
              theme.primaryTextColor.copy(
                alpha =
                  if (stateHolder.isDirty) TextEmphasis.High
                  else TextEmphasis.Disabled,
              ),
            modifier = Modifier.size(20.dp),
          )
        }

        // Delete button -- hidden for new themes
        if (existingTheme != null) {
          Box(
            modifier =
              Modifier
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .testTag("delete_button")
                .semantics { role = Role.Button }
                .clickable {
                  onClearPreview()
                  onDelete(stableThemeId)
                },
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.Filled.Delete,
              contentDescription = "Delete theme",
              tint = theme.warningColor,
              modifier = Modifier.size(20.dp),
            )
          }
        }
      }

      // -- Swatch row (7 color properties) --
      ThemeSwatchRow(
        selected = selectedSwatch,
        onSelected = { selectedSwatch = it },
        theme = buildPreviewTheme(stateHolder, stableThemeId),
        modifier = Modifier.testTag("theme_studio_swatch_section"),
      )

      // -- Color picker for selected swatch --
      when (selectedSwatch) {
        SwatchType.PRIMARY_TEXT ->
          InlineColorPicker(
            color = stateHolder.primaryTextColor,
            onColorChanged = { stateHolder.primaryTextColor = it },
          )
        SwatchType.SECONDARY_TEXT ->
          InlineColorPicker(
            color = stateHolder.secondaryTextColor,
            onColorChanged = { stateHolder.secondaryTextColor = it },
          )
        SwatchType.ACCENT ->
          InlineColorPicker(
            color = stateHolder.accentColor,
            onColorChanged = { stateHolder.accentColor = it },
          )
        SwatchType.HIGHLIGHT ->
          InlineColorPicker(
            color = stateHolder.highlightColor,
            onColorChanged = { stateHolder.highlightColor = it },
          )
        SwatchType.WIDGET_BORDER ->
          InlineColorPicker(
            color = stateHolder.widgetBorderColor,
            onColorChanged = { stateHolder.widgetBorderColor = it },
          )
        SwatchType.BACKGROUND -> {
          Column(verticalArrangement = Arrangement.spacedBy(DashboardSpacing.ItemGap)) {
            GradientTypeSelector(
              selected = stateHolder.backgroundGradientType,
              onSelected = { stateHolder.backgroundGradientType = it },
              modifier = Modifier.testTag("gradient_type_background"),
            )
            GradientStopRow(
              stops = stateHolder.backgroundStops,
              onStopsChanged = { stateHolder.backgroundStops = it },
              modifier = Modifier.testTag("gradient_stops_background"),
            )
          }
        }
        SwatchType.WIDGET_BACKGROUND -> {
          Column(verticalArrangement = Arrangement.spacedBy(DashboardSpacing.ItemGap)) {
            GradientTypeSelector(
              selected = stateHolder.widgetBackgroundGradientType,
              onSelected = { stateHolder.widgetBackgroundGradientType = it },
              modifier = Modifier.testTag("gradient_type_widget_background"),
            )
            GradientStopRow(
              stops = stateHolder.widgetBackgroundStops,
              onStopsChanged = { stateHolder.widgetBackgroundStops = it },
              modifier = Modifier.testTag("gradient_stops_widget_background"),
            )
          }
        }
      }

      // -- isDark toggle --
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).testTag("is_dark_toggle"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          text = "Dark Theme",
          style = DashboardTypography.itemTitle,
          color = theme.primaryTextColor,
        )
        Switch(
          checked = stateHolder.isDark,
          onCheckedChange = { stateHolder.isDark = it },
          colors =
            SwitchDefaults.colors(
              checkedThumbColor = theme.accentColor,
              checkedTrackColor = theme.accentColor.copy(alpha = 0.3f),
            ),
        )
      }
    }
  }
}

/** Builds a preview theme from the current state holder values. */
private fun buildPreviewTheme(
  stateHolder: ThemeStudioStateHolder,
  themeId: String,
): DashboardThemeDefinition = stateHolder.buildCustomTheme(themeId)
