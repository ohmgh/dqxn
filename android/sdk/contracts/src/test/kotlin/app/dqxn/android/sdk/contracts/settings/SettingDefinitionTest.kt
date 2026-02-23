package app.dqxn.android.sdk.contracts.settings

import app.dqxn.android.sdk.contracts.entitlement.isAccessible
import app.dqxn.android.sdk.contracts.setup.InfoStyle
import app.dqxn.android.sdk.contracts.setup.InstructionAction
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("fast")
class SettingDefinitionTest {

  /** Test enum for EnumSetting tests. */
  private enum class TestEnum {
    OPTION_A,
    OPTION_B,
    OPTION_C,
  }

  @Nested
  @DisplayName("Construction tests (12 subtypes)")
  inner class ConstructionTests {

    @Test
    fun `BooleanSetting constructs with required fields`() {
      val setting =
        SettingDefinition.BooleanSetting(
          key = "enabled",
          label = "Enable feature",
          default = true,
        )

      assertThat(setting.key).isEqualTo("enabled")
      assertThat(setting.label).isEqualTo("Enable feature")
      assertThat(setting.default).isTrue()
      assertThat(setting.hidden).isFalse()
      assertThat(setting.visibleWhen).isNull()
      assertThat(setting.requiredAnyEntitlement).isNull()
    }

    @Test
    fun `IntSetting constructs with min, max, and defaults`() {
      val setting =
        SettingDefinition.IntSetting(
          key = "count",
          label = "Count",
          default = 5,
          min = 0,
          max = 10,
        )

      assertThat(setting.key).isEqualTo("count")
      assertThat(setting.default).isEqualTo(5)
      assertThat(setting.min).isEqualTo(0)
      assertThat(setting.max).isEqualTo(10)
    }

    @Test
    fun `FloatSetting constructs with min, max, and optional step`() {
      val setting =
        SettingDefinition.FloatSetting(
          key = "opacity",
          label = "Opacity",
          default = 0.8f,
          min = 0.0f,
          max = 1.0f,
          step = 0.1f,
        )

      assertThat(setting.default).isEqualTo(0.8f)
      assertThat(setting.step).isEqualTo(0.1f)
    }

    @Test
    fun `StringSetting constructs with optional placeholder and maxLength`() {
      val setting =
        SettingDefinition.StringSetting(
          key = "name",
          label = "Name",
          default = "",
          placeholder = "Enter name",
          maxLength = 50,
        )

      assertThat(setting.placeholder).isEqualTo("Enter name")
      assertThat(setting.maxLength).isEqualTo(50)
    }

    @Test
    fun `EnumSetting constructs with options list`() {
      val setting =
        SettingDefinition.EnumSetting(
          key = "mode",
          label = "Mode",
          default = TestEnum.OPTION_A,
          options = listOf(TestEnum.OPTION_A, TestEnum.OPTION_B, TestEnum.OPTION_C),
        )

      assertThat(setting.options).hasSize(3)
      assertThat(setting.default).isEqualTo(TestEnum.OPTION_A)
    }

    @Test
    fun `TimezoneSetting constructs with null default for system`() {
      val setting =
        SettingDefinition.TimezoneSetting(
          key = "tz",
          label = "Timezone",
        )

      assertThat(setting.default).isNull()
    }

    @Test
    fun `DateFormatSetting constructs with DateFormatOption default`() {
      val setting =
        SettingDefinition.DateFormatSetting(
          key = "dateFormat",
          label = "Date Format",
          default = DateFormatOption.ISO_DATE,
        )

      assertThat(setting.default).isEqualTo(DateFormatOption.ISO_DATE)
    }

    @Test
    fun `UriSetting constructs with null default`() {
      val setting =
        SettingDefinition.UriSetting(
          key = "source",
          label = "Data Source",
        )

      assertThat(setting.default).isNull()
    }

    @Test
    fun `AppPickerSetting constructs with suggested packages`() {
      val setting =
        SettingDefinition.AppPickerSetting(
          key = "app",
          label = "Target App",
          suggestedPackages = listOf("com.example.app1", "com.example.app2"),
        )

      assertThat(setting.suggestedPackages).hasSize(2)
      assertThat(setting.default).isNull()
    }

    @Test
    fun `SoundPickerSetting constructs with sound type`() {
      val setting =
        SettingDefinition.SoundPickerSetting(
          key = "sound",
          label = "Alert Sound",
          soundType = SoundType.ALARM,
        )

      assertThat(setting.soundType).isEqualTo(SoundType.ALARM)
    }

    @Test
    fun `InstructionSetting constructs with step number and action`() {
      val setting =
        SettingDefinition.InstructionSetting(
          key = "step1",
          label = "Step 1",
          stepNumber = 1,
          action = InstructionAction.OpenUrl("https://example.com"),
        )

      assertThat(setting.default).isEqualTo(Unit)
      assertThat(setting.stepNumber).isEqualTo(1)
      assertThat(setting.action).isInstanceOf(InstructionAction.OpenUrl::class.java)
    }

    @Test
    fun `InfoSetting constructs with style`() {
      val setting =
        SettingDefinition.InfoSetting(
          key = "info1",
          label = "Important Info",
          style = InfoStyle.WARNING,
        )

      assertThat(setting.default).isEqualTo(Unit)
      assertThat(setting.style).isEqualTo(InfoStyle.WARNING)
    }
  }

  @Nested
  @DisplayName("Constraint validation")
  inner class ConstraintValidationTests {

    @Test
    fun `IntSetting default in min-max range`() {
      val setting =
        SettingDefinition.IntSetting(
          key = "k",
          label = "l",
          default = 5,
          min = 0,
          max = 10,
        )

      assertThat(setting.default).isIn(setting.min..setting.max)
    }

    @Test
    fun `IntSetting default outside range is detectable`() {
      // No constructor validation — document the constraint violation is detectable
      val setting =
        SettingDefinition.IntSetting(
          key = "k",
          label = "l",
          default = 15,
          min = 0,
          max = 10,
        )

      assertThat(setting.default).isNotIn(setting.min..setting.max)
    }

    @Test
    fun `FloatSetting step is positive when provided`() {
      val setting =
        SettingDefinition.FloatSetting(
          key = "k",
          label = "l",
          default = 0.5f,
          min = 0.0f,
          max = 1.0f,
          step = 0.1f,
        )

      assertThat(setting.step).isGreaterThan(0.0f)
    }

    @Test
    fun `EnumSetting options non-empty`() {
      val setting =
        SettingDefinition.EnumSetting(
          key = "k",
          label = "l",
          default = TestEnum.OPTION_A,
          options = listOf(TestEnum.OPTION_A, TestEnum.OPTION_B),
        )

      assertThat(setting.options).isNotEmpty()
    }

    @Test
    fun `EnumSetting empty options is an error state`() {
      // No constructor validation — document that empty options is detectable
      val setting =
        SettingDefinition.EnumSetting(
          key = "k",
          label = "l",
          default = TestEnum.OPTION_A,
          options = emptyList(),
        )

      assertThat(setting.options).isEmpty()
      assertThat(setting.options).doesNotContain(setting.default)
    }

    @Test
    fun `EnumSetting default in options`() {
      val setting =
        SettingDefinition.EnumSetting(
          key = "k",
          label = "l",
          default = TestEnum.OPTION_B,
          options = listOf(TestEnum.OPTION_A, TestEnum.OPTION_B, TestEnum.OPTION_C),
        )

      assertThat(setting.options).contains(setting.default)
    }
  }

  @Nested
  @DisplayName("Visibility semantics")
  inner class VisibilityTests {

    @Test
    fun `visibleWhen null means always visible`() {
      val setting =
        SettingDefinition.BooleanSetting(
          key = "k",
          label = "l",
          default = true,
          visibleWhen = null,
        )

      // null-means-visible convention: visibleWhen?.invoke() != false is true when null
      assertThat(setting.visibleWhen?.invoke(emptyMap()) != false).isTrue()
    }

    @Test
    fun `visibleWhen predicate evaluates correctly`() {
      val setting =
        SettingDefinition.BooleanSetting(
          key = "k",
          label = "l",
          default = true,
          visibleWhen = { it["enabled"] == true },
        )

      assertThat(setting.visibleWhen!!.invoke(mapOf("enabled" to true))).isTrue()
      assertThat(setting.visibleWhen!!.invoke(mapOf("enabled" to false))).isFalse()
    }

    @Test
    fun `hidden setting is always skipped`() {
      val setting =
        SettingDefinition.BooleanSetting(
          key = "k",
          label = "l",
          default = true,
          hidden = true,
        )

      assertThat(setting.hidden).isTrue()
    }
  }

  @Nested
  @DisplayName("Gated inheritance")
  inner class GatedTests {

    @Test
    fun `BooleanSetting with entitlement set respects isAccessible`() {
      val setting =
        SettingDefinition.BooleanSetting(
          key = "premium",
          label = "Premium Feature",
          default = false,
          requiredAnyEntitlement = setOf("plus"),
        )

      assertThat(setting.isAccessible { it == "plus" }).isTrue()
      assertThat(setting.isAccessible { false }).isFalse()
    }
  }

  @Nested
  @DisplayName("Key uniqueness")
  inner class KeyUniquenessTests {

    @Test
    fun `keys are unique in list`() {
      val settings: List<SettingDefinition<*>> =
        listOf(
          SettingDefinition.BooleanSetting(key = "a", label = "A", default = true),
          SettingDefinition.IntSetting(key = "b", label = "B", default = 0, min = 0, max = 10),
          SettingDefinition.StringSetting(key = "c", label = "C", default = ""),
        )

      assertThat(settings.map { it.key }.distinct().size).isEqualTo(settings.size)
    }

    @Test
    fun `duplicate keys detected`() {
      val settings: List<SettingDefinition<*>> =
        listOf(
          SettingDefinition.BooleanSetting(key = "dup", label = "A", default = true),
          SettingDefinition.BooleanSetting(key = "dup", label = "B", default = false),
          SettingDefinition.StringSetting(key = "c", label = "C", default = ""),
        )

      assertThat(settings.map { it.key }.distinct().size).isLessThan(settings.size)
    }
  }

  @Nested
  @DisplayName("IntSetting.getEffectivePresets")
  inner class GetEffectivePresetsTests {

    @Test
    fun `presetsWhen overrides static presets`() {
      val setting =
        SettingDefinition.IntSetting(
          key = "k",
          label = "l",
          default = 1,
          min = 0,
          max = 10,
          presets = listOf(5, 10),
          presetsWhen = { listOf(1, 2, 3) },
        )

      val result = setting.getEffectivePresets(emptyMap())

      assertThat(result).containsExactly(1, 2, 3).inOrder()
    }

    @Test
    fun `falls back to static presets when presetsWhen is null`() {
      val setting =
        SettingDefinition.IntSetting(
          key = "k",
          label = "l",
          default = 5,
          min = 0,
          max = 10,
          presets = listOf(5, 10),
          presetsWhen = null,
        )

      val result = setting.getEffectivePresets(emptyMap())

      assertThat(result).containsExactly(5, 10).inOrder()
    }

    @Test
    fun `falls back to static when presetsWhen returns null`() {
      val setting =
        SettingDefinition.IntSetting(
          key = "k",
          label = "l",
          default = 5,
          min = 0,
          max = 10,
          presets = listOf(5, 10),
          presetsWhen = { null },
        )

      val result = setting.getEffectivePresets(emptyMap())

      assertThat(result).containsExactly(5, 10).inOrder()
    }
  }
}
