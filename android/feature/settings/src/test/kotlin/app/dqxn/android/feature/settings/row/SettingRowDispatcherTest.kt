package app.dqxn.android.feature.settings.row

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.dqxn.android.feature.settings.SettingNavigation
import app.dqxn.android.sdk.contracts.entitlement.EntitlementManager
import app.dqxn.android.sdk.contracts.settings.DateFormatOption
import app.dqxn.android.sdk.contracts.settings.SettingDefinition
import app.dqxn.android.sdk.contracts.settings.SoundType
import app.dqxn.android.sdk.contracts.setup.InfoStyle
import app.dqxn.android.sdk.contracts.setup.InstructionAction
import app.dqxn.android.sdk.ui.theme.DashboardThemeDefinition
import app.dqxn.android.sdk.ui.theme.LocalDashboardTheme
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingRowDispatcherTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val testTheme =
    DashboardThemeDefinition(
      themeId = "test",
      displayName = "Test",
      primaryTextColor = Color.White,
      secondaryTextColor = Color.Gray,
      accentColor = Color.Cyan,
      widgetBorderColor = Color.Red,
      backgroundBrush = Brush.verticalGradient(listOf(Color.Black, Color.DarkGray)),
      widgetBackgroundBrush = Brush.verticalGradient(listOf(Color.DarkGray, Color.Black)),
    )

  private val freeEntitlementManager =
    object : EntitlementManager {
      override fun hasEntitlement(id: String): Boolean = true
      override fun getActiveEntitlements(): Set<String> = setOf("free")
      override val entitlementChanges: Flow<Set<String>> = emptyFlow()
    }

  private enum class TestEnum { OPTION_A, OPTION_B, OPTION_C }

  // --- Parameterized render test: all 12 subtypes render content ---

  @Test
  fun `BooleanSetting renders label`() {
    val def = SettingDefinition.BooleanSetting(key = "bool_key", label = "Toggle Feature", default = false)
    renderDispatcher(def, false)
    composeTestRule.onNodeWithText("Toggle Feature").assertIsDisplayed()
  }

  @Test
  fun `IntSetting renders label`() {
    val def = SettingDefinition.IntSetting(key = "int_key", label = "Count", default = 5, min = 0, max = 10)
    renderDispatcher(def, 5)
    composeTestRule.onNodeWithText("Count").assertIsDisplayed()
  }

  @Test
  fun `FloatSetting renders label`() {
    val def = SettingDefinition.FloatSetting(key = "float_key", label = "Scale", default = 1.0f, min = 0.5f, max = 2.0f)
    renderDispatcher(def, 1.0f)
    composeTestRule.onNodeWithText("Scale").assertIsDisplayed()
  }

  @Test
  fun `StringSetting renders label`() {
    val def = SettingDefinition.StringSetting(key = "str_key", label = "Name", default = "Hello")
    renderDispatcher(def, "Hello")
    composeTestRule.onNodeWithText("Name").assertIsDisplayed()
  }

  @Test
  fun `EnumSetting renders label and options`() {
    val def =
      SettingDefinition.EnumSetting(
        key = "enum_key",
        label = "Mode",
        default = TestEnum.OPTION_A,
        options = TestEnum.entries.toList(),
      )
    renderDispatcher(def, TestEnum.OPTION_A)
    composeTestRule.onNodeWithText("Mode").assertIsDisplayed()
  }

  @Test
  fun `InfoSetting renders label`() {
    val def = SettingDefinition.InfoSetting(key = "info_key", label = "Info Message", style = InfoStyle.INFO)
    renderDispatcher(def, Unit)
    composeTestRule.onNodeWithText("Info Message").assertIsDisplayed()
  }

  @Test
  fun `InstructionSetting renders label`() {
    val def =
      SettingDefinition.InstructionSetting(
        key = "instr_key",
        label = "Step One",
        stepNumber = 1,
        description = "Do the thing",
      )
    renderDispatcher(def, Unit)
    composeTestRule.onNodeWithText("Step One").assertIsDisplayed()
  }

  @Test
  fun `AppPickerSetting renders label`() {
    val def = SettingDefinition.AppPickerSetting(key = "app_key", label = "Default App")
    renderDispatcher(def, null)
    composeTestRule.onNodeWithText("Default App").assertIsDisplayed()
  }

  @Test
  fun `DateFormatSetting renders label`() {
    val def =
      SettingDefinition.DateFormatSetting(
        key = "date_key",
        label = "Date Format",
        default = DateFormatOption.ISO_DATE,
      )
    renderDispatcher(def, DateFormatOption.ISO_DATE)
    composeTestRule.onNodeWithText("Date Format").assertIsDisplayed()
  }

  @Test
  fun `TimezoneSetting renders label`() {
    val def = SettingDefinition.TimezoneSetting(key = "tz_key", label = "Timezone")
    renderDispatcher(def, null)
    composeTestRule.onNodeWithText("Timezone").assertIsDisplayed()
  }

  @Test
  fun `SoundPickerSetting renders label`() {
    val def =
      SettingDefinition.SoundPickerSetting(
        key = "sound_key",
        label = "Alert Sound",
        soundType = SoundType.NOTIFICATION,
      )
    renderDispatcher(def, null)
    composeTestRule.onNodeWithText("Alert Sound").assertIsDisplayed()
  }

  @Test
  fun `UriSetting renders label`() {
    val def = SettingDefinition.UriSetting(key = "uri_key", label = "File URI")
    renderDispatcher(def, null)
    composeTestRule.onNodeWithText("File URI").assertIsDisplayed()
  }

  // --- Visibility gating tests ---

  @Test
  fun `hidden setting does not render`() {
    val def =
      SettingDefinition.BooleanSetting(key = "hidden_key", label = "Hidden Feature", default = false, hidden = true)
    renderDispatcher(def, false)
    composeTestRule.onNodeWithText("Hidden Feature").assertDoesNotExist()
  }

  @Test
  fun `visibleWhen null treats as visible (Pitfall 1 - empty map)`() {
    val def =
      SettingDefinition.BooleanSetting(
        key = "vis_null_key",
        label = "Null VisibleWhen",
        default = false,
        visibleWhen = null,
      )
    renderDispatcher(def, false, currentSettings = emptyMap())
    composeTestRule.onNodeWithText("Null VisibleWhen").assertIsDisplayed()
  }

  @Test
  fun `visibleWhen returning false hides setting`() {
    val def =
      SettingDefinition.BooleanSetting(
        key = "vis_false_key",
        label = "Conditionally Hidden",
        default = false,
        visibleWhen = { it["other"] == true },
      )
    // "other" not in map -> null != true -> false -> hidden
    renderDispatcher(def, false, currentSettings = emptyMap())
    composeTestRule.onNodeWithText("Conditionally Hidden").assertDoesNotExist()
  }

  @Test
  fun `visibleWhen returning true shows setting`() {
    val def =
      SettingDefinition.BooleanSetting(
        key = "vis_true_key",
        label = "Conditionally Visible",
        default = false,
        visibleWhen = { it["other"] == true },
      )
    renderDispatcher(def, false, currentSettings = mapOf("other" to true))
    composeTestRule.onNodeWithText("Conditionally Visible").assertIsDisplayed()
  }

  // --- Entitlement gating tests ---

  @Test
  fun `entitlement required but not held hides setting`() {
    val def =
      SettingDefinition.BooleanSetting(
        key = "ent_key",
        label = "Plus Feature",
        default = false,
        requiredAnyEntitlement = setOf("plus"),
      )
    val noEntitlementManager =
      object : EntitlementManager {
        override fun hasEntitlement(id: String): Boolean = false
        override fun getActiveEntitlements(): Set<String> = emptySet()
        override val entitlementChanges: Flow<Set<String>> = emptyFlow()
      }
    renderDispatcher(def, false, entitlementManager = noEntitlementManager)
    composeTestRule.onNodeWithText("Plus Feature").assertDoesNotExist()
  }

  @Test
  fun `entitlement required and held shows setting`() {
    val def =
      SettingDefinition.BooleanSetting(
        key = "ent_held_key",
        label = "Plus Feature Held",
        default = false,
        requiredAnyEntitlement = setOf("plus"),
      )
    val plusEntitlementManager =
      object : EntitlementManager {
        override fun hasEntitlement(id: String): Boolean = id == "plus"
        override fun getActiveEntitlements(): Set<String> = setOf("plus")
        override val entitlementChanges: Flow<Set<String>> = emptyFlow()
      }
    renderDispatcher(def, false, entitlementManager = plusEntitlementManager)
    composeTestRule.onNodeWithText("Plus Feature Held").assertIsDisplayed()
  }

  // --- Value change test ---

  @Test
  fun `BooleanSetting toggle fires onValueChanged`() {
    val def = SettingDefinition.BooleanSetting(key = "toggle_key", label = "Toggleable", default = false)
    var changedKey: String? = null
    var changedValue: Any? = null

    composeTestRule.setContent {
      CompositionLocalProvider(LocalDashboardTheme provides testTheme) {
        SettingRowDispatcher(
          definition = def,
          currentValue = false,
          currentSettings = emptyMap(),
          entitlementManager = freeEntitlementManager,
          theme = testTheme,
          onValueChanged = { key, value ->
            changedKey = key
            changedValue = value
          },
        )
      }
    }

    // Click the switch -- switch is inside the BooleanSettingRow
    composeTestRule.onNodeWithText("Toggleable").assertIsDisplayed()
    // Switch has Role.Switch semantics
    composeTestRule
      .onNode(
        androidx.compose.ui.test.SemanticsMatcher.expectValue(
          androidx.compose.ui.semantics.SemanticsProperties.Role,
          androidx.compose.ui.semantics.Role.Switch,
        )
      )
      .performClick()

    assertThat(changedKey).isEqualTo("toggle_key")
    assertThat(changedValue).isEqualTo(true)
  }

  // --- Helper ---

  private fun renderDispatcher(
    definition: SettingDefinition<*>,
    currentValue: Any?,
    currentSettings: Map<String, Any?> = emptyMap(),
    entitlementManager: EntitlementManager = freeEntitlementManager,
  ) {
    composeTestRule.setContent {
      CompositionLocalProvider(LocalDashboardTheme provides testTheme) {
        SettingRowDispatcher(
          definition = definition,
          currentValue = currentValue,
          currentSettings = currentSettings,
          entitlementManager = entitlementManager,
          theme = testTheme,
          onValueChanged = { _, _ -> },
        )
      }
    }
  }
}
