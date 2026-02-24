package app.dqxn.android.pack.essentials.integration

import app.dqxn.android.pack.essentials.widgets.ambientlight.AmbientLightRenderer
import app.dqxn.android.pack.essentials.widgets.battery.BatteryRenderer
import app.dqxn.android.pack.essentials.widgets.clock.ClockAnalogRenderer
import app.dqxn.android.pack.essentials.widgets.clock.ClockDigitalRenderer
import app.dqxn.android.pack.essentials.widgets.compass.CompassRenderer
import app.dqxn.android.pack.essentials.widgets.date.DateGridRenderer
import app.dqxn.android.pack.essentials.widgets.date.DateSimpleRenderer
import app.dqxn.android.pack.essentials.widgets.date.DateStackRenderer
import app.dqxn.android.pack.essentials.widgets.shortcuts.ShortcutsRenderer
import app.dqxn.android.pack.essentials.widgets.solar.SolarRenderer
import app.dqxn.android.pack.essentials.widgets.speedlimit.SpeedLimitCircleRenderer
import app.dqxn.android.pack.essentials.widgets.speedlimit.SpeedLimitRectRenderer
import app.dqxn.android.pack.essentials.widgets.speedometer.SpeedometerRenderer
import app.dqxn.android.sdk.contracts.widget.WidgetRenderer
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Integration verification test for the essentials pack. Instantiates all 13 widget renderers
 * and verifies their inventory, typeId format, and metadata consistency.
 *
 * This test serves as the Phase 8 compile-time gate: if any renderer constructor fails,
 * typeId is malformed, or the pack inventory changes unexpectedly, these assertions catch it.
 *
 * Note: KSP-generated EssentialsGeneratedManifest is not tested here because the debugUnitTest
 * KSP variant produces an empty manifest (test sources contain no @DashboardWidget annotations).
 * The KSP manifest is verified indirectly -- if KSP processing fails, the main compilation fails
 * (compileDebugKotlin gate).
 */
class PackCompileVerificationTest {

  private val allRenderers: List<WidgetRenderer> =
    listOf(
      SpeedometerRenderer(),
      ClockDigitalRenderer(),
      ClockAnalogRenderer(),
      DateSimpleRenderer(),
      DateStackRenderer(),
      DateGridRenderer(),
      CompassRenderer(),
      BatteryRenderer(),
      SpeedLimitCircleRenderer(),
      SpeedLimitRectRenderer(),
      ShortcutsRenderer(),
      SolarRenderer(),
      AmbientLightRenderer(),
    )

  @Test
  fun `pack contains exactly 13 widget renderers`() {
    assertThat(allRenderers).hasSize(13)
  }

  @Test
  fun `widget typeIds match expected set`() {
    val expected =
      setOf(
        "essentials:speedometer",
        "essentials:clock",
        "essentials:clock-analog",
        "essentials:date-simple",
        "essentials:date-stack",
        "essentials:date-grid",
        "essentials:compass",
        "essentials:battery",
        "essentials:speedlimit-circle",
        "essentials:speedlimit-rect",
        "essentials:shortcuts",
        "essentials:solar",
        "essentials:ambient-light",
      )
    val actual = allRenderers.map { it.typeId }.toSet()
    assertThat(actual).isEqualTo(expected)
  }

  @Test
  fun `all widget typeIds follow packId colon widget-name format`() {
    for (renderer in allRenderers) {
      assertThat(renderer.typeId).matches("essentials:[a-z][a-z0-9-]*")
    }
  }

  @Test
  fun `all widgets have non-blank displayName`() {
    for (renderer in allRenderers) {
      assertThat(renderer.displayName).isNotEmpty()
    }
  }

  @Test
  fun `all widgets have non-blank description`() {
    for (renderer in allRenderers) {
      assertThat(renderer.description).isNotEmpty()
    }
  }

  @Test
  fun `data-driven widgets have non-empty compatibleSnapshots`() {
    // ShortcutsRenderer is action-only (no data snapshots), so it's excluded
    val dataWidgets = allRenderers.filter { it.typeId != "essentials:shortcuts" }
    for (renderer in dataWidgets) {
      assertThat(renderer.compatibleSnapshots).isNotEmpty()
    }
  }

  @Test
  fun `no duplicate typeIds across renderers`() {
    val typeIds = allRenderers.map { it.typeId }
    assertThat(typeIds).containsNoDuplicates()
  }

  @Test
  fun `fixed-aspect widgets have positive aspectRatio`() {
    // Widgets with null aspectRatio have flexible sizing (no fixed ratio)
    val fixedAspect = allRenderers.filter { it.aspectRatio != null }
    assertThat(fixedAspect).isNotEmpty()
    for (renderer in fixedAspect) {
      assertThat(renderer.aspectRatio).isGreaterThan(0f)
    }
  }

  @Test
  fun `all free-tier widgets have null requiredAnyEntitlement`() {
    for (renderer in allRenderers) {
      assertThat(renderer.requiredAnyEntitlement).isNull()
    }
  }

  @Test
  fun `packId is essentials for all renderers`() {
    for (renderer in allRenderers) {
      assertThat(renderer.typeId).startsWith("essentials:")
    }
  }
}
