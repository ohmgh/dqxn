package app.dqxn.android.pack.essentials.widgets

import app.dqxn.android.pack.essentials.widgets.shortcuts.ShortcutsRenderer
import app.dqxn.android.sdk.contracts.settings.SettingDefinition
import app.dqxn.android.sdk.contracts.testing.WidgetRendererContractTest
import app.dqxn.android.sdk.contracts.widget.WidgetData
import app.dqxn.android.sdk.contracts.widget.WidgetRenderer
import com.google.common.truth.Truth.assertThat
import kotlinx.collections.immutable.persistentMapOf
import org.junit.Test

class ShortcutsRendererTest : WidgetRendererContractTest() {

  override fun createRenderer(): WidgetRenderer = ShortcutsRenderer()

  override fun createTestWidgetData(): WidgetData =
    WidgetData(snapshots = persistentMapOf(), timestamp = 1L)

  @Test
  fun `accessibility description with no package says tap to configure`() {
    val renderer = ShortcutsRenderer()
    val desc = renderer.accessibilityDescription(WidgetData.Empty)
    assertThat(desc).contains("tap to configure")
  }

  @Test
  fun `accessibility description with active data says ready`() {
    val renderer = ShortcutsRenderer()
    val activeData = WidgetData(snapshots = persistentMapOf(), timestamp = 1L)
    val desc = renderer.accessibilityDescription(activeData)
    assertThat(desc).contains("ready")
  }

  @Test
  fun `compatibleSnapshots is empty set`() {
    val renderer = ShortcutsRenderer()
    assertThat(renderer.compatibleSnapshots).isEmpty()
  }

  @Test
  fun `settings schema includes AppPickerSetting with key packageName`() {
    val renderer = ShortcutsRenderer()
    val appPicker = renderer.settingsSchema.filterIsInstance<SettingDefinition.AppPickerSetting>()
    assertThat(appPicker).hasSize(1)
    assertThat(appPicker.first().key).isEqualTo("packageName")
  }

  @Test
  fun `settings schema includes StringSetting with key displayName`() {
    val renderer = ShortcutsRenderer()
    val stringSetting =
      renderer.settingsSchema.filterIsInstance<SettingDefinition.StringSetting>().filter {
        it.key == "displayName"
      }
    assertThat(stringSetting).hasSize(1)
  }

  @Test
  fun `supportsTap is true`() {
    val renderer = ShortcutsRenderer()
    assertThat(renderer.supportsTap).isTrue()
  }

  @Test
  fun `onTap returns false when packageName is null`() {
    val renderer = ShortcutsRenderer()
    val result = renderer.onTap("widget-1", persistentMapOf())
    assertThat(result).isFalse()
  }

  @Test
  fun `onTap returns true when packageName is set`() {
    val renderer = ShortcutsRenderer()
    val settings = persistentMapOf<String, Any>("packageName" to "com.example.app")
    val result = renderer.onTap("widget-1", settings)
    assertThat(result).isTrue()
  }
}
