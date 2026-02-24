package app.dqxn.android.pack.essentials.widgets

import app.dqxn.android.pack.essentials.snapshots.OrientationSnapshot
import app.dqxn.android.pack.essentials.widgets.compass.CompassRenderer
import app.dqxn.android.sdk.contracts.testing.WidgetRendererContractTest
import app.dqxn.android.sdk.contracts.widget.WidgetData
import app.dqxn.android.sdk.contracts.widget.WidgetRenderer
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CompassRendererTest : WidgetRendererContractTest() {

  override fun createRenderer(): WidgetRenderer = CompassRenderer()

  override fun createTestWidgetData(): WidgetData =
    WidgetData.Empty.withSlot(
      OrientationSnapshot::class,
      OrientationSnapshot(bearing = 90f, pitch = 5f, roll = -3f, timestamp = 1L),
    )

  @Test
  fun `getCardinalDirection returns N for 0 degrees`() {
    assertThat(CompassRenderer.getCardinalDirection(0f)).isEqualTo("N")
  }

  @Test
  fun `getCardinalDirection returns E for 90 degrees`() {
    assertThat(CompassRenderer.getCardinalDirection(90f)).isEqualTo("E")
  }

  @Test
  fun `getCardinalDirection returns S for 180 degrees`() {
    assertThat(CompassRenderer.getCardinalDirection(180f)).isEqualTo("S")
  }

  @Test
  fun `getCardinalDirection returns W for 270 degrees`() {
    assertThat(CompassRenderer.getCardinalDirection(270f)).isEqualTo("W")
  }

  @Test
  fun `getCardinalDirection returns NE for 45 degrees`() {
    assertThat(CompassRenderer.getCardinalDirection(45f)).isEqualTo("NE")
  }

  @Test
  fun `getCardinalDirection returns SE for 135 degrees`() {
    assertThat(CompassRenderer.getCardinalDirection(135f)).isEqualTo("SE")
  }

  @Test
  fun `getCardinalDirection returns SW for 225 degrees`() {
    assertThat(CompassRenderer.getCardinalDirection(225f)).isEqualTo("SW")
  }

  @Test
  fun `getCardinalDirection returns NW for 315 degrees`() {
    assertThat(CompassRenderer.getCardinalDirection(315f)).isEqualTo("NW")
  }

  @Test
  fun `getCardinalDirection handles boundary at 337_5 degrees`() {
    assertThat(CompassRenderer.getCardinalDirection(337.5f)).isEqualTo("N")
  }

  @Test
  fun `getCardinalDirection handles negative bearing`() {
    assertThat(CompassRenderer.getCardinalDirection(-90f)).isEqualTo("W")
  }

  @Test
  fun `getCardinalDirection handles bearing over 360`() {
    assertThat(CompassRenderer.getCardinalDirection(450f)).isEqualTo("E")
  }

  @Test
  fun `aspectRatio is 1f`() {
    val renderer = CompassRenderer()
    assertThat(renderer.aspectRatio).isEqualTo(1f)
  }

  @Test
  fun `accessibility description includes bearing degrees`() {
    val renderer = CompassRenderer()
    val data =
      WidgetData.Empty.withSlot(
        OrientationSnapshot::class,
        OrientationSnapshot(bearing = 45f, pitch = 0f, roll = 0f, timestamp = 1L),
      )
    val desc = renderer.accessibilityDescription(data)
    assertThat(desc).contains("45")
    assertThat(desc).contains("NE")
  }

  @Test
  fun `accessibility description for empty data says no heading`() {
    val renderer = CompassRenderer()
    val desc = renderer.accessibilityDescription(WidgetData.Empty)
    assertThat(desc).contains("no heading")
  }
}
