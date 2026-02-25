package app.dqxn.android.data.preset

import app.dqxn.android.data.layout.FallbackLayout
import app.dqxn.android.sdk.observability.log.NoOpLogger
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import java.io.ByteArrayInputStream
import java.util.TimeZone
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class PresetLoaderTest {

  private val originalTimeZone = TimeZone.getDefault()

  @AfterEach
  fun restoreTimezone() {
    TimeZone.setDefault(originalTimeZone)
  }

  // ---------------------------------------------------------------------------
  // Region detection
  // ---------------------------------------------------------------------------

  @Test
  fun `detectRegion America_New_York returns US`() {
    TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
    assertThat(FallbackRegionDetector.detectRegion()).isEqualTo("US")
  }

  @Test
  fun `detectRegion Asia_Singapore returns SG`() {
    TimeZone.setDefault(TimeZone.getTimeZone("Asia/Singapore"))
    assertThat(FallbackRegionDetector.detectRegion()).isEqualTo("SG")
  }

  @Test
  fun `detectRegion Asia_Tokyo returns JP`() {
    TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
    assertThat(FallbackRegionDetector.detectRegion()).isEqualTo("JP")
  }

  @Test
  fun `detectRegion Europe_London returns GB`() {
    TimeZone.setDefault(TimeZone.getTimeZone("Europe/London"))
    assertThat(FallbackRegionDetector.detectRegion()).isEqualTo("GB")
  }

  @Test
  fun `detectRegion Europe_Berlin returns EU`() {
    TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"))
    assertThat(FallbackRegionDetector.detectRegion()).isEqualTo("EU")
  }

  @Test
  fun `detectRegion unknown timezone returns DEFAULT`() {
    TimeZone.setDefault(TimeZone.getTimeZone("Africa/Nairobi"))
    assertThat(FallbackRegionDetector.detectRegion()).isEqualTo("DEFAULT")
  }

  // ---------------------------------------------------------------------------
  // Preset parsing
  // ---------------------------------------------------------------------------

  @Test
  fun `default preset loads 3 widgets`() {
    val loader = createPresetLoader(DEFAULT_PRESET_JSON)
    val widgets = loader.loadPreset("DEFAULT")
    assertThat(widgets).hasSize(3)
  }

  @Test
  fun `default preset has no GPS-dependent widgets`() {
    val loader = createPresetLoader(DEFAULT_PRESET_JSON)
    val widgets = loader.loadPreset("DEFAULT")
    val typeIds = widgets.map { it.typeId }
    assertThat(typeIds).doesNotContain("essentials:speedometer")
    assertThat(typeIds).doesNotContain("essentials:speed-limit-circle")
    assertThat(typeIds).doesNotContain("essentials:speed-limit-rect")
    assertThat(typeIds).doesNotContain("essentials:compass")
    assertThat(typeIds).doesNotContain("essentials:gforce")
    assertThat(typeIds).doesNotContain("essentials:altimeter")
    assertThat(typeIds).doesNotContain("essentials:solar")
  }

  @Test
  fun `default preset excludes GPS-dependent widgets F11_5`() {
    val loader = createPresetLoader(DEFAULT_PRESET_JSON)
    val widgets = loader.loadPreset("DEFAULT")
    val typeIds = widgets.map { it.typeId }
    // F11.5: default preset must ONLY contain non-GPS widgets
    assertThat(typeIds).doesNotContain("essentials:speedometer")
    assertThat(typeIds).doesNotContain("essentials:speed-limit-circle")
    assertThat(typeIds).doesNotContain("essentials:speed-limit-rect")
    // Verify only non-GPS widgets present
    assertThat(typeIds).containsExactly(
      "essentials:clock",
      "essentials:battery",
      "essentials:date-simple",
    )
  }

  @Test
  fun `free typeIds remapped to essentials`() {
    val json =
      """
      {
        "name": "Legacy",
        "description": "Test",
        "schemaVersion": 1,
        "widgets": [
          { "typeId": "free:clock", "gridX": 0, "gridY": 0, "widthUnits": 4, "heightUnits": 4 }
        ]
      }
      """
        .trimIndent()
    val loader = createPresetLoader(json)
    val widgets = loader.loadPreset("DEFAULT")
    assertThat(widgets[0].typeId).isEqualTo("essentials:clock")
  }

  @Test
  fun `GPS-dependent widgets filtered from preset`() {
    val json =
      """
      {
        "name": "WithGPS",
        "description": "Test",
        "schemaVersion": 1,
        "widgets": [
          { "typeId": "essentials:clock", "gridX": 0, "gridY": 0, "widthUnits": 4, "heightUnits": 4 },
          { "typeId": "essentials:speedometer", "gridX": 5, "gridY": 0, "widthUnits": 4, "heightUnits": 4 },
          { "typeId": "essentials:compass", "gridX": 10, "gridY": 0, "widthUnits": 4, "heightUnits": 4 }
        ]
      }
      """
        .trimIndent()
    val loader = createPresetLoader(json)
    val widgets = loader.loadPreset("DEFAULT")
    assertThat(widgets).hasSize(1)
    assertThat(widgets[0].typeId).isEqualTo("essentials:clock")
  }

  @Test
  fun `preset loading failure returns FallbackLayout`() {
    // Create loader that fails to load any asset
    val context = mockk<android.content.Context>()
    val assetManager = mockk<android.content.res.AssetManager>()
    every { context.assets } returns assetManager
    every { assetManager.open(any()) } throws java.io.FileNotFoundException("Not found")

    val loader = PresetLoader(context, NoOpLogger)
    val widgets = loader.loadPreset("NONEXISTENT")
    assertThat(widgets).hasSize(1)
    assertThat(widgets[0]).isEqualTo(FallbackLayout.FALLBACK_WIDGET)
  }

  @Test
  fun `each widget gets unique UUID instanceId`() {
    val loader = createPresetLoader(DEFAULT_PRESET_JSON)
    val widgets = loader.loadPreset("DEFAULT")
    val instanceIds = widgets.map { it.instanceId }
    assertThat(instanceIds.toSet()).hasSize(instanceIds.size)
  }

  @Test
  fun `parsePreset handles all style fields`() {
    val json =
      """
      {
        "name": "Styled",
        "description": "Test",
        "schemaVersion": 1,
        "widgets": [
          {
            "typeId": "essentials:clock",
            "gridX": 0, "gridY": 0, "widthUnits": 4, "heightUnits": 4,
            "style": {
              "backgroundStyle": "SOLID",
              "opacity": 0.8,
              "showBorder": true,
              "hasGlowEffect": true,
              "cornerRadiusPercent": 25,
              "rimSizePercent": 5
            }
          }
        ]
      }
      """
        .trimIndent()
    val loader = createPresetLoader(json)
    val widgets = loader.loadPreset("DEFAULT")
    val style = widgets[0].style
    assertThat(style.opacity).isEqualTo(0.8f)
    assertThat(style.showBorder).isTrue()
    assertThat(style.hasGlowEffect).isTrue()
    assertThat(style.cornerRadiusPercent).isEqualTo(25)
    assertThat(style.rimSizePercent).isEqualTo(5)
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  /** Create a PresetLoader with a mocked Context that serves the given JSON for default.json. */
  private fun createPresetLoader(defaultJson: String): PresetLoader {
    val context = mockk<android.content.Context>()
    val assetManager = mockk<android.content.res.AssetManager>()
    every { context.assets } returns assetManager
    // Region-specific preset: fail (force fallback to default.json)
    every { assetManager.open(match { it != "presets/default.json" }) } throws
      java.io.FileNotFoundException("Not found")
    every { assetManager.open("presets/default.json") } returns
      ByteArrayInputStream(defaultJson.toByteArray())

    return PresetLoader(context, NoOpLogger)
  }

  private companion object {
    val DEFAULT_PRESET_JSON =
      """
      {
        "name": "Default",
        "description": "Default dashboard layout",
        "schemaVersion": 1,
        "widgets": [
          { "typeId": "essentials:clock", "gridX": 10, "gridY": 3, "widthUnits": 10, "heightUnits": 9 },
          { "typeId": "essentials:battery", "gridX": 22, "gridY": 3, "widthUnits": 6, "heightUnits": 6 },
          { "typeId": "essentials:date-simple", "gridX": 10, "gridY": 13, "widthUnits": 10, "heightUnits": 4 }
        ]
      }
      """
        .trimIndent()
  }
}
