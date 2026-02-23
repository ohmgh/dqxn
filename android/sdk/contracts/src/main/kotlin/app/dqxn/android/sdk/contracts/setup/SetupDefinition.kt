package app.dqxn.android.sdk.contracts.setup

import app.dqxn.android.sdk.contracts.entitlement.Gated
import app.dqxn.android.sdk.contracts.settings.SettingDefinition

/**
 * A single item within a setup page.
 *
 * 7 subtypes in 3 categories:
 * - **Requirement** (pass/fail, can block forward navigation): [RuntimePermission],
 *   [SystemServiceToggle], [SystemService], [DeviceScan]
 * - **Display** (always satisfied): [Instruction], [Info]
 * - **Input wrapper** (always satisfied, delegates to [SettingDefinition]): [Setting]
 *
 * All subtypes implement [Gated] and carry three-layer conditional visibility:
 * 1. [hidden] -- hard skip, no animation (feature flags)
 * 2. [visibleWhen] -- evaluated as `!= false` (null = always visible)
 * 3. [requiredAnyEntitlement] -- OR-logic entitlement gating
 *
 * Per replication advisory section 7: the [Setting] wrapper's own [visibleWhen] is checked FIRST,
 * then the inner [SettingDefinition]'s [visibleWhen] -- intentional double-gating.
 */
public sealed interface SetupDefinition : Gated {
  public val id: String
  public val label: String
  public val description: String?
  public val iconName: String?
  public val hidden: Boolean
  public val visibleWhen: ((Map<String, Any?>) -> Boolean)?

  // --- Requirement subtypes (pass/fail semantics, can block forward navigation) ---

  /**
   * Runtime permission requirement.
   *
   * [minSdk] gating: if `minSdk > Build.VERSION.SDK_INT`, evaluator skips the definition entirely
   * -- renderer never sees it.
   */
  public data class RuntimePermission(
    override val id: String,
    override val label: String,
    override val description: String? = null,
    override val iconName: String? = null,
    override val hidden: Boolean = false,
    override val visibleWhen: ((Map<String, Any?>) -> Boolean)? = null,
    override val requiredAnyEntitlement: Set<String>? = null,
    val permissions: List<String>,
    val minSdk: Int = 0,
  ) : SetupDefinition

  /**
   * System service toggle requirement (e.g., enable Bluetooth, Location).
   *
   * Uses declarative [ServiceType] enum instead of `(Context) -> Boolean` lambda to keep
   * `:sdk:contracts` free of Android Context dependencies. The shell interprets the enum to perform
   * the actual system check.
   */
  public data class SystemServiceToggle(
    override val id: String,
    override val label: String,
    override val description: String? = null,
    override val iconName: String? = null,
    override val hidden: Boolean = false,
    override val visibleWhen: ((Map<String, Any?>) -> Boolean)? = null,
    override val requiredAnyEntitlement: Set<String>? = null,
    val settingsAction: String,
    val serviceType: ServiceType,
  ) : SetupDefinition

  /** System service availability check (no toggle UI, just presence). */
  public data class SystemService(
    override val id: String,
    override val label: String,
    override val description: String? = null,
    override val iconName: String? = null,
    override val hidden: Boolean = false,
    override val visibleWhen: ((Map<String, Any?>) -> Boolean)? = null,
    override val requiredAnyEntitlement: Set<String>? = null,
    val serviceType: ServiceType,
  ) : SetupDefinition

  /** BLE device scan/pairing requirement using Companion Device Manager. */
  public data class DeviceScan(
    override val id: String,
    override val label: String,
    override val description: String? = null,
    override val iconName: String? = null,
    override val hidden: Boolean = false,
    override val visibleWhen: ((Map<String, Any?>) -> Boolean)? = null,
    override val requiredAnyEntitlement: Set<String>? = null,
    val handlerId: String,
    val deviceNamePattern: String? = null,
    val serviceUuids: List<String> = emptyList(),
    val maxDevices: Int = 1,
  ) : SetupDefinition

  // --- Display subtypes (always satisfied) ---

  /** Instructional step with optional action and verification. */
  public data class Instruction(
    override val id: String,
    override val label: String,
    override val description: String? = null,
    override val iconName: String? = null,
    override val hidden: Boolean = false,
    override val visibleWhen: ((Map<String, Any?>) -> Boolean)? = null,
    override val requiredAnyEntitlement: Set<String>? = null,
    val stepNumber: Int,
    val action: InstructionAction? = null,
    val verificationStrategy: VerificationStrategy? = null,
    val verificationOptional: Boolean = true,
    val alternativeResolution: String? = null,
  ) : SetupDefinition

  /** Informational display card. */
  public data class Info(
    override val id: String,
    override val label: String,
    override val description: String? = null,
    override val iconName: String? = null,
    override val hidden: Boolean = false,
    override val visibleWhen: ((Map<String, Any?>) -> Boolean)? = null,
    override val requiredAnyEntitlement: Set<String>? = null,
    val style: InfoStyle = InfoStyle.INFO,
  ) : SetupDefinition

  // --- Input wrapper (always satisfied, delegates to SettingDefinition) ---

  /**
   * Wraps a [SettingDefinition] for inclusion on a setup page alongside requirement cards.
   *
   * Delegates [id], [label], [visibleWhen], [requiredAnyEntitlement], and [hidden] to the wrapped
   * [definition] by default. The wrapper's own [visibleWhen] is checked FIRST, then the inner
   * definition's -- intentional double-gating per replication advisory section 7.
   */
  public data class Setting(
    val definition: SettingDefinition<*>,
    override val id: String = definition.key,
    override val label: String = definition.label,
    override val description: String? = definition.description,
    override val iconName: String? = null,
    override val hidden: Boolean = definition.hidden,
    override val visibleWhen: ((Map<String, Any?>) -> Boolean)? = definition.visibleWhen,
    override val requiredAnyEntitlement: Set<String>? = definition.requiredAnyEntitlement,
  ) : SetupDefinition
}

/** Actions that can be triggered from an [SetupDefinition.Instruction] step. */
public sealed interface InstructionAction {
  public data class OpenSystemSettings(val settingsAction: String) : InstructionAction

  public data class OpenUrl(val url: String) : InstructionAction

  public data class LaunchApp(val packageName: String) : InstructionAction
}

/** Visual style for [SetupDefinition.Info] cards. */
public enum class InfoStyle {
  INFO,
  WARNING,
  SUCCESS,
  ERROR,
}

// --- Extension functions ---

/** Wraps this [SettingDefinition] as a [SetupDefinition.Setting] for use on setup pages. */
public fun SettingDefinition<*>.asSetup(): SetupDefinition.Setting =
  SetupDefinition.Setting(definition = this)

/** `true` if this definition is a requirement type (has pass/fail semantics). */
public val SetupDefinition.isRequirement: Boolean
  get() =
    when (this) {
      is SetupDefinition.RuntimePermission,
      is SetupDefinition.SystemServiceToggle,
      is SetupDefinition.SystemService,
      is SetupDefinition.DeviceScan -> true
      is SetupDefinition.Instruction,
      is SetupDefinition.Info,
      is SetupDefinition.Setting -> false
    }

/** `true` if this definition is a display-only type (always satisfied). */
public val SetupDefinition.isDisplay: Boolean
  get() =
    when (this) {
      is SetupDefinition.Instruction,
      is SetupDefinition.Info -> true
      is SetupDefinition.RuntimePermission,
      is SetupDefinition.SystemServiceToggle,
      is SetupDefinition.SystemService,
      is SetupDefinition.DeviceScan,
      is SetupDefinition.Setting -> false
    }

/** `true` if this definition is an input wrapper. */
public val SetupDefinition.isInput: Boolean
  get() = this is SetupDefinition.Setting

/** Returns the default value for this definition, or `null` if not applicable. */
public fun SetupDefinition.getDefaultValue(): Any? =
  when (this) {
    is SetupDefinition.Setting -> definition.default
    is SetupDefinition.RuntimePermission,
    is SetupDefinition.SystemServiceToggle,
    is SetupDefinition.SystemService,
    is SetupDefinition.DeviceScan,
    is SetupDefinition.Instruction,
    is SetupDefinition.Info -> null
  }
