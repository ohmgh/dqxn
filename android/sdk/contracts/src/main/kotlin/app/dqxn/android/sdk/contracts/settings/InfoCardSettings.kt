package app.dqxn.android.sdk.contracts.settings

/**
 * Factory for the standard InfoCard settings schema used by 5+ widgets.
 *
 * Pure factory methods -- no Compose dependencies. Returns `List<SettingDefinition<*>>` for direct
 * use in `WidgetSpec.settingsSchema`.
 */
public fun infoCardSettingsSchema(
  layoutModeKey: String = "info_card_layout_mode",
  sizeOptionKey: String = "info_card_size",
): List<SettingDefinition<*>> =
  listOf(
    SettingDefinition.EnumSetting(
      key = layoutModeKey,
      label = "Layout Mode",
      description = "Card layout style",
      default = InfoCardLayoutMode.STANDARD,
      options = InfoCardLayoutMode.entries,
      optionLabels =
        mapOf(
          InfoCardLayoutMode.STANDARD to "Standard",
          InfoCardLayoutMode.COMPACT to "Compact",
          InfoCardLayoutMode.WIDE to "Wide",
        ),
    ),
    SettingDefinition.EnumSetting(
      key = sizeOptionKey,
      label = "Size",
      description = "Card size option",
      default = SizeOption.MEDIUM,
      options = SizeOption.entries,
      optionLabels =
        mapOf(
          SizeOption.SMALL to "Small",
          SizeOption.MEDIUM to "Medium",
          SizeOption.LARGE to "Large",
          SizeOption.EXTRA_LARGE to "Extra Large",
        ),
    ),
  )
