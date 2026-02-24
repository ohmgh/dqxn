package app.dqxn.android.core.design.token

import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class DashboardSpacingTest {

  @Nested
  @DisplayName("T-shirt sizes")
  inner class TShirtSizes {

    @Test
    fun `SpaceXXS is 4dp`() {
      assertThat(DashboardSpacing.SpaceXXS).isEqualTo(4.dp)
    }

    @Test
    fun `SpaceXS is 8dp`() {
      assertThat(DashboardSpacing.SpaceXS).isEqualTo(8.dp)
    }

    @Test
    fun `SpaceS is 12dp`() {
      assertThat(DashboardSpacing.SpaceS).isEqualTo(12.dp)
    }

    @Test
    fun `SpaceM is 16dp`() {
      assertThat(DashboardSpacing.SpaceM).isEqualTo(16.dp)
    }

    @Test
    fun `SpaceL is 24dp`() {
      assertThat(DashboardSpacing.SpaceL).isEqualTo(24.dp)
    }

    @Test
    fun `SpaceXL is 32dp`() {
      assertThat(DashboardSpacing.SpaceXL).isEqualTo(32.dp)
    }

    @Test
    fun `SpaceXXL is 48dp`() {
      assertThat(DashboardSpacing.SpaceXXL).isEqualTo(48.dp)
    }
  }

  @Nested
  @DisplayName("Semantic aliases")
  inner class SemanticAliases {

    @Test
    fun `ScreenEdgePadding is 16dp`() {
      assertThat(DashboardSpacing.ScreenEdgePadding).isEqualTo(16.dp)
    }

    @Test
    fun `SectionGap is 16dp`() {
      assertThat(DashboardSpacing.SectionGap).isEqualTo(16.dp)
    }

    @Test
    fun `ItemGap is 12dp`() {
      assertThat(DashboardSpacing.ItemGap).isEqualTo(12.dp)
    }

    @Test
    fun `InGroupGap is 8dp`() {
      assertThat(DashboardSpacing.InGroupGap).isEqualTo(8.dp)
    }

    @Test
    fun `ButtonGap is 8dp`() {
      assertThat(DashboardSpacing.ButtonGap).isEqualTo(8.dp)
    }

    @Test
    fun `IconTextGap is 8dp`() {
      assertThat(DashboardSpacing.IconTextGap).isEqualTo(8.dp)
    }

    @Test
    fun `LabelInputGap is 8dp`() {
      assertThat(DashboardSpacing.LabelInputGap).isEqualTo(8.dp)
    }

    @Test
    fun `CardInternalPadding is 16dp`() {
      assertThat(DashboardSpacing.CardInternalPadding).isEqualTo(16.dp)
    }

    @Test
    fun `NestedIndent is 16dp`() {
      assertThat(DashboardSpacing.NestedIndent).isEqualTo(16.dp)
    }

    @Test
    fun `MinTouchTarget is 48dp`() {
      assertThat(DashboardSpacing.MinTouchTarget).isEqualTo(48.dp)
    }

    @Test
    fun `all semantic aliases are non-zero`() {
      val aliases =
        listOf(
          DashboardSpacing.ScreenEdgePadding,
          DashboardSpacing.SectionGap,
          DashboardSpacing.ItemGap,
          DashboardSpacing.InGroupGap,
          DashboardSpacing.ButtonGap,
          DashboardSpacing.IconTextGap,
          DashboardSpacing.LabelInputGap,
          DashboardSpacing.CardInternalPadding,
          DashboardSpacing.NestedIndent,
          DashboardSpacing.MinTouchTarget,
        )
      aliases.forEach { assertThat(it.value).isGreaterThan(0f) }
    }
  }
}
