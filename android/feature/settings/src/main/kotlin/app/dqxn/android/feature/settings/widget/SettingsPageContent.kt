package app.dqxn.android.feature.settings.widget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.dqxn.android.core.design.token.DashboardSpacing
import app.dqxn.android.core.design.token.DashboardTypography
import app.dqxn.android.data.style.WidgetStyleStore
import app.dqxn.android.feature.settings.R
import app.dqxn.android.feature.settings.SettingNavigation
import app.dqxn.android.feature.settings.row.SettingRowDispatcher
import app.dqxn.android.sdk.contracts.entitlement.EntitlementManager
import app.dqxn.android.sdk.contracts.settings.ProviderSettingsStore
import app.dqxn.android.sdk.contracts.settings.SettingDefinition
import app.dqxn.android.sdk.contracts.widget.BackgroundStyle
import app.dqxn.android.sdk.contracts.widget.WidgetRenderer
import app.dqxn.android.sdk.contracts.widget.WidgetStyle
import app.dqxn.android.sdk.ui.theme.DashboardThemeDefinition
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Combined feature + style settings page (page 0).
 *
 * Merges widget-specific feature settings from [WidgetRenderer.settingsSchema] with universal
 * style settings from [styleSettingsSchema] into a single [LazyColumn]. Feature settings appear
 * first, style settings follow contiguously with no section divider (matching old codebase).
 *
 * Feature settings persist via [ProviderSettingsStore]. Style settings persist via
 * [WidgetStyleStore], converting to/from [WidgetStyle] fields.
 */
@Composable
internal fun SettingsPageContent(
  widgetSpec: WidgetRenderer?,
  widgetInstanceId: String,
  providerSettingsStore: ProviderSettingsStore,
  widgetStyleStore: WidgetStyleStore,
  entitlementManager: EntitlementManager,
  theme: DashboardThemeDefinition,
  onNavigate: (SettingNavigation) -> Unit,
  modifier: Modifier = Modifier,
) {
  val featureSchema = widgetSpec?.settingsSchema.orEmpty()
  val visibleFeatureSettings = featureSchema.filterNot { it.hidden }

  if (visibleFeatureSettings.isEmpty() && styleSettingsSchema.all { it.hidden }) {
    EmptySettingsPlaceholder(theme = theme, modifier = modifier)
    return
  }

  val packId = widgetSpec?.typeId?.substringBefore(':') ?: ""
  val providerId = widgetSpec?.typeId?.substringAfter(':') ?: ""

  val featureSettings by
    providerSettingsStore
      .getAllSettings(packId, providerId)
      .collectAsStateWithLifecycle(initialValue = persistentMapOf())

  // Convert WidgetStyle to a flat settings map for style rows
  val styleSettings by
    widgetStyleStore
      .getStyle(widgetInstanceId)
      .map { style -> style.toSettingsMap() }
      .collectAsStateWithLifecycle(initialValue = WidgetStyle.Default.toSettingsMap())

  val scope = rememberCoroutineScope()

  LazyColumn(
    modifier =
      modifier
        .fillMaxSize()
        .testTag("settings_page_content"),
    contentPadding = PaddingValues(vertical = DashboardSpacing.ItemGap),
    verticalArrangement = Arrangement.spacedBy(DashboardSpacing.ItemGap),
  ) {
    // Feature settings
    itemsIndexed(
      items = visibleFeatureSettings,
      key = { _, def -> "feature_${def.key}" },
    ) { _, definition ->
      SettingRowDispatcher(
        definition = definition,
        currentValue = featureSettings[definition.key],
        currentSettings = featureSettings,
        entitlementManager = entitlementManager,
        theme = theme,
        onValueChanged = { key, value ->
          scope.launch { providerSettingsStore.setSetting(packId, providerId, key, value) }
        },
        onNavigate = onNavigate,
        modifier = Modifier.fillMaxWidth(),
      )
    }

    // Style settings (contiguous, no divider)
    itemsIndexed(
      items = styleSettingsSchema.filterNot { it.hidden },
      key = { _, def -> "style_${def.key}" },
    ) { _, definition ->
      SettingRowDispatcher(
        definition = definition,
        currentValue = styleSettings[definition.key],
        currentSettings = styleSettings,
        entitlementManager = entitlementManager,
        theme = theme,
        onValueChanged = { key, value ->
          scope.launch {
            val currentStyle = styleSettings.toWidgetStyle()
            val updated = currentStyle.withSetting(key, value)
            widgetStyleStore.setStyle(widgetInstanceId, updated)
          }
        },
        onNavigate = onNavigate,
        modifier = Modifier.fillMaxWidth(),
      )
    }

    item(key = "bottom_spacer") {
      Spacer(
        modifier =
          Modifier
            .height(24.dp)
            .navigationBarsPadding(),
      )
    }
  }
}

@Composable
internal fun EmptySettingsPlaceholder(
  theme: DashboardThemeDefinition,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier = modifier.fillMaxSize().testTag("feature_settings_empty"),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = stringResource(R.string.widget_settings_no_settings),
      style = DashboardTypography.description,
      color = theme.secondaryTextColor,
    )
  }
}

// -- Style settings schema --

/**
 * Universal widget style settings, matching old codebase `styleSettingsSchema`.
 *
 * Surface (background style), Tint (opacity), Corners (radius), Glow, Outline, Rim.
 * No sliders — old codebase explicitly avoids them (interfere with HorizontalPager gestures).
 */
internal val styleSettingsSchema: List<SettingDefinition<*>> = listOf(
  SettingDefinition.EnumSetting(
    key = STYLE_KEY_BACKGROUND,
    label = "Surface",
    description = "Background fill style",
    options = BackgroundStyle.entries.toList(),
    default = BackgroundStyle.SOLID,
    optionLabels = mapOf(
      BackgroundStyle.NONE to "Air",
      BackgroundStyle.SOLID to "Solid",
    ),
  ),
  SettingDefinition.IntSetting(
    key = STYLE_KEY_OPACITY,
    label = "Tint",
    description = "Strength of the background tint",
    default = 100,
    min = 0,
    max = 100,
    presets = listOf(25, 50, 75, 100),
    visibleWhen = { settings ->
      val bg = settings[STYLE_KEY_BACKGROUND]
      bg != BackgroundStyle.NONE && bg?.toString() != BackgroundStyle.NONE.name
    },
  ),
  SettingDefinition.IntSetting(
    key = STYLE_KEY_CORNER_RADIUS,
    label = "Corners",
    description = "Roundness of background corners",
    default = 25,
    min = 0,
    max = 100,
    presets = listOf(0, 25, 50, 75, 100),
  ),
  SettingDefinition.BooleanSetting(
    key = STYLE_KEY_GLOW,
    label = "Glow Effect",
    description = "Luminous glow around widget edges",
    default = false,
  ),
  SettingDefinition.BooleanSetting(
    key = STYLE_KEY_BORDER,
    label = "Outline",
    description = "Visible border around widget",
    default = false,
  ),
  SettingDefinition.IntSetting(
    key = STYLE_KEY_RIM,
    label = "Rim",
    description = "Decorative rim thickness",
    default = 0,
    min = 0,
    max = 100,
    presets = listOf(0, 25, 50, 100),
  ),
)

// Style setting keys
private const val STYLE_KEY_BACKGROUND = "backgroundStyle"
private const val STYLE_KEY_OPACITY = "opacityPercent"
private const val STYLE_KEY_CORNER_RADIUS = "cornerRadiusPercent"
private const val STYLE_KEY_GLOW = "hasGlowEffect"
private const val STYLE_KEY_BORDER = "showBorder"
private const val STYLE_KEY_RIM = "rimSizePercent"

/** Convert [WidgetStyle] to a flat map for use with [SettingRowDispatcher]. */
private fun WidgetStyle.toSettingsMap(): Map<String, Any?> = mapOf(
  STYLE_KEY_BACKGROUND to backgroundStyle,
  STYLE_KEY_OPACITY to (opacity * 100).toInt(),
  STYLE_KEY_CORNER_RADIUS to cornerRadiusPercent,
  STYLE_KEY_GLOW to hasGlowEffect,
  STYLE_KEY_BORDER to showBorder,
  STYLE_KEY_RIM to rimSizePercent,
)

/** Reconstruct [WidgetStyle] from a flat settings map. */
private fun Map<String, Any?>.toWidgetStyle(): WidgetStyle {
  val bg = this[STYLE_KEY_BACKGROUND]
  val backgroundStyle = when {
    bg is BackgroundStyle -> bg
    bg != null -> runCatching { BackgroundStyle.valueOf(bg.toString()) }
      .getOrDefault(WidgetStyle.Default.backgroundStyle)
    else -> WidgetStyle.Default.backgroundStyle
  }
  return WidgetStyle(
    backgroundStyle = backgroundStyle,
    opacity = ((this[STYLE_KEY_OPACITY] as? Int) ?: 100) / 100f,
    showBorder = (this[STYLE_KEY_BORDER] as? Boolean) ?: WidgetStyle.Default.showBorder,
    hasGlowEffect = (this[STYLE_KEY_GLOW] as? Boolean) ?: WidgetStyle.Default.hasGlowEffect,
    cornerRadiusPercent = (this[STYLE_KEY_CORNER_RADIUS] as? Int) ?: WidgetStyle.Default.cornerRadiusPercent,
    rimSizePercent = (this[STYLE_KEY_RIM] as? Int) ?: WidgetStyle.Default.rimSizePercent,
    zLayer = WidgetStyle.Default.zLayer,
  )
}

/** Apply a single key-value change and produce a new [WidgetStyle]. */
private fun WidgetStyle.withSetting(key: String, value: Any?): WidgetStyle = when (key) {
  STYLE_KEY_BACKGROUND -> {
    val bg = when {
      value is BackgroundStyle -> value
      value != null -> runCatching { BackgroundStyle.valueOf(value.toString()) }
        .getOrDefault(backgroundStyle)
      else -> backgroundStyle
    }
    copy(backgroundStyle = bg)
  }
  STYLE_KEY_OPACITY -> copy(opacity = ((value as? Int) ?: 100) / 100f)
  STYLE_KEY_CORNER_RADIUS -> copy(cornerRadiusPercent = (value as? Int) ?: cornerRadiusPercent)
  STYLE_KEY_GLOW -> copy(hasGlowEffect = (value as? Boolean) ?: hasGlowEffect)
  STYLE_KEY_BORDER -> copy(showBorder = (value as? Boolean) ?: showBorder)
  STYLE_KEY_RIM -> copy(rimSizePercent = (value as? Int) ?: rimSizePercent)
  else -> this
}
