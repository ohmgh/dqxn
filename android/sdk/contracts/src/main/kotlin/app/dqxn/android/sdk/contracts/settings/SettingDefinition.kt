package app.dqxn.android.sdk.contracts.settings

import app.dqxn.android.sdk.contracts.entitlement.Gated
import app.dqxn.android.sdk.contracts.setup.InfoStyle
import app.dqxn.android.sdk.contracts.setup.InstructionAction

/**
 * Schema definition for a single widget or provider setting.
 *
 * 12 subtypes, all implementing [Gated] with three-layer conditional visibility:
 * 1. [hidden] -- hard skip, no animation (feature flags)
 * 2. [visibleWhen] -- evaluated as `!= false` (null = always visible)
 * 3. [requiredAnyEntitlement] -- OR-logic entitlement gating (null/empty = free)
 *
 * Values are persisted via `ProviderSettingsStore` with type-prefixed serialization (e.g.,
 * `"s:America/New_York"`, `"i:42"`, `"b:true"`).
 */
public sealed interface SettingDefinition<T> : Gated {
  public val key: String
  public val label: String
  public val description: String?
  public val default: T
  public val visibleWhen: ((Map<String, Any?>) -> Boolean)?
  public val groupId: String?
  public val hidden: Boolean

  // --- Primitive types ---

  public data class BooleanSetting(
    override val key: String,
    override val label: String,
    override val description: String? = null,
    override val default: Boolean,
    override val visibleWhen: ((Map<String, Any?>) -> Boolean)? = null,
    override val groupId: String? = null,
    override val hidden: Boolean = false,
    override val requiredAnyEntitlement: Set<String>? = null,
  ) : SettingDefinition<Boolean>

  public data class IntSetting(
    override val key: String,
    override val label: String,
    override val description: String? = null,
    override val default: Int,
    override val visibleWhen: ((Map<String, Any?>) -> Boolean)? = null,
    override val groupId: String? = null,
    override val hidden: Boolean = false,
    override val requiredAnyEntitlement: Set<String>? = null,
    val min: Int,
    val max: Int,
    val presets: List<Int>? = null,
    val presetsWhen: ((Map<String, Any?>) -> List<Int>?)? = null,
  ) : SettingDefinition<Int> {

    /** Returns effective presets, checking [presetsWhen] first, then falling back to [presets]. */
    public fun getEffectivePresets(currentSettings: Map<String, Any?>): List<Int>? =
      presetsWhen?.invoke(currentSettings) ?: presets
  }

  public data class FloatSetting(
    override val key: String,
    override val label: String,
    override val description: String? = null,
    override val default: Float,
    override val visibleWhen: ((Map<String, Any?>) -> Boolean)? = null,
    override val groupId: String? = null,
    override val hidden: Boolean = false,
    override val requiredAnyEntitlement: Set<String>? = null,
    val min: Float,
    val max: Float,
    val step: Float? = null,
  ) : SettingDefinition<Float>

  public data class StringSetting(
    override val key: String,
    override val label: String,
    override val description: String? = null,
    override val default: String,
    override val visibleWhen: ((Map<String, Any?>) -> Boolean)? = null,
    override val groupId: String? = null,
    override val hidden: Boolean = false,
    override val requiredAnyEntitlement: Set<String>? = null,
    val placeholder: String? = null,
    val maxLength: Int? = null,
  ) : SettingDefinition<String>

  /**
   * Enum-based setting. Options rendered as chips (<=10) or dropdown (>10).
   *
   * Serialized as `.name` string. Compare with `value?.toString() == option.name`. No
   * `optionPreviews` -- Compose-dependent preview rendering deferred to `:sdk:ui` (Phase 3).
   */
  public data class EnumSetting<E : Enum<E>>(
    override val key: String,
    override val label: String,
    override val description: String? = null,
    override val default: E,
    override val visibleWhen: ((Map<String, Any?>) -> Boolean)? = null,
    override val groupId: String? = null,
    override val hidden: Boolean = false,
    override val requiredAnyEntitlement: Set<String>? = null,
    val options: List<E>,
    val optionLabels: Map<E, String>? = null,
  ) : SettingDefinition<E>

  // --- Picker types ---

  /** Timezone setting. `null` = system default, `"SYSTEM"` sentinel for explicit system choice. */
  public data class TimezoneSetting(
    override val key: String,
    override val label: String,
    override val description: String? = null,
    override val default: String? = null,
    override val visibleWhen: ((Map<String, Any?>) -> Boolean)? = null,
    override val groupId: String? = null,
    override val hidden: Boolean = false,
    override val requiredAnyEntitlement: Set<String>? = null,
  ) : SettingDefinition<String?>

  /** Date format setting with predefined format options. */
  public data class DateFormatSetting(
    override val key: String,
    override val label: String,
    override val description: String? = null,
    override val default: DateFormatOption,
    override val visibleWhen: ((Map<String, Any?>) -> Boolean)? = null,
    override val groupId: String? = null,
    override val hidden: Boolean = false,
    override val requiredAnyEntitlement: Set<String>? = null,
  ) : SettingDefinition<DateFormatOption>

  /** URI reference setting. Fallback rendering in dispatcher. */
  public data class UriSetting(
    override val key: String,
    override val label: String,
    override val description: String? = null,
    override val default: String? = null,
    override val visibleWhen: ((Map<String, Any?>) -> Boolean)? = null,
    override val groupId: String? = null,
    override val hidden: Boolean = false,
    override val requiredAnyEntitlement: Set<String>? = null,
  ) : SettingDefinition<String?>

  /** App package picker setting with suggested packages sorted first. */
  public data class AppPickerSetting(
    override val key: String,
    override val label: String,
    override val description: String? = null,
    override val default: String? = null,
    override val visibleWhen: ((Map<String, Any?>) -> Boolean)? = null,
    override val groupId: String? = null,
    override val hidden: Boolean = false,
    override val requiredAnyEntitlement: Set<String>? = null,
    val suggestedPackages: List<String> = emptyList(),
  ) : SettingDefinition<String?>

  /** Sound picker setting, launches system `ACTION_RINGTONE_PICKER`. */
  public data class SoundPickerSetting(
    override val key: String,
    override val label: String,
    override val description: String? = null,
    override val default: String? = null,
    override val visibleWhen: ((Map<String, Any?>) -> Boolean)? = null,
    override val groupId: String? = null,
    override val hidden: Boolean = false,
    override val requiredAnyEntitlement: Set<String>? = null,
    val soundType: SoundType = SoundType.NOTIFICATION,
  ) : SettingDefinition<String?>

  // --- Display-only types ---

  /**
   * Display-only instruction step within settings. Parallels [SetupDefinition.Instruction].
   * `default = Unit` -- carries no persisted value.
   */
  public data class InstructionSetting(
    override val key: String,
    override val label: String,
    override val description: String? = null,
    override val visibleWhen: ((Map<String, Any?>) -> Boolean)? = null,
    override val groupId: String? = null,
    override val hidden: Boolean = false,
    override val requiredAnyEntitlement: Set<String>? = null,
    val stepNumber: Int = 0,
    val action: InstructionAction? = null,
  ) : SettingDefinition<Unit> {
    override val default: Unit = Unit
  }

  /**
   * Display-only info card within settings. Parallels [SetupDefinition.Info]. `default = Unit` --
   * carries no persisted value.
   */
  public data class InfoSetting(
    override val key: String,
    override val label: String,
    override val description: String? = null,
    override val visibleWhen: ((Map<String, Any?>) -> Boolean)? = null,
    override val groupId: String? = null,
    override val hidden: Boolean = false,
    override val requiredAnyEntitlement: Set<String>? = null,
    val style: InfoStyle = InfoStyle.INFO,
  ) : SettingDefinition<Unit> {
    override val default: Unit = Unit
  }
}
