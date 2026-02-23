package app.dqxn.android.sdk.contracts.testing

import app.dqxn.android.sdk.contracts.entitlement.isAccessible
import app.dqxn.android.sdk.contracts.provider.DataSnapshot
import app.dqxn.android.sdk.contracts.settings.SettingDefinition
import app.dqxn.android.sdk.contracts.widget.WidgetData
import app.dqxn.android.sdk.contracts.widget.WidgetRenderer
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

/**
 * Abstract contract test base for [WidgetRenderer] implementations. JUnit4 for future
 * `ComposeContentTestRule` compatibility (Compose UI tests require JUnit4 runner).
 *
 * Provides 14 inherited assertions covering typeId format, rendering safety, accessibility,
 * snapshot compatibility, settings schema, defaults, gating, and metadata.
 *
 * Pack widget tests extend this class and override [createRenderer] and [createTestWidgetData].
 *
 * **Note:** Tests #2 and #3 (render safety) verify instantiability and `accessibilityDescription`
 * rather than actual Compose rendering, because `:sdk:contracts` does not apply the Compose
 * compiler. Real Compose render tests live in pack modules that have the Compose compiler.
 */
public abstract class WidgetRendererContractTest {

  /** Create the renderer under test. Called once in [setUp]. */
  public abstract fun createRenderer(): WidgetRenderer

  /** Create representative [WidgetData] for this renderer's snapshot types. */
  public abstract fun createTestWidgetData(): WidgetData

  private lateinit var renderer: WidgetRenderer

  @Before
  public fun setUp() {
    renderer = createRenderer()
  }

  // --- Test #1: typeId format ---

  @Test
  public fun `typeId follows packId-colon-name format`() {
    assertThat(renderer.typeId).matches("[a-z]+:[a-z][a-z0-9-]+")
  }

  // --- Test #2: render safety with Empty data (non-Compose fallback) ---

  @Test
  public fun `renderer is instantiable and handles Empty data`() {
    // Without Compose compiler, we verify the renderer is instantiable and accessibilityDescription
    // works with Empty data. Real Compose render tests live in pack modules.
    val description = renderer.accessibilityDescription(WidgetData.Empty)
    assertThat(description).isNotNull()
  }

  // --- Test #3: render safety with Unavailable data (non-Compose fallback) ---

  @Test
  public fun `renderer handles Unavailable data`() {
    // Verify accessibilityDescription handles the Unavailable sentinel without throwing.
    val description = renderer.accessibilityDescription(WidgetData.Unavailable)
    assertThat(description).isNotNull()
  }

  // --- Test #4: accessibility non-empty for empty data ---

  @Test
  public fun `accessibility description is non-empty for empty data`() {
    assertThat(renderer.accessibilityDescription(WidgetData.Empty)).isNotEmpty()
  }

  // --- Test #5: accessibility changes with real data ---

  @Test
  public fun `accessibility description changes with real data`() {
    val testData = createTestWidgetData()
    val emptyDesc = renderer.accessibilityDescription(WidgetData.Empty)
    val dataDesc = renderer.accessibilityDescription(testData)
    assertThat(emptyDesc).isNotEqualTo(dataDesc)
  }

  // --- Test #6: compatibleSnapshots are DataSnapshot subtypes ---

  @Test
  public fun `compatibleSnapshots entries are DataSnapshot subtypes`() {
    for (klass in renderer.compatibleSnapshots) {
      assertThat(DataSnapshot::class.java.isAssignableFrom(klass.java)).isTrue()
    }
  }

  // --- Test #7: settingsSchema keys are unique ---

  @Test
  public fun `settingsSchema keys are unique`() {
    val keys = renderer.settingsSchema.map { it.key }
    assertThat(keys).containsNoDuplicates()
  }

  // --- Test #8: settingsSchema defaults valid for type constraints ---

  @Test
  public fun `settingsSchema defaults are valid for type constraints`() {
    for (setting in renderer.settingsSchema) {
      when (setting) {
        is SettingDefinition.IntSetting -> {
          assertThat(setting.default).isIn(setting.min..setting.max)
        }
        is SettingDefinition.FloatSetting -> {
          assertThat(setting.default).isAtLeast(setting.min)
          assertThat(setting.default).isAtMost(setting.max)
        }
        is SettingDefinition.EnumSetting<*> -> {
          assertThat(setting.options).contains(setting.default)
        }
        is SettingDefinition.StringSetting -> {
          setting.maxLength?.let { maxLen -> assertThat(setting.default.length).isAtMost(maxLen) }
        }
        else -> {
          // BooleanSetting, TimezoneSetting, DateFormatSetting, UriSetting, AppPickerSetting,
          // SoundPickerSetting, InstructionSetting, InfoSetting — no range constraints
        }
      }
    }
  }

  // --- Test #9: SKIP — jqwik property test in WidgetRendererPropertyTest (JUnit5) ---

  // --- Test #10: getDefaults returns positive dimensions ---

  @Test
  public fun `getDefaults returns positive dimensions`() {
    val defaults = renderer.getDefaults(testWidgetContext())
    assertThat(defaults.widthUnits).isGreaterThan(0)
    assertThat(defaults.heightUnits).isGreaterThan(0)
  }

  // --- Test #11: getDefaults respects aspect ratio ---

  @Test
  public fun `getDefaults respects aspect ratio if declared`() {
    val defaults = renderer.getDefaults(testWidgetContext())
    if (renderer.aspectRatio != null) {
      val actualRatio = defaults.widthUnits.toFloat() / defaults.heightUnits.toFloat()
      assertThat(actualRatio).isWithin(0.5f).of(renderer.aspectRatio!!)
    }
  }

  // --- Test #12: gating defaults to free when null ---

  @Test
  public fun `gating defaults to free when requiredAnyEntitlement is null`() {
    if (renderer.requiredAnyEntitlement == null) {
      assertThat(renderer.isAccessible { false }).isTrue()
    }
  }

  // --- Test #13: displayName is non-blank ---

  @Test
  public fun `displayName is non-blank`() {
    assertThat(renderer.displayName).isNotEmpty()
    assertThat(renderer.displayName.isBlank()).isFalse()
  }

  // --- Test #14: description is non-blank ---

  @Test
  public fun `description is non-blank`() {
    assertThat(renderer.description).isNotEmpty()
    assertThat(renderer.description.isBlank()).isFalse()
  }
}
