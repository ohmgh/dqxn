package app.dqxn.android.data.layout

import app.dqxn.android.sdk.contracts.widget.WidgetStyle
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class FallbackLayoutTest {

  @Test
  fun `FALLBACK_WIDGET typeId is essentials clock`() {
    assertThat(FallbackLayout.FALLBACK_WIDGET.typeId).isEqualTo("essentials:clock")
  }

  @Test
  fun `FALLBACK_WIDGET position is centered at col 10 row 5`() {
    assertThat(FallbackLayout.FALLBACK_WIDGET.position).isEqualTo(GridPosition(col = 10, row = 5))
  }

  @Test
  fun `FALLBACK_WIDGET size is 10x9 grid units`() {
    assertThat(FallbackLayout.FALLBACK_WIDGET.size)
      .isEqualTo(GridSize(widthUnits = 10, heightUnits = 9))
  }

  @Test
  fun `FALLBACK_WIDGET uses default style`() {
    assertThat(FallbackLayout.FALLBACK_WIDGET.style).isEqualTo(WidgetStyle.Default)
  }

  @Test
  fun `createFallbackProfile returns profile with exactly one widget`() {
    val profile = FallbackLayout.createFallbackProfile()
    assertThat(profile.widgetsList).hasSize(1)
    assertThat(profile.widgetsList[0].type).isEqualTo("essentials:clock")
  }

  @Test
  fun `createFallbackProfile has default profile id and name`() {
    val profile = FallbackLayout.createFallbackProfile()
    assertThat(profile.profileId).isEqualTo("default")
    assertThat(profile.displayName).isEqualTo("Home")
    assertThat(profile.sortOrder).isEqualTo(0)
  }

  @Test
  fun `createFallbackStore has one profile and correct active id`() {
    val store = FallbackLayout.createFallbackStore()
    assertThat(store.profilesCount).isEqualTo(1)
    assertThat(store.activeProfileId).isEqualTo("default")
    assertThat(store.schemaVersion).isEqualTo(LayoutMigration.CURRENT_VERSION)
  }

  @Test
  fun `fallback layout is a code-level constant with no IO dependency`() {
    // This test verifies that FALLBACK_WIDGET is accessible without any file I/O,
    // JSON parsing, or asset loading — it is a compile-time constant.
    val widget = FallbackLayout.FALLBACK_WIDGET
    assertThat(widget.instanceId).isEqualTo("fallback-clock")
    assertThat(widget.settings).isEmpty()
    assertThat(widget.dataSourceBindings).isEmpty()
  }
}
